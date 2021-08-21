package eu.dariolucia.ccsds.cfdp.protocol.pdu;

public enum ConditionCode {

    CC_NOERROR((byte) 0b0000),
    CC_POS_ACK_LIMIT_REACHED((byte) 0b0001),
    CC_KEEPALIVE_LIMIT_REACHED((byte) 0b0010),
    CC_INVALID_TX_MODE((byte) 0b0011),
    CC_FILESTORE_REJECTION((byte) 0b0100),
    CC_FILE_CHECKSUM_FAILURE((byte) 0b0101),
    CC_FILE_SIZE_ERROR((byte) 0b0110),
    CC_NAK_LIMIT_REACHED((byte) 0b0111),
    CC_INACTIVITY_DETECTED((byte) 0b1000),
    CC_INVALID_FILE_STRUCTURE((byte) 0b1001),
    CC_CHECK_LIMIT_REACHED((byte) 0b1010),
    CC_UNSUPPORTED_CHECKSUM_TYPE((byte) 0b1011),
    CC_SUSPEND_REQUEST_RECEIVED((byte) 0b1110),
    CC_CANCEL_REQUEST_RECEIVED((byte) 0b1111);

    private final byte code;

    ConditionCode(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static ConditionCode fromCode(byte code) {
        for(ConditionCode c : ConditionCode.values()) {
            if(c.getCode() == code) {
                return c;
            }
        }
        throw new IllegalArgumentException(String.format("Condition Code %02X not supported", code));
    }
}
