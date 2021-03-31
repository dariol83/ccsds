package eu.dariolucia.ccsds.cfdp.protocol.pdu;

import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.*;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class MetadataPdu extends FileDirectivePdu {

    private final boolean closureRequested;

    private final byte checksumType;

    private final long fileSize;

    private final String sourceFileName;

    private final String destinationFileName;

    private final List<TLV> options = new LinkedList<>();

    public MetadataPdu(byte[] pdu) {
        super(pdu);
        // PDU-specific parsing
        this.closureRequested = (pdu[getHeaderLength()] & 0x40) != 0;
        this.checksumType = (byte) (pdu[getHeaderLength()] & 0x0F);
        this.fileSize = isLargeFile() ? ByteBuffer.wrap(pdu, getHeaderLength() + 1, 8).getLong() : Integer.toUnsignedLong(ByteBuffer.wrap(pdu, getHeaderLength() + 1, 4).getInt());
        int currentOffset = getHeaderLength() + 1 + (isLargeFile() ? 8 : 4);
        // Source file name
        int len = Byte.toUnsignedInt(pdu[currentOffset]);
        currentOffset += 1;
        if(len > 0) {
            this.sourceFileName = new String(pdu, currentOffset, len);
            currentOffset += len;
        } else {
            this.sourceFileName = null;
        }
        // Destination file name
        len = Byte.toUnsignedInt(pdu[currentOffset]);
        currentOffset += 1;
        if(len > 0) {
            this.destinationFileName = new String(pdu, currentOffset, len);
            currentOffset += len;
        } else {
            this.destinationFileName = null;
        }
        // Options:
        // - Filestore requests.
        // - Messages to user.
        // - Fault Handler overrides.
        // - Flow Label.
        while(currentOffset < pdu.length) {
            // TLV: Get the current tag
            byte type = pdu[currentOffset];
            switch(type) {
                case FilestoreRequestTLV.TLV_TYPE: {
                    int length = Byte.toUnsignedInt(pdu[currentOffset + 1]);
                    FilestoreRequestTLV fr = new FilestoreRequestTLV(pdu, currentOffset + 2);
                    if(fr.getLength() != length) {
                        throw new RuntimeException("Length mismatch when parsing FilestoreRequest in Metadata PDU: read length is " + length + ", but parsed " + fr.getLength());
                    }
                    options.add(fr);
                    currentOffset += 2 + length;
                }
                case MessageToUserTLV.TLV_TYPE: {
                    int length = Byte.toUnsignedInt(pdu[currentOffset + 1]);
                    MessageToUserTLV fr = new MessageToUserTLV(pdu, currentOffset + 2, length);
                    if(fr.getLength() != length) {
                        throw new RuntimeException("Length mismatch when parsing MessageToUser in Metadata PDU: read length is " + length + ", but parsed " + fr.getLength());
                    }
                    options.add(fr);
                    currentOffset += 2 + length;
                }
                break;
                case FaultHandlerOverrideTLV.TLV_TYPE: {
                    int length = Byte.toUnsignedInt(pdu[currentOffset + 1]);
                    FaultHandlerOverrideTLV fr = new FaultHandlerOverrideTLV(pdu, currentOffset + 2);
                    if(fr.getLength() != length) {
                        throw new RuntimeException("Length mismatch when parsing FaultHandlerOverride in Metadata PDU: read length is " + length + ", but parsed " + fr.getLength());
                    }
                    options.add(fr);
                    currentOffset += 2 + length;
                }
                break;
                case FlowLabelTLV.TLV_TYPE: {
                    int length = Byte.toUnsignedInt(pdu[currentOffset + 1]);
                    FlowLabelTLV fr = new FlowLabelTLV(pdu, currentOffset + 2, length);
                    if(fr.getLength() != length) {
                        throw new RuntimeException("Length mismatch when parsing FlowLabel in Metadata PDU: read length is " + length + ", but parsed " + fr.getLength());
                    }
                    options.add(fr);
                    currentOffset += 2 + length;
                }
                break;
                default: {
                    throw new RuntimeException("TLV type not supported in Metadata PDU: " + String.format("0x%02X", type));
                }
            }
        }
    }

    public boolean isClosureRequested() {
        return closureRequested;
    }

    public byte getChecksumType() {
        return checksumType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public String getDestinationFileName() {
        return destinationFileName;
    }

    public List<TLV> getOptions() {
        return Collections.unmodifiableList(options);
    }

    @Override
    public String toString() {
        return super.toString() + " MetadataPdu{" +
                "closureRequested=" + closureRequested +
                ", checksumType=" + checksumType +
                ", fileSize=" + fileSize +
                ", sourceFileName='" + sourceFileName + '\'' +
                ", destinationFileName='" + destinationFileName + '\'' +
                ", options=" + options +
                '}';
    }
}
