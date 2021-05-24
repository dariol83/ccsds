package eu.dariolucia.ccsds.cfdp.entity;

import eu.dariolucia.ccsds.cfdp.common.BytesUtil;
import eu.dariolucia.ccsds.cfdp.entity.indication.FaultIndication;
import eu.dariolucia.ccsds.cfdp.mib.RemoteEntityConfigurationInformation;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.AckPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDirectivePdu;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayer;
import eu.dariolucia.ccsds.cfdp.ut.UtLayerException;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class CfdpTransaction {

    private static final Logger LOG = Logger.getLogger(CfdpTransaction.class.getName());

    public enum TransactionInnerStatus {
        RUNNING,
        SUSPENDED,
        CANCELLED,
        ABANDONED
    }

    private final long transactionId;
    private final CfdpEntity entity;
    private final int entityIdLength;
    private final RemoteEntityConfigurationInformation remoteDestination;
    private final IUtLayer transmissionLayer;

    private final ExecutorService confiner;

    private final Timer timer;
    // Timer for the transaction inactivity limit
    private TimerTask transactionInactivityLimitTimer;

    // Inner status of active transactions
    private TransactionInnerStatus status = TransactionInnerStatus.RUNNING;
    // Overall status of the transaction
    private AckPdu.TransactionStatus overallStatus = AckPdu.TransactionStatus.ACTIVE;

    // Ack timer for Positive Ack Procedure
    private TimerTask ackTimer;
    private int ackTimerCount;

    // Transmission opportunity window state
    private boolean txAllowed;
    // Reception opportunity window state
    private boolean rxAllowed;

    public CfdpTransaction(long transactionId, CfdpEntity cfdpEntity, long remoteEntityId) {
        this.transactionId = transactionId;
        this.entity = cfdpEntity;
        this.remoteDestination = cfdpEntity.getMib().getRemoteEntityById(remoteEntityId);
        this.transmissionLayer = cfdpEntity.getUtLayerByDestinationEntity(remoteEntityId);
        this.confiner = Executors.newFixedThreadPool(1, runnable -> {
            Thread t = new Thread(runnable, String.format("CFDP Transaction [%d] [%d] Handler", transactionId, remoteDestination.getRemoteEntityId()));
            t.setDaemon(true);
            return t;
        });
        this.timer = new Timer(String.format("CFDP Transaction [%d] [%d] Timer", transactionId, remoteDestination.getRemoteEntityId()), true);
        // Compute the entity ID length
        long maxEntityId = Long.max(remoteDestination.getRemoteEntityId(), entity.getMib().getLocalEntity().getLocalEntityId());
        this.entityIdLength = BytesUtil.getEncodingOctetsNb(maxEntityId);
    }

    protected void startPositiveAckTimer(CfdpPdu pdu) {
        stopPositiveAckTimer();
        this.ackTimer = new TimerTask() {
            @Override
            public void run() {
                handle(() -> handlePositiveAckTimerTimerElapsed(this, pdu));
            }
        };
        schedule(this.ackTimer, getRemoteDestination().getPositiveAckTimerInterval(), true);
    }

    protected void stopPositiveAckTimer() {
        if(this.ackTimer != null) {
            this.ackTimer.cancel();
            this.ackTimer = null;
            this.ackTimerCount = 0;
        }
    }

    protected boolean isAckTimerRunning() {
        return this.ackTimer != null;
    }

    protected void handlePositiveAckTimerTimerElapsed(TimerTask timer, CfdpPdu pdu) {
        // 4.7.1 POSITIVE ACKNOWLEDGEMENT PROCEDURES AT PDU SENDING END
        // If Positive Acknowledgement procedures apply to a PDU,
        // a) upon issuing the PDU, the sending CFDP entity shall start a timer and retain the PDU
        //    for retransmission as necessary
        if(this.ackTimer == timer) {
            // c) the sending CFDP entity shall keep a tally of the number of transmission retries
            ++this.ackTimerCount;
            if(this.ackTimerCount == getRemoteDestination().getCheckIntervalExpirationLimit()) {
                // d) if a preset limit is exceeded, the sending CFDP entity shall declare a Positive ACK
                //    Limit Reached fault
                this.ackTimer.cancel();
                this.ackTimer = null;
                getEntity().notifyIndication(new FaultIndication(getTransactionId(), FileDirectivePdu.CC_POS_ACK_LIMIT_REACHED, getProgress()));
                handleDispose(); // TODO: fault handling
            } else {
                // b) if the Expected Response is not received before expiry of the timer, the sending
                //    CFDP entity shall reissue the original PDU
                try {
                    forwardPdu(pdu);
                } catch (UtLayerException e) {
                    if(LOG.isLoggable(Level.SEVERE)) {
                        LOG.log(Level.SEVERE, String.format("Transaction %d with remote entity %d: fail on PDU re-transmission upon ACK timer expire: %s ", getTransactionId(), getRemoteDestination().getRemoteEntityId(), e.getMessage()), e);
                    }
                }
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

    protected RemoteEntityConfigurationInformation getRemoteDestination() {
        return remoteDestination;
    }

    protected IUtLayer getTransmissionLayer() {
        return transmissionLayer;
    }

    protected void handle(Runnable r) {
        this.confiner.submit(r);
    }

    protected void schedule(TimerTask t, long period, boolean periodic) {
        if(periodic) {
            this.timer.schedule(t, period, period);
        } else {
            this.timer.schedule(t, period);
        }
    }

    public void dispose() {
        handle(this::handleDispose);
    }

    protected void handleDispose() {
        handlePreDispose();
        this.confiner.shutdownNow();
        this.timer.cancel();
    }

    public void activate() {
        handle(this::handleActivation);
    }

    public void indication(CfdpPdu pdu) {
        handle(() -> handleIndication(pdu));
    }

    public void cancel(byte conditionCode) {
        handle(() -> handleCancel(conditionCode, getEntity().getMib().getLocalEntity().getLocalEntityId()));
    }

    public void suspend() {
        handle(() -> {
            if(isRunning()) {
                handleSuspend();
            } else {
                // TODO log
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
            // TODO: b) if the transaction to which a Resume.request primitive pertains is currently not only
            //    suspended but also frozen (as defined in 4.12), then the transaction shall be
            //    considered no longer suspended but the only applicable procedure shall be the
            //    issuance of a Resumed.indication.
            if(isSuspended()) {
                handleResume();
            }
        });
    }

    public void updateTxOpportunity(boolean txAllowed) {
        handle(() -> {
            boolean wasFrozen = isFrozen();
            this.txAllowed = txAllowed;
            handleOpportunityTransition(wasFrozen, isFrozen());
        });
    }

    public void updateRxOpportunity(boolean rxAllowed) {
        handle(() -> {
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
        handle(this::handleReport);
    }

    protected void startTransactionInactivityTimer() {
        if(transactionInactivityLimitTimer != null) {
            return;
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
            stopTransactionInactivityTimer();
            startTransactionInactivityTimer();
        }
    }

    protected void stopTransactionInactivityTimer() {
        if (transactionInactivityLimitTimer != null) {
            transactionInactivityLimitTimer.cancel();
            transactionInactivityLimitTimer = null;
        }
    }

    protected boolean isCancelled() {
        return this.status == TransactionInnerStatus.CANCELLED;
    }

    protected void setCancelled() {
        this.status = TransactionInnerStatus.CANCELLED;
        this.overallStatus = AckPdu.TransactionStatus.TERMINATED;
    }

    protected boolean isSuspended() {
        return this.status == TransactionInnerStatus.SUSPENDED;
    }

    protected void setSuspended() {
        if(this.status == TransactionInnerStatus.RUNNING) {
            this.status = TransactionInnerStatus.SUSPENDED;
        }
    }

    protected void setResumed() {
        if(this.status == TransactionInnerStatus.SUSPENDED) {
            this.status = TransactionInnerStatus.RUNNING;
        }
    }

    protected boolean isAbandoned() {
        return this.status == TransactionInnerStatus.ABANDONED;
    }

    protected void setAbandoned() {
        this.status = TransactionInnerStatus.ABANDONED;
    }

    protected boolean isRunning() {
        return this.status == TransactionInnerStatus.RUNNING && !isFrozen();
    }

    protected boolean isFrozen() {
        return !txAllowed || !rxAllowed;
    }

    public AckPdu.TransactionStatus getOverallStatus() {
        return this.overallStatus;
    }

    protected void handleAbandon() {
        setAbandoned();
        handleDispose();
    }

    protected abstract void handleCancel(byte conditionCode, long faultEntityId);

    protected abstract void handleSuspend();

    protected abstract void handleResume();

    protected abstract void handleFreeze();

    protected abstract void handleUnfreeze();

    protected abstract void handleReport();

    protected abstract void handlePreDispose();

    protected abstract void handleActivation();

    protected abstract void handleTransactionInactivity();

    protected abstract void handleIndication(CfdpPdu pdu);

    protected abstract long getProgress();

    protected abstract void forwardPdu(CfdpPdu pdu) throws UtLayerException;
}
