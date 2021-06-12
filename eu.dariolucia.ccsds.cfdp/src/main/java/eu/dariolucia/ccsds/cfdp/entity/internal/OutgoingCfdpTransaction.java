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
import eu.dariolucia.ccsds.cfdp.entity.FaultDeclaredException;
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

public class OutgoingCfdpTransaction extends CfdpTransaction {

    private static final Logger LOG = Logger.getLogger(OutgoingCfdpTransaction.class.getName());

    private final PutRequest request;

    // Variables to handle file data transfer
    private final List<CfdpPdu> sentPduList = new LinkedList<>();
    private final List<CfdpPdu> pendingUtTransmissionPduList = new LinkedList<>();

    private ICfdpFileSegmenter segmentProvider;
    private ICfdpChecksum checksum;
    private long totalFileSize;
    private long sentContiguousFileBytes;

    // Timer for the declaration of transaction completed when transaction closure is required
    private TimerTask transactionFinishCheckTimer;

    // Finished PDU for transactions with closure request
    private FinishedPdu finishedPdu;
    // True if the metadata PDU was sent, otherwise false
    private boolean metadataPduSent;
    // EOF PDU once sent
    private EndOfFilePdu eofPdu;
    // Flag to indicate that PDU transmission can go on and the notice of completion was not called
    private boolean txRunning;
    // Flag to indicated that activate() was called
    private boolean active;

    public OutgoingCfdpTransaction(long transactionId, CfdpEntity entity, PutRequest r) {
        super(transactionId, entity, r.getDestinationCfdpEntityId());
        this.request = r;
        overrideHandlers(entity.getMib().getLocalEntity().getFaultHandlerMap());
        overrideHandlers(r.getFaultHandlerOverrideMap());
        this.txRunning = true;
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
        if(pdu instanceof FinishedPdu && !isCancelled() && !isAckTimerRunning()) {
            handleFinishedPdu((FinishedPdu) pdu);
        } else if(pdu instanceof NakPdu && !isCancelled() && !isAckTimerRunning()) {
            handleNakPdu((NakPdu) pdu);
        } else if(pdu instanceof AckPdu) {
            handleAckPdu((AckPdu) pdu); // For EOF ACK
        } else if(pdu instanceof KeepAlivePdu && !isCancelled() && !isAckTimerRunning()) {
            handleKeepAlivePdu((KeepAlivePdu) pdu);
        }
    }

    @Override
    protected long getProgress() {
        return this.sentContiguousFileBytes;
    }

    @Override
    protected long getTotalFileSize() {
        return this.totalFileSize;
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
        if(this.sentContiguousFileBytes - pdu.getProgress() > limit) {
            if(LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: keep alive limit fault - remote progress: %d bytes, local progress %d bytes, limit %d bytes", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), this.sentContiguousFileBytes, pdu.getProgress(), limit));
            }
            try {
                fault(FileDirectivePdu.CC_KEEPALIVE_LIMIT_REACHED, getLocalEntityId());
            } catch (FaultDeclaredException e) {
                // Nothing to be done here at this stage, all actions performed by the fault handler
            }
        }
    }

    public void requestKeepAlive() {
        if(LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: sending Keep-Alive Prompt PDU", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
        }
        sendPromptPdu(true);
    }

    public void requestNak() {
        if(LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: sending NAK Prompt PDU", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
        }
        sendPromptPdu(false);
    }

    private void sendPromptPdu(boolean isKeepAlive) {
        if (isAcknowledged() && isRunning()) {
            PromptPdu p = preparePromptPdu(isKeepAlive);
            try {
                forwardPdu(p, true); // Do not store this PDU
            } catch (UtLayerException e) {
                if(LOG.isLoggable(Level.SEVERE)) {
                    LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: fail on Prompt PDU transmission: %s ", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), e.getMessage()), e);
                }
            }
        }
    }

    @Override
    protected void handleCancel(byte conditionCode, long faultEntityId) {
        if(LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: handling cancel with condition code %d and fault entity ID %d", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), conditionCode, faultEntityId));
        }
        setLastConditionCode(conditionCode, faultEntityId);
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
        int finalChecksum = this.checksum == null ? 0 : this.checksum.getCurrentChecksum();
        try {
            EndOfFilePdu pdu = prepareEndOfFilePdu(finalChecksum);
            forwardPdu(pdu);
            // Send the EOF indication
            if (getEntity().getMib().getLocalEntity().isEofSentIndicationRequired()) {
                getEntity().notifyIndication(new EofSentIndication(getTransactionId()));
            }
        } catch (UtLayerException e) {
            // 4.11.2.2.3 Any fault declared in the course of transferring the EOF (cancel) PDU must result
            // in abandonment of the transaction.
            if(LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: fail on EOF(cancel) PDU transmission: %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), e.getMessage()), e);
            }
            handleAbandon(conditionCode); // Assuming to use the last condition code of this transaction here
            return;
        }

        // 4.11.2.2.2 If sending in acknowledged mode,
        if(isAcknowledged()) {
            // a) Positive Acknowledgment procedures shall be applied to the EOF (cancel) PDU with
            // the Expected Response being an ACK (EOF) PDU with condition code equal to that
            // of the EOF (cancel) PDU
            startPositiveAckTimer(this.eofPdu);

            // b) any PDU received after issuance of the EOF (cancel) PDU and prior to receipt of the
            // Expected Response shall be ignored, except that all Positive Acknowledgment
            // procedures remain in effect -> handled by the checks in the handleIndication method: if the timer is active,
            // all received PDUs are ignored
        } else {
            // As soon as the EOF(Cancel) is out, this transaction is over
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
            // a) any transmission of Prompt PDUs shall be suspended -> handled in the sendPromptPdu method
            // b) the inactivity timer shall be suspended
            stopTransactionInactivityTimer();
            // c) the application of Positive Acknowledgment Procedures to PDUs previously issued
            //    by this entity shall be suspended. -> stop timer per EOF
            stopPositiveAckTimer();
        }
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
            getEntity().notifyIndication(new ResumedIndication(getTransactionId(), this.sentContiguousFileBytes));
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
        if(!this.active) {
            if(LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: resume() called, but transaction not active yet (due to unfreeze event?), call ignored", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
            }
            return;
        }
        // 4.6.7.2.1 On receipt of a Resume.request primitive, the sending CFDP entity shall
        // a) resume transmission of Metadata PDU, file segments, and EOF PDU
        if(this.metadataPduSent) {
            // Resume the sending of the file
            handle(this::sendFileSegment);
        } else {
            // Start the sending of the file from the beginning
            handle(() -> {
                try {
                    handleStartTransaction();
                } catch (FaultDeclaredException e) {
                    // Nothing to be done at this stage, all done by the fault handler
                }
            });
        }
        // b) issue a Resumed.indication.
        if(sendNotification) {
            getEntity().notifyIndication(new ResumedIndication(getTransactionId(), this.sentContiguousFileBytes));
        }
        // XXX: What about the transaction inactivity timer? I think it should be started
        startTransactionInactivityTimer();

        // 4.6.7.2.2 If operating in acknowledged mode,
        // a) any suspended transmission of Prompt PDUs shall be resumed -> handled in the sendPromptPdu method
        // b) the application of Positive Acknowledgment Procedures to PDUs previously issued
        //    by this entity shall be resumed. -> if EOF was sent, start EOF timer
        if(isAcknowledged() && this.eofPdu != null) {
            startPositiveAckTimer(this.eofPdu);
        }
    }

    private void handleAckPdu(AckPdu pdu) {
        // ACK of EOF PDU (only in case of acknowledged mode)
        if(pdu.getDirectiveCode() == FileDirectivePdu.DC_EOF_PDU) {
            if(LOG.isLoggable(Level.INFO)) {
                LOG.log(Level.INFO, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: ACK PDU(EOF) received", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
            }
            if(isAcknowledged() && this.eofPdu != null && this.eofPdu.getConditionCode() == pdu.getConditionCode()) {
                // Ack received
                this.eofPdu = null;
                stopPositiveAckTimer();
            }
        } else {
            if(LOG.isLoggable(Level.WARNING)) {
                LOG.log(Level.WARNING, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: ACK PDU(for directive code 0x%02X) received", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), pdu.getDirectiveCode()));
            }
        }
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
                if(filePdu.getOffset() + filePdu.getFileData().length < startOfScope ||
                        filePdu.getOffset() > endOfScope) {
                    continue;
                }
                // File is in offset, so check if there is a segment request
                checkAndRetransmitFileDataPdu(filePdu, pdu.getSegmentRequests());
            }
            // Ignore EOF PDUs
        }
    }

    private void checkAndRetransmitFileDataPdu(FileDataPdu filePdu, List<NakPdu.SegmentRequest> segmentRequests) {
        for(NakPdu.SegmentRequest segmentRequest : segmentRequests) {
            // If there is an overlap with the file data pdu, send the pdu again
            if(segmentRequest.overlapWith(filePdu.getOffset(), filePdu.getOffset() + filePdu.getFileData().length)) {
                try {
                    forwardPdu(filePdu, true);
                } catch (UtLayerException e) {
                    if(LOG.isLoggable(Level.SEVERE)) {
                        LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: fail on File Data PDU transmission: %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), e.getMessage()), e);
                    }
                }
                return;
            }
        }
    }

    private void checkAndRetransmitMetadataPdu(MetadataPdu sentPdu, List<NakPdu.SegmentRequest> segmentRequests) {
        for(NakPdu.SegmentRequest segmentRequest : segmentRequests) {
            // If there is a segment with start and end equal to 0, then retransmit the metadata PDU
            if(segmentRequest.getStartOffset() == 0 && segmentRequest.getEndOffset() == 0) {
                try {
                    forwardPdu(sentPdu, true);
                } catch (UtLayerException e) {
                    if(LOG.isLoggable(Level.SEVERE)) {
                        LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: fail on Metadata PDU transmission: %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), e.getMessage()), e);
                    }
                }
                return;
            }
        }
    }

    private void handleFinishedPdu(FinishedPdu pdu) {
        this.finishedPdu = pdu;
        if(!isAcknowledged()) {
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
            try {
                AckPdu toSend = prepareAckPdu(pdu);
                forwardPdu(toSend);
            } catch (UtLayerException e) {
                if(LOG.isLoggable(Level.SEVERE)) {
                    LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: fail on ACK(Finished) PDU transmission: %s ", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), e.getMessage()), e);
                }
            }
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
        if(LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: activation triggered", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
        }
        if(this.active) {
            if(LOG.isLoggable(Level.WARNING)) {
                LOG.log(Level.WARNING, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: activate() called twice, call ignored", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId()));
            }
            return;
        }
        this.active = true;
        // Notify the creation of the new transaction to the subscriber
        getEntity().notifyIndication(new TransactionIndication(getTransactionId(), request));
        // Start the transaction inactivity timer
        startTransactionInactivityTimer();
        // Handle the start of the transaction
        try {
            handleStartTransaction();
        } catch (FaultDeclaredException e) {
            // Nothing to be done at this stage, all done by the fault handler
        }
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

    @Override
    protected long getSourceEntityId() {
        return getLocalEntityId();
    }

    @Override
    protected long getDestinationEntityId() {
        return this.request.getDestinationCfdpEntityId();
    }

    private void handleStartTransaction() throws FaultDeclaredException {
        if(LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: CFDP Copy File procedure started, file to be sent: %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), isFileToBeSent()));
        }
        if(isRunning()) {
            // Initiation of the Copy File procedures shall cause the sending CFDP entity to
            // forward a Metadata PDU to the receiving CFDP entity.
            try {
                MetadataPdu metadataPdu = prepareMetadataPdu();
                forwardPdu(metadataPdu);
                this.metadataPduSent = true;
            } catch (UtLayerException e) {
                if(LOG.isLoggable(Level.SEVERE)) {
                    LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: fail on Metadata PDU transmission: %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), e.getMessage()), e);
                }
            }
            // Keep going anyway
            if (isFileToBeSent()) {
                // For transactions that deliver more than just metadata, Copy File initiation also
                // shall cause the sending CFDP entity to retrieve the file from the sending filestore and to
                // transmit it in File Data PDUs.
                segmentAndForwardFileData();
            } else {
                // For Metadata only transactions, closure might or might not be requested
                handleTransactionClosure();
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
    private void segmentAndForwardFileData() throws FaultDeclaredException {
        this.sentContiguousFileBytes = 0;
        setLastConditionCode(FileDirectivePdu.CC_NOERROR, null);
        // Initialise the chunk provider
        if(this.request.isSegmentationControl()) {
            this.segmentProvider = getEntity().getSegmentProvider(request.getSourceFileName(), getRemoteDestination().getRemoteEntityId());
        } else {
            this.segmentProvider = new FixedSizeSegmenter(getEntity().getFilestore(), request.getSourceFileName(), getRemoteDestination().getMaximumFileSegmentLength());
        }
        // 4.6.1.1.8 If the segmentation control service parameter has requested that record
        // boundaries be respected and there is no observable record structure to the file, an Invalid File
        // Structure fault shall be declared.
        if(this.segmentProvider == null) {
            // The last condition code and generating entity ID is set by the fault method
            fault(FileDirectivePdu.CC_INVALID_FILE_STRUCTURE, getLocalEntityId());
            // If the fault is ignore, let's try with something that makes sense... the standard does not prevent this to happen
            this.segmentProvider = new FixedSizeSegmenter(getEntity().getFilestore(), request.getSourceFileName(), getRemoteDestination().getMaximumFileSegmentLength());
        }
        // Initialise the checksum computer
        try {
            this.checksum = CfdpChecksumRegistry.getChecksum(getRemoteDestination().getDefaultChecksumType()).build();
        } catch (CfdpUnsupportedChecksumType e) {
            setLastConditionCode(FileDirectivePdu.CC_UNSUPPORTED_CHECKSUM_TYPE, getLocalEntityId());
            // Not available, then use the modular checksum
            this.checksum = CfdpChecksumRegistry.getModularChecksum().build();
        }
        // Send the first segment
        sendFileSegment();
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
        // Verify if you can send the segment (transmission contact time, suspended: isRunning() takes care of both)
        if(!isRunning()) {
            return;
        }
        // Extract and send the file segment
        FileSegment gs;
        try {
            gs = this.segmentProvider.nextSegment();
        } catch (FilestoreException e) {
            if(LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: failure on file segmented when requesting next segment: %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), e.getMessage()), e);
            }
            try {
                // The standard does not foresee the inability to read from the file store, so I am reusing this condition code
                fault(FileDirectivePdu.CC_INVALID_FILE_STRUCTURE, getLocalEntityId());
            } catch (FaultDeclaredException faultDeclaredException) {
                // A cancel/abandon/suspend action was triggered, so end here
                return;
            }
            // A NO ACTION was reported, but the segment was actually not retrieved, therefore you assume it is a EOF.
            // The standard says that there is undefined behaviour for NO ACTION faults, so ...
            gs = FileSegment.eof();
        }
        // Check if there are no more segments to send
        if(gs.isEof()) {
            // Construct the EOF pdu
            this.segmentProvider.close();
            int finalChecksum = this.checksum.getCurrentChecksum();
            EndOfFilePdu pdu = prepareEndOfFilePdu(finalChecksum);
            try {
                forwardPdu(pdu);
                // Send the EOF indication
                if(getEntity().getMib().getLocalEntity().isEofSentIndicationRequired()) {
                    getEntity().notifyIndication(new EofSentIndication(getTransactionId()));
                }
            } catch (UtLayerException e) {
                if(LOG.isLoggable(Level.SEVERE)) {
                    LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: fail on EOF PDU transmission: %s ", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), e.getMessage()), e);
                }
            }
            // Start the ACK procedure in case of acknowledged transactions
            if(isAcknowledged()) {
                startPositiveAckTimer(pdu);
            }
            // Handle the closure of the transaction
            handleTransactionClosure();
        } else {
            // Construct the file data PDU
            this.sentContiguousFileBytes += gs.getData().length;
            addToChecksum(gs);
            FileDataPdu pdu = prepareFileDataPdu(gs);
            try {
                forwardPdu(pdu);
            } catch (UtLayerException e) {
                if(LOG.isLoggable(Level.SEVERE)) {
                    LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: fail on File Data PDU transmission: %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), e.getMessage()), e);
                }
            }
            // Schedule the next task to send the next segment
            handle(this::sendFileSegment);
        }
    }

    private void addToChecksum(FileSegment gs) {
        this.checksum.checksum(gs.getData(), gs.getOffset());
    }

    @Override
    protected void forwardPdu(CfdpPdu pdu) throws UtLayerException {
        forwardPdu(pdu, false);
    }

    private void forwardPdu(CfdpPdu pdu, boolean retransmission) throws UtLayerException {
        if(!this.txRunning) {
            // Prevent sending PDUs
            if(LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: forwardPdu(), %s to UT layer %s discarded, TX not enabled", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), pdu, getTransmissionLayer().getName()));
            }
            return;
        }
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: forwardPdu(), %s to UT layer %s - Retransmission: %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), pdu, getTransmissionLayer().getName(), retransmission));
        }
        if(isAcknowledged() && !retransmission) {
            // Remember the PDU
            this.sentPduList.add(pdu);
        }
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
                getTransmissionLayer().request(toSend, getRemoteDestination().getRemoteEntityId());
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
            try {
                if (getEntity().getFilestore().isUnboundedFile(request.getSourceFileName())) {
                    this.totalFileSize = 0;
                } else {
                    this.totalFileSize = getEntity().getFilestore().fileSize(request.getSourceFileName());
                }
            } catch (FilestoreException e) {
                if(LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: cannot determine the size/bounds of the file %s: %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), request.getSourceFileName(), e.getMessage()), e);
                }
                this.totalFileSize = 0;
            }
            b.setFileSize(this.totalFileSize);
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
        b.setFileSize(this.sentContiguousFileBytes);
        b.setConditionCode(getLastConditionCode(), getLastFaultEntity());

        this.eofPdu = b.build();
        return this.eofPdu;
    }

    private AckPdu prepareAckPdu(FinishedPdu pdu) {
        AckPduBuilder b = new AckPduBuilder();
        setCommonPduValues(b);
        b.setTransactionStatus(deriveCurrentAckTransactionStatus());
        b.setConditionCode(pdu.getConditionCode());
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
        b.setDestinationEntityId(this.request.getDestinationCfdpEntityId());
        b.setSourceEntityId(getLocalEntityId());
        b.setDirection(CfdpPdu.Direction.TOWARD_FILE_RECEIVER);
        b.setSegmentationControlPreserved(this.request.isSegmentationControl());
        // Set the length for the entity ID
        long maxEntityId = Long.max(getRemoteDestination().getRemoteEntityId(), getLocalEntityId());
        b.setEntityIdLength(BytesUtil.getEncodingOctetsNb(maxEntityId));
        // Set the transaction ID
        b.setTransactionSequenceNumber(getTransactionId(), BytesUtil.getEncodingOctetsNb(getTransactionId()));
        b.setLargeFile(isLargeFile());
    }

    private void handleTransactionClosure() {
        if(!isAcknowledged()) {
            // 4.6.3.2.1 Transmission of an EOF (No error) PDU shall cause the sending CFDP entity to
            // issue a Notice of Completion (Completed) unless transaction closure was requested.
            if(!isClosureRequested()) {
                handleNoticeOfCompletion(true);
                // Nothing to do here, clean up transaction resources, we are done
                handleDispose();
            } else {
                // 4.6.3.2.2 In the latter case, a transaction-specific Check timer shall be started. The expiry
                // period of the timer shall be determined in an implementation-specific manner.
                this.transactionFinishCheckTimer = new TimerTask() {
                    @Override
                    public void run() {
                        handle(OutgoingCfdpTransaction.this::handleTransactionFinishedCheckTimerElapsed);
                    }
                };
                schedule(this.transactionFinishCheckTimer, getRemoteDestination().getCheckInterval(), false);
            }
        }
        // If acknowledged, the transaction remains open and waits for the EOF ACK and related closure (if present)
    }

    private void handleNoticeOfCompletion(boolean completed) {
        if(LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: notice of completion (%s) called", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), completed));
        }
        // 4.11.1.1.1 On Notice of Completion of the Copy File procedure, the sending CFDP entity
        // shall
        // a) release all unreleased portions of the file retransmission buffer
        this.sentPduList.clear();
        // b) stop transmission of file segments and metadata.
        this.pendingUtTransmissionPduList.clear();
        this.txRunning = false;

        // 4.11.1.1.2 If sending in acknowledged mode,
        // a) any transmission of Prompt PDUs shall be terminated
        // b) the application of Positive Acknowledgment Procedures to PDUs previously issued
        //    by this entity shall be terminated.
        stopPositiveAckTimer();

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
        // TODO: sending entity is the transaction source? (I assume for store-and-forward and for proxy operations,
        //  leave it to be done for now
        if(!isAcknowledged()) {
            if(this.finishedPdu != null) {
                getEntity().notifyIndication(new TransactionFinishedIndication(getTransactionId(),
                        this.finishedPdu.getConditionCode(),
                        this.finishedPdu.getFileStatus(),
                        this.finishedPdu.isDataComplete(),
                        this.finishedPdu.getFilestoreResponses(),
                        createStateObject()));
            } else {
                getEntity().notifyIndication(new TransactionFinishedIndication(getTransactionId(),
                        getLastConditionCode(),
                        FinishedPdu.FileStatus.STATUS_UNREPORTED,
                        completed,
                        null,
                        createStateObject()));
            }
        } else {
            getEntity().notifyIndication(new TransactionFinishedIndication(getTransactionId(),
                    completed ? this.finishedPdu.getConditionCode() : getLastConditionCode(),
                    completed ? this.finishedPdu.getFileStatus() : FinishedPdu.FileStatus.STATUS_UNREPORTED,
                    completed && this.finishedPdu.isDataComplete(),
                    completed ? this.finishedPdu.getFilestoreResponses() : null,
                    createStateObject()));
        }
        // We don't dispose here, as there might be things to be done after the Notice of Completion, prior to disposal
    }

    private void handleTransactionFinishedCheckTimerElapsed() {
        // If you reach this point, then according to the standard:
        // 4.6.3.2.4 If the timer expires prior to reception of a Finished PDU for the associated
        // transaction, a Check Limit Reached fault shall be declared.
        if(this.transactionFinishCheckTimer != null) {
            try {
                fault(FileDirectivePdu.CC_CHECK_LIMIT_REACHED, getLocalEntityId());
            } catch (FaultDeclaredException e) {
                // Nothing to be done at this stage, everything already handled by the fault handler
            }
        }
    }

    @Override
    protected void handlePreDispose() {
        // Cleanup resources and memory
        this.sentPduList.clear();
        this.pendingUtTransmissionPduList.clear();
        this.txRunning = false;
        if(this.segmentProvider != null) {
            this.segmentProvider.close();
            this.segmentProvider = null;
        }
        this.checksum = null;
        if(transactionFinishCheckTimer != null) {
            transactionFinishCheckTimer.cancel();
            transactionFinishCheckTimer = null;
        }
        // Done
    }

    @Override
    protected void handleTransactionInactivity() {
        try {
            fault(FileDirectivePdu.CC_INACTIVITY_DETECTED, getLocalEntityId());
        } catch (FaultDeclaredException e) {
            // Nothing to be done at this stage, everything already handled by the fault handler
        }
    }

    private boolean isFileToBeSent() {
        return request.getSourceFileName() != null && request.getDestinationFileName() != null;
    }

    private boolean isLargeFile() {
        if(request.getSourceFileName() == null) {
            return false;
        } else {
            long fileSize;
            try {
                fileSize = getEntity().getFilestore().fileSize(request.getSourceFileName());
            } catch (FilestoreException e) {
                if(LOG.isLoggable(Level.SEVERE)) {
                    LOG.log(Level.SEVERE, String.format("CFDP Entity [%d]: [%d] with remote entity [%d]: cannot retrieve file size of file %s: %s", getLocalEntityId(), getTransactionId(), getRemoteDestination().getRemoteEntityId(), request.getSourceFileName(), e.getMessage()), e);
                }
                return false;
            }
            int bytesNb = BytesUtil.getEncodingOctetsNb(fileSize);
            return bytesNb > 4;
        }
    }

    @Override
    protected boolean isAcknowledged() {
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
