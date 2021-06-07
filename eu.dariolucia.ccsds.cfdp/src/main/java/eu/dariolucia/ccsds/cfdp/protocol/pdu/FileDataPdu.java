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

package eu.dariolucia.ccsds.cfdp.protocol.pdu;

import eu.dariolucia.ccsds.cfdp.common.BytesUtil;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;

import java.util.Arrays;

/**
 * Finished PDU - CCSDS 727.0-B-5, 5.3
 */
public class FileDataPdu extends CfdpPdu {

    public static final byte RCS_NO_START_NO_END = 0x00;
    public static final byte RCS_START_NO_END = 0x01;
    public static final byte RCS_NO_START_END = 0x02;
    public static final byte RCS_START_END = 0x03;
    public static final byte RCS_NOT_PRESENT = (byte) 0xFF; // not present in the standard, added by this implementation

    /**
     * Present if and only if the value of the segment metadata flag in the PDU header is 1.
     */
    private final byte recordContinuationState;

    /**
     * Present if and only if the value of the segment metadata flag in the PDU header is 1.
     * 0 to 63.
     */
    private final int segmentMetadataLength;

    /**
     * Present if and only if the value of the segment metadata flag in the PDU header is 1 and the
     * segment metadata length is greater than zero.
     * Values are application-specific. Variable length, from 0 to 504 bits (8 times 63 octets).
     */
    private final byte[] segmentMetadata;

    /**
     * FSS, in octets.
     */
    private final long offset;

    /**
     * File data.
     */
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
        this.offset = BytesUtil.readInteger(pdu, currentOffset, isLargeFile() ? 8 : 4);
        currentOffset += isLargeFile() ? 8 : 4;
        this.fileData = !isCrcPresent() ? Arrays.copyOfRange(pdu, currentOffset, pdu.length) : Arrays.copyOfRange(pdu, currentOffset, pdu.length - 2); // This could be optimized only by storing the start of the file data
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
                ", fileData=" + StringUtil.toHexDump(fileData) +
                '}';
    }
}
