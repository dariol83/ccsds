package eu.dariolucia.ccsds.cfdp.entity;

import eu.dariolucia.ccsds.cfdp.common.BytesUtil;
import eu.dariolucia.ccsds.cfdp.entity.indication.*;
import eu.dariolucia.ccsds.cfdp.entity.request.PutRequest;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.FileSegment;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.ICfdpFileSegmenter;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.impl.FixedSizeSegmenter;
import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import eu.dariolucia.ccsds.cfdp.mib.FaultHandlerStrategy;
import eu.dariolucia.ccsds.cfdp.protocol.builder.*;
import eu.dariolucia.ccsds.cfdp.protocol.checksum.CfdpChecksumRegistry;
import eu.dariolucia.ccsds.cfdp.protocol.checksum.CfdpUnsupportedChecksumType;
import eu.dariolucia.ccsds.cfdp.protocol.checksum.ICfdpChecksum;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.*;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.*;
import eu.dariolucia.ccsds.cfdp.ut.UtLayerException;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CfdpOutgoingTransaction extends CfdpTransaction {

    private static final Logger LOG = Logger.getLogger(CfdpOutgoingTransaction.class.getName());

    private final PutRequest request;
    private final Map<Integer, FaultHandlerStrategy.Action> faultHandlers = new HashMap<>();

    // Variables to handle file data transfer
    private final List<CfdpPdu> sentPduList = new LinkedList<>();
    private final List<CfdpPdu> pendingUtTransmissionPduList = new LinkedList<>();

    private ICfdpFileSegmenter segmentProvider;
    private ICfdpChecksum checksum;
    private byte conditionCode = FileDirectivePdu.CC_NOERROR;
    private EntityIdTLV faultEntityId;
    private long effectiveFileSize;

    // Timer for the declaration of transaction completed when transaction closure is required
    private TimerTask transactionFinishCheckTimer;

    // Finished PDU for transactions with closure request
    private FinishedPdu finishedPdu;
    // True if the metadata PDU was sent, otherwise false
    private boolean metadataPduSent;

    public CfdpOutgoingTransaction(long transactionId, CfdpEntity entity, PutRequest r) {
        super(transactionId, entity, r.getDestinationCfdpEntityId());
        this.request = r;
        this.faultHandlers.putAll(entity.getMib().getLocalEntity().getFaultHandlerMap());
        this.faultHandlers.putAll(r.getFaultHandlerOverrideMap());
    }

    /**
     * This method handles the reception of a CFDP PDU from the UT layer.
     *
     * @param pdu the PDU to handle
     */
    @Override
    protected void handleIndication(CfdpPdu pdu) {
        resetTransactionInactivityTimer();
        // As a sender you can expect:
        // 1) ACK PDU for EOF PDU (if in acknowledged mode)
        // 2) Finished PDU (if in acknowledged mode or if closure is requested)
        // 3) NAK PDU (if in acknowledged mode)
        if(pdu instanceof FinishedPdu && !isCancelled()) {
            handleFinishedPdu((FinishedPdu) pdu);
        } else if(pdu instanceof NakPdu && !isCancelled()) {
            handleNakPdu((NakPdu) pdu);
        } else if(pdu instanceof AckPdu) {
            handleAckPdu((AckPdu) pdu); // For EOF ACK
        } else if(pdu instanceof KeepAlivePdu && !isCancelled()) {
            handleKeepAlivePdu((KeepAlivePdu) pdu);
        }
    }

    private void handleKeepAlivePdu(KeepAlivePdu pdu) {
        // 4.6.5.3.1 At the sending CFDP entity, if the discrepancy between the reception progress
        // reported by the Keep Alive PDU, and the transaction’s transmission progress so far at this
        // entity exceeds a preset limit, the sending CFDP entity may optionally declare a Keep Alive
        // Limit Reached fault.
        int limit = getRemoteDestination().getKeepAliveDiscrepancyLimit();
        if(limit == -1) {
            return;
        }
        if(this.effectiveFileSize - pdu.getProgress() > limit) {
            if(LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, String.format("Outgoing transaction %d to entity %d: keep alive limit fault - remote progress: %d bytes, local progress %d bytes, limit %d bytes", getTransactionId(), getRemoteDestination().getRemoteEntityId(), this.effectiveFileSize, pdu.getProgress(), limit));
            }
            getEntity().notifyIndication(new FaultIndication(getTransactionId(), FileDirectivePdu.CC_KEEPALIVE_LIMIT_REACHED,
                    this.effectiveFileSize));
        }
    }

    public void requestKeepAlive() {
        sendPromptPdu(true);
    }

    public void requestNak() {
        sendPromptPdu(false);
    }

    private void sendPromptPdu(boolean isKeepAlive) {
        if (isAcknowledged() && isRunning()) {
            PromptPdu p = preparePromptPdu(isKeepAlive);
            forwardPdu(p, true); // Do not store this PDU
        }
    }

    @Override
    protected void handleCancel(byte conditionCode, long faultEntityId) {
        this.conditionCode = conditionCode;
        this.faultEntityId = new EntityIdTLV(faultEntityId, getEntityIdLength());
        setCancelled();
        // Handling of a cancellation request from user
        // 4.11.2.2.1 On Notice of Cancellation of the Copy File procedure, the sending CFDP entity
        // shall
        // a) issue a Notice of Completion (Canceled)
        handleNoticeOfCompletion(false);
        // b) issue an EOF (cancel) PDU indicating the reason for transaction termination:
        // Cancel.request received or the condition code of the fault whose declaration
        // triggered the Notice of Cancellation. The file size field in the EOF (cancel) PDU
        // shall contain the transaction’s current transmission progress, and the checksum in this
        // PDU shall be the computed checksum over all File Data PDUs sent so far in the
        // course of this transaction.
        int finalChecksum = this.checksum.getCurrentChecksum();
        EndOfFilePdu pdu = prepareEndOfFilePdu(finalChecksum);
        forwardPdu(pdu);
        // Send the EOF indication
        if(getEntity().getMib().getLocalEntity().isEofSentIndicationRequired()) {
            getEntity().notifyIndication(new EofSentIndication(getTransactionId()));
        }
        // TODO: 4.11.2.2.3 Any fault declared in the course of transferring the EOF (cancel) PDU must result
        //  in abandonment of the transaction.

        // 4.11.2.2.2 If sending in acknowledged mode,
        if(isAcknowledged()) {
            // a) Positive Acknowledgment procedures shall be applied to the EOF (cancel) PDU with
            // the Expected Response being an ACK (EOF) PDU with condition code equal to that
            // of the EOF (cancel) PDU
            // TODO: start timer per EOF

            // b) any PDU received after issuance of the EOF (cancel) PDU and prior to receipt of the
            // Expected Response shall be ignored, except that all Positive Acknowledgment
            // procedures remain in effect.

            // This is handled in the handleIndication method
        } else {
            handleDispose();
        }
    }

    @Override
    protected void handleSuspend() {
        if(isSuspended()) {
            // 4.11.2.5.2 However, a Notice of Suspension shall be ignored if it pertains to a transaction
            // that is already suspended [...]
            return;
        }
        // 4.11.2.6.1 On Notice of Suspension of the Copy File procedure, the sending CFDP entity
        // shall
        // a) suspend transmission of Metadata PDU, file segments, and EOF PDU
        setSuspended(); // This call will stop forwarding of metadata, file and EOF PDUs
        // b) save the status of the transaction.
        handleSuspendActions();

        // 4.11.2.6.3 The sending entity shall issue a Suspended.indication.
        getEntity().notifyIndication(new SuspendedIndication(getTransactionId(), FileDirectivePdu.CC_SUSPEND_REQUEST_RECEIVED));
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
        // 4.11.2.6.2 If operating in acknowledged mode,
        if (isAcknowledged()) {
            // a) any transmission of Prompt PDUs shall be suspended -> done in the sendPromptPdu method
            // TODO: b) the inactivity timer shall be suspended
            // TODO: c) the application of Positive Acknowledgment Procedures to PDUs previously issued
            //  by this entity shall be suspended. -> stop timer per EOF
        }
    }

    @Override
    protected void handleResume() {
        setResumed();
        handleResumeActions(true);
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
        // 4.6.7.2.1 On receipt of a Resume.request primitive, the sending CFDP entity shall
        // a) resume transmission of Metadata PDU, file segments, and EOF PDU
        if(this.metadataPduSent) {
            // Resume the sending of the file
            handle(this::sendFileSegment);
        } else {
            // Start the sending of the file from the beginning
            handle(this::handleStartTransaction);
        }
        // b) issue a Resumed.indication.
        if(sendNotification) {
            getEntity().notifyIndication(new ResumedIndication(getTransactionId(), this.effectiveFileSize));
        }

        // 4.6.7.2.2 If operating in acknowledged mode,
        if(isAcknowledged()) {
            // a) any suspended transmission of Prompt PDUs shall be resumed -> handled in the sendPromptPdu method
            // TODO: b) the application of Positive Acknowledgment Procedures to PDUs previously issued
            //    by this entity shall be resumed. -> if EOF was sent, start EOF timer
        }
    }

    @Override
    protected void handleReport() {
        // TODO: define a class with the most important information
        getEntity().notifyIndication(new ReportIndication(getTransactionId(), new Object()));
    }

    private void handleAckPdu(AckPdu pdu) {
        // TODO: stop timer per EOF
    }

    private void handleNakPdu(NakPdu pdu) {
        // 4.6.4.2.1 The sending CFDP entity shall respond to all received NAK PDUs by
        // retransmitting the requested Metadata PDU and/or the extents of the data file defined by the
        // start and end offsets of the segment requests in the NAK PDU.
        long startOfScope = pdu.getStartOfScope();
        long endOfScope = pdu.getEndOfScope();
        for(CfdpPdu sentPdu : this.sentPduList) {
            if(sentPdu instanceof MetadataPdu && startOfScope == 0) {
                checkAndRetransmitMetadataPdu((MetadataPdu) sentPdu, pdu.getSegmentRequests());
            } else if(sentPdu instanceof FileDataPdu) {
                FileDataPdu filePdu = (FileDataPdu) sentPdu;
                if(filePdu.getOffset() + filePdu.getFileData().length < startOfScope) {
                    continue;
                }
                if(filePdu.getOffset() > endOfScope) {
                    continue;
                }
                // File is in offset, so check if there is a segment request
                checkAndRetransmitFileDataPdu(filePdu, pdu.getSegmentRequests());
            } else if(sentPdu instanceof EndOfFilePdu) {
                // Ignore
            }
        }
    }

    private void checkAndRetransmitFileDataPdu(FileDataPdu filePdu, List<NakPdu.SegmentRequest> segmentRequests) {
        for(NakPdu.SegmentRequest segmentRequest : segmentRequests) {
            // If there is an overlap with the file data pdu, send the pdu again
            if(segmentRequest.overlapWith(filePdu.getOffset(), filePdu.getOffset() + filePdu.getFileData().length)) {
                forwardPdu(filePdu, true);
                return;
            }
        }
    }

    private void checkAndRetransmitMetadataPdu(MetadataPdu sentPdu, List<NakPdu.SegmentRequest> segmentRequests) {
        for(NakPdu.SegmentRequest segmentRequest : segmentRequests) {
            // If there is a segment with start and end equal to 0, then retransmit the metadata PDU
            if(segmentRequest.getStartOffset() == 0 && segmentRequest.getEndOffset() == 0) {
                forwardPdu(sentPdu, true);
                return;
            }
        }
    }

    private void handleFinishedPdu(FinishedPdu pdu) {
        if(!isAcknowledged()) {
            this.finishedPdu = pdu;
            // 4.6.3.2.3 Reception of a Finished PDU shall cause
            // a) the Check timer of the associated transaction to be turned off; and
            // b) a Notice of Completion (either Completed or Canceled, depending on the nature of
            //    the Finished PDU) to be issued.
            if (this.transactionFinishCheckTimer != null) {
                this.transactionFinishCheckTimer.cancel();
                this.transactionFinishCheckTimer = null;
            }
            handleNoticeOfCompletion(deriveCompletedStatus(pdu));
            // Clean up the transaction resources
            handleDispose();
        } else {
            // 4.6.4.2.4 Positive Acknowledgment procedures shall be applied to the EOF (No error) and
            // Finished (complete) PDUs with the Expected Responses being ACK (EOF) PDU and ACK
            // (Finished) PDU, respectively.
            AckPdu toSend = prepareAckPdu(pdu);
            forwardPdu(toSend);
            // 4.6.4.2.3 Receipt of the Finished (complete) PDU shall cause the sending CFDP entity to
            // issue a Notice of Completion (Completed).
            handleNoticeOfCompletion(deriveCompletedStatus(pdu));
            // Clean up the transaction resources
            handleDispose();
        }
    }

    private boolean deriveCompletedStatus(FinishedPdu pdu) {
        return pdu.getConditionCode() == FileDirectivePdu.CC_NOERROR || pdu.getConditionCode() == FileDirectivePdu.CC_UNSUPPORTED_CHECKSUM_TYPE &&
                pdu.isDataComplete();
    }

    /**
     * This method implementation reflects the steps specified in clause 4.6.1.1 - Copy File Procedures at Sending Entity
     */
    @Override
    protected void handleActivation() {
        // Notify the creation of the new transaction to the subscriber
        getEntity().notifyIndication(new TransactionIndication(getTransactionId(), request));
        // Start the transaction inactivity timer
        startTransactionInactivityTimer();
        // Handle the start of the transaction
        handleStartTransaction();
        // End of the transmission activation
    }

    @Override
    protected void startTransactionInactivityTimer() {
        // 4.10.2 This requirement does not apply to the sending entity of an unacknowledged mode
        // transfer
        if(isAcknowledged()) {
            super.startTransactionInactivityTimer();
        }
    }

    private void handleStartTransaction() {
        if(isRunning()) {
            // Initiation of the Copy File procedures shall cause the sending CFDP entity to
            // forward a Metadata PDU to the receiving CFDP entity.
            MetadataPdu metadataPdudu = prepareMetadataPdu();
            forwardPdu(metadataPdudu);
            this.metadataPduSent = true;

            if (isFileToBeSent()) {
                // For transactions that deliver more than just metadata, Copy File initiation also
                // shall cause the sending CFDP entity to retrieve the file from the sending filestore and to
                // transmit it in File Data PDUs.
                segmentAndForwardFileData();
            } else {
                // For Metadata only transactions, closure might or might not be requested
                // TODO: to be checked, as handleClosure is related primarily to file transactions
                handle(this::handleClosure);
            }
        }
    }

    /**
     * This method retrieve a file handler from the filestore and starts delivering it in chunks.
     * The division in chunks can happen in two different ways:
     * <ol>
     *     <li>If the segmentation control is requested, this object asks for a ICfdpSegmentationStrategy that can split the file
     *     in segments. If such segmentation exists, then the segmentation strategy returns an Iterator of chunks.</li>
     *     <li>If no segmentation strategy is supported, or if segmentation is not required, this implementation split the file
     *     in chunks of equal, predefined size</li>
     * </ol>
     * From the point of view of the receiver, nothing changes.
     *
     * In order to free up the confiner thread, this method does not put all the chunks for transmission, but put in the
     * execution queue only a task that:
     * <ol>
     *     <li>Verify if a chunk can be sent and what to do in case it cannot be sent</li>
     *     <li>Extract and send one chunk</li>
     *     <li>Queue a copy of itself to send the next chunk</li>
     * </ol>
     *
     * The task sending the last chunk enqueues also a {@link eu.dariolucia.ccsds.cfdp.protocol.pdu.EndOfFilePdu} and
     * check if closure must be handled.
     */
    private void segmentAndForwardFileData() {
        this.effectiveFileSize = 0;
        this.conditionCode = FileDirectivePdu.CC_NOERROR;
        // Initialise the chunk provider
        if(this.request.isSegmentationControl()) {
            this.segmentProvider = getEntity().getSegmentProvider(request.getSourceFileName(), getRemoteDestination().getRemoteEntityId());
        }
        // If there is no segmentation available, or if no segmentation control is needed, use the fixed size segment provider
        if(this.segmentProvider == null) {
            this.segmentProvider = new FixedSizeSegmenter(getEntity().getFilestore(), request.getSourceFileName(), getRemoteDestination().getMaximumFileSegmentLength());
        }
        // Initialise the checksum computer
        try {
            this.checksum = CfdpChecksumRegistry.getChecksum(getRemoteDestination().getDefaultChecksumType()).build();
        } catch (CfdpUnsupportedChecksumType e) {
            this.conditionCode = FileDirectivePdu.CC_UNSUPPORTED_CHECKSUM_TYPE;
            this.faultEntityId = new EntityIdTLV(getEntity().getMib().getLocalEntity().getLocalEntityId(), getEntityIdLength());
            // Not available, then use the modular checksum
            this.checksum = CfdpChecksumRegistry.getModularChecksum().build();
        }
        // Schedule the first task to send the first segment
        handle(this::sendFileSegment);
    }

    /**
     * This method performs the following actions:
     * <ol>
     *     <li>Verify if a chunk can be sent and what to do in case it cannot be sent</li>
     *     <li>Extract and send one chunk</li>
     *     <li>Queue a copy of itself to send the next chunk</li>
     * </ol>
     */
    private void sendFileSegment() {
        // TODO verify if you can send the segment (transmission contact time)
        if(!isRunning()) {
            return;
        }
        // Extract and send the file segment
        FileSegment gs = this.segmentProvider.nextSegment();
        // Check if there are no more segments to send
        if(gs.isEof()) {
            // Construct the EOF pdu
            this.segmentProvider.close();
            int finalChecksum = this.checksum.getCurrentChecksum();
            EndOfFilePdu pdu = prepareEndOfFilePdu(finalChecksum);
            // TODO: Start EOF ACK timer
            forwardPdu(pdu);
            // Send the EOF indication
            if(getEntity().getMib().getLocalEntity().isEofSentIndicationRequired()) {
                getEntity().notifyIndication(new EofSentIndication(getTransactionId()));
            }
            // Handle the closure of the transaction
            handleClosure();
        } else {
            // Construct the file data PDU
            this.effectiveFileSize += gs.getData().length;
            addToChecksum(gs);
            FileDataPdu pdu = prepareFileDataPdu(gs);
            forwardPdu(pdu);
            // Schedule the next task to send the next segment
            handle(this::sendFileSegment);
        }
    }

    private void addToChecksum(FileSegment gs) {
        this.checksum.checksum(gs.getData(), gs.getOffset());
    }

    private boolean forwardPdu(CfdpPdu pdu) {
        return forwardPdu(pdu, false);
    }

    private boolean forwardPdu(CfdpPdu pdu, boolean retransmission) { // NOSONAR: false positive
        if(isAcknowledged() && !retransmission) {
            // Remember the PDU
            this.sentPduList.add(pdu);
        }
        // Add to the pending list
        this.pendingUtTransmissionPduList.add(pdu);
        // Send all PDUs you have to send, stop if you fail
        if(LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, String.format("Outgoing transaction %d to entity %d: sending %d pending PDUs to UT layer %s", getTransactionId(), getRemoteDestination().getRemoteEntityId(), this.pendingUtTransmissionPduList.size(), getTransmissionLayer().getName()));
        }
        while(!pendingUtTransmissionPduList.isEmpty()) {
            CfdpPdu toSend = pendingUtTransmissionPduList.get(0);
            try {
                if(LOG.isLoggable(Level.FINEST)) {
                    LOG.log(Level.FINEST, String.format("Outgoing transaction %d to entity %d: sending PDU %s to UT layer %s", getTransactionId(), getRemoteDestination().getRemoteEntityId(), toSend, getTransmissionLayer().getName()));
                }
                getTransmissionLayer().request(toSend);
                this.pendingUtTransmissionPduList.remove(0);
            } catch(UtLayerException e) {
                if(LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, String.format("Outgoing transaction %d to entity %d: PDU rejected by UT layer %s: %s", getTransactionId(), getRemoteDestination().getRemoteEntityId(), getTransmissionLayer().getName(), e.getMessage()), e);
                }
                return false;
            }
        }
        if(LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, String.format("Outgoing transaction %d to entity %d: pending PDUs sent to UT layer %s", getTransactionId(), getRemoteDestination().getRemoteEntityId(), getTransmissionLayer().getName()));
        }
        return true;
    }

    private MetadataPdu prepareMetadataPdu() {
        MetadataPduBuilder b = new MetadataPduBuilder();
        setCommonPduValues(b);
        // Metadata specific
        b.setSegmentationControlPreserved(false); // Always 0 for file directive PDUs
        b.setClosureRequested(isClosureRequested());
        b.setChecksumType((byte) getRemoteDestination().getDefaultChecksumType());
        if(isFileToBeSent()) {
            // File data
            b.setSourceFileName(request.getSourceFileName());
            b.setDestinationFileName(request.getDestinationFileName());
            long fileSize;
            try {
                if (getEntity().getFilestore().isUnboundedFile(request.getSourceFileName())) {
                    fileSize = 0;
                } else {
                    fileSize = getEntity().getFilestore().fileSize(request.getSourceFileName());
                }
            } catch (FilestoreException e) {
                if(LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, String.format("Cannot determine the size/boundness of the file %s: %s", request.getSourceFileName(), e.getMessage()), e);
                }
                fileSize = 0;
            }
            b.setFileSize(fileSize);
        }
        // Segment metadata not present
        b.setSegmentMetadataPresent(false); // Always 0 for file directive PDUs

        // Add the declared options
        if(request.getFlowLabel() != null && request.getFlowLabel().length > 0) {
            b.addOption(new FlowLabelTLV(request.getFlowLabel()));
        }
        for(MessageToUserTLV t : request.getMessageToUserList()) {
            b.addOption(t);
        }
        for(FilestoreRequestTLV t : request.getFileStoreRequestList()) {
            b.addOption(t);
        }
        for(Map.Entry<Integer, FaultHandlerStrategy.Action> override : request.getFaultHandlerOverrideMap().entrySet()) {
            b.addOption(new FaultHandlerOverrideTLV(override.getKey().byteValue(), FaultHandlerOverrideTLV.HandlerCode.map(override.getValue())));
        }

        return b.build();
    }

    private FileDataPdu prepareFileDataPdu(FileSegment gs) {
        FileDataPduBuilder b = new FileDataPduBuilder();
        setCommonPduValues(b);
        // FileData specific
        b.setOffset(gs.getOffset());
        b.setFileData(gs.getData());
        if(gs.getRecordContinuationState() != FileDataPdu.RCS_NOT_PRESENT) {
            b.setSegmentMetadataPresent(true);
            b.setRecordContinuationState(gs.getRecordContinuationState());
            b.setSegmentMetadata(new byte[0]);
        }
        if(gs.getMetadata() != null && gs.getMetadata().length > 0) {
            b.setSegmentMetadataPresent(true);
            b.setSegmentMetadata(gs.getMetadata());
        }

        return b.build();
    }

    private EndOfFilePdu prepareEndOfFilePdu(int finalChecksum) {
        EndOfFilePduBuilder b = new EndOfFilePduBuilder();
        setCommonPduValues(b);
        // EOF specific
        b.setFileChecksum(finalChecksum);
        b.setFileSize(this.effectiveFileSize);
        b.setConditionCode(this.conditionCode, this.faultEntityId);

        return b.build();
    }

    private AckPdu prepareAckPdu(FinishedPdu pdu) {
        AckPduBuilder b = new AckPduBuilder();
        setCommonPduValues(b);
        b.setTransactionStatus(AckPdu.TransactionStatus.TERMINATED); // TODO: check
        b.setConditionCode(this.conditionCode); // TODO: check
        b.setDirectiveCode(FileDirectivePdu.DC_FINISHED_PDU);
        b.setDirectiveSubtypeCode((byte) 0x01);

        return b.build();
    }

    private PromptPdu preparePromptPdu(boolean keepAlive) {
        PromptPduBuilder b = new PromptPduBuilder();
        setCommonPduValues(b);
        if(keepAlive) {
            b.setKeepAliveResponse();
        } else {
            b.setNakResponse();
        }
        return b.build();
    }

    private <T extends CfdpPdu,K extends CfdpPduBuilder<T, K>> void setCommonPduValues(CfdpPduBuilder<T,K> b) {
        b.setAcknowledged(isAcknowledged());
        b.setCrcPresent(getRemoteDestination().isCrcRequiredOnTransmission());
        b.setDestinationEntityId(getRemoteDestination().getRemoteEntityId());
        b.setSourceEntityId(getEntity().getMib().getLocalEntity().getLocalEntityId());
        b.setDirection(CfdpPdu.Direction.TOWARD_FILE_RECEIVER);
        b.setSegmentationControlPreserved(request.isSegmentationControl());
        // Set the length for the entity ID
        long maxEntityId = Long.max(getRemoteDestination().getRemoteEntityId(), getEntity().getMib().getLocalEntity().getLocalEntityId());
        b.setEntityIdLength(BytesUtil.getEncodingOctetsNb(maxEntityId));
        // Set the transaction ID
        b.setTransactionSequenceNumber(getTransactionId(), BytesUtil.getEncodingOctetsNb(getTransactionId()));
        b.setLargeFile(isLargeFile());
    }

    private void handleClosure() {
        if(!isAcknowledged()) {
            // 4.6.3.2.1 Transmission of an EOF (No error) PDU shall cause the sending CFDP entity to
            // issue a Notice of Completion (Completed) unless transaction closure was requested.
            if(!isClosureRequested()) {
                handleNoticeOfCompletion(true);
                // Nothing to do here, clean up transaction resources
                handleDispose();
            } else {
                // 4.6.3.2.2 In the latter case, a transaction-specific Check timer shall be started. The expiry
                // period of the timer shall be determined in an implementation-specific manner.
                this.transactionFinishCheckTimer = new TimerTask() {
                    @Override
                    public void run() {
                        handle(CfdpOutgoingTransaction.this::handleTransactionFinishedCheckTimerElapsed);
                    }
                };
                schedule(this.transactionFinishCheckTimer, getRemoteDestination().getCheckInterval(), false);
            }
        } else {
            // The transaction remains open and waits for the EOF ACK and related closure (if present)
        }
    }

    private void handleNoticeOfCompletion(boolean completed) {
        // 4.11.1.1.1 On Notice of Completion of the Copy File procedure, the sending CFDP entity
        // shall
        // a) release all unreleased portions of the file retransmission buffer
        // b) stop transmission of file segments and metadata.

        // 4.11.1.1.2 If sending in acknowledged mode,
        // a) any transmission of Prompt PDUs shall be terminated
        // b) the application of Positive Acknowledgment Procedures to PDUs previously issued
        //    by this entity shall be terminated.

        // 4.11.1.1.3 In any case,
        // a) if the sending entity is the transaction's source, it shall issue a TransactionFinished.indication primitive indicating the condition in which the transaction
        //    was completed
        // b) if all the following are true:
        //      1) the sending entity is the transaction's source,
        //      2) the file was sent in acknowledged mode,
        //      3) the procedure disposition cited in the Notice of Completion is 'Completed', and
        //      4) the Finished PDU whose arrival completed the transaction contained a Filestore
        //         Responses parameter,
        //    then that Filestore Responses parameter shall be passed in the TransactionFinished.indication primitive.

        // TODO: sending entity is the transaction source?
        if(!isAcknowledged()) {
            if(this.finishedPdu != null) {
                getEntity().notifyIndication(new TransactionFinishedIndication(getTransactionId(),
                        this.finishedPdu.getConditionCode(),
                        this.finishedPdu.getFileStatus(),
                        this.finishedPdu.isDataComplete(),
                        this.finishedPdu.getFilestoreResponses(),
                        null));
            } else {
                getEntity().notifyIndication(new TransactionFinishedIndication(getTransactionId(),
                        this.finishedPdu.getConditionCode(),
                        this.finishedPdu.getFileStatus(),
                        this.finishedPdu.isDataComplete(),
                        this.finishedPdu.getFilestoreResponses(),
                        null));
            }
        } else {
            // TODO check if this.finishedPdu can be null
            getEntity().notifyIndication(new TransactionFinishedIndication(getTransactionId(),
                    this.finishedPdu.getConditionCode(),
                    this.finishedPdu.getFileStatus(),
                    this.finishedPdu.isDataComplete(),
                    completed ? this.finishedPdu.getFilestoreResponses() : null,
                    null));
        }
        // We don't dispose here, as there might be things to be done after the Notice of Completion, prior to disposal
    }

    private void handleTransactionFinishedCheckTimerElapsed() {
        // If you reach this point, then according to the standard:
        // 4.6.3.2.4 If the timer expires prior to reception of a Finished PDU for the associated
        // transaction, a Check Limit Reached fault shall be declared.
        if(this.transactionFinishCheckTimer != null) {
            getEntity().notifyIndication(new FaultIndication(getTransactionId(), FileDirectivePdu.CC_CHECK_LIMIT_REACHED, this.effectiveFileSize));
            handleDispose();
        }
    }

    @Override
    protected void handlePreDispose() {
        // TODO cleanup resources and memory
    }

    @Override
    protected void handleTransactionInactivity() {
        // TODO refactor fault handling
        getEntity().notifyIndication(new FaultIndication(getTransactionId(), FileDirectivePdu.CC_INACTIVITY_DETECTED, this.effectiveFileSize));
    }

    private boolean isFileToBeSent() {
        return request.getSourceFileName() != null && request.getDestinationFileName() != null;
    }

    private boolean isLargeFile() {
        if(request.getSourceFileName() == null) {
            return false;
        } else {
            long fileSize = 0;
            try {
                fileSize = getEntity().getFilestore().fileSize(request.getSourceFileName());
            } catch (FilestoreException e) {
                if(LOG.isLoggable(Level.SEVERE)) {
                    LOG.log(Level.SEVERE, String.format("Cannot retrieve file size of file %s: %s", request.getSourceFileName(), e.getMessage()), e);
                }
                return false;
            }
            int bytesNb = BytesUtil.getEncodingOctetsNb(fileSize);
            return bytesNb > 4;
        }
    }

    public boolean isAcknowledged() {
        if(request.getAcknowledgedTransmissionMode() != null) {
            return request.getAcknowledgedTransmissionMode();
        } else {
            return getRemoteDestination().isDefaultTransmissionModeAcknowledged();
        }
    }

    public boolean isClosureRequested() {
        if(request.getClosureRequested() != null) {
            return request.getClosureRequested();
        } else {
            return getRemoteDestination().isTransactionClosureRequested();
        }
    }
}
