package eu.dariolucia.ccsds.cfdp.entity;

import eu.dariolucia.ccsds.cfdp.common.BytesUtil;
import eu.dariolucia.ccsds.cfdp.mib.RemoteEntityConfigurationInformation;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayer;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class CfdpTransaction {

    public enum TransactionStatus {
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

    // Status of the transaction
    private TransactionStatus status = TransactionStatus.RUNNING;
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
        return this.status == TransactionStatus.CANCELLED;
    }

    protected void setCancelled() {
        this.status = TransactionStatus.CANCELLED;
    }

    protected boolean isSuspended() {
        return this.status == TransactionStatus.SUSPENDED;
    }

    protected void setSuspended() {
        if(this.status == TransactionStatus.RUNNING) {
            this.status = TransactionStatus.SUSPENDED;
        }
    }

    protected void setResumed() {
        if(this.status == TransactionStatus.SUSPENDED) {
            this.status = TransactionStatus.RUNNING;
        }
    }

    protected boolean isAbandoned() {
        return this.status == TransactionStatus.ABANDONED;
    }

    protected void setAbandoned() {
        this.status = TransactionStatus.ABANDONED;
    }

    protected boolean isRunning() {
        return this.status == TransactionStatus.RUNNING && !isFrozen();
    }

    protected boolean isFrozen() {
        return !txAllowed || !rxAllowed;
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

}
