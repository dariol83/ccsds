package eu.dariolucia.ccsds.cfdp.protocol.builder;

import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.PromptPdu;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Builder class for {@link eu.dariolucia.ccsds.cfdp.protocol.pdu.PromptPdu} objects.
 */
public class PromptPduBuilder extends CfdpPduBuilder<PromptPdu, PromptPduBuilder> {

    private boolean nakResponseRequired;

    /**
     * Construct an empty builder for this file directive PDU.
     */
    public PromptPduBuilder() {
        setType(CfdpPdu.PduType.FILE_DIRECTIVE);
    }

    /**
     * Set the NAK response.
     *
     * @return this
     */
    public PromptPduBuilder setNakResponse() {
        this.nakResponseRequired = true;
        return this;
    }

    /**
     * Set the NAK response.
     *
     * @return this
     */
    public PromptPduBuilder setKeepAliveResponse() {
        this.nakResponseRequired = false;
        return this;
    }

    @Override
    protected int encodeDataField(ByteArrayOutputStream bos) throws IOException {
        int totalLength = 0;
        //
        bos.write(this.nakResponseRequired ? 0x00 : 0x80);
        totalLength += 1;
        return totalLength;
    }

    @Override
    protected PromptPdu buildObject(byte[] pdu) {
        return new PromptPdu(pdu);
    }
}
