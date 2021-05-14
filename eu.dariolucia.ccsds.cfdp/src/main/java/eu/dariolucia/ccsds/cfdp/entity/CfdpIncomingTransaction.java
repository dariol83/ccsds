package eu.dariolucia.ccsds.cfdp.entity;

import eu.dariolucia.ccsds.cfdp.common.BytesUtil;
import eu.dariolucia.ccsds.cfdp.entity.indication.*;
import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import eu.dariolucia.ccsds.cfdp.filestore.IVirtualFilestore;
import eu.dariolucia.ccsds.cfdp.mib.FaultHandlerStrategy;
import eu.dariolucia.ccsds.cfdp.protocol.builder.CfdpPduBuilder;
import eu.dariolucia.ccsds.cfdp.protocol.builder.FinishedPduBuilder;
import eu.dariolucia.ccsds.cfdp.protocol.checksum.CfdpChecksumRegistry;
import eu.dariolucia.ccsds.cfdp.protocol.checksum.CfdpUnsupportedChecksumType;
import eu.dariolucia.ccsds.cfdp.protocol.checksum.ICfdpChecksum;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.*;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.*;
import eu.dariolucia.ccsds.cfdp.ut.UtLayerException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CfdpIncomingTransaction extends CfdpTransaction {

    private static final Logger LOG = Logger.getLogger(CfdpIncomingTransaction.class.getName());

    private final CfdpPdu initialPdu;
    private final Map<Integer, FaultHandlerStrategy.Action> faultHandlers = new HashMap<>();

    private final List<CfdpPdu> pendingUtTransmissionPduList = new LinkedList<>();

    private MetadataPdu metadataPdu;
    private boolean invalidTransmissionModeDetected;

    private Map<Long, FileDataPdu> fileReconstructionMap;
    private ICfdpChecksum checksum;
    private long fullyCompletedPartSize = 0;
    private boolean gapDetected = false;
    private boolean fileCompleted = false;

    private EndOfFilePdu eofPdu;

    private TimerTask transactionFinishCheckTimer;
    private int transactionFinishCheckTimerCount;

    private boolean filestoreProblemDetected;
    private boolean checksumTypeMissingSupportDetected;
    private boolean checksumMismatchDetected;

    private FinishedPdu.FileStatus finalFileStatus = FinishedPdu.FileStatus.STATUS_UNREPORTED;
    private byte finalConditionCode = FileDirectivePdu.CC_NOERROR;
    private EntityIdTLV finalFaultEntity;
    private List<FilestoreResponseTLV> filestoreResponses;

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
            // TODO: not really - to be checked
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
        boolean fileReconstructedAndReady = checkForFullFileReconstruction();

        if(!isAcknowledged()) {
            // Upon receipt of an EOF (No error) PDU:
            if(fileReconstructedAndReady) {
                // a) If file reception is deemed complete as explained in 4.6.1.2.8 above, the receiving
                // CFDP entity shall issue a Notice of Completion (Completed). Moreover, if the
                // Closure Requested flag in the transaction’s Metadata PDU is set to ‘1’ and the
                // receiving entity is the transaction’s destination, the receiving CFDP entity shall issue
                // a Finished complete) PDU. Any filestore responses shall be carried as parameters of
                // the Finished (complete) PDU. The condition code in the Finished (complete) PDU
                // shall be ‘No error’ if file reception was deemed complete following determination
                // that the calculated and received checksums were equal, but ‘Unsupported checksum
                // type’ otherwise.
                handleNoticeOfCompletion(true);
                if(this.metadataPdu.isClosureRequested()) {
                    handleForwardingOfFinishedPdu();
                }
                handleDispose();
            } else {
                // b) Otherwise, a transaction-specific Check timer shall be started. The timer shall have
                // an implementation-specific expiry period, and there shall be an implementationspecific limit on the number of times the Check timer for any single transaction may
                // expire.
                this.transactionFinishCheckTimer = new TimerTask() {
                    @Override
                    public void run() {
                        handle(CfdpIncomingTransaction.this::handleTransactionFinishedCheckTimerElapsed);
                    }
                };
                schedule(this.transactionFinishCheckTimer, getRemoteDestination().getCheckInterval(), true);
            }
        } else {

        }
    }

    private void handleTransactionFinishedCheckTimerElapsed() {
        // When the timer expires, the receiving entity shall determine whether or not
        // file reception is now deemed complete. If so, the receiving entity shall issue a Notice
        // of Completion (Completed) and, if the Closure Requested flag in the transaction’s
        // Metadata PDU is set to ‘1’ and the receiving entity is the transaction’s destination,
        // shall additionally issue a Finished (complete) PDU as described above. Otherwise, if
        // the Check timer expiration limit has been reached, then a Check Limit Reached fault
        // shall be declared; otherwise the Check timer shall be reset.
        if(this.transactionFinishCheckTimer != null) {
            ++this.transactionFinishCheckTimerCount;
            if(this.fileCompleted) {
                handleNoticeOfCompletion(true);
                if(this.metadataPdu.isClosureRequested()) {
                    handleForwardingOfFinishedPdu();
                }
                handleDispose();
            } else if(this.transactionFinishCheckTimerCount == getRemoteDestination().getCheckIntervalExpirationLimit()) {
                this.transactionFinishCheckTimer.cancel();
                this.transactionFinishCheckTimer = null;
                getEntity().notifyIndication(new FaultIndication(getTransactionId(), FileDirectivePdu.CC_CHECK_LIMIT_REACHED, this.fullyCompletedPartSize));
                handleDispose(); // TODO: TBC depending on the CC_ fault handler: with dispose, this sounds like abort
            }
        }
    }

    private void handleNoticeOfCompletion(boolean completed) {
        // 4.11.1.2.1 If receiving in acknowledged mode,
        // a) transmission of NAK PDUs, whether in response to NAK timer expiration or in
        //    response to any other events, shall be terminated
        // b) any transmission of Keep Alive PDUs shall be terminated
        // c) the application of Positive Acknowledgment Procedures to PDUs previously issued
        //    by this entity shall be terminated

        // Derive final status and condition code
        if(this.filestoreProblemDetected) {
            this.finalFileStatus = FinishedPdu.FileStatus.DISCARDED_BY_FILESTORE;
            this.finalConditionCode = FileDirectivePdu.CC_FILESTORE_REJECTION;
            this.finalFaultEntity = new EntityIdTLV(getEntity().getMib().getLocalEntity().getLocalEntityId(), getEntityIdLength());
        } else if(this.checksumTypeMissingSupportDetected) {
            this.finalFileStatus = FinishedPdu.FileStatus.RETAINED_IN_FILESTORE;
            this.finalConditionCode = FileDirectivePdu.CC_UNSUPPORTED_CHECKSUM_TYPE;
            this.finalFaultEntity = new EntityIdTLV(getEntity().getMib().getLocalEntity().getLocalEntityId(), getEntityIdLength());
        } else if(this.checksumMismatchDetected) {
            if(this.fileCompleted) {
                this.finalFileStatus = FinishedPdu.FileStatus.RETAINED_IN_FILESTORE;
            } else {
                this.finalFileStatus = FinishedPdu.FileStatus.DISCARDED_DELIBERATLY;
            }
            this.finalConditionCode = FileDirectivePdu.CC_FILE_CHECKSUM_FAILURE;
            this.finalFaultEntity = new EntityIdTLV(getEntity().getMib().getLocalEntity().getLocalEntityId(), getEntityIdLength());
        } else {
            this.finalFileStatus = FinishedPdu.FileStatus.RETAINED_IN_FILESTORE;
            this.finalConditionCode = FileDirectivePdu.CC_NOERROR;
            this.finalFaultEntity = null;
        }

        // 4.11.1.2.2 In any case,
        // a) if the receiving entity is the transaction’s destination, and the procedure disposition
        //    cited in the Notice of Completion is ‘Completed’, the receiving CFDP entity shall
        //    execute any filestore requests conveyed by the Put procedure
        if(completed) {
            handleFilestoreRequests();
        } else {
            // b) if the procedure disposition cited in the Notice of Completion is ‘Canceled’, and the
            //    receiving entity is the transaction’s destination, then the incomplete data shall be
            //    either discarded or retained according to the option set in the MIB
            //    NOTE – On Notice of Completion (Canceled) for a transaction for which a Notice of
            //           Completion (Completed) was previously declared, all file data have
            //           necessarily already been received, and therefore there are no incomplete data
            //           to discard or retain.
            if(this.metadataPdu.getDestinationEntityId() == getEntity().getMib().getLocalEntity().getLocalEntityId() &&
                    getRemoteDestination().isRetainIncompleteReceivedFilesOnCancellation()) {
                    // TODO: what the hell I do here? :) I might need to invent a way to store a partial file with gaps
            }
        }
        // c) if the receiving entity is the transaction’s destination, then it may optionally issue a
        //    Transaction-Finished.indication primitive indicating the condition in which the
        //    transaction was completed
        // d) if the receiving entity is the transaction’s destination, Filestore Responses and/or a
        //    Status Report shall be passed as parameters of the TransactionFinished.indication primitive as available.
        if(this.metadataPdu.getDestinationEntityId() == getEntity().getMib().getLocalEntity().getLocalEntityId() &&
            getEntity().getMib().getLocalEntity().isTransactionFinishedIndicationRequired()) {
            getEntity().notifyIndication(new TransactionFinishedIndication(getTransactionId(),
                    this.finalConditionCode,
                    this.finalFileStatus,
                    this.fileCompleted,
                    this.filestoreResponses,
                    null));
        }
    }

    private void handleForwardingOfFinishedPdu() {
        FinishedPdu pdu = prepareFinishedPdu();
        forwardPdu(pdu);
    }

    private FinishedPdu prepareFinishedPdu() {
        FinishedPduBuilder b = new FinishedPduBuilder();
        setCommonPduValues(b);
        b.setDataComplete(this.fileCompleted);
        b.setFileStatus(this.finalFileStatus);
        b.setConditionCode(this.finalConditionCode, this.finalFaultEntity);
        // Add filestore responses
        // TODO

        return b.build();
    }

    private <T extends CfdpPdu,K extends CfdpPduBuilder<T, K>> void setCommonPduValues(CfdpPduBuilder<T,K> b) {
        b.setAcknowledged(isAcknowledged());
        b.setCrcPresent(getRemoteDestination().isCrcRequiredOnTransmission());
        b.setDestinationEntityId(getRemoteDestination().getRemoteEntityId());
        b.setSourceEntityId(getEntity().getMib().getLocalEntity().getLocalEntityId());
        b.setDirection(CfdpPdu.Direction.TOWARD_FILE_RECEIVER);
        b.setSegmentationControlPreserved(this.metadataPdu.isSegmentationControlPreserved());
        // Set the length for the entity ID
        long maxEntityId = Long.max(getRemoteDestination().getRemoteEntityId(), getEntity().getMib().getLocalEntity().getLocalEntityId());
        b.setEntityIdLength(BytesUtil.getEncodingOctetsNb(maxEntityId));
        // Set the transaction ID
        b.setTransactionSequenceNumber(getTransactionId(), BytesUtil.getEncodingOctetsNb(getTransactionId()));
        b.setLargeFile(this.metadataPdu.isLargeFile());
    }

    private boolean forwardPdu(CfdpPdu pdu) { // NOSONAR: false positive
        // Add to the pending list
        this.pendingUtTransmissionPduList.add(pdu);
        // Send all PDUs you have to send, stop if you fail
        if(LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, String.format("Incoming transaction %d from entity %d: sending %d pending PDUs to UT layer %s", getTransactionId(), getRemoteDestination().getRemoteEntityId(), this.pendingUtTransmissionPduList.size(), getTransmissionLayer().getName()));
        }
        while(!pendingUtTransmissionPduList.isEmpty()) {
            CfdpPdu toSend = pendingUtTransmissionPduList.get(0);
            try {
                if(LOG.isLoggable(Level.FINEST)) {
                    LOG.log(Level.FINEST, String.format("Incoming transaction %d from entity %d: sending PDU %s to UT layer %s", getTransactionId(), getRemoteDestination().getRemoteEntityId(), toSend, getTransmissionLayer().getName()));
                }
                getTransmissionLayer().request(toSend);
                this.pendingUtTransmissionPduList.remove(0);
            } catch(UtLayerException e) {
                if(LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, String.format("Incoming transaction %d from entity %d: PDU rejected by UT layer %s: %s", getTransactionId(), getRemoteDestination().getRemoteEntityId(), getTransmissionLayer().getName(), e.getMessage()), e);
                }
                return false;
            }
        }
        if(LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, String.format("Incoming transaction %d from entity %d: pending PDUs sent to UT layer %s", getTransactionId(), getRemoteDestination().getRemoteEntityId(), getTransmissionLayer().getName()));
        }
        return true;
    }

    /**
     *
     * @return false if the file cannot be closed yet, true otherwise (no more PDUs are expected)
     */
    private boolean checkForFullFileReconstruction() {
        if(this.fileCompleted) {
            return true;
        }
        if(this.metadataPdu == null || this.eofPdu == null) {
            // Cannot start the reconstruction
            return false;
        }
        // Check the file progress, it must match the file size in the EOF PDU
        if(this.fullyCompletedPartSize != this.eofPdu.getFileSize()) {
            // Still something missing
            return false;
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
        return true;
    }

    private void storeFile() {
        if(this.fileCompleted) {
            return;
        }
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
        this.fileCompleted = true;
    }

    private void handleFilestoreRequests() {
        this.filestoreResponses = new LinkedList<>();
        boolean faultDetected = false;
        for(TLV req : this.metadataPdu.getOptions()) {
            if(req instanceof FilestoreRequestTLV) {
                FilestoreRequestTLV freq = (FilestoreRequestTLV) req;
                if(faultDetected) {
                    this.filestoreResponses.add(new FilestoreResponseTLV(freq.getActionCode(), FilestoreResponseTLV.StatusCode.NOT_PERFORMED, null, null, null));
                } else {
                    FilestoreResponseTLV resp = freq.execute(getEntity().getFilestore());
                    this.filestoreResponses.add(resp);
                    if(resp.getStatusCode() != FilestoreResponseTLV.StatusCode.SUCCESSFUL) {
                        faultDetected = true;
                    }
                }
            }
        }
    }

    private void handleAckPdu(AckPdu pdu) {
        // TODO: ACK of Finished PDU (only in case of closure requested)
    }

    @Override
    protected void handlePreDispose() {
        // TODO
    }

    public boolean isAcknowledged() {
        return this.initialPdu.isAcknowledged() && getRemoteDestination().isAcknowledgedModeSupported();
    }
}
