package eu.dariolucia.ccsds.cfdp.entity.indication;

import java.util.Arrays;

/**
 * The File-Segment-Recv.indication primitive shall be used to indicate the receipt of
 * individual file data (non-metadata) segments to the destination CFDP user.
 *
 * MFile-Segment-Recv.indication is generated on receipt of a File Data PDU.
 *
 * The effect on receipt of File-Segment-Recv.indication by a CFDP user is undefined.
 *
 * Generation of File-Segment-Recv.indication is optional. Offset is the number of octets
 * in the file prior to the first octet of the received File Data PDU’s content.
 *
 * Ref. CCSDS 727.0-B-5, 3.4.10
 */
public class FileSegmentRecvIndication implements ICfdpIndication {

    /**
     * The Transaction ID parameter shall uniquely identify a single instance of FDU
     * delivery and shall contain the ID of the source CFDP entity together with a sequence number
     * that is specific to that entity.
     *
     * At any moment, any given transaction ID is unique within the CFDP addressing
     * domain that encompasses the source CFDP entity.
     */
    private final long transactionId;

    /**
     * The offset parameter shall indicate a displacement from the beginning of the file,
     * expressed as a number of octets.
     */
    private final long offset;

    /**
     * The length parameter shall indicate the number of octets of file data received.
     */
    private final long length;

    /**
     * Optionally present. Present if and only if the value of the segment metadata flag in the PDU header is 1.
     * Possible values are defined as constants in {@link eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDataPdu}.
     */
    private final byte recordContinuationState;

    /**
     * Optionally present. Present if and only if the value of the segment metadata flag in the PDU header is 1.
     * 0 to 63.
     */
    private final int segmentMetadataLength;

    /**
     * Optionally present. The optional segment metadata parameter shall comprise from 0 to 63 octets of
     * metadata pertaining to a received file segment. The length and nature of this metadata are an
     * implementation matter.
     *
     * Present if and only if the value of the segment metadata flag in the PDU header is 1 and the
     * segment metadata length is greater than zero.
     */
    private final byte[] segmentMetadata;

    /**
     * FileSegmentRecvIndication full constructor.
     *
     * @param transactionId The Transaction ID parameter shall uniquely identify a single instance of FDU delivery
     * @param offset displacement from the beginning of the file, expressed as a number of octets.
     * @param length the number of octets of file data received
     * @param recordContinuationState Optionally present. If not present, the value is set to {@link eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDataPdu#RCS_NOT_PRESENT}
     * @param segmentMetadataLength Optionally present. If not present, it must be set to -1
     * @param segmentMetadata Optionally present. Application-specific.
     */
    public FileSegmentRecvIndication(long transactionId, long offset, long length, byte recordContinuationState, int segmentMetadataLength, byte[] segmentMetadata) {
        this.transactionId = transactionId;
        this.offset = offset;
        this.length = length;
        this.recordContinuationState = recordContinuationState;
        this.segmentMetadataLength = segmentMetadataLength;
        this.segmentMetadata = segmentMetadata;
    }

    public long getTransactionId() {
        return transactionId;
    }

    public long getOffset() {
        return offset;
    }

    public long getLength() {
        return length;
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

    @Override
    public String toString() {
        return "FileSegmentRecvIndication{" +
                "transactionId=" + transactionId +
                ", offset=" + offset +
                ", length=" + length +
                ", recordContinuationState=" + recordContinuationState +
                ", segmentMetadataLength=" + segmentMetadataLength +
                ", segmentMetadata=" + Arrays.toString(segmentMetadata) +
                '}';
    }
}
