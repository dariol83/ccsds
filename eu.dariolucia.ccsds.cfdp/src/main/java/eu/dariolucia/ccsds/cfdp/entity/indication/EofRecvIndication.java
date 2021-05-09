package eu.dariolucia.ccsds.cfdp.entity.indication;

/**
 * The EOF-Recv.indication primitive shall be used to indicate to the destination CFDP user
 * that the EOF PDU associated with a transaction has been received.
 *
 * EOF-Recv.indication is generated on initial receipt, at the destination entity of a
 * transaction, of the EOF PDU for the transaction.
 *
 * The effect on receipt of EOF-Recv.indication by the destination CFDP user is undefined.
 *
 * Generation of EOF-Recv.indication is optional.
 *
 * Ref. CCSDS 727.0-B-5, 3.4.16
 */
public class EofRecvIndication implements ICfdpIndication {

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
     * EofRecvIndication full constructor.
     *
     * @param transactionId the transaction ID that uniquely identify a single instance of FDU delivery
     */
    public EofRecvIndication(long transactionId) {
        this.transactionId = transactionId;
    }

    public long getTransactionId() {
        return transactionId;
    }

    @Override
    public String toString() {
        return "EofRecvIndication{" +
                "transactionId=" + transactionId +
                '}';
    }
}
