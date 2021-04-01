package eu.dariolucia.ccsds.cfdp.protocol.pdu;

import eu.dariolucia.ccsds.cfdp.common.IntegerUtil;

import java.util.Arrays;

public class FileDataPdu extends CfdpPdu {

    public static final byte RCS_NO_START_NO_END = 0x00;
    public static final byte RCS_START_NO_END = 0x01;
    public static final byte RCS_NO_START_END = 0x02;
    public static final byte RCS_START_END = 0x03;
    public static final byte RCS_NOT_PRESENT = (byte) 0xFF; // not present in the standard, added by this implementation

    private final byte recordContinuationState;

    private final int segmentMetadataLength;

    private final byte[] segmentMetadata;

    private final long offset;

    private final byte[] fileData;

    public FileDataPdu(byte[] pdu) {
        super(pdu);
        // PDU-specific parsing
        int currentOffset = getHeaderLength();
        if(isSegmentMetadata()) {
            this.recordContinuationState = (byte) ((pdu[currentOffset] & 0xC0) >>> 6);
            this.segmentMetadataLength = pdu[currentOffset] & 0x3F;
            currentOffset += 1;
            this.segmentMetadata = new byte[this.segmentMetadataLength];
            if(this.segmentMetadataLength > 0) {
                System.arraycopy(pdu, currentOffset, this.segmentMetadata, 0, this.segmentMetadataLength);
                currentOffset += this.segmentMetadataLength;
            }
        } else {
            this.recordContinuationState = RCS_NOT_PRESENT;
            this.segmentMetadataLength = -1;
            this.segmentMetadata = null;
        }
        //
        this.offset = IntegerUtil.readInteger(pdu, currentOffset, isLargeFile() ? 8 : 4);
        currentOffset += isLargeFile() ? 8 : 4;
        this.fileData = Arrays.copyOfRange(pdu, currentOffset, pdu.length); // This could be optimized only by storing the start of the file data
    }

    public byte getRecordContinuationState() {
        return recordContinuationState;
    }

    public int getSegmentMetadataLength() {
        return segmentMetadataLength;
    }

    public byte[] getSegmentMetadata() {
        return segmentMetadata;
    }

    public long getOffset() {
        return offset;
    }

    public byte[] getFileData() {
        return fileData;
    }

    @Override
    public String toString() {
        return super.toString() + " FileDataPdu{" +
                "recordContinuationState=" + recordContinuationState +
                ", segmentMetadataLength=" + segmentMetadataLength +
                ", segmentMetadata=" + Arrays.toString(segmentMetadata) +
                ", offset=" + offset +
                ", fileData=" + Arrays.toString(fileData) +
                '}';
    }
}
