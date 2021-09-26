/*
 *   Copyright (c) 2021 Dario Lucia (https://www.dariolucia.eu)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and 
 *   limitations under the License.
 */

package eu.dariolucia.ccsds.cfdp.entity.internal;

import eu.dariolucia.ccsds.cfdp.common.BytesUtil;
import eu.dariolucia.ccsds.cfdp.entity.CfdpTransactionState;
import eu.dariolucia.ccsds.cfdp.entity.CfdpTransactionStatus;
import eu.dariolucia.ccsds.cfdp.entity.CfdpTransmissionMode;
import eu.dariolucia.ccsds.cfdp.entity.FaultDeclaredException;
import eu.dariolucia.ccsds.cfdp.entity.indication.AbandonedIndication;
import eu.dariolucia.ccsds.cfdp.entity.indication.FaultIndication;
import eu.dariolucia.ccsds.cfdp.entity.indication.ReportIndication;
import eu.dariolucia.ccsds.cfdp.entity.indication.TransactionDisposedIndication;
import eu.dariolucia.ccsds.cfdp.mib.FaultHandlerStrategy;
import eu.dariolucia.ccsds.cfdp.mib.RemoteEntityConfigurationInformation;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.AckPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.ConditionCode;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.EntityIdTLV;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayer;
import eu.dariolucia.ccsds.cfdp.ut.UtLayerException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import static eu.dariolucia.ccsds.cfdp.mib.FaultHandlerStrategy.Action.*;

public abstract class CfdpTransaction {

    private static final Logger LOG = Logger.getLogger(CfdpTransaction.class.getName());

    private final long transactionId;
    private final CfdpEntity entity;
    private final int entityIdLength;
    private final RemoteEntityConfigurationInformation remoteDestination;
    private final IUtLayer transmissionLayer;

    private final ExecutorService confiner;

    private final Map<ConditionCode, FaultHandlerStrategy.Action> faultHandlers = new EnumMap<>(ConditionCode.class);

    private final Timer timer;
    // Timer for the transaction inactivity limit
    private TimerTask transactionInactivityLimitTimer;

    // Inner status of active transactions
    // This variable is volatile because it is allowed to read its state also by external threads
    private volatile CfdpTransactionState currentState = CfdpTransactionState.RUNNING;

    // Ack timer for Positive Ack Procedure
    private TimerTask ackTimer;
    private int ackTimerCount;

    // Transmission opportunity window state
    private boolean txAllowed;
    // Reception opportunity window state
    private boolean rxAllowed;

    private ConditionCode lastConditionCode = ConditionCode.CC_NOERROR;
    private EntityIdTLV lastFaultEntity = null;


    public CfdpTransaction(long transactionId, CfdpEntity cfdpEntity, long remoteEntityId) {
        this.transactionId = transactionId;
        this.entity = cfdpEntity;
        if(LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, String.format("CFDP Entity [%d]: Creating CFDP transaction %d on entity %d: remote entity %d", getLocalEntityId(), transactionId, cfdpEntity.getLocalEntityId(), remoteEntityId));
        }
        this.remoteDestination = cfdpEntity.getMib().getRemoteEntityById(remoteEntityId);
        this.transmissionLayer = cfdpEntity.getUtLayerByDestinationEntity(remoteEntityId);
        this.confiner = Executors.newFixedThreadPool(1, runnable -> {
            Thread t = new Thread(runnable, String.format("CFDP Entity %s - Transaction [%d] [%d] Handler",  getLocalEntityId(), transactionId, remoteDestination.getRemoteEntityId()));
            t.setDaemon(true);
            return t;
        });
        this.timer = new Timer(String.format("CFDP Entity %s - Transaction [%d] [%d] Timer", getLocalEntityId(), transactionId, remoteDestination.getRemoteEntityId()), true);
        // Compute the entity ID length
        long maxEntityId = Long.max(remoteDestination.getRemoteEntityId(), entity.getMib().getLocalEntity().getLocalEntityId());
        this.entityIdLength = BytesUtil.getEncodingOctetsNb(maxEntityId);
    }

    protected void overrideHandlers(Map<ConditionCode, FaultHandlerStrategy.Action> faultHandlerMap) {
        for(Map.Entry<ConditionCode, FaultHandlerStrategy.Action> e : faultHandlerMap.entrySet()) {
            overrideHandler(e.getKey(), e.getValue());
        }
    }

    protected void overrideHandler(ConditionCode conditionCode, FaultHandlerStrategy.Action toAction) {
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: overriding handler for code %s with %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), conditionCode, toAction));
        }
        this.faultHandlers.put(conditionCode, toAction);
    }

    protected FaultHandlerStrategy.Action getHandlerForFailure(ConditionCode code) {
        FaultHandlerStrategy.Action action = this.faultHandlers.get(code);
        if(action == null) {
            action = NOTICE_OF_CANCELLATION; // If people don't like this default, better fill the MIB
        }
        return action;
    }

    protected void fault(ConditionCode conditionCode, long generatingEntityId) throws FaultDeclaredException {
        // 4.8.3 All faults shall be logged, even if no action is taken.
        if(LOG.isLoggable(Level.SEVERE)) {
            LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: fault with condition code %s generated by entity %d detected", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), conditionCode, generatingEntityId));
        }
        // In many places in the standard, it is made clear that any fault in handling cancelling of transactions shall result
        // in transaction abandonment. Therefore, I check here if the transaction is in the CANCELLED state already and trigger the
        // handleAbandon in that case
        if(isCancelled()) {
            if(LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: fault with condition code %s generated by entity %d when the transaction was already cancelled - move to abandon the transaction", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), conditionCode, generatingEntityId));
            }
            handleAbandon(conditionCode);
            throw new FaultDeclaredException(getTransactionId(), ABANDON, conditionCode, generatingEntityId);
        }
        //
        setLastConditionCode(conditionCode, generatingEntityId);
        // Check the handler
        FaultHandlerStrategy.Action action = getHandlerForFailure(conditionCode);
        // 4.8.2 Invocation of a fault handler shall result in one of the following:
        switch(action) {
            // a) issuance of a Notice of Cancellation
            case NOTICE_OF_CANCELLATION:
                doInternalCancel(conditionCode, generatingEntityId);
                throw new FaultDeclaredException(getTransactionId(), NOTICE_OF_CANCELLATION, conditionCode, generatingEntityId);
            // b) issuance of a Notice of Suspension, but only if the affected transaction was sent in
            //    'acknowledged' transmission mode, or the fault condition was declared at the source
            //     entity of the transaction
            case NOTICE_OF_SUSPENSION:
                if(isAcknowledged() || generatingEntityId == getSourceEntityId()) {
                    handleSuspend();
                }
                throw new FaultDeclaredException(getTransactionId(), NOTICE_OF_SUSPENSION, conditionCode, generatingEntityId);
            // c) no protocol action; that is, the fault condition is ignored (it should be noted that this
            //    may result in unpredictable protocol behavior), and a Fault.indication with
            //    condition code identifying the fault condition is issued to the CFDP user
            case NO_ACTION:
                FaultIndication indication = new FaultIndication(getTransactionId(), conditionCode, getProgress(), createStateObject());
                getEntity().notifyIndication(indication);
                return;
            // d) abandonment of the transaction, resulting in an Abandoned.indication with
            //    condition code identifying the fault condition.
            case ABANDON:
                handleAbandon(conditionCode);
                throw new FaultDeclaredException(getTransactionId(), ABANDON, conditionCode, generatingEntityId);
        }
    }

    protected void startPositiveAckTimer(CfdpPdu pdu) {
        if(getRemoteDestination().getPositiveAckTimerInterval() == -1) {
            // Positive ACK timer N/A
            return;
        }
        if(LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: starting positive ACK timer for PDU %s", getLocalEntityId(), transactionId, getRemoteDestination().getRemoteEntityId(), pdu));
        }
        stopPositiveAckTimer();
        this.ackTimer = new TimerTask() {
            @Override
            public void run() {
                handle(() -> handlePositiveAckTimerElapsed(this, pdu));
            }
        };
        schedule(this.ackTimer, getRemoteDestination().getPositiveAckTimerInterval(), true);
    }

    protected void stopPositiveAckTimer() {
        if(this.ackTimer != null) {
            if(LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: stopping positive ACK timer", getLocalEntityId(), transactionId, getRemoteDestination().getRemoteEntityId()));
            }
            this.ackTimer.cancel();
        }
        this.ackTimer = null;
        this.ackTimerCount = 0;
    }

    protected boolean isAckTimerRunning() {
        return this.ackTimer != null;
    }

    protected void handlePositiveAckTimerElapsed(TimerTask timer, CfdpPdu pdu) {
        // 4.7.1 POSITIVE ACKNOWLEDGEMENT PROCEDURES AT PDU SENDING END
        // If Positive Acknowledgement procedures apply to a PDU,
        // a) upon issuing the PDU, the sending CFDP entity shall start a timer and retain the PDU
        //    for retransmission as necessary
        try {
            if (this.ackTimer == timer) {
                if(LOG.isLoggable(Level.INFO)) {
                    LOG.log(Level.INFO, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: positive ACK timer elapsed, current count %d, limit %d", getLocalEntityId(), transactionId, getRemoteDestination().getRemoteEntityId(), this.ackTimerCount, getRemoteDestination().getPositiveAckTimerExpirationLimit()));
                }
                // c) the sending CFDP entity shall keep a tally of the number of transmission retries
                ++this.ackTimerCount;
                if (this.ackTimerCount == getRemoteDestination().getPositiveAckTimerExpirationLimit()) {
                    // d) if a preset limit is exceeded, the sending CFDP entity shall declare a Positive ACK
                    //    Limit Reached fault
                    this.ackTimer.cancel();
                    this.ackTimer = null;
                    fault(ConditionCode.CC_POS_ACK_LIMIT_REACHED, getLocalEntityId());
                } else {
                    // b) if the Expected Response is not received before expiry of the timer, the sending
                    //    CFDP entity shall reissue the original PDU
                    sendPduOnAckTimerElapsed(pdu);
                }
            }
        } catch (FaultDeclaredException e) {
            // Do not do anything, disposal/stop of the timer is already done outside.
        }
    }

    private void sendPduOnAckTimerElapsed(CfdpPdu pdu) {
        try {
            if(LOG.isLoggable(Level.INFO)) {
                LOG.log(Level.INFO, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: sending again PDU %s on ACK timer elapsed", getLocalEntityId(), transactionId, getRemoteDestination().getRemoteEntityId(), pdu));
            }
            forwardPdu(pdu);
        } catch (UtLayerException e) {
            if (LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: fail on PDU re-transmission upon ACK timer expire: %s ", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), e.getMessage()), e);
            }
        }
    }

    public long getTransactionId() {
        return transactionId;
    }

    protected CfdpEntity getEntity() {
        return entity;
    }

    protected int getEntityIdLength() {
        return entityIdLength;
    }

    public RemoteEntityConfigurationInformation getRemoteDestination() {
        return remoteDestination;
    }

    protected IUtLayer getTransmissionLayer() {
        return transmissionLayer;
    }

    protected void handle(Runnable r) {
        if(!this.confiner.isShutdown()) {
            this.confiner.submit(() -> {
                try {
                    r.run();
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: exception caught: %s", getLocalEntityId(), transactionId, getRemoteDestination().getRemoteEntityId(), e.getMessage()), e);
                }
            });
        }
        // If it is shut down, it means it is disposed
    }

    protected void schedule(TimerTask t, long period, boolean periodic) {
        if(!this.confiner.isShutdown()) { // Use the confiner status as a sentry to detect timer cancel
            if (periodic) {
                this.timer.schedule(t, period, period);
            } else {
                this.timer.schedule(t, period);
            }
        }
    }

    public void dispose() {
        handle(this::handleDispose);
    }

    public boolean isDisposed() {
        return this.confiner.isShutdown();
    }

    protected void handleDispose() {
        if(LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: handling dispose", getLocalEntityId(), transactionId, getRemoteDestination().getRemoteEntityId()));
        }
        // Subclass cleanup
        handlePreDispose();
        // This class cleanup
        stopTransactionInactivityTimer();
        stopPositiveAckTimer();
        // Mark state as COMPLETED if running
        if(getCurrentState() == CfdpTransactionState.RUNNING) {
            this.currentState = CfdpTransactionState.COMPLETED;
        }
        // Raise a final indication, to indicate that the transaction is actually finalised
        getEntity().notifyIndication(new TransactionDisposedIndication(getTransactionId(), createStateObject()));
        this.confiner.shutdownNow();
        this.timer.cancel();

    }

    public void activate() {
        handle(this::handleActivation);
    }

    public void indication(CfdpPdu pdu) {
        handle(() -> handleIndication(pdu));
    }

    public void cancel(ConditionCode conditionCode) {
        handle(() -> doInternalCancel(conditionCode, getLocalEntityId()));
    }

    private void doInternalCancel(ConditionCode conditionCode, long faultEntityId) {
        if(isCancelled()) {
            if(LOG.isLoggable(Level.WARNING)) {
                LOG.log(Level.WARNING, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: transaction already in cancelled state, not repeating procedure (new condition code %s)", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), conditionCode));
            }
            return;
        }
        if(LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: handling cancel with condition code %s and fault entity ID %d", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), conditionCode, faultEntityId));
        }
        handleCancel(conditionCode, faultEntityId);
    }

    public void suspend() {
        handle(() -> {
            // The following condition implies that the status can be frozen, still the suspension is carried out
            if(this.currentState == CfdpTransactionState.RUNNING) {
                if(LOG.isLoggable(Level.INFO)) {
                    LOG.log(Level.INFO, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: handling suspend", getLocalEntityId(), transactionId, getRemoteDestination().getRemoteEntityId()));
                }
                handleSuspend();
            } else {
                LOG.log(Level.WARNING, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: suspension requested but status is %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), this.currentState));
            }
        });
    }

    public void resume() {
        handle(() -> {
            // 4.6.7.1 General
            // Resume procedures apply upon receipt of a Resume.request primitive submitted by the
            // CFDP user. However:
            // a) a Resume.request primitive shall be ignored if it pertains to a transaction that is not
            //    currently suspended
            // b) if the transaction to which a Resume.request primitive pertains is currently not only
            //    suspended but also frozen (as defined in 4.12), then the transaction shall be
            //    considered no longer suspended but the only applicable procedure shall be the
            //    issuance of a Resumed.indication -> handled in the subclasses
            if(isSuspended()) {
                if(LOG.isLoggable(Level.INFO)) {
                    LOG.log(Level.INFO, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: handling resume", getLocalEntityId(), transactionId, getRemoteDestination().getRemoteEntityId()));
                }
                handleResume();
            }
        });
    }

    public void updateTxOpportunity(boolean txAllowed) {
        handle(() -> {
            if(LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: updating TX available: %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), txAllowed));
            }
            boolean wasFrozen = isFrozen();
            this.txAllowed = txAllowed;
            handleOpportunityTransition(wasFrozen, isFrozen());
        });
    }

    public void updateRxOpportunity(boolean rxAllowed) {
        handle(() -> {
            if(LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: updating RX available: %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), rxAllowed));
            }
            boolean wasFrozen = isFrozen();
            this.rxAllowed = rxAllowed;
            handleOpportunityTransition(wasFrozen, isFrozen());
        });
    }

    private void handleOpportunityTransition(boolean wasFrozen, boolean frozen) {
        if (!wasFrozen && frozen) {
            handleFreeze();
        } else if (wasFrozen && !frozen) {
            handleUnfreeze();
        }
    }

    public void report() {
        // This method is the only one that does not strictly use the confinement, because it can do it (no changes are expected)
        if(!confiner.isShutdown()) {
            handle(this::handleReport);
        } else {
            handleReport();
        }
    }

    protected void startTransactionInactivityTimer() {
        if(transactionInactivityLimitTimer != null) {
            return;
        }
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: start transaction inactivity timer", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
        }
        transactionInactivityLimitTimer = new TimerTask() {
            @Override
            public void run() {
                handleTransactionInactivity();
            }
        };
        timer.schedule(transactionInactivityLimitTimer, getRemoteDestination().getTransactionInactivityLimit());
    }

    protected void resetTransactionInactivityTimer() {
        if(transactionInactivityLimitTimer != null) {
            if(LOG.isLoggable(Level.FINEST)) {
                LOG.log(Level.FINEST, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: reset transaction inactivity timer", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
            }
            stopTransactionInactivityTimer();
            startTransactionInactivityTimer();
        }
    }

    protected void stopTransactionInactivityTimer() {
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: stop transaction inactivity timer", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
        }
        if (transactionInactivityLimitTimer != null) {
            transactionInactivityLimitTimer.cancel();
            transactionInactivityLimitTimer = null;
        }
    }

    protected boolean isCancelled() {
        return this.currentState == CfdpTransactionState.CANCELLED;
    }

    protected void setCancelled() {
        this.currentState = CfdpTransactionState.CANCELLED;
    }

    protected boolean isSuspended() {
        return this.currentState == CfdpTransactionState.SUSPENDED;
    }

    protected void setSuspended() {
        if(this.currentState == CfdpTransactionState.RUNNING) {
            this.currentState = CfdpTransactionState.SUSPENDED;
        }
    }

    protected void setResumed() {
        if(this.currentState == CfdpTransactionState.SUSPENDED) {
            this.currentState = CfdpTransactionState.RUNNING;
        }
    }

    protected void setAbandoned() {
        this.currentState = CfdpTransactionState.ABANDONED;
    }

    protected boolean isRunning() {
        return this.currentState == CfdpTransactionState.RUNNING && !isFrozen();
    }

    protected boolean isFrozen() {
        return !txAllowed || !rxAllowed;
    }

    public CfdpTransactionState getCurrentState() {
        return this.currentState;
    }

    protected AckPdu.TransactionStatus deriveCurrentAckTransactionStatus() {
        switch(getCurrentState()) {
            case RUNNING:
            case SUSPENDED:
                return AckPdu.TransactionStatus.ACTIVE;
            case ABANDONED:
            case CANCELLED:
            case COMPLETED:
                return AckPdu.TransactionStatus.TERMINATED;
            default:
                throw new IllegalAccessError("Enumeration not recognized: " + getCurrentState());
        }
    }

    protected void handleAbandon(ConditionCode condition) {
        setAbandoned();
        getEntity().notifyIndication(new AbandonedIndication(getTransactionId(), condition, getProgress(), createStateObject()));
        handleDispose();
    }

    protected long getLocalEntityId() {
        return getEntity().getLocalEntityId();
    }

    protected ConditionCode getLastConditionCode() {
        return lastConditionCode;
    }

    public EntityIdTLV getLastFaultEntity() {
        return lastFaultEntity;
    }

    public Long getLastFaultEntityAsLong() {
        return lastFaultEntity != null ? lastFaultEntity.getEntityId() : null;
    }

    protected void setLastConditionCode(ConditionCode lastConditionCode, Long faultEntity) {
        this.lastConditionCode = lastConditionCode;
        if(faultEntity != null) {
            this.lastFaultEntity = new EntityIdTLV(faultEntity, getEntityIdLength());
        } else {
            this.lastFaultEntity = null;
        }
    }

    protected void handleReport() {
        getEntity().notifyIndication(new ReportIndication(getTransactionId(), createStateObject()));
    }

    protected CfdpTransactionStatus createStateObject() {
        return new CfdpTransactionStatus(Instant.now(), getEntity(), getTransactionId(), getSourceEntityId(), getDestinationEntityId(), getDestinationEntityId() == getEntity().getMib().getLocalEntity().getLocalEntityId(),
                getLastConditionCode(), getLastFaultEntityAsLong(), getCurrentState(), getProgress(), getTotalFileSize(), getTransmissionMode());
    }

    protected abstract long getSourceEntityId();

    protected abstract long getDestinationEntityId();

    protected abstract boolean isAcknowledged();

    protected abstract void handleCancel(ConditionCode conditionCode, long faultEntityId);

    protected abstract void handleSuspend();

    protected abstract void handleResume();

    protected abstract void handleFreeze();

    protected abstract void handleUnfreeze();

    protected abstract void handlePreDispose();

    protected abstract void handleActivation();

    protected abstract void handleTransactionInactivity();

    protected abstract void handleIndication(CfdpPdu pdu);

    protected abstract long getProgress();

    protected abstract long getTotalFileSize();

    protected abstract CfdpTransmissionMode getTransmissionMode();

    protected abstract void forwardPdu(CfdpPdu pdu) throws UtLayerException;
}
