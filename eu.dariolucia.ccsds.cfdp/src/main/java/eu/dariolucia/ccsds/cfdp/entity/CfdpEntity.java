package eu.dariolucia.ccsds.cfdp.entity;

import eu.dariolucia.ccsds.cfdp.entity.indication.ICfdpIndication;
import eu.dariolucia.ccsds.cfdp.entity.indication.TransactionIndication;
import eu.dariolucia.ccsds.cfdp.entity.request.ICfdpRequest;
import eu.dariolucia.ccsds.cfdp.entity.request.PutRequest;
import eu.dariolucia.ccsds.cfdp.filestore.IVirtualFilestore;
import eu.dariolucia.ccsds.cfdp.mib.Mib;
import eu.dariolucia.ccsds.cfdp.mib.RemoteEntityConfigurationInformation;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
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
        // Ready to go
        startProcessing();
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
        this.subscribers.add(s);
    }

    public void request(ICfdpRequest request) {
        this.entityConfiner.submit(() -> processRequest(request));
    }

    private void processRequest(ICfdpRequest request) {
        if(disposed) {
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
     * <ol>
     * <li>a Transaction Start Notification procedure; and</li>
     * <li>b) a Copy File procedure, for which
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
        CfdpTransaction cfdpTransaction = new CfdpSendTransaction(transactionId, r, this);
        this.id2transaction.put(transactionId, cfdpTransaction);
        // Notify the creation of the new transaction to the subscriber
        notifyIndication(new TransactionIndication(transactionId, r));
        // Start the transaction
        cfdpTransaction.activate();
    }

    private void notifyIndication(ICfdpIndication indication) {
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

    private long generateTransactionId() {
        return this.transactionIdSequencer.incrementAndGet();
    }

    public void dispose() {
        // TODO: delegate to confiner
            // TODO: set disposed to true
            // TODO: manage transactions
            // TODO: inform subscribers and clear

        // Shutdown the thread pools
        this.subscriberNotifier.shutdown();
        this.entityConfiner.shutdown();
    }

    /* **********************************************************************************************************
     * IUtLayerSubscriber methods
     * **********************************************************************************************************/

    @Override
    public void indication(IUtLayer layer, CfdpPdu pdu) {
        // TODO
    }

    @Override
    public void startTxPeriod(IUtLayer layer, long entityId) {
        // TODO
    }

    @Override
    public void endTxPeriod(IUtLayer layer, long entityId) {
        // TODO
    }

    @Override
    public void startRxPeriod(IUtLayer layer, long entityId) {
        // TODO
    }

    @Override
    public void endRxPeriod(IUtLayer layer, long entityId) {
        // TODO
    }
}
