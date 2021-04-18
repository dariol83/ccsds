package eu.dariolucia.ccsds.cfdp.protocol.builder;

import eu.dariolucia.ccsds.cfdp.common.BytesUtil;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.MetadataPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

/**
 * Builder class for {@link eu.dariolucia.ccsds.cfdp.protocol.pdu.MetadataPdu} objects.
 */
public class MetadataPduBuilder extends CfdpPduBuilder<MetadataPdu, MetadataPduBuilder> {

    private boolean closureRequested;

    private byte checksumType;

    private long fileSize;

    private String sourceFileName;

    private String destinationFileName;

    private final List<TLV> options = new LinkedList<>();

    /**
     * Construct an empty builder for this file directive PDU.
     */
    public MetadataPduBuilder() {
        setType(CfdpPdu.PduType.FILE_DIRECTIVE);
    }

    /**
     * If transaction closure is requested. If transaction is in Acknowledged mode,
     * set to ‘0’ and ignored.
     *
     * @param closureRequested true if the transaction closure is requested, otherwise false
     * @return this
     */
    public MetadataPduBuilder setClosureRequested(boolean closureRequested) {
        this.closureRequested = closureRequested;
        return this;
    }

    /**
     * Set the checksum algorithm identifier as registered in the SANA Checksum Types
     * Registry. Value zero indicates use of the legacy modular checksum.
     *
     * @param checksumType the checksum identifier
     * @return this
     */
    public MetadataPduBuilder setChecksumType(byte checksumType) {
        this.checksumType = checksumType;
        return this;
    }

    /**
     * Length of file in octets. Set to 0 means unbounded file.
     *
     * @param fileSize the file size in octets
     * @return this
     */
    public MetadataPduBuilder setFileSize(long fileSize) {
        this.fileSize = fileSize;
        return this;
    }

    /**
     * Source file name. Can be null string when there is no associated file, e.g.
     * for messages used for Proxy operations.
     *
     * @param sourceFileName the source file name or null
     * @return this
     */
    public MetadataPduBuilder setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
        return this;
    }

    /**
     * Destination file name. Can be null string when there is no associated file, e.g.
     * for messages used for Proxy operations.
     *
     * @param destinationFileName the destination file name or null
     * @return this
     */
    public MetadataPduBuilder setDestinationFileName(String destinationFileName) {
        this.destinationFileName = destinationFileName;
        return this;
    }

    /**
     * Add a (not null) option. Allowed ones are:
     * <ul>
     *     <li>{@link FilestoreRequestTLV}</li>
     *     <li>{@link MessageToUserTLV}</li>
     *     <li>{@link FaultHandlerOverrideTLV}</li>
     *     <li>{@link FlowLabelTLV}</li>
     * </ul>
     *
     * @param r the option to add
     * @return this
     */
    public MetadataPduBuilder addOption(TLV r) {
        if(r == null) {
            throw new NullPointerException("TLV option is null");
        }
        if(r instanceof FilestoreRequestTLV || r instanceof MessageToUserTLV || r instanceof  FaultHandlerOverrideTLV || r instanceof FlowLabelTLV) {
            this.options.add(r);
            return this;
        } else {
            throw new IllegalArgumentException("TLV option type " + r.getClass().getName() + " not supported");
        }
    }

    /**
     * Clear all currently added options.
     *
     * @return this
     */
    public MetadataPduBuilder clearOptions() {
        this.options.clear();
        return this;
    }

    @Override
    protected int encodeDataField(ByteArrayOutputStream bos) throws IOException {
        int totalLength = 0;
        // First byte: 1 bit spare - 1 bit closure - 2 bits spare - 4 bits checksum type
        byte firstByte = this.closureRequested && !isAcknowledged() ? (byte) 0x40 : (byte) 0x00;
        firstByte |= this.checksumType & 0xFF;
        bos.write(firstByte & 0xFF);
        totalLength += 1;
        // File size
        bos.write(BytesUtil.encodeInteger(this.fileSize, isLargeFile() ? 8 : 4));
        totalLength += isLargeFile() ? 8 : 4;
        // Source file name
        ByteBuffer tempBuffer = ByteBuffer.allocate(1 + (this.sourceFileName == null ? 0 : this.sourceFileName.length()));
        BytesUtil.writeLVString(tempBuffer, this.sourceFileName);
        bos.write(tempBuffer.array());
        totalLength += tempBuffer.capacity();
        // Destination file name
        tempBuffer = ByteBuffer.allocate(1 + (this.destinationFileName == null ? 0 : this.destinationFileName.length()));
        BytesUtil.writeLVString(tempBuffer, this.destinationFileName);
        bos.write(tempBuffer.array());
        totalLength += tempBuffer.capacity();
        // Options
        for(TLV r : this.options) {
            byte[] encoded = r.encode(true);
            bos.write(encoded);
            totalLength += encoded.length;
        }
        return totalLength;
    }

    @Override
    protected MetadataPdu buildObject(byte[] pdu) {
        return new MetadataPdu(pdu);
    }
}
