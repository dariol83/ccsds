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

import eu.dariolucia.ccsds.cfdp.common.BytesUtil;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDirectivePdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.KeepAlivePdu;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Builder class for {@link eu.dariolucia.ccsds.cfdp.protocol.pdu.KeepAlivePdu} objects.
 */
public class KeepAlivePduBuilder extends CfdpPduBuilder<KeepAlivePdu, KeepAlivePduBuilder> {

    private long progress;

    /**
     * Construct an empty builder for this file directive PDU.
     */
    public KeepAlivePduBuilder() {
        setType(CfdpPdu.PduType.FILE_DIRECTIVE);
    }

    /**
     * In octets. Offset from the start of the file.
     *
     * @param progress offset from the start of the file in octets
     * @return this
     */
    public KeepAlivePduBuilder setProgress(long progress) {
        this.progress = progress;
        return this;
    }

    @Override
    protected int encodeDataField(ByteArrayOutputStream bos) throws IOException {
        int totalLength = 0;
        // Directive code
        bos.write(FileDirectivePdu.DC_KEEPALIVE_PDU);
        totalLength += 1;
        // Progress (4 or 8 bytes, check isLargeFile())
        bos.write(BytesUtil.encodeInteger(this.progress, isLargeFile() ? 8 : 4));
        totalLength += isLargeFile() ? 8 : 4;
        return totalLength;
    }

    @Override
    protected KeepAlivePdu buildObject(byte[] pdu) {
        return new KeepAlivePdu(pdu);
    }
}
