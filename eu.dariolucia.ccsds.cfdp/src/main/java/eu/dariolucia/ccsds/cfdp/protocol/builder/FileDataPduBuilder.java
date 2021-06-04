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

package eu.dariolucia.ccsds.cfdp.protocol.builder;

import eu.dariolucia.ccsds.cfdp.common.BytesUtil;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDataPdu;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Builder class for {@link eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDataPdu} objects.
 */
public class FileDataPduBuilder extends CfdpPduBuilder<FileDataPdu, FileDataPduBuilder> {

    private byte recordContinuationState;

    private int segmentMetadataLength;

    private byte[] segmentMetadata;

    private long offset;

    private byte[] fileData;

    /**
     * Construct an empty builder for this file data PDU.
     */
    public FileDataPduBuilder() {
        setType(CfdpPdu.PduType.FILE_DATA);
        setRecordContinuationState(FileDataPdu.RCS_NOT_PRESENT);
        setSegmentMetadata(null);
    }

    public FileDataPduBuilder setRecordContinuationState(byte recordContinuationState) {
        this.recordContinuationState = recordContinuationState;
        return this;
    }

    public FileDataPduBuilder setSegmentMetadata(byte[] segmentMetadata) {
        this.segmentMetadata = segmentMetadata;
        this.segmentMetadataLength = segmentMetadata != null ? segmentMetadata.length : -1;
        return this;
    }

    public FileDataPduBuilder setOffset(long offset) {
        this.offset = offset;
        return this;
    }

    public FileDataPduBuilder setFileData(byte[] fileData) {
        this.fileData = fileData;
        return this;
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
    protected int encodeDataField(ByteArrayOutputStream bos) throws IOException {
        int totalLength = 0;
        // Metadata present only if segment metadata in header is 1
        if(isSegmentMetadataPresent()) {
            byte first = (byte) ((this.recordContinuationState << 6) & 0xC0);
            first |= (byte) (this.segmentMetadataLength & 0x3F);
            bos.write(first);
            totalLength += 1;
            if(this.segmentMetadataLength > 0) {
                bos.write(this.segmentMetadata);
                totalLength += this.segmentMetadataLength;
            }
        }
        // Offset
        bos.write(BytesUtil.encodeInteger(this.offset, isLargeFile() ? 8 : 4));
        totalLength += isLargeFile() ? 8 : 4;
        // Data
        bos.write(this.fileData);
        totalLength += this.fileData.length;

        return totalLength;
    }

    @Override
    protected FileDataPdu buildObject(byte[] pdu) {
        return new FileDataPdu(pdu);
    }

    @Override
    protected int getInitialBufferAllocation() {
        return super.getInitialBufferAllocation() + this.fileData.length;
    }
}
