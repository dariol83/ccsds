package eu.dariolucia.ccsds.cfdp.protocol.pdu;


/**
 * Prompt PDU - CCSDS 727.0-B-5, 5.2.7
 */
public class PromptPdu extends FileDirectivePdu {

    private final boolean nakResponseRequired;

    private final boolean keepAliveResponseRequired;

    public PromptPdu(byte[] pdu) {
        super(pdu);
        // Directive code check
        if(pdu[getHeaderLength()] != FileDirectivePdu.DC_PROMPT_PDU) {
            throw new IllegalArgumentException("Directive code mismatch: " + String.format("0x%02X",pdu[getHeaderLength()]));
        }
        // PDU-specific parsing
        this.nakResponseRequired = (pdu[getDirectiveParameterIndex()] & 0x80) == 0;
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
