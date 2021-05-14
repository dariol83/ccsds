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

    private final long transactionId;
    private final CfdpEntity entity;
    private final int entityIdLength;
    private final RemoteEntityConfigurationInformation remoteDestination;
    private final IUtLayer transmissionLayer;

    private final ExecutorService confiner;

    private final Timer timer;

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

    protected abstract void handlePreDispose();

    protected abstract void handleActivation();

    protected abstract void handleIndication(CfdpPdu pdu);

}
