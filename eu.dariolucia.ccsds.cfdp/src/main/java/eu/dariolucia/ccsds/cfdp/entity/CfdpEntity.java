package eu.dariolucia.ccsds.cfdp.entity;

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
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDirectivePdu;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayer;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayerSubscriber;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CfdpEntity implements IUtLayerSubscriber {

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

    public CfdpEntity(Mib mib, IVirtualFilestore filestore, IUtLayer... layers) {
        this(mib, filestore, Arrays.asList(layers));
    }

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

    public Mib getMib() {
        return this.mib;
    }

    public IUtLayer getUtLayerByName(String name) {
        return this.utLayers.get(name);
    }

    public IUtLayer getUtLayerByDestinationEntity(long destinationEntityId) {
        RemoteEntityConfigurationInformation re = this.mib.getRemoteEntityById(destinationEntityId);
        return re != null ? this.utLayers.get(re.getUtLayer()) : null;
    }

    public IVirtualFilestore getFilestore() {
        return this.filestore;
    }

    public void register(ICfdpEntitySubscriber s) {
        this.subscribers.add(s);
    }

    public void deregister(ICfdpEntitySubscriber s) {
        this.subscribers.remove(s);
    }

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
     * This method can be invoked also by {@link CfdpTransaction} objects.
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
    public ICfdpFileSegmenter getSegmentProvider(String sourceFileName, long destinationId) {
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
     * IUtLayerSubscriber methods
     * **********************************************************************************************************/

    @Override
    public void indication(IUtLayer layer, CfdpPdu pdu) {
        this.entityConfiner.submit(() -> processIndication(layer, pdu));
    }

    private void processIndication(IUtLayer layer, CfdpPdu pdu) {
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST, String.format("CFDP Entity %d: received PDU from UT layer %s: %s", mib.getLocalEntity().getLocalEntityId(), layer.getName(), pdu));
        }
        // FIXME: not adequate, according to the standard:
        //  Source Entity ID: Identifies the entity that originated the transaction.
        //  Destination Entity ID: Identifies the entity that is the final destination of the transaction’s metadata and file data.
        // The above means that, as long as the source OR the destination ID are equal to the local ID, the PDU must be processed

        // Three possibilities: 1) the pdu is not for this entity -> discard TODO: for store-and-foward this is not appropriate
        if(pdu.getDestinationEntityId() != mib.getLocalEntity().getLocalEntityId()) {
            if(LOG.isLoggable(Level.WARNING)) {
                LOG.log(Level.WARNING, String.format("CFDP Entity %d: PDU from UT layer %s from entity %d not for this entity: received %d", mib.getLocalEntity().getLocalEntityId(), layer.getName(), pdu.getSourceEntityId(), pdu.getDestinationEntityId()));
            }
            return;
        }
        CfdpTransaction transaction = this.id2transaction.get(pdu.getTransactionSequenceNumber());
        if(transaction != null) {
            // 2) the PDU is for this entity and there is a transaction already running -> forward
            transaction.indication(pdu);
            // FIXME: it actually depends: if it is a EOF with ACK request, or a Finished with ACK request, then you need to check the transaction state
            //  and if it is not RUNNING/SUSPENDED, you have to reply accordingly -> TERMINATED.
        } else {
            // FIXME: it actually depends: if it is a EOF with ACK request, or a Finished with ACK request, then you have to reply accordingly -> UNDEFINED.
            // TODO: 3) the PDU is related to this entity (source or destination ID) and there is no transaction already running -> create
            createNewIncomingTransaction(pdu);
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
