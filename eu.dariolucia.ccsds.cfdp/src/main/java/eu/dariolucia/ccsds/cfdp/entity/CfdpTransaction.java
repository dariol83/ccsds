package eu.dariolucia.ccsds.cfdp.entity;

public abstract class CfdpTransaction {

    private final long transactionId;

    public CfdpTransaction(long transactionId) {
        this.transactionId = transactionId;
    }

    public long getTransactionId() {
        return transactionId;
    }

    public abstract void activate();
}
