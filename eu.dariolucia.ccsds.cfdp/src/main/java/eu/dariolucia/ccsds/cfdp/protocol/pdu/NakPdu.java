package eu.dariolucia.ccsds.cfdp.protocol.pdu;

import eu.dariolucia.ccsds.cfdp.common.BytesUtil;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;


/**
 * Nak PDU - CCSDS 727.0-B-5, 5.2.6
 */
public class NakPdu extends FileDirectivePdu {

    public static final class SegmentRequest {
        private final long startOffset;
        private final long endOffset;

        public SegmentRequest(long startOffset, long endOffset) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        public long getStartOffset() {
            return startOffset;
        }

        public long getEndOffset() {
            return endOffset;
        }

        public boolean overlapWith(long start, long end) {
            return (start >= startOffset && start <= endOffset) ||
                    (end >= startOffset && end <= endOffset);
        }
    }

    private final long startOfScope;

    private final long endOfScope;

    private final List<SegmentRequest> segmentRequests = new LinkedList<>();

    public NakPdu(byte[] pdu) {
        super(pdu);
        // Directive code check
        if(pdu[getHeaderLength()] != FileDirectivePdu.DC_NACK_PDU) {
            throw new IllegalArgumentException("Directive code mismatch: " + String.format("0x%02X",pdu[getHeaderLength()]));
        }
        // PDU-specific parsing
        int currentOffset = getDirectiveParameterIndex();
        this.startOfScope = BytesUtil.readInteger(pdu, currentOffset, isLargeFile() ? 8 : 4);
        currentOffset += isLargeFile() ? 8 : 4;
        this.endOfScope = BytesUtil.readInteger(pdu, currentOffset, isLargeFile() ? 8 : 4);
        currentOffset += isLargeFile() ? 8 : 4;
        while (currentOffset < pdu.length) {
            long startOffset = BytesUtil.readInteger(pdu, currentOffset, isLargeFile() ? 8 : 4);
            currentOffset += isLargeFile() ? 8 : 4;
            long endOffset = BytesUtil.readInteger(pdu, currentOffset, isLargeFile() ? 8 : 4);
            currentOffset += isLargeFile() ? 8 : 4;
            segmentRequests.add(new SegmentRequest(startOffset, endOffset));
        }
    }

    /**
     * Start of scope (offset in bytes).
     *
     * @return start of scope
     */
    public long getStartOfScope() {
        return startOfScope;
    }

    /**
     * End of scope (offset in bytes).
     *
     * @return end of scope
     */
    public long getEndOfScope() {
        return endOfScope;
    }

    public List<SegmentRequest> getSegmentRequests() {
        return Collections.unmodifiableList(segmentRequests);
    }

    @Override
    public String toString() {
        return super.toString() + " NakPdu{" +
                "startOfScope=" + startOfScope +
                ", endOfScope=" + endOfScope +
                ", segmentRequests=" + segmentRequests +
                '}';
    }
}
