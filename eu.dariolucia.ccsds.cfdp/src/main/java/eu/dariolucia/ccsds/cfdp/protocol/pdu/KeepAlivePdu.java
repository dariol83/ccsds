package eu.dariolucia.ccsds.cfdp.protocol.pdu;

import eu.dariolucia.ccsds.cfdp.common.IntegerUtil;

public class KeepAlivePdu extends FileDirectivePdu {

    private final long progress;

    public KeepAlivePdu(byte[] pdu) {
        super(pdu);
        // PDU-specific parsing
        this.progress = IntegerUtil.readInteger(pdu, getHeaderLength(), isLargeFile() ? 8 : 4);
    }

    public long getProgress() {
        return progress;
    }

    @Override
    public String toString() {
        return super.toString() + " KeepAlivePdu{" +
                "progress=" + progress +
                '}';
    }
}
