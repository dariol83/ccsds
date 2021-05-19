package eu.dariolucia.ccsds.cfdp.entity.request;

/**
 * The KeepAlive Request is a request not part of the standard.
 *
 * It is used to cover the following clause:
 *
 * 4.6.5.3.2 In response to an implementation-specific stimulus, the sending Entity may
 * generate a Prompt (Keep Alive) PDU to force the receiving entity to send a Keep Alive PDU.
 */
public class KeepAliveRequest implements ICfdpRequest {

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
     * KeepAliveRequest full constructor.
     *
     * @param transactionId the transaction ID that uniquely identify a single instance of FDU delivery
     */
    public KeepAliveRequest(long transactionId) {
        this.transactionId = transactionId;
    }

    public long getTransactionId() {
        return transactionId;
    }

    @Override
    public String toString() {
        return "KeepAliveRequest{" +
                "transactionId=" + transactionId +
                '}';
    }
}
