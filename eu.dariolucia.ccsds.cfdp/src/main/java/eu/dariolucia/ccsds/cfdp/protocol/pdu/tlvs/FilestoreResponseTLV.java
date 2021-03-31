package eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs;

public class FilestoreResponseTLV implements TLV {

    public static final int TLV_TYPE = 0x01;

    public enum StatusCode {
        SUCCESSFUL(null, 0b0000),
        CREATE_NOT_ALLOWED(ActionCode.CREATE, 0b0001),
        FILE_DOES_NOT_EXIST(ActionCode.DELETE, 0b0001),
        DELETE_FILE_NOT_ALLOWED(ActionCode.DELETE, 0b0010),
        OLD_FILE_DOES_NOT_EXIST(ActionCode.RENAME, 0b0001),
        NEW_FILE_ALREADY_EXISTS(ActionCode.RENAME, 0b0010),
        RENAME_NOT_ALLOWED(ActionCode.RENAME, 0b0011),
        FILE_1_DOES_NOT_EXIST(ActionCode.APPEND, 0b0001),
        FILE_2_DOES_NOT_EXIST(ActionCode.APPEND, 0b0010),
        APPEND_NOT_ALLOWED(ActionCode.APPEND, 0b0011),
        FILE_1_NOT_EXIST(ActionCode.REPLACE, 0b0001),
        FILE_2_NOT_EXIST(ActionCode.REPLACE, 0b0010),
        REPLACE_NOT_ALLOWED(ActionCode.REPLACE, 0b0011),
        DIRECTORY_CANNOT_BE_CREATED(ActionCode.CREATE_DIRECTORY, 0b0001),
        DIRECTORY_DOES_NOT_EXIST(ActionCode.REMOVE_DIRECTORY, 0b0001),
        REMOVE_DIRECTORY_NOT_ALLOWED(ActionCode.REMOVE_DIRECTORY, 0b0010),
        DENY_FILE_NOT_ALLOWED(ActionCode.DENY_FILE, 0b0010),
        DENY_DIRECTORY_NOT_ALLOWED(ActionCode.DENY_DIRECTORY, 0b0010),
        NOT_PERFORMED(null, 0b1111);

        private final ActionCode actionCode;
        private final int status;

        StatusCode(ActionCode actionCode, int status) {
            this.actionCode = actionCode;
            this.status = status;
        }

        public static StatusCode from(ActionCode ac, int status) {
            for(StatusCode sc : values()) {
                if((sc.actionCode == null || sc.actionCode == ac) && status == sc.status) {
                    return sc;
                }
            }
            return null;
        }
    }

    private final ActionCode actionCode;

    private final StatusCode statusCode;

    private final String firstFileName;

    private final String secondFileName;

    private final String filestoreMessage;

    private final int encodedLength;

    public FilestoreResponseTLV(ActionCode actionCode, StatusCode statusCode, String firstFileName, String secondFileName, String filestoreMessage) {
        this.actionCode = actionCode;
        this.statusCode = statusCode;
        this.firstFileName = firstFileName;
        this.secondFileName = secondFileName;
        this.filestoreMessage = filestoreMessage;
        this.encodedLength = 1 + (firstFileName == null ? 1 : 1 + firstFileName.length()) + (secondFileName == null ? 1 : 1 + secondFileName.length()) +
                (filestoreMessage == null ? 1 : 1 + filestoreMessage.length());
    }

    public FilestoreResponseTLV(byte[] data, int offset) {
        int originalOffset = offset;
        // Starting from offset, assume that there is an encoded Filestore Response TLV Contents: Table 5-17
        this.actionCode = ActionCode.values()[(data[offset] & 0xF0) >>> 4];
        this.statusCode = StatusCode.from(this.actionCode, data[offset] & 0x0F);
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
        // Filestore message
        len = Byte.toUnsignedInt(data[offset]);
        offset += 1;
        if(len > 0) {
            this.filestoreMessage = new String(data, offset, len);
            offset += len;
        } else {
            this.filestoreMessage = null;
        }
        // Encoded length
        this.encodedLength = offset - originalOffset;
    }

    public ActionCode getActionCode() {
        return actionCode;
    }

    public StatusCode getStatusCode() {
        return statusCode;
    }

    public String getFirstFileName() {
        return firstFileName;
    }

    public String getSecondFileName() {
        return secondFileName;
    }

    public String getFilestoreMessage() {
        return filestoreMessage;
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
        return "FilestoreResponseTLV{" +
                "actionCode=" + actionCode +
                ", statusCode=" + statusCode +
                ", firstFileName='" + firstFileName + '\'' +
                ", secondFileName='" + secondFileName + '\'' +
                ", filestoreMessage='" + filestoreMessage + '\'' +
                ", encodedLength=" + encodedLength +
                '}';
    }
}
