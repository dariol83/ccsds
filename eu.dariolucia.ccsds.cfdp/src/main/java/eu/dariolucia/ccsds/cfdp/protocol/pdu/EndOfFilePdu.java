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

import eu.dariolucia.ccsds.cfdp.common.CfdpRuntimeException;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.EntityIdTLV;

import java.nio.ByteBuffer;

/**
 * End of File PDU - CCSDS 727.0-B-5, 5.2.2
 */
public class EndOfFilePdu extends FileDirectivePdu {

    /**
     * Condition code.
     */
    private final ConditionCode conditionCode;

    /**
     * The checksum shall be computed over the file data and inserted into the EOF (No
     * error) PDU by the sending entity.
     */
    private final int fileChecksum;

    /**
     * In octets. This value shall be the total number of file data octets
     * transmitted by the sender, regardless of the condition code (i.e., it
     * shall be supplied even if the condition code is other than 'No error').
     */
    private final long fileSize;

    /**
     * Omitted if condition code is 'No error'. Otherwise, entity ID in the
     * TLV is the ID of the entity at which transaction cancellation was
     * initiated.
     */
    private final EntityIdTLV faultLocation;

    public EndOfFilePdu(byte[] pdu) {
        super(pdu);
        // Directive code check
        if(pdu[getHeaderLength()] != DirectiveCode.DC_EOF_PDU.getCode()) {
            throw new IllegalArgumentException("Directive code mismatch: " + String.format("0x%02X",pdu[getHeaderLength()]));
        }
        // PDU-specific parsing
        this.conditionCode = ConditionCode.fromCode((byte) ((pdu[getDirectiveParameterIndex()] & 0xF0) >>> 4));
        this.fileChecksum = ByteBuffer.wrap(pdu, getDirectiveParameterIndex() + 1, 4).getInt();
        this.fileSize = isLargeFile() ? ByteBuffer.wrap(pdu, getDirectiveParameterIndex() + 1 + 4, 8).getLong() : Integer.toUnsignedLong(ByteBuffer.wrap(pdu, getDirectiveParameterIndex() + 1 + 4, 4).getInt());
        // Let's check the condition code
        if(this.conditionCode == ConditionCode.CC_NOERROR) {
            // Fault location omitted if condition code is 'No error'.
            this.faultLocation = null;
        } else {
            // Otherwise, entity ID in the TLV is the ID of the entity at which transaction cancellation was initiated.
            // The Type of the Entity ID TLV shall be 06 hex; the Value shall be an Entity ID
            int currentOffset = getDirectiveParameterIndex() + 1 + 4 + (isLargeFile() ? 8 : 4);
            byte type = pdu[currentOffset];
            if(type != EntityIdTLV.TLV_TYPE) {
                throw new CfdpRuntimeException("Cannot parse Fault Location type in End-Of-File PDU: expected " + EntityIdTLV.TLV_TYPE + ", got " + String.format("0x%02X", type));
            }
            int length = Byte.toUnsignedInt(pdu[currentOffset + 1]);
            this.faultLocation = new EntityIdTLV(pdu, currentOffset + 2, length);
        }
    }

    public ConditionCode getConditionCode() {
        return conditionCode;
    }

    public int getFileChecksum() {
        return fileChecksum;
    }

    public long getFileSize() {
        return fileSize;
    }

    public EntityIdTLV getFaultLocation() {
        return faultLocation;
    }

    @Override
    public String toString() {
        return super.toString() + " EndOfFilePdu{" +
                "conditionCode=" + conditionCode +
                ", fileChecksum=" + fileChecksum +
                ", fileSize=" + fileSize +
                ", faultLocation=" + faultLocation +
                '}';
    }
}
