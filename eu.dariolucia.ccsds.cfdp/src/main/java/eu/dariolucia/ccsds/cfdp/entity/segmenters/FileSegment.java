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

package eu.dariolucia.ccsds.cfdp.entity.segmenters;

import eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDataPdu;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;

/**
 * A file segment, with optional metadata and record continuation state information. A special file segment is the eof file
 * segment, which carries no data and indicates that there is no more data to deliver (EOF reached).
 */
public class FileSegment {

    /**
     * Build a EOF file segment.
     *
     * @return an EOF file segment
     */
    public static FileSegment eof() {
        return new FileSegment(-1, null, null, FileDataPdu.RCS_NOT_PRESENT, true);
    }

    /**
     * Build a file segment having the provided offset and data
     * @param offset the offset in bytes
     * @param data the data of the file segment
     * @return the data file segment
     */
    public static FileSegment segment(long offset, byte[] data) {
        return new FileSegment(offset, data, null, FileDataPdu.RCS_NOT_PRESENT, false);
    }

    /**
     * Build a file segment having the provided offset, data, metadata and record continuation state
     * @param offset the offset in bytes
     * @param data the data of the file segment
     * @param metadata the value of the metadata for this specific segment/record
     * @param recordContinuationState the record continuation state (see {@link FileDataPdu constants}
     * @return the file segment
     */
    public static FileSegment segment(long offset, byte[] data, byte[] metadata, byte recordContinuationState) {
        return new FileSegment(offset, data, metadata, recordContinuationState, false);
    }

    private final long offset;
    private final byte[] data;

    private final byte[] metadata;

    private final byte recordContinuationState;

    private final boolean eof;

    private FileSegment(long offset, byte[] data, byte[] metadata, byte recordContinuationState, boolean eof) {
        this.offset = offset;
        this.data = data;
        this.metadata = metadata;
        this.recordContinuationState = recordContinuationState;
        this.eof = eof;
    }

    /**
     * Offset in bytes of the beginning of this file segment with respect to the start of the file.
     *
     * @return the offset, -1 if it the file segment is EOF
     */
    public long getOffset() {
        return offset;
    }

    /**
     * The file segment data.
     *
     * @return the data, is null if the file segment is EOF
     */
    public byte[] getData() {
        return data;
    }

    /**
     * The file segment metadata.
     *
     * @return the metadata, can be null; it is null if the file segment is EOF
     */
    public byte[] getMetadata() {
        return metadata;
    }

    /**
     * The record continuation state.
     *
     * @return the record continuation state; it is {@link FileDataPdu#RCS_NOT_PRESENT} if the file segment is EOF
     */
    public byte getRecordContinuationState() {
        return recordContinuationState;
    }

    /**
     * The EOF flag for the file segment.
     *
     * @return true if the file segment is a EOF indication, otherwise false
     */
    public boolean isEof() {
        return eof;
    }

    @Override
    public String toString() {
        return "FileSegment{" +
                "offset=" + offset +
                ", data=" + (data != null ? StringUtil.toHexDump(data) : "<null>") +
                ", metadata=" + (metadata != null ? StringUtil.toHexDump(metadata) : "<null>") +
                ", recordContinuationState=" + recordContinuationState +
                ", eof=" + eof +
                '}';
    }
}
