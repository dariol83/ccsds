package eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs;

public class FilestoreResponse {

    public enum ActionCode {
        Create,
        Delete,
        Rename,
        Append,
        Replace,
        CreateDirectory,
        RemoveDirectory,
        DenyFile,
        DenyDirectory
    }

    public enum StatusCode {
        Successful(null, 0b0000),
        CreateNotAllowed(ActionCode.Create, 0b0001),
        FileDoesNotExist(ActionCode.Delete, 0b0001),
        DeleteFileNotAllowed(ActionCode.Delete, 0b0010),
        OldFileDoesNotExist(ActionCode.Rename, 0b0001),
        NewFileAlreadyExists(ActionCode.Rename, 0b0010),
        RenameNotAllowed(ActionCode.Rename, 0b0011),
        File1DoesNotExist(ActionCode.Append, 0b0001),
        File2DoesNotExist(ActionCode.Append, 0b0010),
        AppendNotAllowed(ActionCode.Append, 0b0011),
        File1NotExist(ActionCode.Replace, 0b0001),
        File2NotExist(ActionCode.Replace, 0b0010),
        ReplaceNotAllowed(ActionCode.Replace, 0b0011),
        DirectoryCannotBeCreated(ActionCode.CreateDirectory, 0b0001),
        DirectoryDoesNotExist(ActionCode.RemoveDirectory, 0b0001),
        RemoveDirectoryNotAllowed(ActionCode.RemoveDirectory, 0b0010),
        DenyFileNotAllowed(ActionCode.DenyFile, 0b0010),
        DenyDirectoryNotAllowed(ActionCode.DenyDirectory, 0b0010),
        NotPerformed(null, 0b1111);

        private final ActionCode actionCode;
        private final int statusCode;

        StatusCode(ActionCode actionCode, int statusCode) {
            this.actionCode = actionCode;
            this.statusCode = statusCode;
        }

        public static StatusCode from(ActionCode ac, int status) {
            for(StatusCode sc : values()) {
                if(sc.actionCode == null || sc.actionCode == ac) {
                    if(status == sc.statusCode) {
                        return sc;
                    }
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

    public FilestoreResponse(ActionCode actionCode, StatusCode statusCode, String firstFileName, String secondFileName, String filestoreMessage) {
        this.actionCode = actionCode;
        this.statusCode = statusCode;
        this.firstFileName = firstFileName;
        this.secondFileName = secondFileName;
        this.filestoreMessage = filestoreMessage;
        this.encodedLength = 1 + (firstFileName == null ? 1 : 1 + firstFileName.length()) + (secondFileName == null ? 1 : 1 + secondFileName.length()) +
                (filestoreMessage == null ? 1 : 1 + filestoreMessage.length());
    }

    public FilestoreResponse(byte[] data, int offset) {
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

    public int getEncodedLength() {
        return encodedLength;
    }

    @Override
    public String toString() {
        return "FilestoreResponse{" +
                "actionCode=" + actionCode +
                ", statusCode=" + statusCode +
                ", firstFileName='" + firstFileName + '\'' +
                ", secondFileName='" + secondFileName + '\'' +
                ", filestoreMessage='" + filestoreMessage + '\'' +
                ", encodedLength=" + encodedLength +
                '}';
    }
}
