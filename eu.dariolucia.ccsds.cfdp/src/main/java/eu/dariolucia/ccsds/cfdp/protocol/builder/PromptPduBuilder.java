/*
 *   Copyright (c) 2021 Dario Lucia (https://www.dariolucia.eu)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and 
 *   limitations under the License.
 */

package eu.dariolucia.ccsds.cfdp.protocol.builder;

import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.DirectiveCode;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.PromptPdu;

import java.io.ByteArrayOutputStream;

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
    protected int encodeDataField(ByteArrayOutputStream bos) {
        int totalLength = 0;
        // Directive code
        bos.write(DirectiveCode.DC_PROMPT_PDU.getCode());
        totalLength += 1;
        //
        bos.write(this.nakResponseRequired ? 0x00 : 0x80);
        totalLength += 1;
        return totalLength;
    }

    @Override
    protected PromptPdu buildObject(byte[] pdu) {
        return new PromptPdu(pdu);
    }

    public boolean isNakResponse() {
        return nakResponseRequired;
    }

    public boolean isKeepAliveResponse() {
        return !nakResponseRequired;
    }
}
