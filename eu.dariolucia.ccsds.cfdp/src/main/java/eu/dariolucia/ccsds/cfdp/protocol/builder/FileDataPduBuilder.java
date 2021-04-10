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
        setSegmentMetadata(new byte[0]);
    }

    public FileDataPduBuilder setRecordContinuationState(byte recordContinuationState) {
        this.recordContinuationState = recordContinuationState;
        return this;
    }

    public FileDataPduBuilder setSegmentMetadata(byte[] segmentMetadata) {
        this.segmentMetadata = segmentMetadata;
        this.segmentMetadataLength = segmentMetadata != null ? segmentMetadata.length : 0;
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
        if(isLargeFile()) {
            bos.write(BytesUtil.encodeInteger(this.offset, 8));
            totalLength += 8;
        } else {
            bos.write(BytesUtil.encodeInteger(this.offset, 4));
            totalLength += 4;
        }
        // Data
        bos.write(this.fileData);
        totalLength += this.fileData.length;

        return totalLength;
    }

    @Override
    protected FileDataPdu buildObject(byte[] pdu) {
        return new FileDataPdu(pdu);
    }
}
