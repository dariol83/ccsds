package eu.dariolucia.ccsds.cfdp.protocol.pdu;

import eu.dariolucia.ccsds.cfdp.common.CfdpRuntimeException;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.*;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Metadata PDU - CCSDS 727.0-B-5, 5.2.5
 */
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
                case FilestoreRequestTLV.TLV_TYPE:
                    currentOffset = parseFilestoreRequest(pdu, currentOffset);
                break;
                case MessageToUserTLV.TLV_TYPE:
                    currentOffset = parseMessageToUser(pdu, currentOffset);
                break;
                case FaultHandlerOverrideTLV.TLV_TYPE:
                    currentOffset = parseFaultHandlerOverride(pdu, currentOffset);
                break;
                case FlowLabelTLV.TLV_TYPE:
                    currentOffset = parseFlowLabel(pdu, currentOffset);
                break;
                default:
                    throw new CfdpRuntimeException("TLV type not supported in Metadata PDU: " + String.format("0x%02X", type));
            }
        }
    }

    private int parseFlowLabel(byte[] pdu, int currentOffset) {
        int length = Byte.toUnsignedInt(pdu[currentOffset + 1]);
        FlowLabelTLV fr = new FlowLabelTLV(pdu, currentOffset + 2, length);
        if(fr.getLength() != length) {
            throw new CfdpRuntimeException(String.format("Length mismatch when parsing FlowLabel in Metadata PDU: read length is %d, but parsed %d", length, fr.getLength()));
        }
        options.add(fr);
        currentOffset += 2 + length;
        return currentOffset;
    }

    private int parseFaultHandlerOverride(byte[] pdu, int currentOffset) {
        int length = Byte.toUnsignedInt(pdu[currentOffset + 1]);
        FaultHandlerOverrideTLV fr = new FaultHandlerOverrideTLV(pdu, currentOffset + 2);
        if(fr.getLength() != length) {
            throw new CfdpRuntimeException(String.format("Length mismatch when parsing FaultHandlerOverride in Metadata PDU: read length is %d, but parsed %d", length, fr.getLength()));
        }
        options.add(fr);
        currentOffset += 2 + length;
        return currentOffset;
    }

    private int parseMessageToUser(byte[] pdu, int currentOffset) {
        int length = Byte.toUnsignedInt(pdu[currentOffset + 1]);
        MessageToUserTLV fr = new MessageToUserTLV(pdu, currentOffset + 2, length);
        if(fr.getLength() != length) {
            throw new CfdpRuntimeException(String.format("Length mismatch when parsing MessageToUser in Metadata PDU: read length is %d, but parsed %d", length, fr.getLength()));
        }
        options.add(fr);
        currentOffset += 2 + length;
        return currentOffset;
    }

    private int parseFilestoreRequest(byte[] pdu, int currentOffset) {
        int length = Byte.toUnsignedInt(pdu[currentOffset + 1]);
        FilestoreRequestTLV fr = new FilestoreRequestTLV(pdu, currentOffset + 2);
        if(fr.getLength() != length) {
            throw new CfdpRuntimeException(String.format("Length mismatch when parsing FilestoreRequest in Metadata PDU: read length is %d, but parsed %d", length, fr.getLength()));
        }
        options.add(fr);
        currentOffset += 2 + length;
        return currentOffset;
    }

    /**
     * If transaction closure is requested. If transaction is in Acknowledged mode,
     * set to ‘0’ and ignored.
     *
     * @return true if closure is requested (flag set to 1), otherwise false
     */
    public boolean isClosureRequested() {
        return closureRequested;
    }

    /**
     * Checksum algorithm identifier as registered in the SANA Checksum Types
     * Registry. Value zero indicates use of the legacy modular checksum.
     *
     * @return the checksum identifier
     */
    public byte getChecksumType() {
        return checksumType;
    }

    /**
     * Length of file in octets. Set to 0 means unbounded file.
     *
     * @return the file size in octets
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * Source file name. Can be null string when there is no associated file, e.g.
     * for messages used for Proxy operations.
     *
     * @return the source file name or null string if no such file name is present
     */
    public String getSourceFileName() {
        return sourceFileName;
    }

    /**
     * Destination file name. Can be null string when there is no associated file, e.g.
     * for messages used for Proxy operations.
     *
     * @return the destination file name or null string if no such file name is present
     */
    public String getDestinationFileName() {
        return destinationFileName;
    }

    /**
     * Options. Allowed ones are:
     * <ul>
     *     <li>{@link FilestoreRequestTLV}</li>
     *     <li>{@link MessageToUserTLV}</li>
     *     <li>{@link FaultHandlerOverrideTLV}</li>
     *     <li>{@link FlowLabelTLV}</li>
     * </ul>
     *
     * @return the list of options
     */
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
