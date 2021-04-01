package eu.dariolucia.ccsds.cfdp.protocol.pdu;

public class PromptPdu extends FileDirectivePdu {

    private final boolean nakResponseRequired;

    private final boolean keepAliveResponseRequired;

    public PromptPdu(byte[] pdu) {
        super(pdu);
        // PDU-specific parsing
        this.nakResponseRequired = (pdu[getHeaderLength()] & 0x80) == 0;
        this.keepAliveResponseRequired = !this.nakResponseRequired;
    }

    public boolean isNakResponseRequired() {
        return nakResponseRequired;
    }

    public boolean isKeepAliveResponseRequired() {
        return keepAliveResponseRequired;
    }

    @Override
    public String toString() {
        return super.toString() + " PromptPdu{" +
                "nakResponseRequired=" + nakResponseRequired +
                ", keepAliveResponseRequired=" + keepAliveResponseRequired +
                '}';
    }
}
