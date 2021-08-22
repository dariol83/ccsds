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
        if(pdu[getHeaderLength()] != DirectiveCode.DC_PROMPT_PDU.getCode()) {
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
