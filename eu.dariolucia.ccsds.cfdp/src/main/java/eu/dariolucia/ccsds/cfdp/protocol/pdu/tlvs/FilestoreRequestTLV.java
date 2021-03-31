package eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs;

public class FilestoreRequestTLV implements TLV {

    public static final int TLV_TYPE = 0x00;

    private final ActionCode actionCode;

    private final String firstFileName;

    private final String secondFileName;

    private final int encodedLength;

    public FilestoreRequestTLV(ActionCode actionCode, String firstFileName, String secondFileName) {
        this.actionCode = actionCode;
        this.firstFileName = firstFileName;
        this.secondFileName = secondFileName;
        this.encodedLength = 1 + (firstFileName == null ? 1 : 1 + firstFileName.length()) + (secondFileName == null ? 1 : 1 + secondFileName.length());
    }

    public FilestoreRequestTLV(byte[] data, int offset) {
        int originalOffset = offset;
        // Starting from offset, assume that there is an encoded Filestore Request TLV Contents: Table 5-15
        this.actionCode = ActionCode.values()[(data[offset] & 0xF0) >>> 4];
        offset += 1;
        // First file name
        int len = Byte.toUnsignedInt(data[offset]);
        offset += 1;
        if(len > 0) {
            this.firstFileName = new String(data, offset, len);
            offset += len;
        } else {
            this.firstFileName = null;
        }
        // Second file name
        len = Byte.toUnsignedInt(data[offset]);
        offset += 1;
        if(len > 0) {
            this.secondFileName = new String(data, offset, len);
            offset += len;
        } else {
            this.secondFileName = null;
        }
        // Encoded length
        this.encodedLength = offset - originalOffset;
    }

    public ActionCode getActionCode() {
        return actionCode;
    }

    public String getFirstFileName() {
        return firstFileName;
    }

    public String getSecondFileName() {
        return secondFileName;
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
        return "FilestoreRequestTLV{" +
                "actionCode=" + actionCode +
                ", firstFileName='" + firstFileName + '\'' +
                ", secondFileName='" + secondFileName + '\'' +
                ", encodedLength=" + encodedLength +
                '}';
    }
}
