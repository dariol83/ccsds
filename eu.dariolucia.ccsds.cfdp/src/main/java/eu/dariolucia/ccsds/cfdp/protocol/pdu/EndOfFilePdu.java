package eu.dariolucia.ccsds.cfdp.protocol.pdu;

import java.nio.ByteBuffer;

public class EndOfFilePdu extends FileDirectivePdu {

    private final byte conditionCode;

    private final int fileChecksum;

    private final long fileSize;

    private final Long faultLocation;

    public EndOfFilePdu(byte[] pdu) {
        super(pdu);
        // PDU-specific parsing
        this.conditionCode = (byte) ((pdu[getHeaderLength()] & 0xF0) >>> 4);
        this.fileChecksum = ByteBuffer.wrap(pdu, getHeaderLength() + 1, 4).getInt();
        this.fileSize = isLargeFile() ? ByteBuffer.wrap(pdu, getHeaderLength() + 1 + 4, 8).getLong() : Integer.toUnsignedLong(ByteBuffer.wrap(pdu, getHeaderLength() + 1 + 4, 4).getInt());
        // Let's check the condition code
        if(this.conditionCode == FileDirectivePdu.CC_NOERROR) {
            // Fault location omitted if condition code is 'No error'.
            this.faultLocation = null;
        } else {
            // Otherwise, entity ID in the TLV is the ID of the entity at which transaction cancellation was initiated.
            // The Type of the Entity ID TLV shall be 06 hex; the Value shall be an Entity ID
            int currentOffset = getHeaderLength() + 1 + 4 + (isLargeFile() ? 8 : 4);
            byte type = pdu[currentOffset];
            if(type != 0x06) {
                throw new RuntimeException("Cannot parse Fault Location type in End-Of-File PDU: expected 0x06, got " + String.format("0x%02X", type));
            }
            int length = Byte.toUnsignedInt(pdu[currentOffset + 1]);
            this.faultLocation = readInteger(pdu, currentOffset + 2, length);
        }
    }

    public byte getConditionCode() {
        return conditionCode;
    }

    public int getFileChecksum() {
        return fileChecksum;
    }

    public long getFileSize() {
        return fileSize;
    }

    public Long getFaultLocation() {
        return faultLocation;
    }

    @Override
    public String toString() {
        return super.toString() + " EndOfFilePdu{" +
                "conditionCode=" + conditionCode +
                ", fileChecksum=" + fileChecksum +
                ", fileSize=" + fileSize +
                ", faultLocation=" + faultLocation +
                '}';
    }
}
