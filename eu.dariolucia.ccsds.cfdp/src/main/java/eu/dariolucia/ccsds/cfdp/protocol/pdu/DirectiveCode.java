package eu.dariolucia.ccsds.cfdp.protocol.pdu;

public enum DirectiveCode {

    DC_EOF_PDU((byte)0x04),
    DC_FINISHED_PDU((byte)0x05),
    DC_ACK_PDU((byte)0x06),
    DC_METADATA_PDU((byte)0x07),
    DC_NACK_PDU((byte)0x08),
    DC_PROMPT_PDU((byte)0x09),
    DC_KEEPALIVE_PDU((byte)0x0C);

    private final byte code;

    DirectiveCode(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static DirectiveCode fromCode(byte code) {
        for(DirectiveCode c : DirectiveCode.values()) {
            if(c.getCode() == code) {
                return c;
            }
        }
        throw new IllegalArgumentException(String.format("Directive Code %02X not supported", code));
    }
}
