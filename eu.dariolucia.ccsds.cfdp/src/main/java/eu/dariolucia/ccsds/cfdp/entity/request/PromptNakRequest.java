package eu.dariolucia.ccsds.cfdp.entity.request;

/**
 * The KeepAlive Request is a request not part of the standard.
 *
 * It is used to cover the following clause:
 *
 * 4.6.4.5 Incremental Lost Segment Detection Procedures at the Sending Entity
 * In response to an implementation-specific external event, the sending CFDP entity may issue
 * a Prompt (NAK) PDU.
 */
public class PromptNakRequest implements ICfdpRequest {

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
     * PromptNakRequest full constructor.
     *
     * @param transactionId the transaction ID that uniquely identify a single instance of FDU delivery
     */
    public PromptNakRequest(long transactionId) {
        this.transactionId = transactionId;
    }

    public long getTransactionId() {
        return transactionId;
    }

    @Override
    public String toString() {
        return "PromptNakRequest{" +
                "transactionId=" + transactionId +
                '}';
    }
}
