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
import eu.dariolucia.ccsds.cfdp.protocol.pdu.EndOfFilePdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDirectivePdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.EntityIdTLV;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Builder class for {@link EndOfFilePdu} objects.
 */
public class EndOfFilePduBuilder extends CfdpPduBuilder<EndOfFilePdu, EndOfFilePduBuilder> {

    private byte conditionCode;

    private int fileChecksum;

    private long fileSize;

    private EntityIdTLV faultLocation;

    /**
     * Construct an empty builder for this file directive PDU.
     */
    public EndOfFilePduBuilder() {
        setType(CfdpPdu.PduType.FILE_DIRECTIVE);
    }

    /**
     * Condition code of the acknowledged PDU, as per {@link eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDirectivePdu} CC_ constants.
     * The fault location is ignored if condition code is 'No error'. Otherwise, entity ID in the
     * TLV is the ID of the entity at which transaction cancellation was initiated.
     * @param conditionCode the condition code
     * @param faultLocation the fault location
     * @return this
     */
    public EndOfFilePduBuilder setConditionCode(byte conditionCode, EntityIdTLV faultLocation) {
        this.conditionCode = conditionCode;
        if(conditionCode != FileDirectivePdu.CC_NOERROR) {
            this.faultLocation = faultLocation;
        } else {
            this.faultLocation = null;
        }
        return this;
    }

    /**
     * The checksum shall be computed over the file data and inserted into the EOF (No
     * error) PDU by the sending entity.
     *
     * @param fileChecksum the file checksum
     * @return this
     */
    public EndOfFilePduBuilder setFileChecksum(int fileChecksum) {
        this.fileChecksum = fileChecksum;
        return this;
    }

    /**
     * In octets. This value shall be the total number of file data octets
     * transmitted by the sender, regardless of the condition code (i.e., it
     * shall be supplied even if the condition code is other than 'No error').
     *
     * @param fileSize the file size
     * @return this
     */
    public EndOfFilePduBuilder setFileSize(long fileSize) {
        this.fileSize = fileSize;
        return this;
    }

    public byte getConditionCode() {
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
    protected int encodeDataField(ByteArrayOutputStream bos) throws IOException {
        int totalLength = 0;
        // Directive code
        bos.write(FileDirectivePdu.DC_EOF_PDU);
        totalLength += 1;
        // Condition code (4 bits) and spare (4 bits)
        bos.write((this.conditionCode << 4) & 0xFF);
        totalLength += 1;
        // Checksum (4 bytes)
        bos.write(BytesUtil.encodeInteger(this.fileChecksum, Integer.BYTES));
        totalLength += 4;
        // File size (4 or 8 bytes, check isLargeFile())
        bos.write(BytesUtil.encodeInteger(this.fileSize, isLargeFile() ? 8 : 4));
        totalLength += isLargeFile() ? 8 : 4;
        // Fault location
        if(this.conditionCode != FileDirectivePdu.CC_NOERROR) {
            byte[] encoded = this.faultLocation.encode();
            bos.write(encoded);
            totalLength += encoded.length;
        }
        return totalLength;
    }

    @Override
    protected EndOfFilePdu buildObject(byte[] pdu) {
        return new EndOfFilePdu(pdu);
    }
}
