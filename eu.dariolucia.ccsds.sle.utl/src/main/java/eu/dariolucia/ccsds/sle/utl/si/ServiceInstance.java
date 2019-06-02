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
import eu.dariolucia.ccsds.sle.utl.config.network.PortMapping;
import eu.dariolucia.ccsds.sle.utl.config.network.RemotePeer;
import eu.dariolucia.ccsds.sle.utl.config.ServiceInstanceConfiguration;
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

// TODO move here stop invocation, stop return handling, schedule status report, schedule status report return handling
public abstract class ServiceInstance implements ITmlChannelObserver {

	private static final int BIND_INTERNAL_INVOKE_ID = -1;
	private static final int UNBIND_INTERNAL_INVOKE_ID = -2;

	private static final int SI_INIT_MODE_NOT_SELECTED = 0;
	private static final int SI_INIT_MODE_UIB = 1;
	private static final int SI_INIT_MODE_PIB = 2;

	private static final Logger LOG = Logger.getLogger(ServiceInstance.class.getName());

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
		dispatchFromUser(this::setup);
	}

	protected final <T> void registerPduReceptionHandler(Class<T> clazz, Consumer<T> handler) {
		this.handlers.put(clazz, handler);
	}

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

	protected final void setError(Exception e) {
		setError(e.getMessage(), e);
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

	private Integer getSleVersion() {
		return this.sleVersion;
	}

	protected final void dispatchFromUser(Runnable r) {
		SleTask t = new SleTask(SleTask.FROM_USER_TYPE, r);
		this.dispatcher.execute(t);
	}

	private void dispatchFromProvider(Runnable r) {
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
								getServiceInstanceIdentifier() + ": Service instance cannot notify listener " + l, e);
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
							getServiceInstanceIdentifier() + ": Service instance cannot notify listener " + l, e);
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
							getServiceInstanceIdentifier() + ": Service instance cannot notify listener " + l, e);
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
							getServiceInstanceIdentifier() + ": Service instance cannot notify listener " + l, e);
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
							getServiceInstanceIdentifier() + ": Service instance cannot notify listener " + l, e);
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
							getServiceInstanceIdentifier() + ": Service instance cannot notify listener " + l, e);
				}
			}
		});
	}

	public final void register(final IServiceInstanceListener l) {
		try {
			dispatchFromUser(() -> doRegister(l));
		} catch (Exception e) {
			LOG.log(Level.SEVERE, getServiceInstanceIdentifier() + ": Listener cannot be registered: " + l, e);
		}
	}

	private void doRegister(final IServiceInstanceListener l) {
		final ServiceInstanceState state = buildCurrentState();
		this.listeners.add(l);
		notify(() -> l.onStateUpdated(this, state));
	}

	public final void deregister(final IServiceInstanceListener l) {
		try {
			dispatchFromUser(() -> doDeregister(l));
		} catch (Exception e) {
			LOG.log(Level.SEVERE, getServiceInstanceIdentifier() + ": Listener cannot deregistered: " + l, e);
		}
	}

	private void doDeregister(IServiceInstanceListener l) {
		this.listeners.remove(l);
	}

	public final void setBindReturnBehaviour(boolean sendPositiveBindReturn,
			BindDiagnosticsEnum negativeBindReturnDiagnostics) {
		dispatchFromUser(() -> {
			this.sendPositiveBindReturn = sendPositiveBindReturn;
			this.negativeBindReturnDiagnostics = negativeBindReturnDiagnostics;
			LOG.log(Level.INFO,
					getServiceInstanceIdentifier() + ": BIND-RETURN behaviour updated: send positive BIND-RETURN="
							+ sendPositiveBindReturn + ", negative diagnostics=" + negativeBindReturnDiagnostics);
		});
	}

	public final void setUnbindReturnBehaviour(boolean sendPositiveUnbindReturn) {
		dispatchFromUser(() -> {
			this.sendPositiveUnbindReturn = sendPositiveUnbindReturn;
			LOG.log(Level.INFO,
					getServiceInstanceIdentifier() + ": UNBIND-RETURN behaviour updated: send positive BIND-RETURN="
							+ sendPositiveUnbindReturn);
		});
	}

	public final void waitForBind(boolean sendPositiveBindReturn, BindDiagnosticsEnum negativeBindReturnDiagnostics) {
		dispatchFromUser(() -> doWaitForBind(sendPositiveBindReturn, negativeBindReturnDiagnostics));
	}

	private void doWaitForBind(boolean sendPositiveBindReturn, BindDiagnosticsEnum negativeBindReturnDiagnostics) {
		clearError();

		//
		if (this.initMode != SI_INIT_MODE_NOT_SELECTED) {
			setError(getServiceInstanceIdentifier()
					+ ": Wait for bind requested, but service instance is not in clean init mode");
			notifyStateUpdate();
			return;
		}

		if (this.serviceInstanceConfiguration.getInitiator() == InitiatorRoleEnum.USER) {
			setError(getServiceInstanceIdentifier()
					+ ": Wait for bind requested, but service instance initiator is set to USER");
			notifyStateUpdate();
			return;
		}

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.UNBOUND) {
			setError(getServiceInstanceIdentifier() + ": Wait for bind requested, but service instance is in state "
					+ this.currentState);
			notifyStateUpdate();
			return;
		}

		// Set the invokeId sequencer to 0
		this.invokeIdSequencer.set(0);

		// Establish connection, just open the port
		// From the responder port, go to API configuration and check the
		// foreign local port and the related IP address.
		Optional<PortMapping> port = this.peerConfiguration.getPortMappings().stream()
				.filter((flp) -> flp.getPortName().equals(getResponderPortIdentifier())).findFirst();
		if (port.isPresent()) {
			// Create a new TML channel
			try {
				this.tmlChannel = TmlChannel.createServerTmlChannel(port.get().getRemotePort(), this);
			} catch (TmlChannelException e) {
				setError("TML channel cannot be created: " + e.getMessage(), e);
				notifyStateUpdate();
				return;
			}
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
			setServiceInstanceState(ServiceInstanceBindingStateEnum.UNBOUND);
			disconnect("Cannot wait for connection", e, null);
		}

		// Generate state and notify update
		notifyStateUpdate();
	}

	public final void bind(int version) {
		dispatchFromUser(() -> doBind(version));
	}

	private void doBind(int version) {
		clearError();

		//
		if (this.initMode != SI_INIT_MODE_NOT_SELECTED) {
			setError(getServiceInstanceIdentifier()
					+ ": Bind requested, but service instance is not in clean init mode");
			notifyStateUpdate();
			return;
		}

		if (this.serviceInstanceConfiguration.getInitiator() == InitiatorRoleEnum.PROVIDER) {
			setError(getServiceInstanceIdentifier()
					+ ": Bind requested, but service instance initiator is set to PROVIDER");
			notifyStateUpdate();
			return;
		}

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.UNBOUND) {
			setError(getServiceInstanceIdentifier() + ": Bind requested, but service instance is in state "
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
			notifyPduSentError(pdu, "BIND", null);
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
				.filter((flp) -> flp.getPortName().equals(getResponderPortIdentifier())).findFirst();
		if (port.isPresent()) {
			// Create a new TML channel
			this.tmlChannel = TmlChannel.createClientTmlChannel(port.get().getRemoteHost(),
					port.get().getRemotePort(), port.get().getHeartbeatInterval(), port.get().getDeadFactor(), this);
		} else {
			setError("Foreign local port " + getResponderPortIdentifier()
					+ " not found in the SLE configuration file for service instance "
					+ getServiceInstanceIdentifier());
			notifyPduSentError(pdu, "BIND", null);
			notifyStateUpdate();
			return;
		}

		// Go for connection
		try {
			this.tmlChannel.connect();
		} catch (TmlChannelException e) {
			disconnect("Cannot connect", e, null);
			notifyPduSentError(pdu, "BIND", null);
			notifyStateUpdate();
			return;
		}

		boolean resultOk = encodeAndSend(BIND_INTERNAL_INVOKE_ID, pdu, "BIND");

		if (resultOk) {
			// If all fine, transition to new state: BIND_PENDING and notify PDU sent
			setServiceInstanceState(ServiceInstanceBindingStateEnum.BIND_PENDING);
			notifyPduSent(pdu, "BIND", getLastPduSent());

			// Init mode to be set
			this.initMode = SI_INIT_MODE_UIB;

			// Generate state and notify update
			notifyStateUpdate();
		}
	}

	private void startReturnTimeout(final long opInvokeId, Object pdu) {
		if(LOG.isLoggable(Level.FINE)) {
			LOG.fine(getServiceInstanceIdentifier() + ": Return timeout started for invokeId=" + opInvokeId + ", operation: " + pdu);
		}
		TimerTask tt = new TimerTask() {
			@Override
			public void run() {
				try {
					dispatchFromUser(() -> returnTimeoutExpired(opInvokeId));
				} catch (Exception e) {
					LOG.log(Level.WARNING, getServiceInstanceIdentifier()
							+ ": Cannot run return timeout expire task for operation with invokeId=" + opInvokeId, e);
				}
			}
		};
		this.invokeId2timeout.put(opInvokeId, tt);
		this.returnTimeoutTimer.schedule(tt, this.serviceInstanceConfiguration.getReturnTimeoutPeriod() * 1000);
	}

	private void returnTimeoutExpired(long opInvokeId) {
		LOG.warning(getServiceInstanceIdentifier() + ": Return timeout expired for invokeId=" + opInvokeId);
		TimerTask tt = this.invokeId2timeout.remove(opInvokeId);
		if (tt != null) {
			disconnect("Return timeout expired for operation with invokeId=" + opInvokeId);
			// Generate state and notify update
			notifyStateUpdate();
		}
	}

	protected void cancelReturnTimeout(long opInvokeId) {
		LOG.fine(getServiceInstanceIdentifier() + ": Return timeout cancelled for invokeId=" + opInvokeId);
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
			LOG.info(getServiceInstanceIdentifier() + ": PDU " + invokeStr + name + " encoded: "
					+ PduStringUtil.instance().toHexDump(encodedPdu));
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
				.filter((rp) -> rp.getId().equals(responderIdentifier)).findFirst();
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
		if(newState != this.currentState) {
			LOG.log(Level.INFO, getServiceInstanceIdentifier() + ": State transition from " + this.currentState + " to " + newState);
			this.currentState = newState;
		}
	}

	public final void unbind(UnbindReasonEnum reason) {
		dispatchFromUser(() -> doUnbind(reason));
	}

	private void doUnbind(UnbindReasonEnum reason) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.READY) {
			setError(getServiceInstanceIdentifier() + ": Unbind requested, but service instance is in state "
					+ this.currentState);
			notifyStateUpdate();
			return;
		}

		//
		if (this.initMode != SI_INIT_MODE_UIB) {
			setError(getServiceInstanceIdentifier()
					+ ": Unbind requested, but service instance is not in init mode UIB");
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
			notifyPduSentError(pdu, "UNBIND", null);
			notifyStateUpdate();
			return;
		} else {
			pdu.setInvokerCredentials(creds);
		}

		boolean resultOk = encodeAndSend(UNBIND_INTERNAL_INVOKE_ID, pdu, "UNBIND");

		if (resultOk) {
			// If all fine, transition to new state: UNBIND_PENDING and notify PDU sent
			setServiceInstanceState(ServiceInstanceBindingStateEnum.UNBIND_PENDING);
			notifyPduSent(pdu, "UNBIND", getLastPduSent());

			// Generate state and notify update
			notifyStateUpdate();
		}
	}

	public final void peerAbort(PeerAbortReasonEnum reason) {
		dispatchFromUser(() -> doPeerAbort(reason));
	}

	private void doPeerAbort(PeerAbortReasonEnum reason) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND) {
			setError(getServiceInstanceIdentifier() + ": Peer abort requested, but service instance is in state "
					+ this.currentState);
			notifyStateUpdate();
			return;
		}

		disconnect("Peer abort requested", null, reason);

		notifyStateUpdate();
	}

	private void handleSleUnbindReturn(SleUnbindReturn pdu) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.UNBIND_PENDING) {
			disconnect("Unbind return received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, "UNBIND-RETURN", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if (!authenticate(pdu.getResponderCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Unbind return received, but wrong credentials");
			notifyPduReceived(pdu, "UNBIND-RETURN", getLastPduReceived());
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
		notifyPduReceived(pdu, "UNBIND-RETURN", getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected final boolean authenticate(Credentials responderCredentials, AuthenticationModeEnum... authModeEnums) {
		int authDelay = this.peerConfiguration.getAuthenticationDelay();
		Optional<RemotePeer> remotePeer = this.peerConfiguration.getRemotePeers().stream()
				.filter((a) -> a.getId().equals(getResponderIdentifier())).findFirst();
		if (remotePeer.isPresent()) {
			AuthenticationModeEnum requiredAuthMode = remotePeer.get().getAuthenticationMode();
			boolean credentialsRequired = requiredAuthMode == AuthenticationModeEnum.ALL
					|| (requiredAuthMode == AuthenticationModeEnum.BIND
							&& Arrays.asList(authModeEnums).contains(AuthenticationModeEnum.BIND));
			if (credentialsRequired && responderCredentials.getUnused() != null) {
				// Credential required, but they are set as unused
				LOG.severe(() -> getServiceInstanceIdentifier()
						+ ": Credential required but the received operation does not provide them");
				return false;
			} else if (!credentialsRequired && responderCredentials.getUsed() != null) {
				// No credentials required, but they are present
				LOG.severe(() -> getServiceInstanceIdentifier()
						+ ": Credential not required but the received operation provides them");
				return false;
			} else if (!credentialsRequired) {
				// No credentials required
				LOG.fine(() -> getServiceInstanceIdentifier()
						+ ": Credential not required, operation credentials empty, ok");
				return true;
			} else {
				byte[] encodedCredentials = responderCredentials.getUsed().value;
				boolean result = PduFactoryUtil.performAuthentication(remotePeer.get(), encodedCredentials, authDelay);
				if (!result) {
					LOG.severe(
							() -> getServiceInstanceIdentifier() + ": Credential check failed on provided credentials");
				}
				return result;
			}
		} else {
			LOG.severe(() -> getServiceInstanceIdentifier() + ": Credentials for remote peer "
					+ getResponderIdentifier() + " not present");
			return false;
		}
	}

	private void handleSleBindInvocation(SleBindInvocation pdu) {
		clearError();

		// Notify the reception on the PDU
		notifyPduReceived(pdu, "BIND", getLastPduReceived());

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
			notifyPduSentError(pdu, "BIND-RETURN", null);
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
			encodeAndSend(null, resp, "BIND-RETURN");
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
			encodeAndSend(null, resp, "BIND-RETURN");
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
			encodeAndSend(null, resp, "BIND-RETURN");
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
			encodeAndSend(null, resp, "BIND-RETURN");
			// Update state
			setServiceInstanceState(ServiceInstanceBindingStateEnum.READY);
		} else {
			// Send response
			resp.getResult().setNegative(new BindDiagnostic(this.negativeBindReturnDiagnostics.getCode()));
			encodeAndSend(null, resp, "BIND-RETURN");
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
		notifyPduReceived(pdu, "UNBIND", getLastPduReceived());

		// Validate mode
		if (this.initMode != SI_INIT_MODE_PIB) {
			// Disconnect
			disconnect("Unbind invocation received, but service instance is not in PIB mode");
			notifyStateUpdate();
			return;
		}

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
			notifyPduSentError(pdu, "UNBIND-RETURN", null);
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
			// Send response
			resp.getResult().setPositive(new BerNull());
			encodeAndSend(null, resp, "UNBIND-RETURN");
			// Update state
			setServiceInstanceState(ServiceInstanceBindingStateEnum.UNBOUND);
		} else {
			// Do nothing
			LOG.log(Level.WARNING, getServiceInstanceIdentifier()
					+ ": Received UNBIND but service instance not configured to respond");
		}

		// Generate state and notify update
		notifyStateUpdate();
	}

	private void handleSleBindReturn(SleBindReturn pdu) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.BIND_PENDING) {
			disconnect("Bind return received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, "BIND-RETURN", getLastPduReceived());
			notifyStateUpdate();
			return;
		}
		// Validate operation attributes
		if (pdu.getResponderIdentifier() == null || pdu.getResponderIdentifier().value == null) {
			disconnect("Bind return received, but no responder identifier set");
			notifyPduReceived(pdu, "BIND-RETURN", getLastPduReceived());
			notifyStateUpdate();
			return;
		}
		String responderId = pdu.getResponderIdentifier().toString();
		if (!responderId.equals(this.serviceInstanceConfiguration.getResponderIdentifier())) {
			disconnect("Bind return received, but responder identifier does not match: expected "
					+ this.serviceInstanceConfiguration.getResponderIdentifier() + ", got " + responderId);
			notifyPduReceived(pdu, "BIND-RETURN", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if (!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL, AuthenticationModeEnum.BIND)) {
			disconnect("Bind return received, but wrong credentials");
			notifyPduReceived(pdu, "BIND-RETURN", getLastPduReceived());
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
		notifyPduReceived(pdu, "BIND-RETURN", getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void disconnect(String reason) {
		disconnect(reason, null, null);
	}

	private void disconnect(String reason, Exception e, PeerAbortReasonEnum peerAbortReason) {
		if (reason != null) {
			LOG.log(Level.FINER, getServiceInstanceIdentifier() + ": Disconnection with reason detected: " + reason, e);
		}
		setServiceInstanceState(ServiceInstanceBindingStateEnum.UNBOUND);
		this.expectConnectionToBeClosed = true;
		if (reason != null) {
			setError(reason, e);
			this.tmlChannel.abort(Objects.requireNonNullElse(peerAbortReason, PeerAbortReasonEnum.OTHER_REASON).getCode());
		} else {
			this.tmlChannel.aboutToDisconnect();
			this.tmlChannel.disconnect();
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
		LOG.info(getServiceInstanceIdentifier() + ": TML channel " + channel + " connected");
	}

	@Override
	public void onChannelDisconnected(TmlChannel channel, TmlDisconnectionReasonEnum reason) {
		dispatchFromUser(() -> {
			clearError();

			// If disconnection is expected, close the channel and cleanup.
			if (this.expectConnectionToBeClosed) {
				disconnect(null);
			} else {
				// Otherwise, set the error
				disconnect("Unexpected disconnection detected: " + reason.name());
			}
			// Generate state and notify update
			notifyStateUpdate();
		});
	}

	@Override
	public void onPduReceived(TmlChannel channel, final byte[] pdu) {
		if(LOG.isLoggable(Level.FINE)) {
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

			if (op == null) {
				disconnect("Decoded PDU is null: " + Arrays.toString(pdu) + " from channel " + channel);
				//
				notifyPduDecodingError(pdu);
				// Generate state and notify update
				notifyStateUpdate();
				return;
			}
			// Use a map <class 2 runnable> to process the PDU accordingly to its type. This
			// must be in this class, and
			// handlers must be registered by children classes in the setup.
			@SuppressWarnings("unchecked")
			Consumer<Object> c = (Consumer<Object>) getHandler(op.getClass());
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

	public final String getServiceInstanceIdentifier() {
		return this.serviceInstanceConfiguration.getServiceInstanceIdentifier();
	}

	public final String getResponderPortIdentifier() {
		return this.serviceInstanceConfiguration.getResponderPortIdentifier();
	}

	public final String getInitiatorIdentifier() {
		return this.serviceInstanceConfiguration.getInitiatorIdentifier();
	}

	public final String getResponderIdentifier() {
		return this.serviceInstanceConfiguration.getResponderIdentifier();
	}

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

	public ServiceInstanceBindingStateEnum getCurrentBindingState() {
		return this.currentState;
	}

	protected abstract void setup();

	protected abstract ServiceInstanceState buildCurrentState();

	protected abstract Object decodePdu(byte[] pdu) throws IOException;

	protected abstract byte[] encodePdu(BerType pdu) throws IOException;

	protected abstract void updateHandlersForVersion(int version);

	protected abstract void resetState();

	public abstract ApplicationIdentifierEnum getApplicationIdentifier();

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
	}
}
