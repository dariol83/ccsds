package eu.dariolucia.ccsds.cfdp.protocol.builder;

import eu.dariolucia.ccsds.cfdp.common.BytesUtil;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDirectivePdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.NakPdu;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Builder class for {@link eu.dariolucia.ccsds.cfdp.protocol.pdu.NakPdu} objects.
 */
public class NakPduBuilder extends CfdpPduBuilder<NakPdu, NakPduBuilder> {

    private long startOfScope;

    private long endOfScope;

    private final List<NakPdu.SegmentRequest> segmentRequests = new LinkedList<>();

    /**
     * Construct an empty builder for this file directive PDU.
     */
    public NakPduBuilder() {
        setType(CfdpPdu.PduType.FILE_DIRECTIVE);
    }

    /**
     * Set the start of scope.
     *
     * @param startOfScope the start of scope (offset in bytes)
     * @return this
     */
    public NakPduBuilder setStartOfScope(long startOfScope) {
        this.startOfScope = startOfScope;
        return this;
    }

    /**
     * Add a (not null) segment request.
     *
     * @param r the segment request to add
     * @return this
     */
    public NakPduBuilder addSegmentRequest(NakPdu.SegmentRequest r) {
        if(r == null) {
            throw new NullPointerException("Segment request is null");
        }
        this.segmentRequests.add(r);
        return this;
    }

    /**
     * Clear all currently added segment requests.
     *
     * @return this
     */
    public NakPduBuilder clearSegmentRequests() {
        this.segmentRequests.clear();
        return this;
    }

    /**
     * Set the end of scope.
     *
     * @param endOfScope the end of the scope (offset in bytes)
     * @return this
     */
    public NakPduBuilder setEndOfScope(long endOfScope) {
        this.endOfScope = endOfScope;
        return this;
    }

    @Override
    protected int encodeDataField(ByteArrayOutputStream bos) throws IOException {
        int totalLength = 0;
        // Directive code
        bos.write(FileDirectivePdu.DC_NACK_PDU);
        totalLength += 1;
        // Start of scope (4 or 8 bytes, check isLargeFile())
        bos.write(BytesUtil.encodeInteger(this.startOfScope, isLargeFile() ? 8 : 4));
        totalLength += isLargeFile() ? 8 : 4;
        // End of scope (4 or 8 bytes, check isLargeFile())
        bos.write(BytesUtil.encodeInteger(this.endOfScope, isLargeFile() ? 8 : 4));
        totalLength += isLargeFile() ? 8 : 4;
        // Segment requests
        for(NakPdu.SegmentRequest sr : this.segmentRequests) {
            // Start of segment (4 or 8 bytes, check isLargeFile())
            bos.write(BytesUtil.encodeInteger(sr.getStartOffset(), isLargeFile() ? 8 : 4));
            totalLength += isLargeFile() ? 8 : 4;
            // End of segment (4 or 8 bytes, check isLargeFile())
            bos.write(BytesUtil.encodeInteger(sr.getEndOffset(), isLargeFile() ? 8 : 4));
            totalLength += isLargeFile() ? 8 : 4;
        }
        return totalLength;
    }

    @Override
    protected NakPdu buildObject(byte[] pdu) {
        return new NakPdu(pdu);
    }
}
