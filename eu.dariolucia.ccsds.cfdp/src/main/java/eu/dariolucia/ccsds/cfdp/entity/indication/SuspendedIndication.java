package eu.dariolucia.ccsds.cfdp.entity.indication;

/**
 * The Suspended.indication primitive shall be used to indicate to the CFDP user that the
 * transaction has been suspended.
 *
 * Suspended.indication is generated on Notice of Suspension of a file transmission
 * procedure.
 *
 * The effect on receipt of Suspended.indication by a CFDP user is undefined.
 *
 * Ref. CCSDS 727.0-B-5, 3.4.11
 */
public class SuspendedIndication implements ICfdpIndication {

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
     * SuspendedIndication full constructor.
     *
     * @param transactionId The Transaction ID parameter shall uniquely identify a single instance of FDU delivery
     * @param conditionCode The Condition code parameter shall provide additional information on some change in the transaction status
     */
    public SuspendedIndication(long transactionId, byte conditionCode) {
        this.transactionId = transactionId;
        this.conditionCode = conditionCode;
    }

    public long getTransactionId() {
        return transactionId;
    }

    public byte getConditionCode() {
        return conditionCode;
    }

    @Override
    public String toString() {
        return "SuspendedIndication{" +
                "transactionId=" + transactionId +
                ", conditionCode=" + conditionCode +
                '}';
    }
}
