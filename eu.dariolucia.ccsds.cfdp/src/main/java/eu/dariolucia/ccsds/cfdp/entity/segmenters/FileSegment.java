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

public class FileSegment {

    public static FileSegment eof() {
        return new FileSegment(0, null, null, FileDataPdu.RCS_NOT_PRESENT, true);
    }

    public static FileSegment segment(long offset, byte[] data) {
        return new FileSegment(offset, data, null, FileDataPdu.RCS_NOT_PRESENT, false);
    }

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

    public long getOffset() {
        return offset;
    }

    public byte[] getData() {
        return data;
    }

    public byte[] getMetadata() {
        return metadata;
    }

    public byte getRecordContinuationState() {
        return recordContinuationState;
    }

    public boolean isEof() {
        return eof;
    }

    @Override
    public String toString() {
        return "FileSegment{" +
                "offset=" + offset +
                ", data=" + StringUtil.toHexDump(data) +
                ", metadata=" + StringUtil.toHexDump(metadata) +
                ", recordContinuationState=" + recordContinuationState +
                ", eof=" + eof +
                '}';
    }
}
