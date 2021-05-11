package eu.dariolucia.ccsds.cfdp.entity;

import eu.dariolucia.ccsds.cfdp.entity.request.PutRequest;

public class CfdpSendTransaction extends CfdpTransaction {

    private final PutRequest request;
    private final CfdpEntity entity;

    public CfdpSendTransaction(long transactionId, PutRequest r, CfdpEntity cfdpEntity) {
        super(transactionId);
        this.request = r;
        this.entity = cfdpEntity;
    }

    @Override
    public void activate() {
        // TODO: implement activate
    }
}
