package eu.dariolucia.ccsds.cfdp.protocol.pdu;

public class AckPdu extends FileDirectivePdu {

    public enum TransactionStatus {
        /**
         * The transaction to which the acknowledged PDU belongs is not
         * currently active at this entity, and the CFDP implementation does not retain
         * transaction history. The transaction might be one that was formerly active
         * and has been terminated, or it might be one that has never been active at this
         * entity.
         */
        Undefined,
        /**
         * The transaction to which the acknowledged PDU belongs is currently
         * active at this entity.
         */
        Active,
        /**
         * The transaction to which the acknowledged PDU belongs is not
         * currently active at this entity, the CFDP implementation does retain
         * transaction history, and the transaction is thereby known to be one that was
         * formerly active and has been terminated.
         */
        Terminated,
        /**
         * The transaction to which the acknowledged PDU belongs is
         * not currently active at this entity, the CFDP implementation does retain
         * transaction history, and the transaction is thereby known to be one that has
         * never been active at this entity
         */
        Unrecognized
    }

    /**
     * Directive code of the PDU that this ACK PDU acknowledges.
     * Only EOF and Finished PDUs are acknowledged.
     */
    private final byte directiveCode;

    /**
     * Values depend on the directive code of the PDU that this ACK PDU acknowledges.
     * For ACK of Finished PDU: binary '0001'.
     * Binary '0000' for ACKs of all other file directives.
     */
    private final byte directiveSubtypeCode;

    /**
     * Condition code of the acknowledged PDU.
     */
    private final byte conditionCode;

    /**
     * Status of the transaction in the context of the entity that is issuing the acknowledgment.
     */
    private final TransactionStatus transactionStatus;

    public AckPdu(byte[] pdu) {
        super(pdu);
        // PDU-specific parsing
        this.directiveCode = (byte) ((pdu[getHeaderLength()] & 0xF0) >>> 4);
        this.directiveSubtypeCode = (byte) (pdu[getHeaderLength()] & 0x0F);
        this.conditionCode = (byte) ((pdu[getHeaderLength() + 1] & 0xF0) >>> 4);
        this.transactionStatus = TransactionStatus.values()[((pdu[getHeaderLength() + 1] & 0x03))];
    }

    public byte getDirectiveCode() {
        return directiveCode;
    }

    public byte getDirectiveSubtypeCode() {
        return directiveSubtypeCode;
    }

    public byte getConditionCode() {
        return conditionCode;
    }

    public TransactionStatus getTransactionStatus() {
        return transactionStatus;
    }

    @Override
    public String toString() {
        return super.toString() + " AckPdu{" +
                "directiveCode=" + directiveCode +
                ", directiveSubtypeCode=" + directiveSubtypeCode +
                ", conditionCode=" + conditionCode +
                ", transactionStatus=" + transactionStatus +
                '}';
    }
}
