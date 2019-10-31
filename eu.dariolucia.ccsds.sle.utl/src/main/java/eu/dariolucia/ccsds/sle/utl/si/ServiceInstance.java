/*
 *  Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package eu.dariolucia.ccsds.sle.utl.si;

import com.beanit.jasn1.ber.types.BerNull;
import com.beanit.jasn1.ber.types.BerType;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.Credentials;
import eu.dariolucia.ccsds.sle.utl.config.PeerConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.ServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.network.PortMapping;
import eu.dariolucia.ccsds.sle.utl.config.network.RemotePeer;
import eu.dariolucia.ccsds.sle.utl.network.tml.ITmlChannelObserver;
import eu.dariolucia.ccsds.sle.utl.network.tml.TmlChannel;
import eu.dariolucia.ccsds.sle.utl.network.tml.TmlChannelException;
import eu.dariolucia.ccsds.sle.utl.network.tml.TmlDisconnectionReasonEnum;
import eu.dariolucia.ccsds.sle.utl.pdu.PduFactoryUtil;
import eu.dariolucia.ccsds.sle.utl.pdu.PduStringUtil;
import eu.dariolucia.ccsds.sle.utl.util.DataRateCalculator;
import eu.dariolucia.ccsds.sle.utl.util.DataRateSample;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is the core class of the SLE User Test Library. In an SLE association it represents the user peer: it
 * can act as initiator (User Initiated Bind) or responder (Provider Initiated Bind). It is the entry point to send
 * and receive SLE operations from the SLE provider, further specialised by service-type specific classes.
 * <p>
 * This class is thread-safe and uses two threads for its operations:
 * <ul>
 *     <li>A dispatcher thread, which implements a thread confinement managing the internals of the object state</li>
 *     <li>A notifier thread, which is responsible to notify state changes and SLE operations to the subscribers</li>
 * </ul>
 * Operations requested on this object by the user code or by the TML channel threads are queued in a queue and processed
 * by a single thread (the dispatcher thread). When a change in state must be notified to subscribers, the state object
 * is created and the notification is delegated to a separate thread. Therefore operations performed by instances of
 * this class are fully asynchronous and lock-free.
 */
public abstract class ServiceInstance implements ITmlChannelObserver {

    private static final int BIND_INTERNAL_INVOKE_ID = -1;
    private static final int UNBIND_INTERNAL_INVOKE_ID = -2;

    private static final int SI_INIT_MODE_NOT_SELECTED = 0;
    private static final int SI_INIT_MODE_UIB = 1;
    private static final int SI_INIT_MODE_PIB = 2;

    private static final Logger LOG = Logger.getLogger(ServiceInstance.class.getName());

    private static final String BIND_RETURN_NAME = "BIND-RETURN";
    private static final String UNBIND_NAME = "UNBIND";
    private static final String BIND_NAME = "BIND";
    private static final String UNBIND_RETURN_NAME = "UNBIND-RETURN";

    private final ExecutorService dispatcher;
    private final ExecutorService notifier;

    // The SLE API configuration
    private final PeerConfiguration peerConfiguration;
    // The service instance configuration
    protected final ServiceInstanceConfiguration serviceInstanceConfiguration;

    // The listeners to this service instance
    private final List<IServiceInstanceListener> listeners = new CopyOnWriteArrayList<>();

    private volatile int initMode = SI_INIT_MODE_NOT_SELECTED;
    // Service instance state
    protected volatile ServiceInstanceBindingStateEnum currentState = ServiceInstanceBindingStateEnum.UNBOUND;

    private Integer sleVersion = null; // Set by the bind request

    private TmlChannel tmlChannel = null; // The TML channel
    private boolean expectConnectionToBeClosed = false; // To be set to true when it is clear that the channel will be
    // closed (peer abort, unbind return)

    // The sequencer for operations
    protected final AtomicInteger invokeIdSequencer = new AtomicInteger(0);

    // Set of timer tasks and timer to handle return timeouts
    private final Map<Long, TimerTask> invokeId2timeout = new HashMap<>();
    private final Timer returnTimeoutTimer = new Timer(true);

    // Handler map: one handler registered for each return operation
    private final Map<Class<?>, Consumer<?>> handlers = new HashMap<>();

    private String lastErrorMessage = null; // Set in case of protocol abort or peer abort or other problem
    private Exception lastErrorException = null; // Set in case of protocol abort or peer abort or other problem

    private boolean statusReportScheduled = false; // If the status report was scheduled

    private byte[] lastPduSent = null;
    private byte[] lastPduReceived = null;

    private final DataRateCalculator statsCounter = new DataRateCalculator();

    private boolean sendPositiveBindReturn = true;
    private boolean sendPositiveUnbindReturn = true;
    private BindDiagnosticsEnum negativeBindReturnDiagnostics = BindDiagnosticsEnum.OTHER_REASON;

    private boolean configured = false;

    protected ServiceInstance(PeerConfiguration peerConfiguration,
                              ServiceInstanceConfiguration serviceInstanceConfiguration) {
        this.peerConfiguration = peerConfiguration;
        this.serviceInstanceConfiguration = serviceInstanceConfiguration;

        this.dispatcher = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new PriorityBlockingQueue<>(30),
                r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName(ServiceInstance.this.serviceInstanceConfiguration.getServiceInstanceIdentifier()
                            + " - SI Dispatcher");
                    return t;
                });
        this.notifier = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName(ServiceInstance.this.serviceInstanceConfiguration.getServiceInstanceIdentifier()
                    + " - SI Notifier");
            return t;
        });

        // register handlers for unbind return and bind return operations
        registerPduReceptionHandler(SleBindInvocation.class, this::handleSleBindInvocation);
        registerPduReceptionHandler(SleUnbindInvocation.class, this::handleSleUnbindInvocation);
        registerPduReceptionHandler(SleBindReturn.class, this::handleSleBindReturn);
        registerPduReceptionHandler(SleUnbindReturn.class, this::handleSleUnbindReturn);
        // Custom setup
        setup();
    }

    protected final <T> void registerPduReceptionHandler(Class<T> clazz, Consumer<T> handler) {
        this.handlers.put(clazz, handler);
    }

    /**
     * This method returns the configuration of the service instance. This is a pointer to the internal configuration
     * (not a copy) and shall not be changed by the calling object. The service-type specific subclass is returned.
     *
     * @return the service instance configuration
     */
    public ServiceInstanceConfiguration getServiceInstanceConfiguration() {
        return serviceInstanceConfiguration;
    }

    @SuppressWarnings("unchecked")
    private <T> Consumer<T> getHandler(Class<T> clazz) {
        return (Consumer<T>) this.handlers.get(clazz);
    }

    private final void setError(String message, Exception e) {
        LOG.log(Level.SEVERE, getServiceInstanceIdentifier() + ": " + message, e);
        this.lastErrorMessage = message;
        this.lastErrorException = e;
    }

    protected final void setError(String message) {
        setError(message, null);
    }

    protected final void clearError() {
        this.lastErrorException = null;
        this.lastErrorMessage = null;
    }

    protected final byte[] getLastPduReceived() {
        return this.lastPduReceived;
    }

    protected final byte[] getLastPduSent() {
        return this.lastPduSent;
    }

    private Exception getErrorException() {
        return this.lastErrorException;
    }

    private String getErrorMessage() {
        return this.lastErrorMessage;
    }

    protected final Integer getSleVersion() {
        return this.sleVersion;
    }

    /**
     * This method can be overridden by subclasses to indicate that they implement a provider role. Currently,
     * all the production-code of this library purely implement a user role. Provider-role implementations are
     * available in the test location.
     *
     * @return true if the service instance is a user-side peer, false if it is a provider-side peer
     */
    protected boolean isUserSide() {
        return true;
    }

    protected final void dispatchFromUser(Runnable r) {
        if (!this.configured) {
            throw new IllegalStateException("Service instance not configured: call configure() before invoking any method");
        }
        if (this.dispatcher.isShutdown()) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine(getServiceInstanceIdentifier() + ": Invocation not dispatched, dispatcher was shut down");
            }
            return;
        }
        SleTask t = new SleTask(SleTask.FROM_USER_TYPE, r);
        this.dispatcher.execute(t);
    }

    protected final void dispatchFromProvider(Runnable r) {
        if (!this.configured) {
            throw new IllegalStateException("Service instance not configured: call configure() before invoking any method");
        }
        if (this.dispatcher.isShutdown()) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine(getServiceInstanceIdentifier() + ": Invocation not dispatched, dispatcher was shut down");
            }
            return;
        }
        SleTask t = new SleTask(SleTask.FROM_PROVIDER_TYPE, r);
        this.dispatcher.execute(t);
    }

    private void notify(Runnable r) {
        this.notifier.execute(r);
    }

    protected final void notifyStateUpdate() {
        final ServiceInstanceState state = buildCurrentState();
        if (state != null) {
            notify(() -> {
                for (IServiceInstanceListener l : this.listeners) {
                    try {
                        l.onStateUpdated(this, state);
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE,
                                getServiceInstanceIdentifier() + ": Service instance cannot notify (state update) listener " + l, e);
                    }
                }
            });
        }
    }

    protected final void notifyPduSent(Object pdu, String name, byte[] encodedOperation) {
        notify(() -> {
            for (IServiceInstanceListener l : this.listeners) {
                try {
                    l.onPduSent(this, pdu, name, encodedOperation);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE,
                            getServiceInstanceIdentifier() + ": Service instance cannot notify (PDU sent) listener " + l, e);
                }
            }
        });
    }

    protected final void notifyPduSentError(Object pdu, String name, byte[] encodedOperation) {
        notify(() -> {
            for (IServiceInstanceListener l : this.listeners) {
                try {
                    l.onPduSentError(this, pdu, name, encodedOperation, getErrorMessage(), getErrorException());
                } catch (Exception e) {
                    LOG.log(Level.SEVERE,
                            getServiceInstanceIdentifier() + ": Service instance cannot notify (PDU sent error) listener " + l, e);
                }
            }
        });
    }

    protected final void notifyPduReceived(Object pdu, String name, byte[] encodedOperation) {
        notify(() -> {
            for (IServiceInstanceListener l : this.listeners) {
                try {
                    l.onPduReceived(this, pdu, name, encodedOperation);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE,
                            getServiceInstanceIdentifier() + ": Service instance cannot notify (PDU received) listener " + l, e);
                }
            }
        });
    }

    private void notifyPduDecodingError(byte[] encodedOperation) {
        notify(() -> {
            for (IServiceInstanceListener l : this.listeners) {
                try {
                    l.onPduDecodingError(this, encodedOperation);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE,
                            getServiceInstanceIdentifier() + ": Service instance cannot notify (PDU decoding error) listener " + l, e);
                }
            }
        });
    }

    private void notifyPduHandlingError(Object pdu, byte[] encodedOperation) {
        notify(() -> {
            for (IServiceInstanceListener l : this.listeners) {
                try {
                    l.onPduHandlingError(this, pdu, encodedOperation);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE,
                            getServiceInstanceIdentifier() + ": Service instance cannot notify (PDU handling error) listener " + l, e);
                }
            }
        });
    }

    /**
     * This method registers a listener to this service instance.
     *
     * @param l the listener to be registered
     */
    public final void register(final IServiceInstanceListener l) {
        dispatchFromUser(() -> doRegister(l));
    }

    private void doRegister(final IServiceInstanceListener l) {
        final ServiceInstanceState state = buildCurrentState();
        this.listeners.add(l);
        notify(() -> l.onStateUpdated(this, state));
    }

    /**
     * This method deregisters a listener from this service instance.
     *
     * @param l the listener to be deregistered
     */
    public final void deregister(final IServiceInstanceListener l) {
        dispatchFromUser(() -> doDeregister(l));
    }

    private void doDeregister(IServiceInstanceListener l) {
        this.listeners.remove(l);
    }

    /**
     * This method sets the BIND response behaviour of the service instance if configured for Provider Initiated Bind.
     * This method can be called at any time and the change in configuration will be applied immediately, i.e. they will
     * overwrite the configuration applied when calling a prior waitForBind(...).
     *
     * @param sendPositiveBindReturn        if true (default), the user will respond with a positive BIND return, otherwise it will send back a negative response
     * @param negativeBindReturnDiagnostics if sendPositiveBindReturn is false, the negative response will deliver this diagnostics
     */
    public final void setBindReturnBehaviour(boolean sendPositiveBindReturn,
                                             BindDiagnosticsEnum negativeBindReturnDiagnostics) {
        dispatchFromUser(() -> {
            this.sendPositiveBindReturn = sendPositiveBindReturn;
            this.negativeBindReturnDiagnostics = negativeBindReturnDiagnostics;
            LOG.log(Level.CONFIG,
                    getServiceInstanceIdentifier() + ": BIND-RETURN behaviour updated: send positive BIND-RETURN="
                            + sendPositiveBindReturn + ", negative diagnostics=" + negativeBindReturnDiagnostics);
        });
    }

    /**
     * This method sets the UNBIND response behaviour of the service instance if configured for the Provided Initiated Bind.
     * This method can be called at any time and the change in configuration will be applied immediately.
     *
     * @param sendPositiveUnbindReturn if true (default), the user will responde with a positive BIND return, otherwise it will send back a negative response
     */
    public final void setUnbindReturnBehaviour(boolean sendPositiveUnbindReturn) {
        dispatchFromUser(() -> {
            this.sendPositiveUnbindReturn = sendPositiveUnbindReturn;
            LOG.log(Level.CONFIG,
                    getServiceInstanceIdentifier() + ": UNBIND-RETURN behaviour updated: send positive BIND-RETURN="
                            + sendPositiveUnbindReturn);
        });
    }

    /**
     * This method requests the service instance to start waiting for a connection plus BIND coming from the provider.
     * This method does not block and returns immediately. The provided arguments will be applied immediately, i.e. they will
     * overwrite the configuration applied when calling a prior setBindReturnBehaviour(...).
     *
     * @param sendPositiveBindReturn        if true, the user will respond with a positive BIND return, otherwise it will send back a negative response
     * @param negativeBindReturnDiagnostics if sendPositiveBindReturn is false, the negative response will deliver this diagnostics
     */
    public final void waitForBind(boolean sendPositiveBindReturn, BindDiagnosticsEnum negativeBindReturnDiagnostics) {
        dispatchFromUser(() -> doWaitForBind(sendPositiveBindReturn, negativeBindReturnDiagnostics));
    }

    private void doWaitForBind(boolean sendPositiveBindReturn, BindDiagnosticsEnum negativeBindReturnDiagnostics) {
        clearError();

        //
        if (this.initMode != SI_INIT_MODE_NOT_SELECTED) {
            setError("Wait for bind requested, but service instance is not in clean init mode");
            notifyStateUpdate();
            return;
        }

        if (this.serviceInstanceConfiguration.getInitiator() == InitiatorRoleEnum.USER && isUserSide()) {
            setError("Wait for bind requested, but service instance initiator is set to USER");
            notifyStateUpdate();
            return;
        }
        if (this.serviceInstanceConfiguration.getInitiator() == InitiatorRoleEnum.PROVIDER && !isUserSide()) {
            setError("Wait for bind requested, but service instance initiator is set to PROVIDER");
            notifyStateUpdate();
            return;
        }

        // Validate state
        if (this.currentState != ServiceInstanceBindingStateEnum.UNBOUND) {
            setError("Wait for bind requested, but service instance is in state " + this.currentState);
            notifyStateUpdate();
            return;
        }

        // Set the invokeId sequencer to 0
        this.invokeIdSequencer.set(0);

        // Establish connection, just open the port
        // From the responder port, go to API configuration and check the
        // foreign local port and the related IP address.
        Optional<PortMapping> port = this.peerConfiguration.getPortMappings().stream()
                .filter(flp -> flp.getPortName().equals(getResponderPortIdentifier())).findFirst();
        if (port.isPresent()) {
            // Create a new TML channel
            this.tmlChannel = TmlChannel.createServerTmlChannel(port.get().getRemotePort(), this, port.get().getTcpTxBufferSize(), port.get().getTcpRxBufferSize());
        } else {
            setError("Foreign local port " + getResponderPortIdentifier()
                    + " not found in the SLE configuration file for service instance "
                    + getServiceInstanceIdentifier());
            notifyStateUpdate();
            return;
        }

        // At this point in time, the TML is in PIB mode
        this.initMode = SI_INIT_MODE_PIB;
        // Initialise predefined behaviour
        this.sendPositiveBindReturn = sendPositiveBindReturn;
        this.negativeBindReturnDiagnostics = negativeBindReturnDiagnostics;

        // Go for connection
        try {
            setServiceInstanceState(ServiceInstanceBindingStateEnum.UNBOUND_WAIT);
            this.tmlChannel.connect();
            LOG.log(Level.INFO, getServiceInstanceIdentifier() + ": Waiting for incoming connections");
        } catch (TmlChannelException e) {
            LOG.log(Level.SEVERE, getServiceInstanceIdentifier() + ": error when waiting for connection", e);
            setServiceInstanceState(ServiceInstanceBindingStateEnum.UNBOUND);
            disconnect("Cannot wait for connection", e, null);
        }

        // Generate state and notify update
        notifyStateUpdate();
    }

    /**
     * This method requests the user to initiate the TML connection and send a BIND operation to the provider, with the
     * specification of the provided SLE version. This method does not block and returns immediately.
     *
     * @param version the SLE version to be used
     */
    public final void bind(int version) {
        dispatchFromUser(() -> doBind(version));
    }

    private void doBind(int version) {
        clearError();

        //
        if (this.initMode != SI_INIT_MODE_NOT_SELECTED) {
            setError("Bind requested, but service instance is not in clean init mode");
            notifyStateUpdate();
            return;
        }

        if (this.serviceInstanceConfiguration.getInitiator() == InitiatorRoleEnum.PROVIDER && isUserSide()) {
            setError("Bind requested, but service instance initiator is set to PROVIDER");
            notifyStateUpdate();
            return;
        }
        if (this.serviceInstanceConfiguration.getInitiator() == InitiatorRoleEnum.USER && !isUserSide()) {
            setError("Bind requested, but service instance initiator is set to USER");
            notifyStateUpdate();
            return;
        }

        // Validate state
        if (this.currentState != ServiceInstanceBindingStateEnum.UNBOUND) {
            setError("Bind requested, but service instance is in state "
                    + this.currentState);
            notifyStateUpdate();
            return;
        }

        // Set the invokeId sequencer to 0
        this.invokeIdSequencer.set(0);
        // Set the version to be used
        this.sleVersion = version;

        // Create operation
        SleBindInvocation pdu = new SleBindInvocation();
        pdu.setVersionNumber(new VersionNumber(version));
        pdu.setInitiatorIdentifier(new AuthorityIdentifier(getInitiatorIdentifier().getBytes()));
        pdu.setResponderPortIdentifier(new PortId(getResponderIdentifier().getBytes()));
        pdu.setServiceType(new ApplicationIdentifier(getApplicationIdentifier().getCode()));
        pdu.setServiceInstanceIdentifier(PduFactoryUtil.buildServiceInstanceIdentifier(getServiceInstanceIdentifier(),
                getApplicationIdentifier()));

        // Add credentials
        // From the API configuration (remote peers) and SI configuration (responder
        // id), check remote peer and check if authentication must be used.
        Credentials creds = generateCredentials(getResponderIdentifier(), AuthenticationModeEnum.ALL,
                AuthenticationModeEnum.BIND);
        if (creds == null) {
            // Error while generating credentials, set by generateCredentials()
            notifyPduSentError(pdu, BIND_NAME, null);
            notifyStateUpdate();
            return;
        } else {
            pdu.setInvokerCredentials(creds);
        }

        updateHandlersForVersion(this.sleVersion);

        // Establish connection
        // From the responder port of the bind, go to API configuration and check the
        // foreign local port and the related IP address.
        Optional<PortMapping> port = this.peerConfiguration.getPortMappings().stream()
                .filter(flp -> flp.getPortName().equals(getResponderPortIdentifier())).findFirst();
        if (port.isPresent()) {
            // Create a new TML channel
            this.tmlChannel = TmlChannel.createClientTmlChannel(port.get().getRemoteHost(),
                    port.get().getRemotePort(), port.get().getHeartbeatInterval(), port.get().getDeadFactor(), this,
                    port.get().getTcpTxBufferSize(), port.get().getTcpRxBufferSize());
        } else {
            setError("Foreign local port " + getResponderPortIdentifier()
                    + " not found in the SLE configuration file for service instance "
                    + getServiceInstanceIdentifier());
            notifyPduSentError(pdu, BIND_NAME, null);
            notifyStateUpdate();
            return;
        }

        // Go for connection
        try {
            this.tmlChannel.connect();
        } catch (TmlChannelException e) {
            disconnect("Cannot connect", e, null);
            notifyPduSentError(pdu, BIND_NAME, null);
            notifyStateUpdate();
            return;
        }

        boolean resultOk = encodeAndSend(BIND_INTERNAL_INVOKE_ID, pdu, BIND_NAME);

        if (resultOk) {
            // If all fine, transition to new state: BIND_PENDING and notify PDU sent
            setServiceInstanceState(ServiceInstanceBindingStateEnum.BIND_PENDING);
            notifyPduSent(pdu, BIND_NAME, getLastPduSent());

            // Init mode to be set
            this.initMode = SI_INIT_MODE_UIB;

            // Generate state and notify update
            notifyStateUpdate();
        }
    }

    private void startReturnTimeout(final long opInvokeId, Object pdu) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine(getServiceInstanceIdentifier() + ": Return timeout started for invokeId=" + opInvokeId + ", operation: " + pdu);
        }
        TimerTask tt = new TimerTask() {
            @Override
            public void run() {
                try {
                    dispatchFromUser(() -> returnTimeoutExpired(opInvokeId));
                } catch (Exception e) {
                    LOG.log(Level.WARNING, String.format("%s: Cannot run return timeout expire task for operation with invokeId=%d", getServiceInstanceIdentifier(), opInvokeId), e);
                }
            }
        };
        this.invokeId2timeout.put(opInvokeId, tt);
        this.returnTimeoutTimer.schedule(tt, this.serviceInstanceConfiguration.getReturnTimeoutPeriod() * 1000L);
    }

    private void returnTimeoutExpired(long opInvokeId) {
        if (LOG.isLoggable(Level.WARNING)) {
            LOG.warning(String.format("%s: Return timeout expired for invokeId=%d", getServiceInstanceIdentifier(), opInvokeId));
        }
        TimerTask tt = this.invokeId2timeout.remove(opInvokeId);
        if (tt != null) {
            disconnect("Return timeout expired for operation with invokeId=" + opInvokeId);
            // Generate state and notify update
            notifyStateUpdate();
        }
    }

    protected void cancelReturnTimeout(long opInvokeId) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine(String.format("%s: Return timeout cancelled for invokeId=%d", getServiceInstanceIdentifier(), opInvokeId));
        }
        TimerTask tt = this.invokeId2timeout.remove(opInvokeId);
        if (tt != null) {
            tt.cancel();
        }
    }

    protected boolean encodeAndSend(Integer invokeId, BerType pdu, String name) {
        // Encode the pdu
        // Use a map class 2 function to convert the PDU into a byte array. See encdec
        // package.
        byte[] encodedPdu;
        try {
            encodedPdu = encodePdu(pdu);
            String invokeStr = invokeId != null && invokeId >= 0 ? "(" + invokeId + ") " : "(<no invoke ID>) ";
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine(String.format("%s: PDU %s%s encoded: %s", getServiceInstanceIdentifier(), invokeStr, name, PduStringUtil.instance().toHexDump(encodedPdu)));
            }
        } catch (IOException e1) {
            disconnect("Cannot encode PDU", e1, null);
            notifyPduSentError(pdu, name, null);
            notifyStateUpdate();
            return false;
        }

        // Send to TML
        try {
            this.tmlChannel.sendPdu(encodedPdu);
        } catch (TmlChannelException e) {
            disconnect("Cannot send PDU", e, null);
            notifyPduSentError(pdu, name, encodedPdu);
            notifyStateUpdate();
            return false;
        }

        // Start return timeout
        // Start a timer task for this confirmed operation and add it in a map.
        // This is done only if the invoke ID is set.
        if (invokeId != null) {
            startReturnTimeout(invokeId, pdu);
        }

        this.statsCounter.addOut(1);
        // Save in the last pdu sent
        this.lastPduSent = encodedPdu;

        return true;
    }

    protected final Credentials generateCredentials(String responderIdentifier,
                                                    AuthenticationModeEnum... requiredAuthModes) {
        Optional<RemotePeer> remotePeer = this.peerConfiguration.getRemotePeers().stream()
                .filter(rp -> rp.getId().equals(responderIdentifier)).findFirst();
        if (remotePeer.isPresent()) {
            Credentials credentials;
            // If so, build and add credentials.
            if (Arrays.asList(requiredAuthModes).contains(remotePeer.get().getAuthenticationMode())) {
                credentials = PduFactoryUtil.buildCredentials(true, this.peerConfiguration.getLocalId(),
                        this.peerConfiguration.getLocalPassword(), remotePeer.get().getAuthenticationHash());
            } else {
                credentials = PduFactoryUtil.buildEmptyCredentials();
            }
            return credentials;
        } else {
            setError("Remote peer " + responderIdentifier
                    + " not found in the SLE configuration file for service instance "
                    + getServiceInstanceIdentifier());
            return null;
        }
    }

    protected void setServiceInstanceState(ServiceInstanceBindingStateEnum newState) {
        if (newState != this.currentState) {
            if (LOG.isLoggable(Level.INFO)) {
                LOG.log(Level.INFO, String.format("%s: State transition from %s to %s", getServiceInstanceIdentifier(), this.currentState, newState));
            }
            this.currentState = newState;
        }
    }

    /**
     * This method requests to send an UNBIND operation with the provided reason. This method does not block and returns immediately.
     *
     * @param reason the unbind reason
     */
    public final void unbind(UnbindReasonEnum reason) {
        dispatchFromUser(() -> doUnbind(reason));
    }

    private void doUnbind(UnbindReasonEnum reason) {
        clearError();

        // Validate state
        if (this.currentState != ServiceInstanceBindingStateEnum.READY) {
            setError("Unbind requested, but service instance is in state "
                    + this.currentState);
            notifyStateUpdate();
            return;
        }

        // Create operation
        SleUnbindInvocation pdu = new SleUnbindInvocation();
        pdu.setUnbindReason(new UnbindReason(reason.getCode())); // Check end/suspend

        // Add credentials
        // From the API configuration (remote peers) and SI configuration (responder
        // id), check remote peer and check if authentication must be used.
        Credentials creds = generateCredentials(getResponderIdentifier(), AuthenticationModeEnum.ALL,
                AuthenticationModeEnum.BIND);
        if (creds == null) {
            // Error while generating credentials, set by generateCredentials()
            notifyPduSentError(pdu, UNBIND_NAME, null);
            notifyStateUpdate();
            return;
        } else {
            pdu.setInvokerCredentials(creds);
        }

        boolean resultOk = encodeAndSend(UNBIND_INTERNAL_INVOKE_ID, pdu, UNBIND_NAME);

        if (resultOk) {
            // About to close the TML channel, do not be surprised
            this.tmlChannel.aboutToDisconnect();
            // If all fine, transition to new state: UNBIND_PENDING and notify PDU sent
            setServiceInstanceState(ServiceInstanceBindingStateEnum.UNBIND_PENDING);
            notifyPduSent(pdu, UNBIND_NAME, getLastPduSent());

            // Generate state and notify update
            notifyStateUpdate();
        }
    }

    /**
     * This method requests a local PEER-ABORT to be propagated to the remote peer. This method does not block and returns immediately.
     *
     * @param reason the reason of the peer abort
     */
    public final void peerAbort(PeerAbortReasonEnum reason) {
        dispatchFromUser(() -> doPeerAbort(reason));
    }

    private void doPeerAbort(PeerAbortReasonEnum reason) {
        clearError();

        // Validate state
        if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND) {
            setError("Peer abort requested, but service instance is in state "
                    + this.currentState);
            notifyStateUpdate();
            return;
        }

        disconnect("Peer abort requested", null, reason);

        notifyStateUpdate();
    }

    protected final String getRemotePeer() {
        return getInitiatorIdentifier().equals(this.peerConfiguration.getLocalId()) ? getResponderIdentifier() : getInitiatorIdentifier();
    }

    private void handleSleUnbindReturn(SleUnbindReturn pdu) {
        clearError();

        // Validate state
        if (this.currentState != ServiceInstanceBindingStateEnum.UNBIND_PENDING) {
            disconnect("Unbind return received, but service instance is in state " + this.currentState);
            notifyPduReceived(pdu, UNBIND_RETURN_NAME, getLastPduReceived());
            notifyStateUpdate();
            return;
        }

        // Validate credentials
        // From the API configuration (remote peers) and SI configuration (remote peer),
        // check remote peer and check if authentication must be used.
        // If so, verify credentials.
        if (!authenticate(pdu.getResponderCredentials(), AuthenticationModeEnum.ALL)) {
            disconnect("Unbind return received, but wrong credentials");
            notifyPduReceived(pdu, UNBIND_RETURN_NAME, getLastPduReceived());
            notifyStateUpdate();
            return;
        }

        // Cancel timer task for unbind operation
        cancelReturnTimeout(UNBIND_INTERNAL_INVOKE_ID);

        // If all fine (result positive), transition to new state: UNBOUND and notify
        // PDU received
        if (pdu.getResult().getPositive() != null) {
            setServiceInstanceState(ServiceInstanceBindingStateEnum.UNBOUND);
            disconnect(null);
        } else {
            // If problems (result negative), UNBOUND, disconnect, cleanup
            disconnect("Unbind return received, null result");
        }
        // Notify PDU
        notifyPduReceived(pdu, UNBIND_RETURN_NAME, getLastPduReceived());
        // Generate state and notify update
        notifyStateUpdate();
    }

    protected final boolean authenticate(Credentials remoteCredentials, AuthenticationModeEnum... authModeEnums) {
        String remoteId = getRemotePeer();
        int authDelay = this.peerConfiguration.getAuthenticationDelay();
        Optional<RemotePeer> remotePeer = this.peerConfiguration.getRemotePeers().stream()
                .filter(a -> a.getId().equals(remoteId)).findFirst();
        if (remotePeer.isPresent()) {
            AuthenticationModeEnum requiredAuthMode = remotePeer.get().getAuthenticationMode();
            boolean credentialsRequired = requiredAuthMode == AuthenticationModeEnum.ALL
                    || (requiredAuthMode == AuthenticationModeEnum.BIND
                    && Arrays.asList(authModeEnums).contains(AuthenticationModeEnum.BIND));
            if (credentialsRequired && remoteCredentials.getUnused() != null) {
                // Credential required, but they are set as unused
                LOG.severe(() -> String.format("%s: Credential required but the received operation does not provide them", getServiceInstanceIdentifier()));
                return false;
            } else if (!credentialsRequired && remoteCredentials.getUsed() != null) {
                // No credentials required, but they are present
                LOG.severe(() -> String.format("%s: Credential not required but the received operation provides them", getServiceInstanceIdentifier()));
                return false;
            } else if (!credentialsRequired) {
                // No credentials required
                LOG.fine(() -> String.format("%s: Credential not required, operation credentials empty, ok", getServiceInstanceIdentifier()));
                return true;
            } else {
                byte[] encodedCredentials = remoteCredentials.getUsed().value;
                boolean result = PduFactoryUtil.performAuthentication(remotePeer.get(), encodedCredentials, authDelay);
                if (!result) {
                    LOG.severe(() -> String.format("%s: Credential check failed on provided credentials", getServiceInstanceIdentifier()));
                }
                return result;
            }
        } else {
            LOG.severe(() -> String.format("%s: Credentials for remote peer %s not present", getServiceInstanceIdentifier(), remoteId));
            return false;
        }
    }

    private void handleSleBindInvocation(SleBindInvocation pdu) {
        clearError();

        // Notify the reception on the PDU
        notifyPduReceived(pdu, BIND_NAME, getLastPduReceived());

        // Validate state (this implies that the service instance is in PIB mode)
        if (this.currentState != ServiceInstanceBindingStateEnum.UNBOUND_WAIT) {
            // Quite odd, to be honest, disconnect
            disconnect("Bind invocation received, but service instance is in state " + this.currentState);
            notifyStateUpdate();
            return;
        }

        // At this point, let's prepare the return operation
        SleBindReturn resp = new SleBindReturn();
        resp.setResult(new SleBindReturn.Result());
        resp.setResponderIdentifier(
                new AuthorityIdentifier(this.serviceInstanceConfiguration.getResponderIdentifier().getBytes()));

        // Add credentials
        // From the API configuration (remote peers) and SI configuration (responder
        // id), check remote peer and check if authentication must be used.
        Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL,
                AuthenticationModeEnum.BIND);
        if (creds == null) {
            // Error while generating credentials, set by generateCredentials()
            notifyPduSentError(pdu, BIND_RETURN_NAME, null);
            notifyStateUpdate();
            return;
        } else {
            resp.setPerformerCredentials(creds);
        }

        // Validate operation attributes
        if (pdu.getInitiatorIdentifier() == null || pdu.getInitiatorIdentifier().value == null) {
            // Send response
            resp.getResult()
                    .setNegative(new BindDiagnostic(BindDiagnosticsEnum.SI_NOT_ACCESSIBLE_TO_THIS_INITIATOR.getCode()));
            encodeAndSend(null, resp, BIND_RETURN_NAME);
            // Then disconnect
            disconnect("Bind invocation received, but no initiator identifier set");
            notifyStateUpdate();
            return;
        }
        String initiatorId = pdu.getInitiatorIdentifier().toString();
        if (!initiatorId.equals(this.serviceInstanceConfiguration.getInitiatorIdentifier())) {
            // Send response
            resp.getResult()
                    .setNegative(new BindDiagnostic(BindDiagnosticsEnum.SI_NOT_ACCESSIBLE_TO_THIS_INITIATOR.getCode()));
            encodeAndSend(null, resp, BIND_RETURN_NAME);
            // Then disconnect
            disconnect("Bind invocation received, but initiator identifier does not match: expected "
                    + this.serviceInstanceConfiguration.getInitiatorIdentifier() + ", got " + initiatorId);
            notifyStateUpdate();
            return;
        }

        // Validate credentials
        // From the API configuration (remote peers) and SI configuration (remote peer),
        // check remote peer and check if authentication must be used.
        // If so, verify credentials.
        if (!authenticate(pdu.getInvokerCredentials(), AuthenticationModeEnum.ALL, AuthenticationModeEnum.BIND)) {
            // Send response
            resp.getResult().setNegative(new BindDiagnostic(BindDiagnosticsEnum.ACCESS_DENIED.getCode()));
            encodeAndSend(null, resp, BIND_RETURN_NAME);
            // Then disconnect
            disconnect("Bind invocation received, but wrong credentials for initiator " + initiatorId);
            notifyStateUpdate();
            return;
        }

        // All fine, check what to do with the bind
        if (this.sendPositiveBindReturn) {
            // Update version to use
            this.sleVersion = pdu.getVersionNumber().intValue();
            updateHandlersForVersion(this.sleVersion);
            // Send response
            resp.getResult().setPositive(new VersionNumber(pdu.getVersionNumber().intValue()));
            encodeAndSend(null, resp, BIND_RETURN_NAME);
            // Update state
            setServiceInstanceState(ServiceInstanceBindingStateEnum.READY);
            notifyPduSent(pdu, BIND_RETURN_NAME, getLastPduSent());
        } else {
            // Send response
            resp.getResult().setNegative(new BindDiagnostic(this.negativeBindReturnDiagnostics.getCode()));
            encodeAndSend(null, resp, BIND_RETURN_NAME);
            // Then disconnect
            disconnect("Bind invocation received, but rejected by user configuration with diagnostics "
                    + this.negativeBindReturnDiagnostics);
        }

        // Generate state and notify update
        notifyStateUpdate();
    }

    private void handleSleUnbindInvocation(SleUnbindInvocation pdu) {
        clearError();

        // Notify the reception on the PDU
        notifyPduReceived(pdu, UNBIND_NAME, getLastPduReceived());

        // Validate state
        if (this.currentState != ServiceInstanceBindingStateEnum.READY) {
            // Peer abort, according to specs
            doPeerAbort(PeerAbortReasonEnum.PROTOCOL_ERROR);
            return;
        }

        // At this point, let's prepare the return operation
        SleUnbindReturn resp = new SleUnbindReturn();
        resp.setResult(new SleUnbindReturn.Result());

        // Add credentials
        // From the API configuration (remote peers) and SI configuration (responder
        // id), check remote peer and check if authentication must be used.
        Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL);
        if (creds == null) {
            // Error while generating credentials, set by generateCredentials()
            notifyPduSentError(pdu, UNBIND_RETURN_NAME, null);
            notifyStateUpdate();
            return;
        } else {
            resp.setResponderCredentials(creds);
        }

        // Validate credentials
        // From the API configuration (remote peers) and SI configuration (remote peer),
        // check remote peer and check if authentication must be used.
        // If so, verify credentials.
        if (!authenticate(pdu.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
            doPeerAbort(PeerAbortReasonEnum.ACCESS_DENIED);
            return;
        }

        // All fine, check what to do with the bind
        if (this.sendPositiveUnbindReturn) {
            // Inform TML that this will be the last one, disconnection will follow soon
            this.tmlChannel.aboutToDisconnect();
            // Send response
            resp.getResult().setPositive(new BerNull());
            encodeAndSend(null, resp, UNBIND_RETURN_NAME);
            // Update state
            setServiceInstanceState(ServiceInstanceBindingStateEnum.UNBOUND);
            notifyPduSent(pdu, UNBIND_RETURN_NAME, getLastPduSent());
            // We disconnect and wait again for bind if the reason was SUSPEND
            disconnect(null);
            if (pdu.getUnbindReason().intValue() == UnbindReasonEnum.SUSPEND.getCode()) {
                waitForBind(this.sendPositiveBindReturn, this.negativeBindReturnDiagnostics);
            }
        } else {
            // Do nothing
            if (LOG.isLoggable(Level.WARNING)) {
                LOG.log(Level.WARNING, String.format("%s: Received UNBIND but service instance not configured to respond", getServiceInstanceIdentifier()));
            }
        }

        // Generate state and notify update
        notifyStateUpdate();
    }

    private void handleSleBindReturn(SleBindReturn pdu) {
        clearError();

        // Validate state
        if (this.currentState != ServiceInstanceBindingStateEnum.BIND_PENDING) {
            disconnect("Bind return received, but service instance is in state " + this.currentState);
            notifyPduReceived(pdu, BIND_RETURN_NAME, getLastPduReceived());
            notifyStateUpdate();
            return;
        }
        // Validate operation attributes
        if (pdu.getResponderIdentifier() == null || pdu.getResponderIdentifier().value == null) {
            disconnect("Bind return received, but no responder identifier set");
            notifyPduReceived(pdu, BIND_RETURN_NAME, getLastPduReceived());
            notifyStateUpdate();
            return;
        }
        String responderId = pdu.getResponderIdentifier().toString();
        if (!responderId.equals(this.serviceInstanceConfiguration.getResponderIdentifier())) {
            disconnect("Bind return received, but responder identifier does not match: expected "
                    + this.serviceInstanceConfiguration.getResponderIdentifier() + ", got " + responderId);
            notifyPduReceived(pdu, BIND_RETURN_NAME, getLastPduReceived());
            notifyStateUpdate();
            return;
        }

        // Validate credentials
        // From the API configuration (remote peers) and SI configuration (remote peer),
        // check remote peer and check if authentication must be used.
        // If so, verify credentials.
        if (!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL, AuthenticationModeEnum.BIND)) {
            disconnect("Bind return received, but wrong credentials");
            notifyPduReceived(pdu, BIND_RETURN_NAME, getLastPduReceived());
            notifyStateUpdate();
            return;
        }

        // Cancel timer task for bind operation
        cancelReturnTimeout(BIND_INTERNAL_INVOKE_ID);

        // If all fine (result positive), transition to new state: BOUND and notify PDU
        // received
        if (pdu.getResult().getPositive() != null) {
            setServiceInstanceState(ServiceInstanceBindingStateEnum.READY);
        } else {
            // If problems (result negative), UNBOUND, disconnect, cleanup
            disconnect("Bind return received, negative result: "
                    + BindDiagnosticsEnum.getBindDiagnostics(pdu.getResult().getNegative().intValue()));
        }
        // Notify PDU
        notifyPduReceived(pdu, BIND_RETURN_NAME, getLastPduReceived());
        // Generate state and notify update
        notifyStateUpdate();
    }

    /**
     * This method disconnects or abort the TML channel, frees up the resources and shutdowns the internal dispatcher
     * thread, hence making this instance not usable anymore.
     */
    public void dispose() {
        dispatchFromUser(this::doDispose);
        this.dispatcher.shutdown();
        try {
            this.dispatcher.awaitTermination(5000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOG.log(Level.WARNING, getServiceInstanceIdentifier() + ": problem while waiting for full disposal", e);
        }
    }

    private void doDispose() {
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, getServiceInstanceIdentifier() + ": Dispose requested");
        }
        // Disconnect if UNBOUND/UNBOUND_WAIT, PEER-ABORT if !UNBOUND/UNBOUND_WAIT
        if (getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND ||
                getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND_WAIT) {
            disconnect(null);
        } else {
            doPeerAbort(PeerAbortReasonEnum.OPERATIONAL_REQUIREMENTS);
        }
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, getServiceInstanceIdentifier() + ": Dispose completed");
        }
    }

    protected void disconnect(String reason) {
        disconnect(reason, null, null);
    }

    private void disconnect(String reason, Exception e, PeerAbortReasonEnum peerAbortReason) {
        if (reason != null && LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, getServiceInstanceIdentifier() + ": Disconnection with reason detected: " + reason, e);
        }
        setServiceInstanceState(ServiceInstanceBindingStateEnum.UNBOUND);
        this.expectConnectionToBeClosed = true;
        if (this.tmlChannel != null) {
            if (reason != null) {
                setError(reason, e);
                this.tmlChannel.abort(Objects.requireNonNullElse(peerAbortReason, PeerAbortReasonEnum.OTHER_REASON).getCode());
            } else {
                this.tmlChannel.aboutToDisconnect();
                this.tmlChannel.disconnect();
            }
        }
        this.sleVersion = null;
        this.tmlChannel = null;
        this.invokeId2timeout.values().forEach(TimerTask::cancel);
        this.invokeId2timeout.clear();
        this.statusReportScheduled = false;

        // Init mode reset
        this.initMode = SI_INIT_MODE_NOT_SELECTED;

        // Allow child classes to clean up
        resetState();
    }

    @Override
    public void onChannelConnected(TmlChannel channel) {
        if (LOG.isLoggable(Level.INFO)) {
            LOG.info(String.format("%s: TML channel %s connected", getServiceInstanceIdentifier(), channel));
        }
    }

    @Override
    public void onChannelDisconnected(TmlChannel channel, TmlDisconnectionReasonEnum reason, PeerAbortReasonEnum peerAbortReason) {
        dispatchFromUser(() -> {
            if (channel != tmlChannel) {
                // Old event, to be ignored
                if (LOG.isLoggable(Level.FINER)) {
                    LOG.log(Level.FINER, getServiceInstanceIdentifier() + ": Ignoring disconnection of TML channel " + channel + ", not current channel");
                }
                return;
            }
            clearError();

            // If disconnection is expected, close the channel and cleanup.
            if (this.expectConnectionToBeClosed) {
                disconnect(null);
            } else {
                // Otherwise, set the error
                disconnect("Unexpected disconnection detected: " + reason.name() + (Objects.isNull(peerAbortReason) ? "" : ", reason " + peerAbortReason));
            }
            // Generate state and notify update
            notifyStateUpdate();
        });
    }

    @Override
    public void onPduReceived(TmlChannel channel, final byte[] pdu) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine(getServiceInstanceIdentifier() + ": PDU received: " + PduStringUtil.instance().toHexDump(pdu));
        }
        dispatchFromProvider(() -> {
            clearError();
            // See package encdec
            Object op;
            try {
                op = decodePdu(pdu);
            } catch (IOException e) {
                disconnect("Exception while decoding PDU " + Arrays.toString(pdu) + " from channel " + channel, e,
                        null);
                //
                notifyPduDecodingError(pdu);
                // Generate state and notify update
                notifyStateUpdate();
                return;
            }
            // At this stage, op cannot be null

            // Use a map <class 2 runnable> to process the PDU accordingly to its type. This
            // must be in this class, and handlers must be registered by children classes in the setup.
            @SuppressWarnings("unchecked")
            Consumer<? super Object> c = (Consumer<Object>) getHandler(op.getClass());
            if (c != null) {
                this.statsCounter.addIn(1);
                this.lastPduReceived = pdu;
                c.accept(op);
            } else {
                disconnect("No handler to handle received PDU " + op.getClass().getSimpleName() + " from channel "
                        + channel);
                //
                notifyPduHandlingError(op, pdu);
                // Generate state and notify update
                notifyStateUpdate();
            }
        });
    }

    /**
     * This method returns the service instance identifier.
     *
     * @return the service instance identifier
     */
    public final String getServiceInstanceIdentifier() {
        return this.serviceInstanceConfiguration.getServiceInstanceIdentifier();
    }

    /**
     * This method returns the responder port identifier.
     *
     * @return the responder port identifier
     */
    public final String getResponderPortIdentifier() {
        return this.serviceInstanceConfiguration.getResponderPortIdentifier();
    }

    /**
     * This method returns the initiator identifier.
     *
     * @return the initiator identifier
     */
    public final String getInitiatorIdentifier() {
        return this.serviceInstanceConfiguration.getInitiatorIdentifier();
    }

    /**
     * This method returns the responder identifier.
     *
     * @return the responder identifier
     */
    public final String getResponderIdentifier() {
        return this.serviceInstanceConfiguration.getResponderIdentifier();
    }

    /**
     * This method returns the return timeout period.
     *
     * @return the return timeout period
     */
    public final int getReturnTimeoutPeriod() {
        return this.serviceInstanceConfiguration.getReturnTimeoutPeriod();
    }

    protected void copyCommonState(ServiceInstanceState state) {
        state.setInitiatorIdentifier(getInitiatorIdentifier());
        state.setResponderIdentifier(getResponderIdentifier());
        state.setResponderPortIdentifier(getResponderPortIdentifier());
        state.setServiceInstanceIdentifier(getServiceInstanceIdentifier());
        state.setSleVersion(getSleVersion());
        state.setReturnTimeoutPeriod(getReturnTimeoutPeriod());
        state.setState(currentState);
        state.setStatusReportScheduled(statusReportScheduled);
        state.setLastError(lastErrorMessage);
        state.setLastException(lastErrorException);
    }

    /**
     * This method is used to compute the current rate of the service instance. It is important to understand that
     * invocations of this method drive the sample rate. If this method is invoked very fast (i.e. two immediate sub
     * sequent calls), the second call might receive a misleading result (possibly 0 units/set).
     *
     * @return the rate sample (related to SLE PDUs and bytes)
     */
    public RateSample getCurrentRate() {
        Instant instant = Instant.now();
        DataRateSample pdus = this.statsCounter.sample();
        DataRateSample datas;
        if (this.tmlChannel != null) {
            datas = this.tmlChannel.getDataRate();
        } else {
            datas = new DataRateSample(new Date(), 0, 0, 0, 0);
        }
        return new RateSample(instant, pdus, datas);
    }

    /**
     * This method returns the current service instance state.
     *
     * @return the service instance state
     */
    public ServiceInstanceBindingStateEnum getCurrentBindingState() {
        return this.currentState;
    }

    /**
     * Configure this service instance to accept operations.
     */
    public void configure() {
        if (this.configured) {
            throw new IllegalStateException("Service instance already configured");
        }
        this.configured = true;
        resetState();
    }

    protected abstract void setup();

    protected abstract ServiceInstanceState buildCurrentState();

    protected abstract Object decodePdu(byte[] pdu) throws IOException;

    protected abstract byte[] encodePdu(BerType pdu) throws IOException;

    protected abstract void updateHandlersForVersion(int version);

    protected abstract void resetState();

    /**
     * This method returns the service type.
     *
     * @return the service type
     */
    public abstract ApplicationIdentifierEnum getApplicationIdentifier();

    /**
     * This internal task (a {@link FutureTask} extension that returns Void) is used to schedule an operation to be
     * executed by the dispatcher thread. The dispatcher uses a priority blocking queue, and the priority is provided
     * by the following hierarchy:
     * <ul>
     *    <li>the request type: a user request always has precedence compared to a remote peer request (PDU reception).
     *    This design decision allows to remove backlog processing on the dispatcher queue (e.g. due to high data rate)
     *    and to process immediately a user request (e.g. a STOP request)</li>
     *    <li>the creation time: if two requests have the same type, then the one scheduled before will be executed
     *    before</li>
     *    <li>the task hashcode: to prevent that two requests with the same type and the same creation time can be
     *    considered equal from the point of view of the priority blocking queue comparator</li>
     * </ul>
     */
    private class SleTask extends FutureTask<Void> implements Comparable<SleTask> {

        private static final byte FROM_USER_TYPE = 0x00;
        private static final byte FROM_PROVIDER_TYPE = 0x01;

        private final long creation;
        private final byte type;
        private final Runnable task;

        SleTask(byte type, Runnable task) {
            super(task, null);
            this.type = type;
            this.task = task;
            this.creation = System.currentTimeMillis();
        }

        @Override
        public int compareTo(SleTask o) {
            if (this.type != o.type) {
                return this.type - o.type;
            } else if (this.creation != o.creation) {
                return (int) (this.creation - o.creation);
            } else {
                return this.task.hashCode() - o.task.hashCode();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SleTask sleTask = (SleTask) o;
            return creation == sleTask.creation &&
                    type == sleTask.type &&
                    task.equals(sleTask.task);
        }

        @Override
        public int hashCode() {
            return Objects.hash(creation, type, task);
        }
    }
}
