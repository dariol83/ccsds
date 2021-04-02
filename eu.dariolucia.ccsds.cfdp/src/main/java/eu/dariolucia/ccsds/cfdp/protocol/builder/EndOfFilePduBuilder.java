package eu.dariolucia.ccsds.cfdp.protocol.builder;

import eu.dariolucia.ccsds.cfdp.common.IntegerUtil;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.EndOfFilePdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDirectivePdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.EntityIdTLV;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class EndOfFilePduBuilder extends CfdpPduBuilder<EndOfFilePdu, EndOfFilePduBuilder> {

    private byte conditionCode;

    private int fileChecksum;

    private long fileSize;

    private EntityIdTLV faultLocation;

    public EndOfFilePduBuilder setConditionCode(byte conditionCode, EntityIdTLV faultLocation) {
        this.conditionCode = conditionCode;
        this.faultLocation = faultLocation;
        return this;
    }

    public EndOfFilePduBuilder setFileChecksum(int fileChecksum) {
        this.fileChecksum = fileChecksum;
        return this;
    }

    public EndOfFilePduBuilder setFileSize(long fileSize) {
        this.fileSize = fileSize;
        return this;
    }

    @Override
    protected int encodeDataField(ByteArrayOutputStream bos) throws IOException {
        int totalLength = 0;
        // Condition code (4 bits) and spare (4 bits)
        bos.write((this.conditionCode << 4) & 0xFF);
        totalLength += 1;
        // Checksum (4 bytes)
        bos.write(IntegerUtil.encodeInteger(this.fileChecksum, Integer.BYTES));
        totalLength += 4;
        // File size (4 or 8 bytes, check isLargeFile())
        bos.write(IntegerUtil.encodeInteger(this.fileSize, isLargeFile() ? 8 : 4));
        totalLength += isLargeFile() ? 8 : 4;
        // Fault location
        if(this.conditionCode != FileDirectivePdu.CC_NOERROR) {
            byte[] encoded = this.faultLocation.encode(true);
            bos.write(encoded);
            totalLength += encoded.length;
        }
        return totalLength;
    }

    @Override
    protected EndOfFilePdu buildObject(byte[] pdu) {
        return new EndOfFilePdu(pdu);
    }
}
