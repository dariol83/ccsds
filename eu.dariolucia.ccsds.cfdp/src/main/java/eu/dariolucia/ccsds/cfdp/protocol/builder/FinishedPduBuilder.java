package eu.dariolucia.ccsds.cfdp.protocol.builder;

import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDirectivePdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.FinishedPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.EntityIdTLV;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.FilestoreResponseTLV;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Builder class for {@link eu.dariolucia.ccsds.cfdp.protocol.pdu.FinishedPdu} objects.
 */
public class FinishedPduBuilder extends CfdpPduBuilder<FinishedPdu, FinishedPduBuilder> {

    private byte conditionCode;

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
    public FinishedPduBuilder setConditionCode(byte conditionCode, EntityIdTLV faultLocation) {
        this.conditionCode = conditionCode;
        if(conditionCode != FileDirectivePdu.CC_NOERROR && conditionCode != FileDirectivePdu.CC_UNSUPPORTED_CHECKSUM_TYPE) {
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
     * @param dataComplete
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

    /**
     * Clear all currently added filestore response TLVs.
     *
     * @return this
     */
    public FinishedPduBuilder clearFilestoreResponses() {
        this.filestoreResponses.clear();
        return this;
    }

    @Override
    protected int encodeDataField(ByteArrayOutputStream bos) throws IOException {
        int totalLength = 0;
        // Condition code (4 bits), spare bit, delivery code (1 bit) and file status (2 bits)
        byte first = (byte) ((this.conditionCode << 4) & 0xF0);
        first |= (byte) (this.dataComplete ? 0x00 : 0x04);
        first |= (byte) (this.fileStatus.ordinal() & 0x03);
        bos.write(first);
        totalLength += 1;
        // Filestore responses
        for(FilestoreResponseTLV r : this.filestoreResponses) {
            byte[] encoded = r.encode(true);
            bos.write(encoded);
            totalLength += encoded.length;
        }
        // Fault location
        if(this.conditionCode != FileDirectivePdu.CC_NOERROR && this.conditionCode != FileDirectivePdu.CC_UNSUPPORTED_CHECKSUM_TYPE) {
            byte[] encoded = this.faultLocation.encode(true);
            bos.write(encoded);
            totalLength += encoded.length;
        }
        return totalLength;
    }

    @Override
    protected FinishedPdu buildObject(byte[] pdu) {
        return new FinishedPdu(pdu);
    }
}
