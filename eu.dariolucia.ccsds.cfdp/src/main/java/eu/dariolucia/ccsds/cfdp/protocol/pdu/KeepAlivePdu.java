package eu.dariolucia.ccsds.cfdp.protocol.pdu;

import eu.dariolucia.ccsds.cfdp.common.BytesUtil;

/**
 * KeepAlive PDU - CCSDS 727.0-B-5, 5.2.8
 */
public class KeepAlivePdu extends FileDirectivePdu {

    private final long progress;

    public KeepAlivePdu(byte[] pdu) {
        super(pdu);
        // Directive code check
        if(pdu[getHeaderLength()] != FileDirectivePdu.DC_KEEPALIVE_PDU) {
            throw new IllegalArgumentException("Directive code mismatch: " + String.format("0x%02X",pdu[getHeaderLength()]));
        }
        // PDU-specific parsing
        this.progress = BytesUtil.readInteger(pdu, getDirectiveParameterIndex(), isLargeFile() ? 8 : 4);
    }

    /**
     * In octets. Offset from the start of the file.
     * @return the offset in octets from the start of the file
     */
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
