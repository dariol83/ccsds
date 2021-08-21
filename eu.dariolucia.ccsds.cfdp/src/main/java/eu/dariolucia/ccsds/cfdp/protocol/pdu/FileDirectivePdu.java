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
 * Parent class for file directive PDUs
 */
public class FileDirectivePdu extends CfdpPdu {

    public static final byte DC_EOF_PDU = 0x04;
    public static final byte DC_FINISHED_PDU = 0x05;
    public static final byte DC_ACK_PDU = 0x06;
    public static final byte DC_METADATA_PDU = 0x07;
    public static final byte DC_NACK_PDU = 0x08;
    public static final byte DC_PROMPT_PDU = 0x09;
    public static final byte DC_KEEPALIVE_PDU = 0x0C;

    public FileDirectivePdu(byte[] pdu) {
        super(pdu);
    }

    public int getDirectiveParameterIndex() {
        return getHeaderLength() + 1;
    }
}
