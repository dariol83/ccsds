package eu.dariolucia.ccsds.cfdp.entity.indication;

/**
 * The Resumed.indication primitive shall be used to indicate to the CFDP user that the
 * transaction has been resumed.
 *
 * Resumed.indication shall be generated in response to Resume.request
 *
 * The effect on receipt of Resumed.indication by a CFDP user is undefined.
 *
 * Progress indicates how far the issuing CFDP entity had progressed, in sending or receiving
 * the transaction’s transmitted file, as of the moment at which the Resumed.indication was
 * generated.
 *
 * Ref. CCSDS 727.0-B-5, 3.4.12
 */
public class ResumedIndication implements ICfdpIndication {

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
     * The progress parameter shall report on current file transmission or reception
     * progress, as defined in 1.3.3.3.
     */
    private final long progress;

    /**
     * ResumedIndication full constructor.
     *
     * @param transactionId The Transaction ID parameter shall uniquely identify a single instance of FDU delivery
     * @param progress The number of octets that indicates the progress of the transaction.
     */
    public ResumedIndication(long transactionId, long progress) {
        this.transactionId = transactionId;
        this.progress = progress;
    }

    public long getTransactionId() {
        return transactionId;
    }

    public long getProgress() {
        return progress;
    }

    @Override
    public String toString() {
        return "ResumedIndication{" +
                "transactionId=" + transactionId +
                ", progress=" + progress +
                '}';
    }
}
