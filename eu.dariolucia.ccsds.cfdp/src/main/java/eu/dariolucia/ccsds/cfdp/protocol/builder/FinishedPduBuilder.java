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
import eu.dariolucia.ccsds.cfdp.protocol.pdu.ConditionCode;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDirectivePdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.FinishedPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.EntityIdTLV;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.FilestoreResponseTLV;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Builder class for {@link eu.dariolucia.ccsds.cfdp.protocol.pdu.FinishedPdu} objects.
 */
public class FinishedPduBuilder extends CfdpPduBuilder<FinishedPdu, FinishedPduBuilder> {

    private ConditionCode conditionCode = ConditionCode.CC_NOERROR;

    private boolean dataComplete;

    private FinishedPdu.FileStatus fileStatus;

    private final List<FilestoreResponseTLV> filestoreResponses = new LinkedList<>();

    private EntityIdTLV faultLocation;

    /**
     * Construct an empty builder for this file directive PDU.
     */
    public FinishedPduBuilder() {
        setType(CfdpPdu.PduType.FILE_DIRECTIVE);
    }

    /**
     * Condition code of the acknowledged PDU, as per {@link FileDirectivePdu} CC_ constants.
     * The fault location is ignored if condition code is 'No error' or 'Unsupported checksum type'.
     * Otherwise, entity ID in the TLV is the ID of the entity at which transaction cancellation
     * was initiated.
     *
     * @param conditionCode the condition code
     * @param faultLocation the fault location
     * @return this
     */
    public FinishedPduBuilder setConditionCode(ConditionCode conditionCode, EntityIdTLV faultLocation) {
        this.conditionCode = conditionCode;
        if(conditionCode != ConditionCode.CC_NOERROR && conditionCode != ConditionCode.CC_UNSUPPORTED_CHECKSUM_TYPE) {
            this.faultLocation = faultLocation;
        } else {
            this.faultLocation = null;
        }
        return this;
    }

    /**
     * 'Data Complete' means that metadata, all file data, and EOF have been received, and the
     * checksum has been verified.
     *
     * @param dataComplete true if completed, otherwise false
     * @return this
     */
    public FinishedPduBuilder setDataComplete(boolean dataComplete) {
        this.dataComplete = dataComplete;
        return this;
    }

    /**
     * File status is meaningful only when the transaction includes the transmission of
     * file data.
     *
     * @param fileStatus the file status
     * @return this
     */
    public FinishedPduBuilder setFileStatus(FinishedPdu.FileStatus fileStatus) {
        this.fileStatus = fileStatus;
        return this;
    }

    /**
     * A filestore response TLV must be included for each filestore request TLV of the
     * Metadata PDU.
     *
     * @param r the filestore response to add
     * @return this
     */
    public FinishedPduBuilder addFilestoreResponse(FilestoreResponseTLV r) {
        this.filestoreResponses.add(r);
        return this;
    }

    @Override
    protected int encodeDataField(ByteArrayOutputStream bos) throws IOException {
        int totalLength = 0;
        // Directive code
        bos.write(FileDirectivePdu.DC_FINISHED_PDU);
        totalLength += 1;
        // Condition code (4 bits), spare bit, delivery code (1 bit) and file status (2 bits)
        byte first = (byte) ((this.conditionCode.getCode() << 4) & 0xF0);
        first |= (byte) (this.dataComplete ? 0x00 : 0x04);
        first |= (byte) (this.fileStatus.ordinal() & 0x03);
        bos.write(first);
        totalLength += 1;
        // Filestore responses
        for(FilestoreResponseTLV r : this.filestoreResponses) {
            byte[] encoded = r.encode();
            bos.write(encoded);
            totalLength += encoded.length;
        }
        // Fault location
        if(this.conditionCode != ConditionCode.CC_NOERROR && this.conditionCode != ConditionCode.CC_UNSUPPORTED_CHECKSUM_TYPE) {
            byte[] encoded = this.faultLocation.encode();
            bos.write(encoded);
            totalLength += encoded.length;
        }
        return totalLength;
    }

    @Override
    protected FinishedPdu buildObject(byte[] pdu) {
        return new FinishedPdu(pdu);
    }

    public ConditionCode getConditionCode() {
        return conditionCode;
    }

    public boolean isDataComplete() {
        return dataComplete;
    }

    public FinishedPdu.FileStatus getFileStatus() {
        return fileStatus;
    }

    public List<FilestoreResponseTLV> getFilestoreResponses() {
        return Collections.unmodifiableList(filestoreResponses);
    }

    public EntityIdTLV getFaultLocation() {
        return faultLocation;
    }
}
