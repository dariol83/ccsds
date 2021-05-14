package eu.dariolucia.ccsds.cfdp.entity;

import eu.dariolucia.ccsds.cfdp.entity.indication.EofRecvIndication;
import eu.dariolucia.ccsds.cfdp.entity.indication.FaultIndication;
import eu.dariolucia.ccsds.cfdp.entity.indication.FileSegmentRecvIndication;
import eu.dariolucia.ccsds.cfdp.entity.indication.MetadataRecvIndication;
import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import eu.dariolucia.ccsds.cfdp.filestore.IVirtualFilestore;
import eu.dariolucia.ccsds.cfdp.mib.FaultHandlerStrategy;
import eu.dariolucia.ccsds.cfdp.protocol.checksum.CfdpChecksumRegistry;
import eu.dariolucia.ccsds.cfdp.protocol.checksum.CfdpUnsupportedChecksumType;
import eu.dariolucia.ccsds.cfdp.protocol.checksum.ICfdpChecksum;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.*;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.FaultHandlerOverrideTLV;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.MessageToUserTLV;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.TLV;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CfdpIncomingTransaction extends CfdpTransaction {

    private static final Logger LOG = Logger.getLogger(CfdpIncomingTransaction.class.getName());

    private final CfdpPdu initialPdu;
    private final Map<Integer, FaultHandlerStrategy.Action> faultHandlers = new HashMap<>();

    private MetadataPdu metadataPdu;
    private boolean invalidTransmissionModeDetected;

    private Map<Long, FileDataPdu> fileReconstructionMap;
    private ICfdpChecksum checksum;
    private long fullyCompletedPartSize = 0;
    private boolean gapDetected = false;

    private EndOfFilePdu eofPdu;

    private boolean filestoreProblemDetected;
    private boolean checksumTypeMissingSupportDetected;
    private boolean checksumMismatchDetected;

    public CfdpIncomingTransaction(CfdpPdu pdu, CfdpEntity entity) {
        super(pdu.getTransactionSequenceNumber(), entity, pdu.getSourceEntityId());
        this.initialPdu = pdu;
        this.faultHandlers.putAll(entity.getMib().getLocalEntity().getFaultHandlerMap());
    }

    @Override
    protected void handleActivation() {
        // 4.6.1.2.2 The receiving CFDP entity shall store the value of the transmission mode bit
        // contained in the first received PDU of a transaction and use it for subsequent processing of
        // the transaction. If the receiving CFDP entity is incapable of operating in this mode, an
        // Invalid Transmission Mode fault shall be declared.
        if(this.initialPdu.isAcknowledged() && !getRemoteDestination().isAcknowledgedModeSupported()) {
            this.invalidTransmissionModeDetected = true;
            getEntity().notifyIndication(new FaultIndication(getTransactionId(), FileDirectivePdu.CC_INVALID_TX_MODE,0));
        }
        // Process the first PDU
        handleIndication(this.initialPdu);
    }

    @Override
    protected void handleIndication(CfdpPdu pdu) {
        // As a receiver you can expect:
        // 1) Metadata PDU
        // 2) FileData PDU
        // 3) EOF PDU
        // 4) ACK (Finished) PDU
        if(pdu instanceof MetadataPdu) {
            handleMetadataPdu((MetadataPdu) pdu);
        } else if(pdu instanceof FileDataPdu) {
            handleFileDataPdu((FileDataPdu) pdu);
        } else if(pdu instanceof EndOfFilePdu) {
            handleEndOfFilePdu((EndOfFilePdu) pdu);
        } else if(pdu instanceof AckPdu) {
            handleAckPdu((AckPdu) pdu);
        }
    }

    private void handleMetadataPdu(MetadataPdu pdu) {
        if(this.metadataPdu != null) {
            // 4.6.1.2.4 Any repeated Metadata PDU shall be discarded
            return;
        }
        this.metadataPdu = pdu;
        // 4.6.1.2.3 The receiving CFDP entity shall store fault handler overrides, file size, flow label,
        // and file name information contained in the Metadata PDU and use it for subsequent
        // processing of the transaction.
        List<MessageToUserTLV> messagesToUser = new LinkedList<>();
        for(TLV option : this.metadataPdu.getOptions()) {
            if(option instanceof FaultHandlerOverrideTLV) {
                this.faultHandlers.put((int) ((FaultHandlerOverrideTLV) option).getConditionCode(), ((FaultHandlerOverrideTLV) option).getHandlerCode().toAction());
            } else if(option instanceof MessageToUserTLV) {
                messagesToUser.add((MessageToUserTLV) option);
            }
        }
        // 4.6.1.2.6 If the receiver is the transaction’s destination, receipt of a Metadata PDU shall
        // cause the destination entity to issue a Metadata-Recv.indication.
        if(this.metadataPdu.getDestinationEntityId() == getEntity().getMib().getLocalEntity().getLocalEntityId()) {
            getEntity().notifyIndication(new MetadataRecvIndication(
                    this.metadataPdu.getTransactionSequenceNumber(),
                    this.metadataPdu.getSourceEntityId(),
                    this.metadataPdu.getFileSize(),
                    this.metadataPdu.getSourceFileName(),
                    this.metadataPdu.getDestinationFileName(),
                    messagesToUser));
        }
        // File or not?
        if(this.metadataPdu.getSourceFileName() != null && this.metadataPdu.getDestinationFileName() != null) {
            if(this.fileReconstructionMap == null) {
                this.fileReconstructionMap = new TreeMap<>();
            }
            // 4.2.4.2 For checksum computation at the receiving entity, the preferred checksum
            // computation algorithm shall be the algorithm identified by the checksum type specified in the
            // Metadata PDU.
            try {
                this.checksum = CfdpChecksumRegistry.getChecksum(this.metadataPdu.getChecksumType()).build();
            } catch (CfdpUnsupportedChecksumType e) {
                // 4.2.2.8 If the preferred checksum computation algorithm is not one of the available
                // checksum computation algorithms, an Unsupported Checksum Type fault shall be raised, and
                // the applicable checksum computation algorithm shall be selected as follows:
                getEntity().notifyIndication(new FaultIndication(getTransactionId(), FileDirectivePdu.CC_UNSUPPORTED_CHECKSUM_TYPE,0));
                // 4.2.2.8.2 If the checksum is to be computed by the receiving entity, the applicable
                // checksum computation algorithm shall be the null checksum algorithm defined in 4.2.2.4.
                this.checksum = CfdpChecksumRegistry.getNullChecksum().build();
                this.checksumTypeMissingSupportDetected = true;
            }
            // At this stage, if there are File PDUs already stored, process them
            if(this.fileReconstructionMap != null && !this.fileReconstructionMap.isEmpty()) {
                for(Map.Entry<Long, FileDataPdu> e : this.fileReconstructionMap.entrySet()) {
                    this.checksum.checksum(e.getValue().getFileData(), e.getValue().getOffset());
                }
            }
            // Check whether you can reconstruct the file
            checkForFullFileReconstruction();
        } else {
            // Only metadata information, so the transaction can be closed here
            handleClosure();
        }
    }

    private void handleFileDataPdu(FileDataPdu pdu) {
        // If this is the first PDU ever, then it means that the metadata PDU got lost but still allocates the reconstruction map
        if(this.fileReconstructionMap == null) {
            this.fileReconstructionMap = new TreeMap<>();
        }
        // Check if there is already a FileData PDU like you in the map
        if(this.fileReconstructionMap.containsKey(pdu.getOffset())) {
            // 4.6.1.2.7 any repeated data shall be discarded
            return;
        }
        // Add the PDU in the map
        this.fileReconstructionMap.put(pdu.getOffset(), pdu);
        // Identify the fully completed part offset and if there are gaps (to request retransmission if enabled), compute progress
        verifyGapPresence(pdu);

        // 4.6.1.2.7 if the sum of the File Data PDU’s offset and segment size exceeds the file size
        // indicated in the first previously received EOF (No error) PDU, if any, then a File Size
        // Error fault shall be declared.
        if(this.eofPdu != null && this.eofPdu.getConditionCode() == FileDirectivePdu.CC_NOERROR && pdu.getOffset() + pdu.getFileData().length > this.eofPdu.getFileSize()) {
            getEntity().notifyIndication(new FaultIndication(getTransactionId(), FileDirectivePdu.CC_FILE_SIZE_ERROR, this.fullyCompletedPartSize));
        }

        // Receipt of a File Data PDU may optionally cause the receiving CFDP, if it is the
        // transaction's destination, to issue a File-Segment-Recv.indication.
        if(pdu.getDestinationEntityId() == getEntity().getMib().getLocalEntity().getLocalEntityId() &&
                getEntity().getMib().getLocalEntity().isFileSegmentRecvIndicationRequired()) {
            getEntity().notifyIndication(new FileSegmentRecvIndication(pdu.getTransactionSequenceNumber(), pdu.getOffset(), pdu.getFileData().length,
                    pdu.getRecordContinuationState(), pdu.getSegmentMetadataLength(), pdu.getSegmentMetadata()));
        }
        // Check whether you can reconstruct the file
        checkForFullFileReconstruction();
    }

    /**
     * This method verifies if there is a gap linked to the reception of a new FileDataPdu, and recompute the progress as
     * well.
     *
     * @param pdu the PDU to process
     */
    private void verifyGapPresence(FileDataPdu pdu) {
        if(pdu.getOffset() < this.fullyCompletedPartSize) {
            throw new IllegalStateException("Software bug: FileDataPdu offset is " + pdu.getOffset() + " but completion is at " + this.fullyCompletedPartSize + ", the PDU should have been ignored");
        }
        // Assuming that this.fullyCompletedPartSize reports the number of consecutive, no-gaps bytes of the file from the file start,
        // if pdu.offset is equal to this number and there was so gap signalled before, then the pdu will increase this.fullyCompletedPartSize
        // by pdu.filedata.length.
        if(pdu.getOffset() == this.fullyCompletedPartSize) {
            this.fullyCompletedPartSize += pdu.getFileData().length;
            // If there was a gap detected beforehand, then it could be that fullyCompletedPartSize is actually larger
            if(this.gapDetected) {
                for(Map.Entry<Long, FileDataPdu> e : this.fileReconstructionMap.entrySet()) {
                    // All segments having an offset lower than fullyCompletedPartSize can be safely skipped
                    if(e.getKey() == fullyCompletedPartSize) {
                        // We found a segment that can contribute to the progress of the file, so we take this into account and we go on
                        this.fullyCompletedPartSize += e.getValue().getFileData().length;
                        // At this stage we reset the gap indicator, but we keep iterating on the map, since further gaps can be detected
                        this.gapDetected = false;
                    } else if(e.getKey() > fullyCompletedPartSize) {
                        // We found a segment that is not following the detected progress in a continuous way. We need to stop here and we
                        // raise the gapDetected flag.
                        this.gapDetected = true;
                        break;
                    }
                }
            }
        } else {
            // Here we need to expect that the offset is not the one we expect, so we raise the gapDetected flag
            // TODO compute gap size for NAK
            this.gapDetected = true;
        }
    }

    private void handleEndOfFilePdu(EndOfFilePdu pdu) {
        // If this is the first PDU ever, then it means that the metadata PDU got lost but still allocates the reconstruction map
        if(this.fileReconstructionMap == null) {
            this.fileReconstructionMap = new TreeMap<>();
        }
        // Check if there is already a EOF (No error) PDU
        if(this.eofPdu != null && this.eofPdu.getConditionCode() == FileDirectivePdu.CC_NOERROR) {
            // Discard
            return;
        }
        this.eofPdu = pdu;
        // 4.6.1.2.9 Upon initial receipt of the EOF (No error) PDU, the file size indicated in the PDU
        // shall be compared to the transaction reception progress and a File Size Error fault declared if
        // the progress exceeds the file size.
        if(this.eofPdu.getConditionCode() == FileDirectivePdu.CC_NOERROR && this.fullyCompletedPartSize > this.eofPdu.getFileSize()) {
            getEntity().notifyIndication(new FaultIndication(getTransactionId(), FileDirectivePdu.CC_FILE_SIZE_ERROR,this.fullyCompletedPartSize));
        }
        // Initial receipt of the
        // EOF PDU for a transaction may optionally cause the receiving CFDP, if it is the
        // transaction's destination, to issue an EOF-Recv.indication.
        if(pdu.getDestinationEntityId() == getEntity().getMib().getLocalEntity().getLocalEntityId() &&
                getEntity().getMib().getLocalEntity().isEofRecvIndicationRequired()) {
            getEntity().notifyIndication(new EofRecvIndication(pdu.getTransactionSequenceNumber()));
        }
        // TODO identify the fully completed part offset and if there are gaps (to request retransmission if enabled)
        // Check whether you can reconstruct the file
        checkForFullFileReconstruction();
    }

    private void checkForFullFileReconstruction() {
        if(this.metadataPdu == null || this.eofPdu == null) {
            // Cannot start the reconstruction
            return;
        }
        // Check the file progress, it must match the file size in the EOF PDU
        if(this.fullyCompletedPartSize != this.eofPdu.getFileSize()) {
            // Still something missing
            return;
        }
        // 4.6.1.2.8 At the earliest time at which the transaction’s Metadata, File Data (if any), and
        // EOF (No error) PDUs have all been received by the receiving entity:

        // a) a checksum shall be calculated for the delivered file by means of the applicable
        //    checksum algorithm, determined as described in 4.2.2 above
        // b) the calculated and received file checksums shall be compared
        if(this.checksum.getCurrentChecksum() == this.eofPdu.getFileChecksum() || this.checksum.type() == CfdpChecksumRegistry.NULL_CHECKSUM_TYPE) {
            // c) if the compared checksums are equal or the applicable checksum algorithm is the null
            //    checksum algorithm, file delivery shall be deemed Complete
            storeFile();
        } else {
            // d) otherwise, a File Checksum Failure fault shall be declared.
            getEntity().notifyIndication(new FaultIndication(getTransactionId(), FileDirectivePdu.CC_FILE_CHECKSUM_FAILURE, this.fullyCompletedPartSize));
            this.checksumMismatchDetected = true;
            if(faultHandlers.get((int) FileDirectivePdu.CC_FILE_CHECKSUM_FAILURE) == FaultHandlerStrategy.Action.NO_ACTION) {
                // The action taken upon such error need not necessarily entail discarding the
                // delivered file. The default handler for File Checksum Failure faults may be
                // Ignore, causing the discrepancy to be announced to the user in a
                // Fault.indication but permitting the completion of the Copy File procedure
                // at the receiving entity. This configuration setting might be especially
                // appropriate for transactions conducted in unacknowledged mode.
                storeFile();
            }
        }
        // You can close the transaction
        handleClosure();
    }

    private void storeFile() {
        IVirtualFilestore filestore = getEntity().getFilestore();
        try {
            filestore.createFile(this.metadataPdu.getDestinationFileName());
            OutputStream os = filestore.writeFile(this.metadataPdu.getDestinationFileName(), false);
            for(Map.Entry<Long, FileDataPdu> e : this.fileReconstructionMap.entrySet()) {
                os.write(e.getValue().getFileData());
            }
            os.flush();
            os.close();
        } catch (FilestoreException | IOException e) {
            if(LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, String.format("Incoming transaction %d from entity %d: problem when storing file %s to filestore: %s", getTransactionId(), getRemoteDestination().getRemoteEntityId(), this.metadataPdu.getDestinationFileName(), e.getMessage()), e);
            }
            this.filestoreProblemDetected = true;
            getEntity().notifyIndication(new FaultIndication(getTransactionId(), FileDirectivePdu.CC_FILESTORE_REJECTION, this.fullyCompletedPartSize));
        }
    }

    private void handleAckPdu(AckPdu pdu) {
        // TODO: ACK of Finished PDU (only in case of closure requested)
    }

    private void handleClosure() {
        // TODO: if ack requested and EOF PDU present, send EOF ack with appropriate code
        // TODO: if closure requested and destination is this entity, send finished PDU
        // TODO: if no failures and destination is this entity TBC, execute file store requests
    }

    @Override
    protected void handleDispose() {
        // TODO
    }

    public boolean isAcknowledged() {
        return this.initialPdu.isAcknowledged() && getRemoteDestination().isAcknowledgedModeSupported();
    }
}
