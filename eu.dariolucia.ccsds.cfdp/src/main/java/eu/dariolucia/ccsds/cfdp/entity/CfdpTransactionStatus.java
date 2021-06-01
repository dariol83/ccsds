package eu.dariolucia.ccsds.cfdp.entity;

import java.time.Instant;

public class CfdpTransactionStatus {

    private final Instant time;
    private final CfdpEntity managingEntity;
    private final long transactionId;
    private final long sourceEntityId;
    private final long destinationEntityId;
    private final boolean isDestination;
    private final byte lastConditionCode;
    private final CfdpTransactionState cfdpTransactionState;
    private final long progress;
    private final long totalFileSize;

    public CfdpTransactionStatus(Instant time, CfdpEntity managingEntity, long transactionId, long sourceEntityId, long destinationEntityId,
                                 boolean isDestination, byte lastConditionCode, CfdpTransactionState cfdpTransactionState,
                                 long progress, long totalFileSize) {
        this.time = time;
        this.managingEntity = managingEntity;
        this.transactionId = transactionId;
        this.sourceEntityId = sourceEntityId;
        this.destinationEntityId = destinationEntityId;
        this.isDestination = isDestination;
        this.lastConditionCode = lastConditionCode;
        this.cfdpTransactionState = cfdpTransactionState;
        this.progress = progress;
        this.totalFileSize = totalFileSize;
    }

    public Instant getTime() {
        return time;
    }

    public CfdpEntity getManagingEntity() {
        return managingEntity;
    }

    public long getTransactionId() {
        return transactionId;
    }

    public long getSourceEntityId() {
        return sourceEntityId;
    }

    public long getDestinationEntityId() {
        return destinationEntityId;
    }

    public boolean isDestination() {
        return isDestination;
    }

    public byte getLastConditionCode() {
        return lastConditionCode;
    }

    public CfdpTransactionState getCfdpTransactionState() {
        return cfdpTransactionState;
    }

    public long getProgress() {
        return progress;
    }

    public long getTotalFileSize() {
        return totalFileSize;
    }

    @Override
    public String toString() {
        return "CfdpTransactionStatus{" +
                "time=" + time +
                ", managingEntity=" + managingEntity +
                ", transactionId=" + transactionId +
                ", sourceEntityId=" + sourceEntityId +
                ", destinationEntityId=" + destinationEntityId +
                ", isDestination=" + isDestination +
                ", lastConditionCode=" + lastConditionCode +
                ", cfdpTransactionState=" + cfdpTransactionState +
                ", progress=" + progress +
                ", totalFileSize=" + totalFileSize +
                '}';
    }
}
