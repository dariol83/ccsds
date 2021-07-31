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

package eu.dariolucia.ccsds.cfdp.entity.internal;

import eu.dariolucia.ccsds.cfdp.common.BytesUtil;
import eu.dariolucia.ccsds.cfdp.common.CfdpRuntimeException;
import eu.dariolucia.ccsds.cfdp.entity.FaultDeclaredException;
import eu.dariolucia.ccsds.cfdp.entity.indication.*;
import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import eu.dariolucia.ccsds.cfdp.filestore.IVirtualFilestore;
import eu.dariolucia.ccsds.cfdp.mib.FaultHandlerStrategy;
import eu.dariolucia.ccsds.cfdp.protocol.builder.*;
import eu.dariolucia.ccsds.cfdp.protocol.checksum.CfdpChecksumRegistry;
import eu.dariolucia.ccsds.cfdp.protocol.checksum.CfdpUnsupportedChecksumType;
import eu.dariolucia.ccsds.cfdp.protocol.checksum.ICfdpChecksum;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.*;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.*;
import eu.dariolucia.ccsds.cfdp.ut.UtLayerException;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IncomingCfdpTransaction extends CfdpTransaction {

    public static final String PARTIAL_FILE_EXTENSION = ".PART";

    private static final Logger LOG = Logger.getLogger(IncomingCfdpTransaction.class.getName());
    private static final byte[] FILE_PADDING_BUFFER = new byte[4096];

    private final CfdpPdu initialPdu;

    private final List<CfdpPdu> pendingUtTransmissionPduList = new LinkedList<>();

    private MetadataPdu metadataPdu;

    private Map<Long, FileDataPduSummary> fileReconstructionMap; // optimisation: use a temporary random access file, use a stripped down version of the FileDataPdu, only offset, length
    private RandomAccessFile temporaryReconstructionFileMap;
    private final File temporaryReconstructionFile;

    private ICfdpChecksum checksum;
    private long receivedContiguousFileBytes = 0;
    private boolean gapDetected = false;
    private boolean fileCompleted = false;

    private EndOfFilePdu eofPdu;

    private TimerTask transactionFinishCheckTimer;
    private int transactionFinishCheckTimerCount;

    private TimerTask nakComputationTimer;

    private TimerTask nakTimer;
    private int nakTimerCount;

    private TimerTask keepAliveSendingTimer;

    private boolean filestoreProblemDetected;
    private boolean checksumTypeMissingSupportDetected;
    private boolean checksumMismatchDetected;

    private FinishedPdu.FileStatus finalFileStatus = FinishedPdu.FileStatus.STATUS_UNREPORTED;
    private List<FilestoreResponseTLV> filestoreResponses;

    // The last Finished Pdu, once sent while pending acknowledgement
    private FinishedPdu finishedPdu;
    // Inform the transaction if a NAK timer was started by the reception of a EOF PDU. In such case, a resume operation
    // will resume also the NAK timer.
    private boolean nakTimerOnEofActivated;


    public IncomingCfdpTransaction(CfdpPdu pdu, CfdpEntity entity) {
        super(pdu.getTransactionSequenceNumber(), entity, pdu.getSourceEntityId());
        this.initialPdu = pdu;
        try {
            this.temporaryReconstructionFile = Files.createTempFile("cfdp_in_file_" + pdu.getDestinationEntityId() + "_" + pdu.getTransactionSequenceNumber() + "_", ".tmp").toFile();
            this.temporaryReconstructionFileMap = new RandomAccessFile(this.temporaryReconstructionFile, "rw");
        } catch (IOException e) {
            if(LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: fail on local temp file creation: %s ", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), e.getMessage()), e);
            }
            throw new CfdpRuntimeException(e);
        }
        overrideHandlers(entity.getMib().getLocalEntity().getFaultHandlerMap());
    }

    @Override
    protected void handleActivation() {
        // 4.6.1.2.2 The receiving CFDP entity shall store the value of the transmission mode bit
        // contained in the first received PDU of a transaction and use it for subsequent processing of
        // the transaction. If the receiving CFDP entity is incapable of operating in this mode, an
        // Invalid Transmission Mode fault shall be declared.

        // This implementation can operate in Ack mode, so no reason to check.

        // Start the transaction inactivity timer
        startTransactionInactivityTimer();
        // Process the first PDU
        handleIndication(this.initialPdu);
        // Activate keep alive (if conditions are met)
        startKeepAliveSendingTimer();
    }

    private void startKeepAliveSendingTimer() {
        // If the keepAliveSendingInterval is 0, then no keep alive is sent.
        if(this.keepAliveSendingTimer != null) {
            return;
        }

        // 4.6.5.2.1 In all acknowledged modes, the receiving CFDP entity may periodically send a
        // Keep Alive PDU to the sending CFDP entity reporting on the transaction’s reception
        // progress so far at this entity.
        if(!isAcknowledged()) {
            return;
        }
        if(getRemoteDestination().getKeepAliveInterval() == -1) {
            // Keep alive disabled
            return;
        }
        this.keepAliveSendingTimer = new TimerTask() {
            @Override
            public void run() {
                final TimerTask expiredTimer = this;
                handle(() -> {
                    if (IncomingCfdpTransaction.this.keepAliveSendingTimer == expiredTimer) {
                        handleKeepAliveTransmission();
                    }
                });
            }
        };
        schedule(this.keepAliveSendingTimer, getRemoteDestination().getKeepAliveInterval(), true);
    }

    private void stopKeepAliveSendingTimer() {
        if(this.keepAliveSendingTimer != null) {
            this.keepAliveSendingTimer.cancel();
            this.keepAliveSendingTimer = null;
        }
    }

    private void handleKeepAliveTransmission() {
        if(!isRunning()) {
            return;
        }
        KeepAlivePdu pdu = prepareKeepAlivePdu();
        sendPdu(pdu);
    }

    private void sendPdu(CfdpPdu pdu) {
        try {
            forwardPdu(pdu);
        } catch (UtLayerException e) {
            if(LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: fail on PDU transmission: %s ", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), e.getMessage()), e);
            }
        }
    }

    @Override
    protected void handleIndication(CfdpPdu pdu) {
        // As a receiver you can expect:
        // 1) Metadata PDU
        // 2) FileData PDU
        // 3) EOF PDU
        // 4) ACK (Finished) PDU
        // 5) Prompt PDU

        // 4.10.1 For a particular transaction, if there is a cessation of PDU reception for a specified
        // time period (the transaction inactivity limit), then an Inactivity fault condition shall be
        // declared.
        resetTransactionInactivityTimer();

        try {
            if (pdu instanceof MetadataPdu && !isCancelled()) {
                handleMetadataPdu((MetadataPdu) pdu);
            } else if (pdu instanceof FileDataPdu && !isCancelled()) {
                handleFileDataPdu((FileDataPdu) pdu);
            } else if (pdu instanceof EndOfFilePdu && !isCancelled()) {
                handleEndOfFilePdu((EndOfFilePdu) pdu);
            } else if (pdu instanceof AckPdu) {
                handleAckPdu((AckPdu) pdu);
            } else if (pdu instanceof PromptPdu && !isCancelled()) {
                handlePromptPdu((PromptPdu) pdu);
            }
        } catch (FaultDeclaredException e) {
            if(LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: fault raised on PDU reception %s: %s ", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), pdu, e.getMessage()), e);
            }
        }
    }

    @Override
    protected long getProgress() {
        return this.receivedContiguousFileBytes;
    }

    @Override
    protected long getTotalFileSize() {
        // If the EOF PDU arrived, then use the information there
        if(this.eofPdu != null) {
            return this.eofPdu.getFileSize();
        }
        // If it is mentioned in the metadata PDU, report that. If 0, file is unbounded
        if(this.metadataPdu != null) {
            return this.metadataPdu.getFileSize();
        }
        // If you have no information, report 0
        return 0;
    }

    private void handlePromptPdu(PromptPdu pdu) {
        if(isAcknowledged()) {
            if(pdu.isNakResponseRequired()) {
                // Run immediately the task that computes and sends the required NAKs
                handleNakComputation(false);
                // Reset the timer
                restartNakComputation();
            } else if(pdu.isKeepAliveResponseRequired()) {
                // 4.6.5.2.2 The Keep Alive PDU shall also be sent in response to receipt of a Prompt (Keep
                // Alive) PDU.
                handleKeepAliveTransmission();
            }
        }
    }

    private void handleMetadataPdu(MetadataPdu pdu) throws FaultDeclaredException {
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
                overrideHandler(((FaultHandlerOverrideTLV) option).getConditionCode(), ((FaultHandlerOverrideTLV) option).getHandlerCode().toAction());
            } else if(option instanceof MessageToUserTLV) {
                messagesToUser.add((MessageToUserTLV) option);
            }
        }
        // 4.6.1.2.6 If the receiver is the transaction’s destination, receipt of a Metadata PDU shall
        // cause the destination entity to issue a Metadata-Recv.indication.
        if(this.metadataPdu.getDestinationEntityId() == getLocalEntityId()) {
            getEntity().notifyIndication(new MetadataRecvIndication(
                    this.metadataPdu.getTransactionSequenceNumber(),
                    this.metadataPdu.getSourceEntityId(),
                    this.metadataPdu.getFileSize(),
                    this.metadataPdu.getSourceFileName(),
                    this.metadataPdu.getDestinationFileName(),
                    messagesToUser,
                    createStateObject()));
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
                if(LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: checksum unsupported: %s ", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), e.getMessage()), e);
                }
                // 4.2.2.8 If the preferred checksum computation algorithm is not one of the available
                // checksum computation algorithms, an Unsupported Checksum Type fault shall be raised, and
                // the applicable checksum computation algorithm shall be selected as follows:
                try {
                    fault(FileDirectivePdu.CC_UNSUPPORTED_CHECKSUM_TYPE,getLocalEntityId());
                } catch (FaultDeclaredException faultDeclaredException) {
                    // A exception at this stage means that suspend, cancel or abandon has been thrown. If cancel or abandon,
                    // stop here
                    if(faultDeclaredException.getAction() == FaultHandlerStrategy.Action.NOTICE_OF_CANCELLATION ||
                    faultDeclaredException.getAction() == FaultHandlerStrategy.Action.ABANDON) {
                        return;
                    }
                }
                // 4.2.2.8.2 If the checksum is to be computed by the receiving entity, the applicable
                // checksum computation algorithm shall be the null checksum algorithm defined in 4.2.2.4.
                this.checksum = CfdpChecksumRegistry.getNullChecksum().build();
                this.checksumTypeMissingSupportDetected = true;
            }
            // At this stage, if there are File PDUs already stored, process them and
            // check whether you can reconstruct the file
            boolean fileReconstructed = checkForFullFileReconstruction();
            // If the file is reconstructed...
            if(fileReconstructed) {
                // ... handle the transaction closure now
                handleEndTransactionOnMetadataReception(pdu);
            }
        } else {
            //  Only metadata information, so the transaction can be closed here, with the sending of the Finished PDU
            //  if the closure is requested. However, with store-and-forward and proxy operations, this might not be the case
            //  anymore.
            handleEndTransactionOnMetadataReception(pdu);
        }
    }

    private void handleEndTransactionOnMetadataReception(MetadataPdu pdu) {
        // If the file is reconstructed (or maybe there is no file at all) and...
        if (isAcknowledged()) {
            // ...you are in acknowledged mode, then you should go ahead with the completion
            // of the transaction and send the Finished PDU
            handleNoticeOfCompletion(true);
            sendFinishedPdu(true);
        } else {
            // ...you are not in acknowledged mode, then you should go ahead with the completion
            // of the transaction and, in case, send the Finished PDU
            handleNoticeOfCompletion(true);
            if (pdu.isClosureRequested()) {
                sendFinishedPdu();
            }
            // Dispose here, as there is not much else to do
            handleDispose();
        }
    }

    private void handleFileDataPdu(FileDataPdu pdu) throws FaultDeclaredException {
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: File Data PDU offset %d length %d received", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), pdu.getOffset(), pdu.getFileData().length));
        }
        // If this is the first PDU ever, then it means that the metadata PDU got lost but still allocates the reconstruction map
        if(this.fileReconstructionMap == null) {
            this.fileReconstructionMap = new TreeMap<>();
            if(this.metadataPdu == null && getRemoteDestination().isImmediateNakModeEnabled() && isAcknowledged()) {
                sendMetadataNak();
            }
        }
        // Check if there is already a FileData PDU like you in the map
        FileDataPduSummary existing = this.fileReconstructionMap.get(pdu.getOffset());
        // We cannot rely on the behaviour of other implementations, therefore here we need to overwrite the entry,
        // if the length of the data is greater than the one currently in the map, or skip it if it is a subset of what
        // we already have
        if(existing != null && existing.length >= pdu.getFileData().length) {
            // 4.6.1.2.7 any repeated data shall be discarded
            return;
        }
        // Add the PDU in the map
        this.fileReconstructionMap.put(pdu.getOffset(), new FileDataPduSummary(pdu.getOffset(), pdu.getFileData().length));
        // Write it to the temp file
        try {
            this.temporaryReconstructionFileMap.seek(pdu.getOffset());
            this.temporaryReconstructionFileMap.write(pdu.getFileData());
        } catch (IOException e) {
            if(LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: failure in adding received FileDataPdu contents to temporary storage: %s ", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), e.getMessage()), e);
            }
            fault(FileDirectivePdu.CC_FILESTORE_REJECTION, getLocalEntityId());
        }

        // Identify the fully completed part offset and if there are gaps (to request retransmission if enabled), compute progress
        verifyGapPresence(pdu);

        // Receipt of a File Data PDU may optionally cause the receiving CFDP, if it is the
        // transaction's destination, to issue a File-Segment-Recv.indication.
        if(pdu.getDestinationEntityId() == getLocalEntityId() &&
                getEntity().getMib().getLocalEntity().isFileSegmentRecvIndicationRequired()) {
            getEntity().notifyIndication(new FileSegmentRecvIndication(pdu.getTransactionSequenceNumber(), pdu.getOffset(), pdu.getFileData().length,
                    pdu.getRecordContinuationState(), pdu.getSegmentMetadataLength(), pdu.getSegmentMetadata(), createStateObject()));
        }

        // 4.6.1.2.7 if the sum of the File Data PDU’s offset and segment size exceeds the file size
        // indicated in the first previously received EOF (No error) PDU, if any, then a File Size
        // Error fault shall be declared.
        if(this.eofPdu != null && this.eofPdu.getConditionCode() == FileDirectivePdu.CC_NOERROR && pdu.getOffset() + pdu.getFileData().length > this.eofPdu.getFileSize()) {
            try {
                fault(FileDirectivePdu.CC_FILE_SIZE_ERROR, getLocalEntityId());
            } catch (FaultDeclaredException e) {
                // Processing was handled by the fault handler, nothing to be done here
                return;
            }
        }

        // Check whether you can reconstruct the file
        boolean fileReconstructed = checkForFullFileReconstruction();
        if(isAcknowledged() && fileReconstructed) {
            handleNoticeOfCompletion(true);
            sendFinishedPdu(true);
        }
    }

    private void handleForwardingOfEofAckPdu(EndOfFilePdu eofPdu) {
        if(LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: forwarding ACK for EOF PDU %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), eofPdu));
        }
        AckPdu pdu = prepareAckPdu(eofPdu);
        sendPdu(pdu);
    }

    private AckPdu prepareAckPdu(EndOfFilePdu pdu) {
        AckPduBuilder b = new AckPduBuilder();
        setCommonPduValues(b);
        b.setTransactionStatus(deriveCurrentAckTransactionStatus());
        b.setConditionCode(pdu.getConditionCode());
        b.setDirectiveCode(FileDirectivePdu.DC_EOF_PDU);
        b.setDirectiveSubtypeCode((byte) 0x00);

        return b.build();
    }

    /**
     * This method verifies if there is a gap linked to the reception of a new FileDataPdu, and recompute the progress as
     * well.
     *
     * @param pdu the PDU to process
     */
    private void verifyGapPresence(FileDataPdu pdu) {
        if(pdu.getOffset() < this.receivedContiguousFileBytes) {
            throw new IllegalStateException("Software bug: FileDataPdu offset is " + pdu.getOffset() + " but completion is at " + this.receivedContiguousFileBytes + ", the PDU should have been ignored");
        }
        // Assuming that this.fullyCompletedPartSize reports the number of consecutive, no-gaps bytes of the file from the file start,
        // if pdu.offset is equal to this number and there was so gap signalled before, then the pdu will increase this.fullyCompletedPartSize
        // by pdu.getFileData().length.
        if(pdu.getOffset() == this.receivedContiguousFileBytes) {
            this.receivedContiguousFileBytes += pdu.getFileData().length;
            // If there was a gap detected beforehand, then it could be that fullyCompletedPartSize is actually larger
            if(this.gapDetected) {
                for(Map.Entry<Long, FileDataPduSummary> e : this.fileReconstructionMap.entrySet()) {
                    // All segments having an offset lower than fullyCompletedPartSize can be safely skipped
                    if(e.getKey() == receivedContiguousFileBytes) {
                        // We found a segment that can contribute to the progress of the file, so we take this into account and we go on
                        this.receivedContiguousFileBytes += e.getValue().length;
                        // At this stage we reset the gap indicator, but we keep iterating on the map, since further gaps can be detected
                        this.gapDetected = false;
                    } else if(e.getKey() > receivedContiguousFileBytes) {
                        // We found a segment that is not following the detected progress in a continuous way. We need to stop here and we
                        // raise the gapDetected flag.
                        this.gapDetected = true;
                        break;
                    }
                }
            }
        } else {
            // Here we need to expect that the offset is not the one we expect, so we raise the gapDetected flag
            this.gapDetected = true;
            if(isAcknowledged() && this.eofPdu == null && this.nakComputationTimer == null) {
                // Start a periodic task that computes and sends the required NAKs: this you do until the EOF PDU arrives.
                // When the EOF PDU arrives, then you go for the NAK Timer.
                restartNakComputation();
            }
        }
    }

    private void handleEndOfFilePdu(EndOfFilePdu pdu) throws FaultDeclaredException {
        // Receipt of a PDU to which Positive Acknowledgement procedures are applied shall cause the
        // receiving CFDP entity immediately to issue the Expected Response.
        // NOTES
        //  1   By issuing the Expected Response, the CFDP entity only confirms receipt of the
        //      PDU; issuance of the Expected Response does not imply that the receiving CFDP
        //      entity has taken any other action as a result. For example, production of an ACK
        //      (EOF (Cancel)) PDU implies that an EOF (Cancel) PDU was received but not
        //      necessarily that the referenced transaction was canceled.
        //  2   The receiving CFDP entity is always required to issue the Expected Response upon
        //      receipt of a PDU to which Positive Acknowledgment procedures are applied. The
        //      purpose of the Expected Response is to turn off the PDU retransmission timer at the
        //      sending end. This purpose must be served regardless of the status of the transaction:
        //      undefined, active, terminated, or unrecognized.

        // 4.6.6.1.2 If an acknowledged mode is in effect, Positive Acknowledgement procedures shall
        // be applied to the EOF (cancel) PDU with the Expected Response being an ACK (EOF) PDU.
        if(isAcknowledged()) {
            handleForwardingOfEofAckPdu(pdu);
        }
        // 4.6.6.1.1 Receipt of an EOF (cancel) PDU shall cause the receiving CFDP entity to issue a
        // Notice of Completion (Canceled).
        if(pdu.getConditionCode() == FileDirectivePdu.CC_CANCEL_REQUEST_RECEIVED) {
            this.eofPdu = pdu;
            setLastConditionCode(FileDirectivePdu.CC_CANCEL_REQUEST_RECEIVED, getRemoteDestination().getRemoteEntityId());
            handleNoticeOfCompletion(false);
            handleDispose();
            return;
        }

        // If this is the first PDU ever, then it means that the metadata PDU got lost but still allocates the reconstruction map
        boolean metadataNakJustSent = false;
        if(this.fileReconstructionMap == null) {
            this.fileReconstructionMap = new TreeMap<>();
            if(isAcknowledged() && this.metadataPdu == null) {
                sendMetadataNak();
                metadataNakJustSent = true;
            }
        }
        // Check if there is already a EOF (No error) PDU
        if(this.eofPdu != null && this.eofPdu.getConditionCode() == FileDirectivePdu.CC_NOERROR) {
            // Discard
            return;
        }
        this.eofPdu = pdu;

        // 4.6.5.2.3 However, transmission of Keep Alive PDUs shall cease upon receipt of the
        // transaction’s EOF (No error) PDU.
        stopKeepAliveSendingTimer();

        // Initial receipt of the EOF PDU for a transaction may optionally cause the receiving CFDP, if it is the
        // transaction's destination, to issue an EOF-Recv.indication.
        if(pdu.getDestinationEntityId() == getLocalEntityId() &&
                getEntity().getMib().getLocalEntity().isEofRecvIndicationRequired()) {
            getEntity().notifyIndication(new EofRecvIndication(pdu.getTransactionSequenceNumber(), createStateObject()));
        }

        // 4.6.1.2.9 Upon initial receipt of the EOF (No error) PDU, the file size indicated in the PDU
        // shall be compared to the transaction reception progress and a File Size Error fault declared if
        // the progress exceeds the file size.
        if(this.eofPdu.getConditionCode() == FileDirectivePdu.CC_NOERROR && this.receivedContiguousFileBytes > this.eofPdu.getFileSize()) {
            try {
                fault(FileDirectivePdu.CC_FILE_SIZE_ERROR, getLocalEntityId());
            } catch (FaultDeclaredException e) {
                // If we are here, all required actions have been already handled by the fault handler
                return;
            }
        }

        // Check whether you can reconstruct the file
        boolean fileReconstructedAndReady = checkForFullFileReconstruction();

        if(!isAcknowledged()) {
            // Upon receipt of an EOF (No error) PDU:
            if(this.eofPdu.getConditionCode() == FileDirectivePdu.CC_NOERROR) {
                if (fileReconstructedAndReady) {
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
                    if (this.metadataPdu.isClosureRequested()) {
                        sendFinishedPdu();
                    }
                    // Let's dispose here
                    handleDispose();
                } else {
                    // b) Otherwise, a transaction-specific Check timer shall be started. The timer shall have
                    // an implementation-specific expiry period, and there shall be an implementation specific limit on
                    // the number of times the Check timer for any single transaction may expire.
                    if(LOG.isLoggable(Level.INFO)) {
                        LOG.log(Level.INFO, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: starting check limit timer: %d ms", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), getRemoteDestination().getCheckInterval()));
                    }
                    this.transactionFinishCheckTimer = new TimerTask() {
                        @Override
                        public void run() {
                            handle(IncomingCfdpTransaction.this::handleTransactionFinishedCheckTimerElapsed);
                        }
                    };
                    schedule(this.transactionFinishCheckTimer, getRemoteDestination().getCheckInterval(), true);
                }
            } else {
                // XXX: At this stage, the standard is obscure: it is unclear if such EOF can ever come. In my implementation
                // it might come if the sender detects an error and must interrupt the sending of the file.
                // If this is the case, we assume that the transaction is cancelled.
                handleNoticeOfCompletion(false);
                handleDispose();
            }
        } else {
            if(this.eofPdu.getConditionCode() == FileDirectivePdu.CC_NOERROR) {
                if (fileReconstructedAndReady && this.eofPdu.getConditionCode() == FileDirectivePdu.CC_NOERROR) {
                    handleNoticeOfCompletion(true);
                    // Send the Finished PDU here
                    sendFinishedPdu();
                    // Start the timer here, as the finished PDU should be generated and ready to be sent
                    startPositiveAckTimer(this.finishedPdu);
                    // Do not dispose here, until the positive ack procedure terminates
                } else {
                    // Stop the as-you-go timer
                    stopNakComputation();
                    // Run immediately the task that computes and sends the required NAKs
                    handleNakComputation(metadataNakJustSent);
                    // Start the NAK timer, as per 4.6.4.6
                    // Upon initial receipt of the EOF (No error) PDU for a transaction, the receiving entity shall
                    // determine whether or not any of the transaction’s file data or metadata have yet to be
                    // received. If so:
                    // a) If any file data gaps or lost metadata are detected for which no segment requests were
                    // included in previously issued NAK PDUs, the receiving CFDP entity shall issue a
                    // NAK sequence.
                    // b) A transaction-specific NAK timer shall be started. The timer shall have an
                    // implementation-specific expiry period. When the timer expires, the receiving entity
                    // shall determine whether or not any of the transaction’s file data or metadata have yet to
                    // be received. If so, the receiving entity shall issue a NAK sequence whose scope begins
                    // at zero and extends through the entire length of the file, and the timer shall be reset.
                    this.nakTimerOnEofActivated = true;
                    startNakTimer();
                }
            } else {
                // XXX: At this stage, the standard is obscure: it is unclear if such EOF can ever come. In my implementation
                // it might come if the sender detects an error and must interrupt the sending of the file.
                // If this is the case, we assume that the transaction is cancelled.
                handleNoticeOfCompletion(false);
                handleDispose();
            }
        }
    }

    private void sendMetadataNak() {
        if(LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: sending Metadata PDU NAK", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
        }
        NakPduBuilder b = new NakPduBuilder();
        setCommonPduValues(b);
        b.setStartOfScope(this.receivedContiguousFileBytes);
        b.setEndOfScope(this.receivedContiguousFileBytes);
        b.addSegmentRequest(new NakPdu.SegmentRequest(0,0));
        NakPdu pdu = b.build();
        sendPdu(pdu);
    }

    private void handleNakComputation(boolean metadataNakJustSent) {
        if(LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: starting NAK computation", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
        }
        if(!isRunning()) {
            return;
        }
        // First of all, check if the metadata arrived, and if not, request the NAK
        if(this.metadataPdu == null && !metadataNakJustSent) {
            if(LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: missing Metadata PDU detected", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
            }
            sendMetadataNak();
        }
        // Then, inspect what you have in terms of file reconstruction
        List<NakPdu.SegmentRequest> missingSegments = new LinkedList<>();
        // Initialise it with the number of bytes that we are sure we received
        long startOfScope = receivedContiguousFileBytes;

        long tmpFilePartOffset = receivedContiguousFileBytes;
        for(Map.Entry<Long, FileDataPduSummary> e : this.fileReconstructionMap.entrySet()) {
            // All segments having an offset lower than tmpFilePartOffset can be safely skipped
            if(e.getKey() == tmpFilePartOffset) {
                // We found a segment that can contribute to the progress of the file, so we take this into account and we go on
                tmpFilePartOffset += e.getValue().length;
            } else if(e.getKey() > tmpFilePartOffset) {
                // The file part from tmpFilePartOffset and e.getValue().getOffset() is missing, create segment request
                if(LOG.isLoggable(Level.FINER)) {
                    LOG.log(Level.FINER, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: missing segment detected [%d - %d]", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), tmpFilePartOffset, e.getKey()));
                }
                missingSegments.add(new NakPdu.SegmentRequest(tmpFilePartOffset, e.getKey()));
                // Set the progress to e.getValue().offset + e.getValue().length
                tmpFilePartOffset = e.getValue().offset + e.getValue().length;
            }
        }
        long endOfScope = tmpFilePartOffset;
        // If EOF(No error) PDU was received, then add a segment from tmpFilePartOffset and the expected file size
        if(this.eofPdu != null && this.eofPdu.getConditionCode() == FileDirectivePdu.CC_NOERROR && tmpFilePartOffset < this.eofPdu.getFileSize()) {
            if(LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: missing segment detected (last) [%d - %d]", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), tmpFilePartOffset, this.eofPdu.getFileSize()));
            }
            missingSegments.add(new NakPdu.SegmentRequest(tmpFilePartOffset, this.eofPdu.getFileSize()));
            endOfScope = this.eofPdu.getFileSize();
        }
        // Build and send the NAK PDUs (group 4 segments into each NAK PDU - implementation-dependant)
        while(!missingSegments.isEmpty()) {
            NakPduBuilder b = new NakPduBuilder();
            setCommonPduValues(b);
            b.setStartOfScope(startOfScope);
            b.setEndOfScope(endOfScope);
            int segmentsToAdd = 4;
            while(!missingSegments.isEmpty() && segmentsToAdd > 0) {
                b.addSegmentRequest(missingSegments.remove(0));
                --segmentsToAdd;
            }
            NakPdu pdu = b.build();
            sendPdu(pdu);
        }
    }

    @Override
    protected void handleCancel(byte conditionCode, long faultEntityId) {
        setLastConditionCode(conditionCode, faultEntityId);
        setCancelled();
        // 4.11.2.3.1 On Notice of Cancellation of the Copy File procedure, the receiving CFDP entity
        // shall issue a Notice of Completion (Canceled).
        handleNoticeOfCompletion(false);
        // 4.11.2.3.2 If receiving in acknowledged mode,
        if(isAcknowledged()) {
            try {
                // a) the receiving CFDP entity shall issue a Finished (cancel) PDU indicating the reason
                // for transaction termination: Cancel.request received or the condition code of the
                // fault whose declaration triggered the Notice of Cancellation
                FinishedPdu theFinishedPdu = handleForwardingOfFinishedPdu();
                // b) Positive Acknowledgment procedures shall be applied to the Finished (cancel) PDU
                // with the Expected Response being an ACK (Finished) PDU with condition code
                // equal to that of the Finished (cancel) PDU
                startPositiveAckTimer(theFinishedPdu);

                // c) any PDU received after issuance of the Finished (cancel) PDU and prior to receipt of
                // the Expected Response shall be ignored, except that all Positive Acknowledgment
                // procedures remain in effect

                // Handled in handleIndication method
            } catch (UtLayerException e) {
                // d) any fault declared in the course of transferring this PDU must result in abandonment
                //    of the transaction.
                if(LOG.isLoggable(Level.SEVERE)) {
                    LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: fail on Finished PDU transmission upon cancelling (acknowledged): %s ", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), e.getMessage()), e);
                }
                handleAbandon(getLastConditionCode()); // Assume you abandon with the last condition code
            }
        } else {
            // 4.11.2.3.3 If receiving in unacknowledged mode, and if the transaction’s Metadata PDU has
            // been received and the Closure Requested flag is set to ‘1’ in that PDU, then the receiving
            // CFDP entity shall issue a Finished (cancel) PDU indicating the reason for transaction
            // termination: Cancel.request received or the condition code of the fault whose declaration
            // triggered the Notice of Cancellation.
            if(this.metadataPdu != null && this.metadataPdu.isClosureRequested()) {
                sendFinishedPdu();
            }
            // At this stage we should probably dispose the transaction: if we do not do it here, the transaction inactivity timer will actually
            // stay open (implementation-dependant).

            // 4.10.1 For a particular transaction, if there is a cessation of PDU reception for a specified
            // time period (the transaction inactivity limit), then an Inactivity fault condition shall be
            // declared.
            // NOTE – The 'cessation of PDU reception' that indicates inactivity is not limited to the
            // cessation of File Data PDU reception at the receiving entity. The cessation of
            // expected File Directive PDU reception (dependent upon the transaction's
            // transmission mode) at either the sending or the receiving entity may likewise
            // indicate inactivity. Detection of this condition is a local implementation matter.
            handleDispose();
        }
    }

    @Override
    protected void handleSuspend() {
        // 4.11.2.5.2 However, a Notice of Suspension shall be ignored if it pertains to a transaction
        // that is already suspended or if it is issued by the receiving CFDP entity for a transaction sent
        // in Unacknowledged mode.
        if(isSuspended() || !isAcknowledged()) {
            return;
        }
        setSuspended();
        handleSuspendActions();
        // f) issue a Suspended.indication if so configured in the MIB (see table 8-1)
        if(getEntity().getMib().getLocalEntity().isSuspendedIndicationRequired()) {
            getEntity().notifyIndication(new SuspendedIndication(getTransactionId(), FileDirectivePdu.CC_SUSPEND_REQUEST_RECEIVED, createStateObject()));
        }
        // g) save the status of the transaction.
    }

    @Override
    protected void handleFreeze() {
        // 4.12.2.1 On notification of the end of an opportunity to transmit to a specified remote CFDP
        // entity, the CFDP entity shall ‘freeze’ transmission for all transactions for which it is the
        // sending entity and the specified remote entity is the receiving entity.

        // 4.12.2.2 The freezing of transmission for a transaction shall have the same effects as
        // suspension of that transaction by the sending entity (see 4.11.2.6), except that no
        // Suspended.indication shall be issued and the transaction shall not be considered suspended.

        // 4.12.2.6 On notification of the end of an opportunity to receive from a specified remote
        // CFDP entity, the CFDP entity shall freeze reception for all transactions for which it is the
        // receiving entity and the specified remote entity is the sending entity, except those that are in
        // Unacknowledged mode (XXX: unacknowledged condition not handled).

        // 4.12.2.7 The freezing of reception for a transaction shall have the same effects as suspension
        // of that transaction by the receiving entity (see 4.11.2.7), except that no
        // Suspended.indication shall be issued, and the transaction shall not be considered
        // suspended.
        handleSuspendActions();
    }

    private void handleSuspendActions() {
        // 4.11.2.7 Notice of Suspension Procedures at the Receiving Entity
        // On Notice of Suspension of the Copy File procedure, the receiving CFDP entity shall
        // a) suspend transmission of NAK PDUs -> handled also in handleNakComputation method
        stopNakComputation();
        stopNakTimer();
        // b) suspend any transmission of Keep Alive PDUs -> handled also in handleKeepAliveTransmission
        stopKeepAliveSendingTimer();
        // c) suspend the inactivity timer
        stopTransactionInactivityTimer();
        // d) suspend transmission of Finished (complete) PDUs -> handled in handleForwardingOfFinishedPdu
        // e) suspend the application of Positive Acknowledgment Procedures to PDUs previously
        //    issued by this entity -> stop Finished PDU ACK timer
        stopPositiveAckTimer();
    }

    @Override
    protected void handleResume() {
        setResumed();
        // 4.6.7.1 Resume procedures apply upon receipt of a Resume.request primitive submitted by the
        // CFDP user. However:
        // a) [...]
        // b) if the transaction to which a Resume.request primitive pertains is currently not only
        //    suspended but also frozen (as defined in 4.12), then the transaction shall be
        //    considered no longer suspended but the only applicable procedure shall be the
        //    issuance of a Resumed.indication.
        if(!isFrozen()) {
            // Full resume
            handleResumeActions(true);
        } else {
            // Just send the notification, because the transaction is still frozen
            getEntity().notifyIndication(new ResumedIndication(getTransactionId(), this.receivedContiguousFileBytes, createStateObject()));
        }
    }

    @Override
    protected void handleUnfreeze() {
        // 4.12.2.3 On notification of the start of an opportunity to transmit to a specified remote
        // CFDP entity, the CFDP entity shall ‘thaw’ transmission for all transactions for which it is the
        // sending entity and the specified remote entity is the receiving entity.

        // 4.12.2.4 The thawing of transmission for a transaction shall have the same effects as
        // resumption of that transaction by the sending entity (see 4.6.7.2.1), except that (a) no
        // Resumed.indication shall be issued and (b) thawing transmission for a suspended
        // transaction shall have no effect whatsoever.

        // 4.12.2.8 On notification of the start of an opportunity to receive from a specified remote
        // CFDP entity, the CFDP entity shall thaw reception for all transactions for which it is the
        // receiving entity and the specified remote entity is the sending entity, except those that are in
        // Unacknowledged mode (XXX: unacknowledged condition not handled).

        // 4.12.2.9 The thawing of reception for a transaction shall have the same effects as resumption
        // of that transaction by the receiving entity (see 4.6.7.3), except that (a) no
        // Resumed.indication shall be issued, and (b) thawing reception for a suspended transaction
        // shall have no effect whatsoever.
        if(isSuspended()) {
            return;
        }
        handleResumeActions(false);
    }

    private void handleResumeActions(boolean sendNotification) {
        // 4.6.7.3.1 On receipt of a Resume.request primitive, the receiving CFDP entity shall
        // a) resume transmission of NAK PDUs
        if(isAcknowledged()) {
            restartNakComputation(); // Only if acknowledged
            if(this.nakTimerOnEofActivated) {
                startNakTimer(); // Only if acknowledged and already started by EOF
            }
        }
        // b) resume any suspended transmission of Keep Alive PDUs
        startKeepAliveSendingTimer(); // Only if acknowledged: handled inside the method
        // c) issue a Resumed.indication. // XXX: this clause conflicts with the MIB property:
        // Resumed.indication required when acting as receiving entity (Table 8-1)
        if(sendNotification && getEntity().getMib().getLocalEntity().isResumedIndicationRequired()) {
            getEntity().notifyIndication(new ResumedIndication(getTransactionId(), this.receivedContiguousFileBytes, createStateObject()));
        }
        // XXX: What about the transaction inactivity timer? I think it should be started
        startTransactionInactivityTimer();

        // 4.6.7.3.2 The application of Positive Acknowledgment Procedures to PDUs previously
        // issued by this entity shall be resumed.

        // Start Finished PDU ACK timer if Finished PDU was sent and not yet acknowledged
        if(this.finishedPdu != null && isAcknowledged()) {
            startPositiveAckTimer(this.finishedPdu);
        }
    }

    private void stopNakTimer() {
        if(this.nakTimer != null) {
            this.nakTimer.cancel();
            this.nakTimer = null;
        }
    }

    private void startNakTimer() {
        if(this.nakTimer != null) {
            return;
        }
        if(getRemoteDestination().getNakTimerInterval() == -1) {
            // No NAK timer defined
            return;
        }
        this.nakTimer = createNakTimerTask();
        schedule(this.nakTimer, getRemoteDestination().getNakTimerInterval(), false);
    }

    private TimerTask createNakTimerTask() {
        return new TimerTask() {
            @Override
            public void run() {
                handle(() -> {
                    if (IncomingCfdpTransaction.this.nakTimer == this) {
                        ++nakTimerCount;
                        if (fileCompleted) {
                            // Timer expired but file completed in the meantime... Should this ever happen?
                            return;
                        }
                        // 4.6.4.7 An implementation-specific measure of NAK activity shall be maintained for each
                        // transaction, and, if an implementation-specific limit is reached for a given transaction, a
                        // NAK Limit Reached fault shall be declared.

                        // NOTE – A typical implementation of NAK activity limit is a limit on the number of
                        // successive times the NAK timer is allowed to expire without intervening
                        // reception of file data and/or metadata that had not previously been received.
                        if (nakTimerCount < getRemoteDestination().getNakTimerExpirationLimit()) {
                            if(LOG.isLoggable(Level.WARNING)) {
                                LOG.log(Level.WARNING, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: NAK timer expired, count %d  ", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), nakTimerCount));
                            }
                            handleNakComputation(false);
                            IncomingCfdpTransaction.this.nakTimer = createNakTimerTask();
                            schedule(IncomingCfdpTransaction.this.nakTimer, getRemoteDestination().getNakTimerInterval(), false);
                        } else {
                            try {
                                fault(FileDirectivePdu.CC_NAK_LIMIT_REACHED, getLocalEntityId());
                            } catch (FaultDeclaredException e) {
                                // Nothing to be done here in any case
                            }
                        }
                    }
                });
            }
        };
    }

    private void stopNakComputation() {
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: stopping NAK computation timer", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
        }
        if(this.nakComputationTimer != null) {
            this.nakComputationTimer.cancel();
            this.nakComputationTimer = null;
        }
    }

    private void restartNakComputation() {
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: restarting NAK computation timer, interval %d ms", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), getRemoteDestination().getNakRecomputationInterval()));
        }
        if(this.nakComputationTimer != null) {
            this.nakComputationTimer.cancel();
            this.nakComputationTimer = null;
        }
        // Start the recomputation timer here
        this.nakComputationTimer = new TimerTask() {
            @Override
            public void run() {
                final TimerTask expiredTimer = this;
                handle(() -> {
                    if (IncomingCfdpTransaction.this.nakComputationTimer == expiredTimer) {
                        if(LOG.isLoggable(Level.INFO)) {
                            LOG.log(Level.INFO, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: NAK computation timer expired", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
                        }
                        handleNakComputation(false);
                    }
                });
            }
        };
        schedule(this.nakComputationTimer, getRemoteDestination().getNakRecomputationInterval(), true);
    }

    private void handleTransactionFinishedCheckTimerElapsed() {
        // This method can be called ONLY if the transaction is unacknowledged.
        if(LOG.isLoggable(Level.WARNING)) {
            LOG.log(Level.WARNING, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: check limit timer elapsed ", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
        }
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
                    sendFinishedPdu();
                }
                // Let's dispose everything
                handleDispose();
            } else if(this.transactionFinishCheckTimerCount == getRemoteDestination().getCheckIntervalExpirationLimit()) {
                if(this.transactionFinishCheckTimer != null) {
                    this.transactionFinishCheckTimer.cancel();
                }
                this.transactionFinishCheckTimer = null;
                try {
                    fault(FileDirectivePdu.CC_CHECK_LIMIT_REACHED, getLocalEntityId());
                } catch (FaultDeclaredException e) {
                    // Nothing more to be done here
                }
            }
        }
    }

    private void sendFinishedPdu() {
        sendFinishedPdu(false);
    }

    private void sendFinishedPdu(boolean startAckTimerOnSuccess) {
        try {
            FinishedPdu pdu = handleForwardingOfFinishedPdu();
            if(startAckTimerOnSuccess) {
                startPositiveAckTimer(pdu);
            }
        } catch (UtLayerException e) {
            if(LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: fail on Finished PDU transmission: %s ", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), e.getMessage()), e);
            }
        }
    }

    private void handleNoticeOfCompletion(boolean completed) {
        if(LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: notice of completion (%s) called", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), completed));
        }
        // 4.11.1.2.1 If receiving in acknowledged mode,
        // a) transmission of NAK PDUs, whether in response to NAK timer expiration or in
        //    response to any other events, shall be terminated
        stopNakComputation();
        stopNakTimer();
        // b) any transmission of Keep Alive PDUs shall be terminated
        stopKeepAliveSendingTimer();
        // c) the application of Positive Acknowledgment Procedures to PDUs previously issued
        //    by this entity shall be terminated
        stopPositiveAckTimer();
        // Derive final status and condition code
        if(completed) {
            if (this.filestoreProblemDetected) {
                this.finalFileStatus = FinishedPdu.FileStatus.DISCARDED_BY_FILESTORE;
                setLastConditionCode(FileDirectivePdu.CC_FILESTORE_REJECTION, getLocalEntityId());
            } else if (this.checksumTypeMissingSupportDetected) {
                this.finalFileStatus = FinishedPdu.FileStatus.RETAINED_IN_FILESTORE;
                setLastConditionCode(FileDirectivePdu.CC_UNSUPPORTED_CHECKSUM_TYPE, getLocalEntityId());
            } else if (this.checksumMismatchDetected) {
                if (this.fileCompleted) {
                    this.finalFileStatus = FinishedPdu.FileStatus.RETAINED_IN_FILESTORE;
                } else {
                    this.finalFileStatus = FinishedPdu.FileStatus.DISCARDED_DELIBERATLY;
                }
                setLastConditionCode(FileDirectivePdu.CC_FILE_CHECKSUM_FAILURE, getLocalEntityId());
            } else {
                this.finalFileStatus = FinishedPdu.FileStatus.RETAINED_IN_FILESTORE;
                setLastConditionCode(FileDirectivePdu.CC_NOERROR, null);
            }
        }
        // If 'cancelled', i.e. completed = false, then do not modify the last condition code

        // 4.11.1.2.2 In any case,
        // a) if the receiving entity is the transaction’s destination, and the procedure disposition
        //    cited in the Notice of Completion is ‘Completed’, the receiving CFDP entity shall
        //    execute any filestore requests conveyed by the Put procedure
        if(completed) {
            // 4.9.1 Filestore requests shall be executed only if any associated Copy File procedure
            // proceeded to completion with no error
            handleFilestoreRequests();
        } else {
            // b) if the procedure disposition cited in the Notice of Completion is ‘Canceled’, and the
            //    receiving entity is the transaction’s destination, then the incomplete data shall be
            //    either discarded or retained according to the option set in the MIB
            //    NOTE – On Notice of Completion (Canceled) for a transaction for which a Notice of
            //           Completion (Completed) was previously declared, all file data have
            //           necessarily already been received, and therefore there are no incomplete data
            //           to discard or retain.

            // If I do not have the metadata I do not even know how to call this file, how long is this, so sorry... discarded
            if(!this.fileCompleted && this.metadataPdu != null && this.metadataPdu.getDestinationEntityId() == getLocalEntityId() &&
                    getRemoteDestination().isRetainIncompleteReceivedFilesOnCancellation() &&
                    this.metadataPdu.getDestinationFileName() != null) { // You need to have a file to store!
                // Just a way to store a partial file with gaps: as an exception to the usual approach, this method below
                // does not raise any fault.
                storePartialFile();
                this.finalFileStatus = FinishedPdu.FileStatus.RETAINED_IN_FILESTORE;
            } else {
                // If the status is still unreported, then move it to DISCARDED_DELIBERATLY
                if(this.finalFileStatus == FinishedPdu.FileStatus.STATUS_UNREPORTED) {
                    this.finalFileStatus = FinishedPdu.FileStatus.DISCARDED_DELIBERATLY;
                }
            }
        }
        // c) if the receiving entity is the transaction’s destination, then it may optionally issue a
        //    Transaction-Finished.indication primitive indicating the condition in which the
        //    transaction was completed
        // d) if the receiving entity is the transaction’s destination, Filestore Responses and/or a
        //    Status Report shall be passed as parameters of the TransactionFinished.indication primitive as available.

        // If I do not have the metadata I might not know where this file is supposed to go, so as the indication is optional,
        // the code below is still standard-compliant
        if(this.metadataPdu != null && this.metadataPdu.getDestinationEntityId() == getLocalEntityId() &&
            getEntity().getMib().getLocalEntity().isTransactionFinishedIndicationRequired()) {
            getEntity().notifyIndication(new TransactionFinishedIndication(getTransactionId(),
                    getLastConditionCode(),
                    this.finalFileStatus,
                    this.fileCompleted,
                    this.filestoreResponses,
                    createStateObject()));
        }
    }

    private FinishedPdu handleForwardingOfFinishedPdu() throws UtLayerException {
        if(LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: handling forwarding of Finished PDU ", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
        }
        if(isRunning() || isCancelled()) { // This PDU must be forwarded even if it the transaction is cancelled
            this.finishedPdu = prepareFinishedPdu();
            forwardPdu(finishedPdu);
            return finishedPdu;
        } else {
            return null;
        }
    }

    private FinishedPdu prepareFinishedPdu() {
        FinishedPduBuilder b = new FinishedPduBuilder();
        setCommonPduValues(b);
        b.setDataComplete(this.fileCompleted);
        b.setFileStatus(this.finalFileStatus);
        b.setConditionCode(getLastConditionCode(), getLastFaultEntity());
        // Add filestore responses
        if(filestoreResponses != null) {
            for (FilestoreResponseTLV tlv : this.filestoreResponses) {
                b.addFilestoreResponse(tlv);
            }
        }
        return b.build();
    }

    private KeepAlivePdu prepareKeepAlivePdu() {
        KeepAlivePduBuilder b = new KeepAlivePduBuilder();
        setCommonPduValues(b);
        b.setProgress(this.receivedContiguousFileBytes);

        return b.build();
    }

    private <T extends CfdpPdu,K extends CfdpPduBuilder<T, K>> void setCommonPduValues(CfdpPduBuilder<T,K> b) {
        b.setAcknowledged(isAcknowledged());
        b.setCrcPresent(getRemoteDestination().isCrcRequiredOnTransmission());
        b.setDestinationEntityId(getLocalEntityId());
        b.setSourceEntityId(this.initialPdu.getSourceEntityId());
        b.setDirection(CfdpPdu.Direction.TOWARD_FILE_SENDER);
        b.setSegmentationControlPreserved(this.initialPdu.isSegmentationControlPreserved());
        // Set the length for the entity ID
        long maxEntityId = Long.max(getRemoteDestination().getRemoteEntityId(), getLocalEntityId());
        b.setEntityIdLength(BytesUtil.getEncodingOctetsNb(maxEntityId));
        // Set the transaction ID
        b.setTransactionSequenceNumber(getTransactionId(), BytesUtil.getEncodingOctetsNb(getTransactionId()));
        b.setLargeFile(this.initialPdu.isLargeFile());
    }

    @Override
    protected void forwardPdu(CfdpPdu pdu) throws UtLayerException {
        // Add to the pending list
        this.pendingUtTransmissionPduList.add(pdu);
        // Send all PDUs you have to send, stop if you fail
        if(LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: sending %d pending PDUs to UT layer %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), this.pendingUtTransmissionPduList.size(), getTransmissionLayer().getName()));
        }
        while(!pendingUtTransmissionPduList.isEmpty()) {
            CfdpPdu toSend = pendingUtTransmissionPduList.get(0);
            try {
                if(LOG.isLoggable(Level.FINEST)) {
                    LOG.log(Level.FINEST, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: sending PDU %s to UT layer %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), toSend, getTransmissionLayer().getName()));
                }
                getTransmissionLayer().request(toSend, this.initialPdu.getSourceEntityId());
                this.pendingUtTransmissionPduList.remove(0);
            } catch(UtLayerException e) {
                if(LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: PDU rejected by UT layer %s: %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), getTransmissionLayer().getName(), e.getMessage()), e);
                }
                throw e;
            }
        }
        if(LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: pending PDUs sent to UT layer %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), getTransmissionLayer().getName()));
        }
    }

    /**
     * This method checks if the file can be fully reconstructed. In such case, the file is reconstructed and stored.
     *
     * @return false if the file cannot be closed yet, true otherwise (no more PDUs are expected)
     */
    private boolean checkForFullFileReconstruction() throws FaultDeclaredException {
        if(LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: checking for full file reconstruction", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
        }
        if(this.fileCompleted) {
            if(LOG.isLoggable(Level.FINEST)) {
                LOG.log(Level.FINEST, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: file already completed, nothing to be done", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
            }
            return true;
        }
        if(this.metadataPdu == null || this.eofPdu == null) {
            if(LOG.isLoggable(Level.FINEST)) {
                LOG.log(Level.FINEST, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: Metadata PDU/EOF PDU missing, cannot start reconstruction", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
            }
            // Cannot start the reconstruction
            return false;
        }
        // Check the file progress, it must match the file size in the EOF PDU
        if(this.receivedContiguousFileBytes != this.eofPdu.getFileSize()) {
            if(LOG.isLoggable(Level.FINEST)) {
                LOG.log(Level.FINEST, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: not all bytes received, expected %d but contiguously received are %d", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), this.eofPdu.getFileSize(), this.receivedContiguousFileBytes));
            }
            // Still something missing
            return false;
        }
        // 4.6.1.2.8 At the earliest time at which the transaction’s Metadata, File Data (if any), and
        // EOF (No error) PDUs have all been received by the receiving entity:

        // a) a checksum shall be calculated for the delivered file by means of the applicable
        //    checksum algorithm, determined as described in 4.2.2 above
        // b) the calculated and received file checksums shall be compared
        int finalChecksum = computeFinalChecksum();
        if(finalChecksum == this.eofPdu.getFileChecksum() ||
                this.checksum.type() == CfdpChecksumRegistry.NULL_CHECKSUM_TYPE) {
            // c) if the compared checksums are equal or the applicable checksum algorithm is the null
            //    checksum algorithm, file delivery shall be deemed Complete
            storeFile();
        } else {
            if(LOG.isLoggable(Level.WARNING)) {
                int currentChecksum = this.checksum.getCurrentChecksum();
                int eofChecksum = this.eofPdu.getFileChecksum();
                LOG.log(Level.WARNING, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: checksum mismatch, computed %d but EOF is %d", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), currentChecksum, eofChecksum));
            }
            this.checksumMismatchDetected = true;
            // d) otherwise, a File Checksum Failure fault shall be declared.
            fault(FileDirectivePdu.CC_FILE_CHECKSUM_FAILURE, getLocalEntityId());
            // If a fault that is not NO_ACTION is raised, then an exception is propagated and this method
            // is interrupted here. Otherwise, it will process with the storage of the file.

            // The action taken upon such error need not necessarily entail discarding the
            // delivered file. The default handler for File Checksum Failure faults may be
            // Ignore, causing the discrepancy to be announced to the user in a
            // Fault.indication but permitting the completion of the Copy File procedure
            // at the receiving entity. This configuration setting might be especially
            // appropriate for transactions conducted in unacknowledged mode.
            storeFile();
        }
        return true;
    }

    /**
     * This method computes the checksum from the temporary random access file.
     *
     * @return the computed checksum
     */
    private int computeFinalChecksum() {
        try {
            long length = this.temporaryReconstructionFileMap.length();
            this.temporaryReconstructionFileMap.seek(0);
            byte[] tmpBuffer = new byte[1024];
            long position = 0;
            while (position < length) {
                // read(...) advances the position of the file pointer, no seek needed after that
                int read = this.temporaryReconstructionFileMap.read(tmpBuffer);
                this.checksum.checksum(tmpBuffer, 0, read, position);
                position += read;
            }
            return this.checksum.getCurrentChecksum();
        } catch (IOException e) {
            if(LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: problem when computing checksum for file %s: %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), this.metadataPdu.getDestinationFileName(), e.getMessage()), e);
            }
            // I return this.eofPdu.getFileChecksum() + 1, to ensure a checksum failure
            return this.eofPdu.getFileChecksum() + 1;
        }
    }

    /**
     * This method stores the contents currently present in the file reconstruction map in a temporary file.
     * In case of filestore failure, no fault(..) are raised, but the condition code is retained accordingly.
     */
    private void storePartialFile() {
        IVirtualFilestore filestore = getEntity().getFilestore();
        try {
            filestore.createFile(this.metadataPdu.getDestinationFileName() + PARTIAL_FILE_EXTENSION);
            OutputStream os = filestore.writeFile(this.metadataPdu.getDestinationFileName() + PARTIAL_FILE_EXTENSION, false);
            // Iterate of the map (sorted by offset) and write either the segment, or 0 byte filling and the segment
            long currentOffset = writeFileToStorage(os);
            // Check if the file is completed
            if(getTotalFileSize() > 0 && currentOffset < getTotalFileSize()) {
                long bytesToWrite = getTotalFileSize() - currentOffset;
                long cycles = bytesToWrite / FILE_PADDING_BUFFER.length;
                long rest = bytesToWrite % FILE_PADDING_BUFFER.length;
                for(int i = 0; i < cycles; ++i) {
                    os.write(FILE_PADDING_BUFFER);
                }
                os.write(new byte[(int) rest]); // Less than 4 KB
            }
            os.flush();
            os.close();
        } catch (FilestoreException | IOException e) {
            if(LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: problem when storing (partial) file %s to filestore: %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), this.metadataPdu.getDestinationFileName(), e.getMessage()), e);
            }
            this.filestoreProblemDetected = true;
            // No fault raised here, but an indication of the condition code is provided
            setLastConditionCode(FileDirectivePdu.CC_FILESTORE_REJECTION, getLocalEntityId());
        }
    }

    private void storeFile() throws FaultDeclaredException {
        if(this.fileCompleted) {
            if(LOG.isLoggable(Level.INFO)) {
                LOG.log(Level.INFO, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: file already attempted for storage, won't store it again", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
            }
            return;
        }
        if(LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: storing file to disk", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
        }

        // No matter what, the file was completed
        this.fileCompleted = true;

        IVirtualFilestore filestore = getEntity().getFilestore();
        try {
            if(LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: creating file %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), this.metadataPdu.getDestinationFileName()));
            }
            filestore.createFile(this.metadataPdu.getDestinationFileName());
            if(LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: writing contents to file %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), this.metadataPdu.getDestinationFileName()));
            }
            OutputStream os = filestore.writeFile(this.metadataPdu.getDestinationFileName(), false);
            writeFileToStorage(os);
            os.flush();
            os.close();
            if(LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: file %s stored", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), this.metadataPdu.getDestinationFileName()));
            }
        } catch (FilestoreException | IOException e) {
            if(LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: problem when storing file %s to filestore: %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), this.metadataPdu.getDestinationFileName(), e.getMessage()), e);
            }
            this.filestoreProblemDetected = true;
            this.finalFileStatus = FinishedPdu.FileStatus.DISCARDED_BY_FILESTORE;
            fault(FileDirectivePdu.CC_FILESTORE_REJECTION, getLocalEntityId());
        }
    }

    private long writeFileToStorage(OutputStream os) throws IOException {
        long length = this.temporaryReconstructionFileMap.length();
        this.temporaryReconstructionFileMap.seek(0);
        byte[] tmpBuffer = new byte[1024];
        long position = 0;
        while (position < length) {
            // read(...) advances the position of the file pointer, no seek needed after that
            int read = this.temporaryReconstructionFileMap.read(tmpBuffer);
            os.write(tmpBuffer, 0, read);
            position += read;
        }
        return position;
    }

    private void handleFilestoreRequests() {
        this.filestoreResponses = new LinkedList<>();
        boolean faultDetected = false;
        // 4.9.2 Filestore requests shall be transmitted in the Directive Parameter field of the
        // Metadata PDU in the order in which they were submitted in the Put primitive (see 3.4.1).
        // 4.9.3 Execution of filestore requests is mandatory. Filestore requests shall be executed in
        // the order in which they are received in the Directive Parameter field of the Metadata PDU.
        for(TLV req : this.metadataPdu.getOptions()) {
            if(req instanceof FilestoreRequestTLV) {
                FilestoreRequestTLV freq = (FilestoreRequestTLV) req;
                if(LOG.isLoggable(Level.INFO)) {
                    LOG.log(Level.INFO, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: filestore request to execute: %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), freq));
                }
                if(faultDetected) {
                    if(LOG.isLoggable(Level.WARNING)) {
                        LOG.log(Level.WARNING, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: execution of filestore request %s not performed, due to previous fault", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), freq));
                    }
                    // 4.9.5 If any filestore request obtained from a given Metadata PDU does not succeed, no
                    // subsequent filestore requests from the same Metadata PDU shall be executed. For each of
                    // these non-executed subsequent filestore requests, the filestore request status code returned in
                    // the resulting Filestore Responses parameter shall be ‘not performed’.
                    this.filestoreResponses.add(
                            new FilestoreResponseTLV(freq.getActionCode(),
                                FilestoreResponseTLV.StatusCode.NOT_PERFORMED,
                                    null, null, null));
                } else {
                    // 4.9.4 Execution of a filestore request shall result in the generation of a Filestore Responses
                    // parameter. If acknowledged mode is in effect or transaction closure is requested, the
                    // Filestore Responses parameter shall be transmitted to the originator of the request via the
                    // Finished (complete) PDU.
                    FilestoreResponseTLV resp = freq.execute(getEntity().getFilestore());
                    this.filestoreResponses.add(resp);
                    if(LOG.isLoggable(Level.INFO)) {
                        LOG.log(Level.INFO, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: filestore request to execute: %s - Response: %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), freq, resp));
                    }
                    if(resp.getStatusCode() != FilestoreResponseTLV.StatusCode.SUCCESSFUL) {
                        faultDetected = true;
                    }
                }
            }
        }
        // 4.9.6 Failure of a filestore request shall not result in the declaration of a fault of any kind.
    }

    private void handleAckPdu(AckPdu pdu) {
        // ACK of Finished PDU (only in case of acknowledged mode)
        if(pdu.getDirectiveCode() == FileDirectivePdu.DC_FINISHED_PDU) {
            if(LOG.isLoggable(Level.INFO)) {
                LOG.log(Level.INFO, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: ACK PDU(Finished) received", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
            }
            if(isAcknowledged() && this.finishedPdu != null && this.finishedPdu.getConditionCode() == pdu.getConditionCode()) {
                // Ack received
                this.finishedPdu = null;
                stopPositiveAckTimer();
                // At this stage, probably the procedure must be disposed
                handleDispose();
            }
        } else {
            if(LOG.isLoggable(Level.WARNING)) {
                LOG.log(Level.WARNING, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: ACK PDU(for directive code 0x%02X) received", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), pdu.getDirectiveCode()));
            }
        }
    }

    @Override
    protected void handleTransactionInactivity() {
        if(LOG.isLoggable(Level.WARNING)) {
            LOG.log(Level.WARNING, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: transaction inactivity detected", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
        }
        try {
            fault(FileDirectivePdu.CC_INACTIVITY_DETECTED, getLocalEntityId());
        } catch (FaultDeclaredException e) {
            // Nothing to be done here
        }
    }

    @Override
    protected void handlePreDispose() {
        // Cleanup resources and memory
        this.pendingUtTransmissionPduList.clear();
        if(this.fileReconstructionMap != null) {
            this.fileReconstructionMap.clear();
        }
        if(this.temporaryReconstructionFileMap != null) {
            try {
                this.temporaryReconstructionFileMap.close();
            } catch (IOException e) {
                if(LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: error when closing the temporary file: %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), e.getMessage()), e);
                }
            }
            this.temporaryReconstructionFile.delete(); // NOSONAR no need to check the boolean value here
            this.temporaryReconstructionFileMap = null;
        }
        this.checksum = null;
        if(this.filestoreResponses != null) {
            this.filestoreResponses.clear();
        }
        if(transactionFinishCheckTimer != null) {
            transactionFinishCheckTimer.cancel();
            transactionFinishCheckTimer = null;
        }
        stopKeepAliveSendingTimer();
        stopNakComputation();
        stopNakTimer();
        // Done
    }

    @Override
    protected long getSourceEntityId() {
        return this.initialPdu.getSourceEntityId();
    }

    @Override
    protected long getDestinationEntityId() {
        return this.initialPdu.getDestinationEntityId();
    }

    @Override
    protected boolean isAcknowledged() {
        return this.initialPdu.isAcknowledged();
    }

    /**
     * Internal class to be used to keep the summary of a received {@link FileDataPdu}.
     */
    private static class FileDataPduSummary {

        /**
         * The file offset as delivered by the {@link FileDataPdu}
         */
        public final long offset;
        /**
         * The file data length as delivered by the {@link FileDataPdu}
         */
        public final long length;

        public FileDataPduSummary(long offset, long length) {
            this.offset = offset;
            this.length = length;
        }
    }
}
