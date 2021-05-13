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
