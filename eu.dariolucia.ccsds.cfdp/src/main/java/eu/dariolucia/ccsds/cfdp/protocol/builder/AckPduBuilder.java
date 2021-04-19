package eu.dariolucia.ccsds.cfdp.protocol.builder;

import eu.dariolucia.ccsds.cfdp.protocol.pdu.AckPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDirectivePdu;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Builder class for {@link AckPdu} objects.
 */
public class AckPduBuilder extends CfdpPduBuilder<AckPdu, AckPduBuilder> {

    private byte directiveCode;

    private byte directiveSubtypeCode;

    private byte conditionCode;

    private AckPdu.TransactionStatus transactionStatus;

    /**
     * Construct an empty builder for this file directive PDU.
     */
    public AckPduBuilder() {
        setType(CfdpPdu.PduType.FILE_DIRECTIVE);
    }

    /**
     * Directive code of the PDU that this ACK PDU acknowledges, as per {@link eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDirectivePdu} DC_ constants.
     * Only EOF and Finished PDUs are acknowledged.
     *
     * @param directiveCode the directive code
     * @return this
     */
    public AckPduBuilder setDirectiveCode(byte directiveCode) {
        this.directiveCode = directiveCode;
        return this;
    }

    /**
     * Values depend on the directive code of the PDU that this ACK PDU acknowledges.
     * For ACK of Finished PDU: binary '0001'.
     * Binary '0000' for ACKs of all other file directives.
     *
     * @param directiveSubtypeCode the directive subtype code
     * @return this
     */
    public AckPduBuilder setDirectiveSubtypeCode(byte directiveSubtypeCode) {
        this.directiveSubtypeCode = directiveSubtypeCode;
        return this;
    }

    /**
     * Condition code of the acknowledged PDU, as per {@link eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDirectivePdu} CC_ constants.
     *
     * @param conditionCode the condition code
     * @return this
     */
    public AckPduBuilder setConditionCode(byte conditionCode) {
        this.conditionCode = conditionCode;
        return this;
    }

    /**
     * Status of the transaction in the context of the entity that is issuing the acknowledgment.
     *
     * @param transactionStatus the transaction status
     * @return this
     */
    public AckPduBuilder setTransactionStatus(AckPdu.TransactionStatus transactionStatus) {
        this.transactionStatus = transactionStatus;
        return this;
    }

    @Override
    protected int encodeDataField(ByteArrayOutputStream bos) throws IOException {
        int totalLength = 0;
        // Directive code
        bos.write(FileDirectivePdu.DC_ACK_PDU);
        totalLength += 1;
        // Directive code and subtype
        byte first = (byte) ((this.directiveCode << 4) & 0xF0);
        first |= (byte) (this.directiveSubtypeCode & 0x0F);
        bos.write(first);
        totalLength += 1;
        // Condition code (4 bits), spare bit and transaction status (2 bits)
        byte second = (byte) ((this.conditionCode << 4) & 0xF0);
        second |= (byte) ((this.transactionStatus.ordinal()) & 0x03);
        bos.write(second);
        totalLength += 1;
        return totalLength;
    }

    @Override
    protected AckPdu buildObject(byte[] pdu) {
        return new AckPdu(pdu);
    }
}
