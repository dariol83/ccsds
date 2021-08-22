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

import eu.dariolucia.ccsds.cfdp.common.BytesUtil;

/**
 * KeepAlive PDU - CCSDS 727.0-B-5, 5.2.8
 */
public class KeepAlivePdu extends FileDirectivePdu {

    private final long progress;

    public KeepAlivePdu(byte[] pdu) {
        super(pdu);
        // Directive code check
        if(pdu[getHeaderLength()] != DirectiveCode.DC_KEEPALIVE_PDU.getCode()) {
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
