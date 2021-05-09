package eu.dariolucia.ccsds.cfdp.entity.indication;

/**
 * The Fault.indication primitive shall be used to indicate to the CFDP user the occurrence
 * of a fault condition for which the designated fault handler was ‘Ignore’.
 *
 * Fault.indication shall be generated upon detection of a fault condition for which the
 * designated fault handler is 'Ignore'.
 *
 * The effect on receipt of Fault.indication by a CFDP user is undefined.
 *
 * The progress parameter indicates how far the issuing CFDP entity had progressed, in sending
 * or receiving the transaction’s transmitted file, as of the moment at which the
 * Fault.indication was generated.
 *
 * Ref. CCSDS 727.0-B-5, 3.4.14
 */
public class FaultIndication implements ICfdpIndication {

    /**
     * The Transaction ID parameter shall uniquely identify a single instance of FDU
     * delivery and shall contain the ID of the source CFDP entity together with a sequence number
     * that is specific to that entity.
     *
     * At any moment, any given transaction ID is unique within the CFDP addressing
     * domain that encompasses the source CFDP entity.
     */
    private final long transactionId;

    /**
     * The Condition code parameter shall provide additional information on some change
     * in transaction status.
     */
    private final byte conditionCode;

    /**
     * The progress parameter shall report on current file transmission or reception
     * progress, as defined in 1.3.3.3.
     */
    private final long progress;

    /**
     * FaultIndication full constructor.
     *
     * @param transactionId The Transaction ID parameter shall uniquely identify a single instance of FDU delivery
     * @param conditionCode The Condition code parameter shall provide additional information on some change in the transaction status
     * @param progress The progress parameter shall report on current file transmission or reception progress
     */
    public FaultIndication(long transactionId, byte conditionCode, long progress) {
        this.transactionId = transactionId;
        this.conditionCode = conditionCode;
        this.progress = progress;
    }

    public long getTransactionId() {
        return transactionId;
    }

    public byte getConditionCode() {
        return conditionCode;
    }

    public long getProgress() {
        return progress;
    }

    @Override
    public String toString() {
        return "FaultIndication{" +
                "transactionId=" + transactionId +
                ", conditionCode=" + conditionCode +
                ", progress=" + progress +
                '}';
    }
}
