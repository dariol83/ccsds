/*
 *   Copyright (c) 2021 Dario Lucia (https://www.dariolucia.eu)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and 
 *   limitations under the License.
 */

package eu.dariolucia.ccsds.cfdp.entity.indication;

import eu.dariolucia.ccsds.cfdp.entity.CfdpTransactionStatus;

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

    private final long transactionId;

    private final long offset;

    private final long length;

    private final byte recordContinuationState;

    private final int segmentMetadataLength;

    private final byte[] segmentMetadata;

    private final CfdpTransactionStatus statusReport;

    /**
     * FileSegmentRecvIndication full constructor.
     *
     * @param transactionId The Transaction ID parameter shall uniquely identify a single instance of FDU delivery
     * @param offset displacement from the beginning of the file, expressed as a number of octets.
     * @param length the number of octets of file data received
     * @param recordContinuationState Optionally present. If not present, the value is set to {@link eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDataPdu#RCS_NOT_PRESENT}
     * @param segmentMetadataLength Optionally present. If not present, it must be set to -1
     * @param segmentMetadata Optionally present. Application-specific.
     * @param statusReport The status report provides additional information on some change in the transaction status
     */
    public FileSegmentRecvIndication(long transactionId, long offset, long length, byte recordContinuationState, int segmentMetadataLength, byte[] segmentMetadata, CfdpTransactionStatus statusReport) {
        this.transactionId = transactionId;
        this.offset = offset;
        this.length = length;
        this.recordContinuationState = recordContinuationState;
        this.segmentMetadataLength = segmentMetadataLength;
        this.segmentMetadata = segmentMetadata;
        this.statusReport = statusReport;
    }

    /**
     * The Transaction ID parameter shall uniquely identify a single instance of FDU
     * delivery and shall contain the ID of the source CFDP entity together with a sequence number
     * that is specific to that entity.
     *
     * At any moment, any given transaction ID is unique within the CFDP addressing
     * domain that encompasses the source CFDP entity.
     *
     * @return the transaction ID
     */
    public long getTransactionId() {
        return transactionId;
    }

    /**
     * The offset parameter shall indicate a displacement from the beginning of the file,
     * expressed as a number of octets.
     *
     * @return the offset (octets)
     */
    public long getOffset() {
        return offset;
    }

    /**
     * The length parameter shall indicate the number of octets of file data received.
     *
     * @return number of octets of file data received
     */
    public long getLength() {
        return length;
    }

    /**
     * Optionally present. Present if and only if the value of the segment metadata flag in the PDU header is 1.
     * Possible values are defined as constants in {@link eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDataPdu}.
     *
     * @return the recording continuation state
     */
    public byte getRecordContinuationState() {
        return recordContinuationState;
    }

    /**
     * Optionally present. Present if and only if the value of the segment metadata flag in the PDU header is 1.
     * If so, the value is from 0 to 63. If not present, it is set to -1.
     *
     * @return the length of the segmented metadata, or -1 if not present
     */
    public int getSegmentMetadataLength() {
        return segmentMetadataLength;
    }

    /**
     * Optionally present. The optional segment metadata parameter shall comprise from 0 to 63 octets of
     * metadata pertaining to a received file segment. The length and nature of this metadata are an
     * implementation matter.
     *
     * Present if and only if the value of the segment metadata flag in the PDU header is 1 and the
     * segment metadata length is greater or equal than zero.
     *
     * @return the segment metadata (can have 0 length)
     */
    public byte[] getSegmentMetadata() {
        return segmentMetadata;
    }

    /**
     * The Status report parameter shall indicate the status of the indicated file delivery
     * transaction. The format and scope of the status report parameter are specific to the
     * implementation. It could contain information such as:
     * <ol>
     *     <li>whether the transaction is finished, canceled, suspended, or active;</li>
     *     <li>what extents of the FDU are known to have been successfully received by the
     * receiving CFDP entity;</li>
     *     <li>what extents of the FDU are known to have been transmitted by the sending CFDP
     * entity.</li>
     * </ol>
     *
     * @return the status report
     */
    public CfdpTransactionStatus getStatusReport() {
        return statusReport;
    }

    @Override
    public String toString() {
        return "FileSegmentRecvIndication{" +
                "transactionId=" + getTransactionId() +
                ", offset=" + getOffset() +
                ", length=" + getLength() +
                ", recordContinuationState=" + getRecordContinuationState() +
                ", segmentMetadataLength=" + getSegmentMetadataLength() +
                ", segmentMetadata=" + Arrays.toString(getSegmentMetadata()) +
                ", statusReport=" + getStatusReport() +
                '}';
    }
}
