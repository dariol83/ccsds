package eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs;

public class FaultHandlerOverrideTLV implements TLV {

    public static final int TLV_TYPE = 0x04;

    public enum HandlerCode {
        RESERVED,
        ISSUE_NOTICE_OF_CANCELLATION,
        ISSUE_NOTICE_OF_SUSPENSION,
        IGNORE_ERROR,
        ABANDON_TRANSACTION
    }

    private final byte conditionCode;

    private final HandlerCode handlerCode;

    private final int encodedLength;

    public FaultHandlerOverrideTLV(byte conditionCode, HandlerCode handlerCode) {
        this.conditionCode = conditionCode;
        this.handlerCode = handlerCode;
        this.encodedLength = 1;
    }

    public FaultHandlerOverrideTLV(byte[] data, int offset) {
        // Starting from offset, assume that there is an encoded Fault Handler Override TLV Contents: Table 5-19
        this.conditionCode = (byte) ((data[offset] & 0xF0) >>> 4);
        this.handlerCode = HandlerCode.values()[data[offset] & 0x0F];
        // Encoded length
        this.encodedLength = 1;
    }

    public byte getConditionCode() {
        return conditionCode;
    }

    public HandlerCode getHandlerCode() {
        return handlerCode;
    }

    @Override
    public int getType() {
        return TLV_TYPE;
    }

    @Override
    public int getLength() {
        return encodedLength;
    }

    @Override
    public byte[] encode(boolean withTypeLength) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public String toString() {
        return "FaultHandlerOverrideTLV{" +
                "conditionCode=" + conditionCode +
                ", handlerCode=" + handlerCode +
                ", encodedLength=" + encodedLength +
                '}';
    }
}
