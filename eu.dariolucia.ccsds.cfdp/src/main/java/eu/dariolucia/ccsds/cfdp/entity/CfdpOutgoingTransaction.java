package eu.dariolucia.ccsds.cfdp.entity;

import eu.dariolucia.ccsds.cfdp.common.BytesUtil;
import eu.dariolucia.ccsds.cfdp.entity.indication.EofSentIndication;
import eu.dariolucia.ccsds.cfdp.entity.indication.TransactionIndication;
import eu.dariolucia.ccsds.cfdp.entity.request.PutRequest;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.FileSegment;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.ICfdpFileSegmenter;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.impl.FixedSizeSegmenter;
import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import eu.dariolucia.ccsds.cfdp.mib.FaultHandlerStrategy;
import eu.dariolucia.ccsds.cfdp.mib.RemoteEntityConfigurationInformation;
import eu.dariolucia.ccsds.cfdp.protocol.builder.CfdpPduBuilder;
import eu.dariolucia.ccsds.cfdp.protocol.builder.EndOfFilePduBuilder;
import eu.dariolucia.ccsds.cfdp.protocol.builder.FileDataPduBuilder;
import eu.dariolucia.ccsds.cfdp.protocol.builder.MetadataPduBuilder;
import eu.dariolucia.ccsds.cfdp.protocol.checksum.CfdpChecksumRegistry;
import eu.dariolucia.ccsds.cfdp.protocol.checksum.CfdpUnsupportedChecksumType;
import eu.dariolucia.ccsds.cfdp.protocol.checksum.ICfdpChecksum;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.*;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.*;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayer;
import eu.dariolucia.ccsds.cfdp.ut.UtLayerException;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CfdpOutgoingTransaction extends CfdpTransaction {

    private static final Logger LOG = Logger.getLogger(CfdpOutgoingTransaction.class.getName());

    private final PutRequest request;
    private final CfdpEntity entity;
    private final int entityIdLength;
    private final RemoteEntityConfigurationInformation remoteDestination;
    private final IUtLayer transmissionLayer;
    private final Map<Integer, FaultHandlerStrategy.Action> faultHandlers = new HashMap<>();

    private final ExecutorService confiner;

    private final Timer timer;

    private final List<CfdpPdu> pduList = new LinkedList<>();
    private final List<CfdpPdu> pendingUtTransmissionPdu = new LinkedList<>();

    // Variables to handle file data transfer
    private ICfdpFileSegmenter segmentProvider;
    private ICfdpChecksum checksum;
    private byte conditionCode;
    private EntityIdTLV faultEntityId;
    private long effectiveFileSize;

    public CfdpOutgoingTransaction(long transactionId, PutRequest r, CfdpEntity cfdpEntity) {
        super(transactionId);
        this.request = r;
        this.entity = cfdpEntity;
        this.remoteDestination = cfdpEntity.getMib().getRemoteEntityById(r.getDestinationCfdpEntityId());
        this.transmissionLayer = cfdpEntity.getUtLayerByDestinationEntity(r.getDestinationCfdpEntityId());
        this.faultHandlers.putAll(cfdpEntity.getMib().getLocalEntity().getFaultHandlerMap());
        this.faultHandlers.putAll(r.getFaultHandlerOverrideMap());
        this.confiner = Executors.newFixedThreadPool(1, runnable -> {
           Thread t = new Thread(runnable, String.format("CFDP Outgoing Transaction [%d] [%d] Handler", transactionId, remoteDestination.getRemoteEntityId()));
           t.setDaemon(true);
           return t;
        });
        this.timer = new Timer(String.format("CFDP Outgoing Transaction [%d] [%d] Timer", transactionId, remoteDestination.getRemoteEntityId()), true);
        // Compute the entity ID length
        long maxEntityId = Long.max(remoteDestination.getRemoteEntityId(), entity.getMib().getLocalEntity().getLocalEntityId());
        this.entityIdLength = BytesUtil.getEncodingOctetsNb(maxEntityId);
    }

    @Override
    public void activate() {
        this.confiner.submit(this::handleTransmissionActivation);
    }

    /**
     * This method implementation reflects the steps specified in clause 4.6.1.1 - Copy File Procedures at Sending Entity
     */
    private void handleTransmissionActivation() {
        // Notify the creation of the new transaction to the subscriber
        entity.notifyIndication(new TransactionIndication(getTransactionId(), request));
        // Initiation of the Copy File procedures shall cause the sending CFDP entity to
        // forward a Metadata PDU to the receiving CFDP entity.
        MetadataPdu metadataPdudu = prepareMetadataPdu();
        forwardPdu(metadataPdudu);

        if(isFileToBeSent()) {
            // For transactions that deliver more than just metadata, Copy File initiation also
            // shall cause the sending CFDP entity to retrieve the file from the sending filestore and to
            // transmit it in File Data PDUs.
            segmentAndForwardFileData();
        } else {
            // For Metadata only transactions, closure might or might not be requested
            this.confiner.submit(this::handleClosure);
        }
        // End of the transmission activation
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
            this.segmentProvider = this.entity.getSegmentProvider(request.getSourceFileName(), remoteDestination.getRemoteEntityId());
        }
        // If there is no segmentation available, or if no segmentation control is needed, use the fixed size segment provider
        if(this.segmentProvider == null) {
            this.segmentProvider = new FixedSizeSegmenter(entity.getFilestore(), request.getSourceFileName(), remoteDestination.getMaximumFileSegmentLength());
        }
        // Initialise the checksum computer
        try {
            this.checksum = CfdpChecksumRegistry.getChecksum(remoteDestination.getDefaultChecksumType()).build();
        } catch (CfdpUnsupportedChecksumType e) {
            this.conditionCode = FileDirectivePdu.CC_UNSUPPORTED_CHECKSUM_TYPE;
            this.faultEntityId = new EntityIdTLV(entity.getMib().getLocalEntity().getLocalEntityId(), this.entityIdLength);
            // Not available, then use the modular checksum
            this.checksum = CfdpChecksumRegistry.getModularChecksum().build();
        }
        // Schedule the first task to send the first segment
        this.confiner.submit(this::sendFileSegment);
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
        // TODO verify if you can send the segment (transmission contact time, suspended)

        // Extract and send the file segment
        FileSegment gs = this.segmentProvider.nextSegment();
        // Check if there are no more segments to send
        if(gs.isEof()) {
            // Construct the EOF pdu
            this.segmentProvider.close();
            int finalChecksum = this.checksum.getCurrentChecksum();
            EndOfFilePdu pdu = prepareEndOfFilePdu(finalChecksum);
            forwardPdu(pdu);
            // Send the EOF indication
            if(this.entity.getMib().getLocalEntity().isEofSentIndicationRequired()) {
                this.entity.notifyIndication(new EofSentIndication(getTransactionId()));
            }
            // Handle the closure of the transaction
            this.confiner.submit(this::handleClosure);
        } else {
            // Construct the file data PDU
            this.effectiveFileSize += gs.getData().length;
            addToChecksum(gs);
            FileDataPdu pdu = prepareFileDataPdu(gs);
            forwardPdu(pdu);
            // Schedule the next task to send the next segment
            this.confiner.submit(this::sendFileSegment);
        }
    }

    private void addToChecksum(FileSegment gs) {
        this.checksum.checksum(gs.getData(), gs.getOffset());
    }

    private boolean forwardPdu(CfdpPdu pdu) {
        if(isAcknowledged()) {
            // Remember the PDU
            this.pduList.add(pdu);
        }
        // Add to the pending list
        this.pendingUtTransmissionPdu.add(pdu);
        // Send all PDUs you have to send, stop if you fail
        if(LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, String.format("Outgoing transaction %d to entity %d: sending %d pending PDUs to UT layer %s", getTransactionId(), this.remoteDestination.getRemoteEntityId(), this.pendingUtTransmissionPdu.size(), this.transmissionLayer.getName()));
        }
        while(!pendingUtTransmissionPdu.isEmpty()) {
            CfdpPdu toSend = pendingUtTransmissionPdu.get(0);
            try {
                if(LOG.isLoggable(Level.FINEST)) {
                    LOG.log(Level.FINEST, String.format("Outgoing transaction %d to entity %d: sending PDU %s to UT layer %s", getTransactionId(), this.remoteDestination.getRemoteEntityId(), toSend, this.transmissionLayer.getName()));
                }
                this.transmissionLayer.request(toSend);
                this.pendingUtTransmissionPdu.remove(0);
            } catch(UtLayerException e) {
                if(LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, String.format("Outgoing transaction %d to entity %d: PDU rejected by UT layer %s: %s", getTransactionId(), this.remoteDestination.getRemoteEntityId(), this.transmissionLayer.getName(), e.getMessage()), e);
                }
                return false;
            }
        }
        if(LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, String.format("Outgoing transaction %d to entity %d: pending PDUs sent to UT layer %s", getTransactionId(), this.remoteDestination.getRemoteEntityId(), this.transmissionLayer.getName()));
        }
        return true;
    }

    private MetadataPdu prepareMetadataPdu() {
        MetadataPduBuilder b = new MetadataPduBuilder();
        setCommonPduValues(b);
        // Metadata specific
        b.setSegmentationControlPreserved(false); // Always 0 for file directive PDUs
        b.setClosureRequested(isClosureRequested());
        b.setChecksumType((byte) remoteDestination.getDefaultChecksumType());
        if(isFileToBeSent()) {
            // File data
            b.setSourceFileName(request.getSourceFileName());
            b.setDestinationFileName(request.getDestinationFileName());
            long fileSize;
            try {
                if (entity.getFilestore().isUnboundedFile(request.getSourceFileName())) {
                    fileSize = 0;
                } else {
                    fileSize = entity.getFilestore().fileSize(request.getSourceFileName());
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
        b.setConditionCode(conditionCode, faultEntityId);

        return b.build();
    }

    private <T extends CfdpPdu,K extends CfdpPduBuilder<T, K>> void setCommonPduValues(CfdpPduBuilder<T,K> b) {
        b.setAcknowledged(isAcknowledged());
        b.setCrcPresent(remoteDestination.isCrcRequiredOnTransmission());
        b.setDestinationEntityId(remoteDestination.getRemoteEntityId());
        b.setSourceEntityId(entity.getMib().getLocalEntity().getLocalEntityId());
        b.setDirection(CfdpPdu.Direction.TOWARD_FILE_RECEIVER);
        b.setSegmentationControlPreserved(request.isSegmentationControl());
        // Set the length for the entity ID
        long maxEntityId = Long.max(remoteDestination.getRemoteEntityId(), entity.getMib().getLocalEntity().getLocalEntityId());
        b.setEntityIdLength(BytesUtil.getEncodingOctetsNb(maxEntityId));
        // Set the transaction ID
        b.setTransactionSequenceNumber(getTransactionId(), BytesUtil.getEncodingOctetsNb(getTransactionId()));
        b.setLargeFile(isLargeFile());
    }

    private void handleClosure() {
        if(!isAcknowledged() && !isClosureRequested()) {
            // The transaction does not expect anything back and can be disposed
            this.confiner.submit(this::dispose);
        } else {
            // The transaction remains open and waits for the closure
        }
    }

    public void dispose() {
        // TODO generate TransactionFinishedIndication
        // TODO cleanup
        this.confiner.shutdown();
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
                fileSize = entity.getFilestore().fileSize(request.getSourceFileName());
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
            return remoteDestination.isDefaultTransmissionModeAcknowledged();
        }
    }

    public boolean isClosureRequested() {
        if(request.getClosureRequested() != null) {
            return request.getClosureRequested();
        } else {
            return remoteDestination.isTransactionClosureRequested();
        }
    }
}
