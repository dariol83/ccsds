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

import eu.dariolucia.ccsds.cfdp.entity.CfdpTransactionState;
import eu.dariolucia.ccsds.cfdp.entity.ICfdpEntity;
import eu.dariolucia.ccsds.cfdp.entity.ICfdpEntitySubscriber;
import eu.dariolucia.ccsds.cfdp.entity.indication.EntityDisposedIndication;
import eu.dariolucia.ccsds.cfdp.entity.indication.ICfdpIndication;
import eu.dariolucia.ccsds.cfdp.entity.request.*;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.ICfdpFileSegmenter;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.ICfdpSegmentationStrategy;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.impl.FixedSizeSegmentationStrategy;
import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import eu.dariolucia.ccsds.cfdp.filestore.IVirtualFilestore;
import eu.dariolucia.ccsds.cfdp.mib.Mib;
import eu.dariolucia.ccsds.cfdp.mib.RemoteEntityConfigurationInformation;
import eu.dariolucia.ccsds.cfdp.protocol.builder.AckPduBuilder;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.*;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayer;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayerSubscriber;
import eu.dariolucia.ccsds.cfdp.ut.UtLayerException;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CfdpEntity implements IUtLayerSubscriber, ICfdpEntity {

    private static final Logger LOG = Logger.getLogger(CfdpEntity.class.getName());

    private final Mib mib;
    private final IVirtualFilestore filestore;
    private final Map<String, IUtLayer> utLayers = new TreeMap<>();

    // Subscribers
    private final List<ICfdpEntitySubscriber> subscribers = new CopyOnWriteArrayList<>();

    // Notification thread
    private final ExecutorService subscriberNotifier;
    // Confinement thread
    private final ExecutorService entityConfiner;
    // Map of ongoing transactions
    private final Map<Long, CfdpTransaction> id2transaction = new HashMap<>();
    // Request processor map
    private final Map<Class<? extends ICfdpRequest>, Consumer<ICfdpRequest>> requestProcessors = new HashMap<>();
    // Transaction ID sequencer
    private final AtomicLong transactionIdSequencer = new AtomicLong(0); // XXX: this will need to be revised
    // File segmentation strategies
    private final List<ICfdpSegmentationStrategy> supportedSegmentationStrategies = new CopyOnWriteArrayList<>();

    // Disposed flag
    private boolean disposed;

    public CfdpEntity(Mib mib, IVirtualFilestore filestore,  Collection<IUtLayer> layers) {
        this.mib = mib;
        this.filestore = filestore;
        for(IUtLayer l : layers) {
            this.utLayers.put(l.getName(), l);
        }
        // 1 separate thread to notify all listeners
        this.subscriberNotifier = Executors.newFixedThreadPool(1, r -> {
            Thread t = new Thread(r, "CFDP Entity " + mib.getLocalEntity().getLocalEntityId() + " - Subscribers Notifier");
            t.setDaemon(true);
            return t;
        });
        // 1 separate thread to manage the entity
        this.entityConfiner = Executors.newFixedThreadPool(1, r -> {
            Thread t = new Thread(r, "CFDP Entity " + mib.getLocalEntity().getLocalEntityId() + " - Manager");
            t.setDaemon(true);
            return t;
        });
        // Register request processors
        this.requestProcessors.put(PutRequest.class, this::processPutRequest);
        this.requestProcessors.put(KeepAliveRequest.class, this::processKeepAliveRequest);
        this.requestProcessors.put(PromptNakRequest.class, this::processPromptNakRequest);
        this.requestProcessors.put(CancelRequest.class, this::processCancelRequest);
        this.requestProcessors.put(SuspendRequest.class, this::processSuspendRequest);
        this.requestProcessors.put(ResumeRequest.class, this::processResumeRequest);
        this.requestProcessors.put(ReportRequest.class, this::processReportRequest);
        // Add default segmentation strategy
        this.supportedSegmentationStrategies.add(new FixedSizeSegmentationStrategy());
        // Ready to go
        startProcessing();
    }

    @Override
    public void addSegmentationStrategy(ICfdpSegmentationStrategy strategy) {
        this.supportedSegmentationStrategies.add(0, strategy);
    }

    private void startProcessing() {
        // Subscribe to the UT layers, so that in case something arrives for you (proxy, or any PDU)
        // then you can handle it
        for(IUtLayer l : this.utLayers.values()) {
            l.register(this);
        }
    }

    @Override
    public Mib getMib() {
        return this.mib;
    }

    @Override
    public IUtLayer getUtLayerByName(String name) {
        return this.utLayers.get(name);
    }

    @Override
    public IUtLayer getUtLayerByDestinationEntity(long destinationEntityId) {
        RemoteEntityConfigurationInformation re = this.mib.getRemoteEntityById(destinationEntityId);
        return re != null ? this.utLayers.get(re.getUtLayer()) : null;
    }

    @Override
    public IVirtualFilestore getFilestore() {
        return this.filestore;
    }

    @Override
    public void register(ICfdpEntitySubscriber s) {
        this.subscribers.add(s);
    }

    @Override
    public void deregister(ICfdpEntitySubscriber s) {
        this.subscribers.remove(s);
    }

    @Override
    public void request(ICfdpRequest request) {
        this.entityConfiner.submit(() -> processRequest(request));
    }

    private void processRequest(ICfdpRequest request) {
        if(isDisposed()) {
            if(LOG.isLoggable(Level.SEVERE)) {
                LOG.severe(String.format("Entity %d disposed, request rejected", mib.getLocalEntity().getLocalEntityId()));
            }
            return;
        }
        Consumer<ICfdpRequest> processor = this.requestProcessors.get(request.getClass());
        if(processor != null) {
            processor.accept(request);
        } else {
            if(LOG.isLoggable(Level.SEVERE)) {
                LOG.severe(String.format("Entity %d cannot handle request %s: processor not found", mib.getLocalEntity().getLocalEntityId(), request));
            }
        }
    }

    /**
     * Receipt of a Put.request primitive shall cause the source CFDP entity to initiate:
     * <ol type="a">
     * <li>a Transaction Start Notification procedure; and</li>
     * <li>a Copy File procedure, for which
     * <ol>
     * <li>any fault handler overrides shall be derived from the Put.request,</li>
     * <li>any Messages to User or filestore requests shall be derived from the
     * Put.request,</li>
     * <li>omission of source and destination filenames shall indicate that only metadata
     * will be delivered,</li>
     * <li>the transmission mode (acknowledged or unacknowledged) is defined by the
     * existing content of the MIB unless overridden by the transmission mode
     * parameter of the Put.request,</li>
     * <li>the transaction closure requested indication (true or false) is defined by the
     * existing content of the MIB unless overridden by the closure requested parameter
     * of the Put.request. </li>
     * </ol>
     * </li>
     * </ol>
     *
     * @param request the {@link PutRequest} object
     */
    private void processPutRequest(ICfdpRequest request) {
        PutRequest r = (PutRequest) request;
        // Get a new transaction ID
        long transactionId = generateTransactionId();
        // Create a new transaction object to send the specified file
        CfdpTransaction cfdpTransaction = new OutgoingCfdpTransaction(transactionId, this, r);
        // Register the transaction in the map
        this.id2transaction.put(transactionId, cfdpTransaction);
        // Start the transaction
        cfdpTransaction.activate();
    }

    private void processKeepAliveRequest(ICfdpRequest request) {
        KeepAliveRequest r = (KeepAliveRequest) request;
        // Get the transaction ID
        long transactionId = r.getTransactionId();
        // Get the transaction
        CfdpTransaction t = this.id2transaction.get(transactionId);
        if(t instanceof OutgoingCfdpTransaction) {
            ((OutgoingCfdpTransaction) t).requestKeepAlive();
        } else {
            if(LOG.isLoggable(Level.WARNING)) {
                LOG.log(Level.WARNING, String.format("Entity %d cannot request keep alive for transaction %d: transaction does not exist or has incorrect type", this.mib.getLocalEntity().getLocalEntityId(), transactionId));
            }
        }
    }

    private void processPromptNakRequest(ICfdpRequest request) {
        PromptNakRequest r = (PromptNakRequest) request;
        // Get the transaction ID
        long transactionId = r.getTransactionId();
        // Get the transaction
        CfdpTransaction t = this.id2transaction.get(transactionId);
        if(t instanceof OutgoingCfdpTransaction) {
            ((OutgoingCfdpTransaction) t).requestNak();
        } else {
            if(LOG.isLoggable(Level.WARNING)) {
                LOG.log(Level.WARNING, String.format("Entity %d cannot request prompt NAK for transaction %d: transaction does not exist or has incorrect type", this.mib.getLocalEntity().getLocalEntityId(), transactionId));
            }
        }
    }

    private void processCancelRequest(ICfdpRequest request) {
        CancelRequest r = (CancelRequest) request;
        // Get the transaction ID
        long transactionId = r.getTransactionId();
        // Get the transaction
        CfdpTransaction t = this.id2transaction.get(transactionId);
        t.cancel(FileDirectivePdu.CC_CANCEL_REQUEST_RECEIVED);
    }

    private void processSuspendRequest(ICfdpRequest request) {
        SuspendRequest r = (SuspendRequest) request;
        // Get the transaction ID
        long transactionId = r.getTransactionId();
        // Get the transaction
        CfdpTransaction t = this.id2transaction.get(transactionId);
        t.suspend();
    }

    private void processResumeRequest(ICfdpRequest request) {
        ResumeRequest r = (ResumeRequest) request;
        // Get the transaction ID
        long transactionId = r.getTransactionId();
        // Get the transaction
        CfdpTransaction t = this.id2transaction.get(transactionId);
        t.resume();
    }

    private void processReportRequest(ICfdpRequest request) {
        ReportRequest r = (ReportRequest) request;
        // Get the transaction ID
        long transactionId = r.getTransactionId();
        // Get the transaction
        CfdpTransaction t = this.id2transaction.get(transactionId);
        t.report();
    }

    /**
     * This method is supposed to be invoked by {@link CfdpTransaction} objects.
     *
     * @param indication the indication to notify
     */
    void notifyIndication(ICfdpIndication indication) {
        this.subscriberNotifier.submit(() -> {
            for(ICfdpEntitySubscriber s : this.subscribers) {
                try {
                    s.indication(this, indication);
                } catch (Exception e) {
                    if(LOG.isLoggable(Level.SEVERE)) {
                        LOG.log(Level.SEVERE, String.format("Entity %d cannot notify subscriber %s: %s", this.mib.getLocalEntity().getLocalEntityId(), s, e.getMessage()), e);
                    }
                }
            }
        });
    }

    /**
     * This method looks for a viable strategy to segment the provided file. If no such alternative is known, the
     * entity will fall back to a standard fixed-side segmenter.
     *
     * @param sourceFileName the source file name for which the segmentation strategy is requested
     * @param destinationId the destination of the transaction
     * @return a new segmenter of first segmentation strategy that can be applied to the file, or null if no suitable
     * strategy can be found
     */
    ICfdpFileSegmenter getSegmentProvider(String sourceFileName, long destinationId) {
        for(ICfdpSegmentationStrategy s : this.supportedSegmentationStrategies) {
            try {
                if (s.support(this.mib, this.filestore, sourceFileName)) {
                    return s.newSegmenter(this.mib, this.filestore, sourceFileName, destinationId);
                }
            } catch (FilestoreException e) {
                if(LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, String.format("Problem when checking segmentation strategy for file %s to entity %d on strategy instance %s: %s", sourceFileName, destinationId, s, e.getMessage()), e);
                }
            }
        }
        return null;
    }

    private long generateTransactionId() {
        return this.transactionIdSequencer.incrementAndGet();
    }

    @Override
    public void dispose() {
        // Delegate to confiner
        this.entityConfiner.submit(this::processDispose);
    }

    private boolean isDisposed() {
        return disposed;
    }

    private void processDispose() {
        // Set disposed to true
        this.disposed = true;
        // Deregister from UT layer
        for(IUtLayer l : this.utLayers.values()) {
            l.deregister(this);
        }
        // Cancel all transactions
        for(CfdpTransaction t : this.id2transaction.values()) {
            // Cancel running transactions
            if(t.getCurrentState() == CfdpTransactionState.RUNNING || t.getCurrentState() == CfdpTransactionState.SUSPENDED) {
                t.cancel(FileDirectivePdu.CC_CANCEL_REQUEST_RECEIVED);
            }
        }
        this.id2transaction.clear();
        // Inform subscribers and clear
        notifyIndication(new EntityDisposedIndication());
        // Add job for subscription cleanup
        this.subscriberNotifier.submit(this.subscribers::clear);
        // Shutdown the thread pools (keep them running for the final notifications)
        this.subscriberNotifier.shutdown();
        this.entityConfiner.shutdown();
        // All done
    }

    /* **********************************************************************************************************
     * IUtLayerSubscriber methods and handlings
     * **********************************************************************************************************/

    @Override
    public void indication(IUtLayer layer, CfdpPdu pdu) {
        this.entityConfiner.submit(() -> processIndication(layer, pdu));
    }

    private void processIndication(IUtLayer layer, CfdpPdu pdu) {
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST, String.format("CFDP Entity %d: received PDU from UT layer %s: %s", mib.getLocalEntity().getLocalEntityId(), layer.getName(), pdu));
        }
        // This entity got this PDU, so if this entity knows about the related transaction, it should process the PDU
        CfdpTransaction transaction = this.id2transaction.get(pdu.getTransactionSequenceNumber());
        if(transaction != null) {
            if(transaction.getCurrentState() == CfdpTransactionState.RUNNING || transaction.getCurrentState() == CfdpTransactionState.SUSPENDED) {
                // 1) the entity knows the transaction and the transaction is running -> forward
                transaction.indication(pdu);
            } else if(pdu.isAcknowledged() && (pdu instanceof EndOfFilePdu || pdu instanceof FinishedPdu)) {
                // 2) the entity knows the transaction and it is a EOF in Acknowledged mode, or a Finished in Acknowledged mode,
                // you have to reply accordingly -> send ACK(TERMINATED).
                generateAck(pdu, AckPdu.TransactionStatus.TERMINATED);
            }
            // 3) the entity knows the transaction but the PDU is not to be acknowledged -> ignore
        } else {
            if(pdu.isAcknowledged() && pdu instanceof FinishedPdu) {
                // 4) the entity does not know the transaction, so if it is a Finished in
                // Acknowledged mode, then you have to reply accordingly -> send ACK(UNDEFINED).
                generateAck(pdu, AckPdu.TransactionStatus.UNDEFINED);
            } else if(pdu.getDestinationEntityId() == getMib().getLocalEntity().getLocalEntityId()) {
                // 5) the entity does not know the transaction, but it is the destination of such transaction -> create
                // an incoming transaction, will take care of the pdu processing
                createNewIncomingTransaction(pdu);
            } else {
                // 6) the entity got a PDU that was completely unexepected -> log and ignore
                if(LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, String.format("CFDP Entity %d received PDU %s not linked to any transaction and not supposed to be handled, ignoring", getMib().getLocalEntity().getLocalEntityId(), pdu));
                }
            }
        }
    }

    private void generateAck(CfdpPdu pdu, AckPdu.TransactionStatus transactionAckStatus) {
        AckPduBuilder b = new AckPduBuilder();
        b.setAcknowledged(pdu.isAcknowledged());
        b.setCrcPresent(pdu.isCrcPresent());
        b.setSegmentationControlPreserved(pdu.isSegmentationControlPreserved());
        // Set the length for the entity ID
        b.setEntityIdLength(pdu.getEntityIdLength());
        // Set the transaction ID
        b.setTransactionSequenceNumber(pdu.getTransactionSequenceNumber(), pdu.getTransactionSequenceNumberLength());
        b.setLargeFile(pdu.isLargeFile());
        b.setDestinationEntityId(pdu.getDestinationEntityId());
        b.setSourceEntityId(pdu.getSourceEntityId());
        b.setDestinationEntityId(pdu.getDestinationEntityId());
        b.setSourceEntityId(pdu.getSourceEntityId());
        b.setTransactionStatus(transactionAckStatus);
        if(pdu instanceof EndOfFilePdu) {
            // If the pdu is a EOF, then the EOF is generated by the sending entity, use such entity ID to send back the ACK
            b.setDirection(CfdpPdu.Direction.TOWARD_FILE_SENDER);
            b.setConditionCode(((EndOfFilePdu) pdu).getConditionCode());
            b.setDirectiveCode(FileDirectivePdu.DC_EOF_PDU);
            b.setDirectiveSubtypeCode((byte) 0x00);
        } else if(pdu instanceof FinishedPdu) {
            // If the pdu is a Finished, then the Finished is generated by the destination entity, use such entity ID to send back the ACK
            b.setDirection(CfdpPdu.Direction.TOWARD_FILE_RECEIVER);
            b.setConditionCode(((FinishedPdu) pdu).getConditionCode());
            b.setDirectiveCode(FileDirectivePdu.DC_FINISHED_PDU);
            b.setDirectiveSubtypeCode((byte) 0x01);
        }
        // Send it out
        RemoteEntityConfigurationInformation remoteConf = b.getDirection() == CfdpPdu.Direction.TOWARD_FILE_RECEIVER ? getMib().getRemoteEntityById(b.getDestinationEntityId()) : getMib().getRemoteEntityById(b.getSourceEntityId());
        IUtLayer utLayerToUse = this.utLayers.get(remoteConf.getUtLayer());
        if(utLayerToUse == null) {
            if (LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, String.format("Entity %d cannot acknowledge PDU %s to entity %s: no suitable UT layer found", getMib().getLocalEntity().getLocalEntityId(), pdu, remoteConf.getRemoteEntityId()));
            }
        } else {
            try {
                utLayerToUse.request(b.build(), remoteConf.getRemoteEntityId());
            } catch (UtLayerException e) {
                if (LOG.isLoggable(Level.SEVERE)) {
                    LOG.log(Level.SEVERE, String.format("Entity %d cannot acknowledge PDU %s to entity %s on UT layer %s: %s", getMib().getLocalEntity().getLocalEntityId(), pdu, remoteConf.getRemoteEntityId(), utLayerToUse.getName(), e.getMessage()), e);
                }
            }
        }
    }

    private void createNewIncomingTransaction(CfdpPdu pdu) {
        // Create a new transaction object to handle the requested transaction
        CfdpTransaction cfdpTransaction = new IncomingCfdpTransaction(pdu, this);
        // Register the transaction in the map
        this.id2transaction.put(cfdpTransaction.getTransactionId(), cfdpTransaction);
        // Start the transaction
        cfdpTransaction.activate();
    }

    @Override
    public void startTxPeriod(IUtLayer layer, long entityId) {
        // Check all transactions that are affected by this and update them
        updateTxOpportunity(entityId, true);
    }

    @Override
    public void endTxPeriod(IUtLayer layer, long entityId) {
        updateTxOpportunity(entityId, false);
    }

    private void updateTxOpportunity(long entityId, boolean b) {
        // Check all transactions that are affected by this and update them
        for (Map.Entry<Long, CfdpTransaction> e : this.id2transaction.entrySet()) {
            if (e.getValue().getRemoteDestination().getRemoteEntityId() == entityId) {
                e.getValue().updateTxOpportunity(b);
            }
        }
    }

    @Override
    public void startRxPeriod(IUtLayer layer, long entityId) {
        updateRxOpportunity(entityId, true);
    }

    @Override
    public void endRxPeriod(IUtLayer layer, long entityId) {
        updateRxOpportunity(entityId, false);
    }

    private void updateRxOpportunity(long entityId, boolean b) {
        // Check all transactions that are affected by this and update them
        for (Map.Entry<Long, CfdpTransaction> e : this.id2transaction.entrySet()) {
            if (e.getValue().getRemoteDestination().getRemoteEntityId() == entityId) {
                e.getValue().updateRxOpportunity(b);
            }
        }
    }
}
