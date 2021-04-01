package eu.dariolucia.ccsds.cfdp.protocol.pdu;

import eu.dariolucia.ccsds.cfdp.common.CfdpRuntimeException;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.EntityIdTLV;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.FilestoreResponseTLV;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class FinishedPdu extends FileDirectivePdu {

    public enum FileStatus {
        DISCARDED_DELIBERATLY,
        DISCARDED_BY_FILESTORE,
        RETAINED_IN_FILESTORE,
        STATUS_UNREPORTED
    }

    private final byte conditionCode;

    /**
     * 'Data Complete' means that metadata, all file data, and EOF have been received, and the
     * checksum has been verified.
     */
    private final boolean dataComplete;

    /**
     * File status is meaningful only when the transaction includes the transmission of
     * file data.
     */
    private final FileStatus fileStatus;

    private final List<FilestoreResponseTLV> filestoreResponses = new LinkedList<>();

    private final EntityIdTLV faultLocation;

    public FinishedPdu(byte[] pdu) {
        super(pdu);
        // PDU-specific parsing
        this.conditionCode = (byte) ((pdu[getHeaderLength()] & 0xF0) >>> 4);
        this.dataComplete = ((pdu[getHeaderLength()] & 0x04) >>> 2) == 0;
        this.fileStatus = FileStatus.values()[pdu[getHeaderLength()] & 0x03];
        // Filestore responses
        int currentOffset = getHeaderLength() + 1;
        while(currentOffset < pdu.length) {
            // TLV: Get the current tag
            byte type = pdu[currentOffset];
            if(type == 0x01) {
                int length = Byte.toUnsignedInt(pdu[currentOffset + 1]);
                FilestoreResponseTLV fr = new FilestoreResponseTLV(pdu, currentOffset + 2);
                if(fr.getLength() != length) {
                    throw new CfdpRuntimeException("Length mismatch when parsing FilestoreResponse in Finished PDU: read length is " + length + ", but parsed " + fr.getLength());
                }
                filestoreResponses.add(fr);
                currentOffset += 2 + length;
            } else {
                // quit from loop
                break;
            }
        }
        // Let's check condition code
        if(this.conditionCode == FileDirectivePdu.CC_NOERROR || this.conditionCode == CC_UNSUPPORTED_CHECKSUM_TYPE) {
            // Omitted if condition code is 'No error' or 'Unsupported checksum type'
            this.faultLocation = null;
        } else {
            // Otherwise, entity ID in the TLV is the ID of the entity at which transaction cancellation was initiated.
            // The Type of the Entity ID TLV shall be 06 hex; the Value shall be an Entity ID
            byte type = pdu[currentOffset];
            if(type != EntityIdTLV.TLV_TYPE) {
                throw new CfdpRuntimeException("Cannot parse Fault Location type in Finished PDU: expected " + EntityIdTLV.TLV_TYPE + ", got " + String.format("0x%02X", type));
            }
            int length = Byte.toUnsignedInt(pdu[currentOffset + 1]);
            this.faultLocation = new EntityIdTLV(pdu, currentOffset + 2, length);
        }
    }

    public byte getConditionCode() {
        return conditionCode;
    }

    public boolean isDataComplete() {
        return dataComplete;
    }

    public FileStatus getFileStatus() {
        return fileStatus;
    }

    public List<FilestoreResponseTLV> getFilestoreResponses() {
        return Collections.unmodifiableList(filestoreResponses);
    }

    public EntityIdTLV getFaultLocation() {
        return faultLocation;
    }

    @Override
    public String toString() {
        return super.toString() + " FinishedPdu{" +
                "conditionCode=" + conditionCode +
                ", dataComplete=" + dataComplete +
                ", fileStatus=" + fileStatus +
                ", filestoreResponses=" + filestoreResponses +
                ", faultLocation=" + faultLocation +
                '}';
    }
}
