package eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs;

import java.util.Arrays;

public class MessageToUserTLV implements TLV {

    public static final int TLV_TYPE = 0x02;

    private final byte[] data;

    private final int encodedLength;

    public MessageToUserTLV(byte[] data) {
        this.data = data;
        this.encodedLength = data == null ? 0 : data.length;
    }

    public MessageToUserTLV(byte[] pdu, int offset, int len) {
        // Starting from offset, assume that there is an encoded message with length len
        this.data = len > 0 ? Arrays.copyOfRange(pdu, offset, len) : null;
        // Encoded length
        this.encodedLength = this.data == null ? 0 : this.data.length;;
    }

    public byte[] getData() {
        return data;
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
        return "MessageToUserTLV{" +
                "data=" + Arrays.toString(data) +
                ", encodedLength=" + encodedLength +
                '}';
    }
}
