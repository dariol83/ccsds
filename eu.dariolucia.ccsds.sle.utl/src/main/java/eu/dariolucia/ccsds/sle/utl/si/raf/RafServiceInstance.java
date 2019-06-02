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

package eu.dariolucia.ccsds.sle.utl.si.raf;

import com.beanit.jasn1.ber.types.BerNull;
import com.beanit.jasn1.ber.types.BerType;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.incoming.pdus.RafGetParameterInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.incoming.pdus.RafStartInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.structures.RafParameterName;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.structures.RequestedFrameQuality;
import eu.dariolucia.ccsds.sle.utl.config.PeerConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.raf.RafServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.encdec.RafEncDec;
import eu.dariolucia.ccsds.sle.utl.pdu.PduFactoryUtil;
import eu.dariolucia.ccsds.sle.utl.si.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

public class RafServiceInstance extends ServiceInstance {

	private static final Logger LOG = Logger.getLogger(RafServiceInstance.class.getName());

	// Read from configuration, updated via GET_PARAMETER
	private Integer latencyLimit; // NULL if offline, otherwise a value
	private List<RafRequestedFrameQualityEnum> permittedFrameQuality;
	private Integer minReportingCycle;
	private int returnTimeoutPeriod;
	private int transferBufferSize;
	private DeliveryModeEnum deliveryMode = null;
	
	// Updated via START and GET_PARAMETER
	private RafRequestedFrameQualityEnum requestedFrameQuality = null;
	private Integer reportingCycle = null; // NULL if off, otherwise a value
	private Date startTime = null;
	private Date endTime = null;
	
	// Updated via STATUS_REPORT
	private int errorFreeFrameNumber = 0;
	private int deliveredFrameNumber = 0;
	private LockStatusEnum frameSyncLockStatus = LockStatusEnum.UNKNOWN;
	private LockStatusEnum symbolSyncLockStatus = LockStatusEnum.UNKNOWN;
	private LockStatusEnum subcarrierLockStatus = LockStatusEnum.UNKNOWN;
	private LockStatusEnum carrierLockStatus = LockStatusEnum.UNKNOWN;
	private ProductionStatusEnum productionStatus = ProductionStatusEnum.UNKNOWN;

	// Encoder/decoder
	private final RafEncDec encDec = new RafEncDec();

	public RafServiceInstance(PeerConfiguration apiConfiguration,
                              RafServiceInstanceConfiguration serviceInstanceConfiguration) throws Exception {
		super(apiConfiguration, serviceInstanceConfiguration);
	}

	@Override
	protected void setup() {
		// Register handlers
		registerPduReceptionHandler(RafStartReturn.class, this::handleRafStartReturn);
		registerPduReceptionHandler(SleAcknowledgement.class, this::handleRafStopReturn);
		registerPduReceptionHandler(SleScheduleStatusReportReturn.class, this::handleRafScheduleStatusReportReturn);
		registerPduReceptionHandler(RafStatusReportInvocation.class, this::handleRafStatusReport);
		registerPduReceptionHandler(RafStatusReportInvocationV1toV2.class, this::handleRafStatusReportV1toV2);
		registerPduReceptionHandler(RafTransferBuffer.class, this::handleRafTransferBuffer);
		registerPduReceptionHandler(RafGetParameterReturn.class, this::handleRafGetParameterReturn);
		registerPduReceptionHandler(RafGetParameterReturnV1toV4.class, this::handleRafGetParameterV1toV4Return);

		// Update status parameters from configuration
		resetState();
	}

	public void start(Date start, Date end, RafRequestedFrameQualityEnum frameQuality) {
		dispatchFromUser(() -> doStart(start, end, frameQuality));
	}

	private void doStart(Date start, Date end, RafRequestedFrameQualityEnum frameQuality) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.READY) {
			setError("Start requested, but service instance is in state "
					+ this.currentState);
			notifyStateUpdate();
			return;
		}

		int invokeId = this.invokeIdSequencer.incrementAndGet();

		// Create operation
		RafStartInvocation pdu = new RafStartInvocation();
		pdu.setInvokeId(new InvokeId(invokeId));
		// start time
		pdu.setStartTime(new ConditionalTime());
		if (start == null) {
			pdu.getStartTime().setUndefined(new BerNull());
		} else {
			pdu.getStartTime().setKnown(new Time());
			pdu.getStartTime().getKnown().setCcsdsFormat(new TimeCCSDS());
			pdu.getStartTime().getKnown().getCcsdsFormat().value = PduFactoryUtil.buildCDSTime(start.getTime(), 0);
		}
		// stop time
		pdu.setStopTime(new ConditionalTime());
		if (end == null) {
			pdu.getStopTime().setUndefined(new BerNull());
		} else {
			pdu.getStopTime().setKnown(new Time());
			pdu.getStopTime().getKnown().setCcsdsFormat(new TimeCCSDS());
			pdu.getStopTime().getKnown().getCcsdsFormat().value = PduFactoryUtil.buildCDSTime(end.getTime(), 0);
		}
		// Frame quality
		pdu.setRequestedFrameQuality(new RequestedFrameQuality(frameQuality.getCode()));

		// Add credentials
		// From the API configuration (remote peers) and SI configuration (responder
		// id), check remote peer and check if authentication must be used.
		Credentials creds = generateCredentials(getResponderIdentifier(), AuthenticationModeEnum.ALL);
		if (creds == null) {
			// Error while generating credentials, set by generateCredentials()
			notifyPduSentError(pdu, "START", null);
			notifyStateUpdate();
			return;
		} else {
			pdu.setInvokerCredentials(creds);
		}

		boolean resultOk = encodeAndSend(invokeId, pdu, "START");

		if (resultOk) {
			// If all fine, transition to new state: START_PENDING and notify PDU sent
			setServiceInstanceState(ServiceInstanceBindingStateEnum.START_PENDING);
			// Set the requested GVCID
			this.requestedFrameQuality = frameQuality;
			// Set times
			this.startTime = start;
			this.endTime = end;
			// Notify PDU
			notifyPduSent(pdu, "START", getLastPduSent());

			// Generate state and notify update
			notifyStateUpdate();
		}
	}

	public void stop() {
		dispatchFromUser(() -> doStop());
	}

	private void doStop() {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.ACTIVE) {
			setError("Stop requested, but service instance is in state "
					+ this.currentState);
			notifyStateUpdate();
			return;
		}

		int invokeId = this.invokeIdSequencer.incrementAndGet();

		// Create operation
		SleStopInvocation pdu = new SleStopInvocation();
		pdu.setInvokeId(new InvokeId(invokeId));

		// Add credentials
		// From the API configuration (remote peers) and SI configuration (responder
		// id), check remote peer and check if authentication must be used.
		Credentials creds = generateCredentials(getResponderIdentifier(), AuthenticationModeEnum.ALL);
		if (creds == null) {
			// Error while generating credentials, set by generateCredentials()
			notifyPduSentError(pdu, "STOP", null);
			notifyStateUpdate();
			return;
		} else {
			pdu.setInvokerCredentials(creds);
		}

		boolean resultOk = encodeAndSend(invokeId, pdu, "STOP");
		
		if (resultOk) {
			// If all fine, transition to new state: STOP_PENDING and notify PDU sent
			setServiceInstanceState(ServiceInstanceBindingStateEnum.STOP_PENDING);
			notifyPduSent(pdu, "STOP", getLastPduSent());
			// Generate state and notify update
			notifyStateUpdate();
		}
	}

	public void scheduleStatusReport(boolean isStop, Integer period) {
		dispatchFromUser(() -> doScheduleStatusReport(isStop, period));
	}

	private void doScheduleStatusReport(boolean isStop, Integer period) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND
				|| this.currentState == ServiceInstanceBindingStateEnum.BIND_PENDING
				|| this.currentState == ServiceInstanceBindingStateEnum.UNBIND_PENDING) {
			setError("Schedule status report requested, but service instance is in state " + this.currentState);
			notifyStateUpdate();
			return;
		}

		int invokeId = this.invokeIdSequencer.incrementAndGet();

		// Create operation
		SleScheduleStatusReportInvocation pdu = new SleScheduleStatusReportInvocation();
		pdu.setInvokeId(new InvokeId(invokeId));
		//
		pdu.setReportRequestType(new ReportRequestType());
		if (isStop) {
			pdu.getReportRequestType().setStop(new BerNull());
		} else if (period != null) {
			pdu.getReportRequestType().setPeriodically(new ReportingCycle(period.intValue()));
		} else {
			pdu.getReportRequestType().setImmediately(new BerNull());
		}

		// Add credentials
		// From the API configuration (remote peers) and SI configuration (responder
		// id), check remote peer and check if authentication must be used.
		Credentials creds = generateCredentials(getResponderIdentifier(), AuthenticationModeEnum.ALL);
		if (creds == null) {
			// Error while generating credentials, set by generateCredentials()
			notifyPduSentError(pdu, "SCHEDULE-STATUS-REPORT", null);
			notifyStateUpdate();
			return;
		} else {
			pdu.setInvokerCredentials(creds);
		}

		boolean resultOk = encodeAndSend(invokeId, pdu, "SCHEDULE-STATUS-REPORT");

		if (resultOk) {
			// If all fine, notify PDU sent
			notifyPduSent(pdu, "SCHEDULE-STATUS-REPORT", getLastPduSent());

			// Generate state and notify update
			notifyStateUpdate();
		}
	}

	public void getParameter(RafParameterEnum parameter) throws Exception {
		dispatchFromUser(() -> doGetParameter(parameter));
	}

	private void doGetParameter(RafParameterEnum parameter) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND
				|| this.currentState == ServiceInstanceBindingStateEnum.BIND_PENDING
				|| this.currentState == ServiceInstanceBindingStateEnum.UNBIND_PENDING) {
			setError("Get parameter requested, but service instance is in state "
					+ this.currentState);
			notifyStateUpdate();
			return;
		}

		int invokeId = this.invokeIdSequencer.incrementAndGet();

		// Create operation
		RafGetParameterInvocation pdu = new RafGetParameterInvocation();
		pdu.setInvokeId(new InvokeId(invokeId));
		//
		pdu.setRafParameter(new RafParameterName(parameter.getCode()));

		// Add credentials
		// From the API configuration (remote peers) and SI configuration (responder
		// id), check remote peer and check if authentication must be used.
		Credentials creds = generateCredentials(getResponderIdentifier(), AuthenticationModeEnum.ALL);
		if (creds == null) {
			// Error while generating credentials, set by generateCredentials()
			notifyPduSentError(pdu, "GET-PARAMETER", null);
			notifyStateUpdate();
			return;
		} else {
			pdu.setInvokerCredentials(creds);
		}

		boolean resultOk = encodeAndSend(invokeId, pdu, "GET-PARAMETER");

		if (resultOk) {
			// If all fine, notify PDU sent
			notifyPduSent(pdu, "GET-PARAMETER", getLastPduSent());

			// Generate state and notify update
			notifyStateUpdate();
		}
	}

	protected void handleRafGetParameterReturn(RafGetParameterReturn pdu) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND
				|| this.currentState == ServiceInstanceBindingStateEnum.BIND_PENDING
				|| this.currentState == ServiceInstanceBindingStateEnum.UNBIND_PENDING) {
			disconnect("Get parameter return received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, "GET-PARAMETER-RETURN", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Get parameter return received, but wrong credentials");
			notifyPduReceived(pdu, "GET-PARAMETER-RETURN", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Cancel timer task for operation
		int invokeId = pdu.getInvokeId().intValue();
		cancelReturnTimeout(invokeId);

		// If all fine (result positive), update configuration parameter and notify
		// PDU received
		if (pdu.getResult().getPositiveResult() != null) {
			if (pdu.getResult().getPositiveResult().getParBufferSize() != null) {
				int val = pdu.getResult().getPositiveResult().getParBufferSize().getParameterValue().intValue();
				this.transferBufferSize = val;
			} else if (pdu.getResult().getPositiveResult().getParDeliveryMode() != null) {
				DeliveryModeEnum val = DeliveryModeEnum.values()[pdu.getResult().getPositiveResult()
						.getParDeliveryMode().getParameterValue().intValue()];
				this.deliveryMode = val;
			} else if (pdu.getResult().getPositiveResult().getParReturnTimeout() != null) {
				int val = pdu.getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue();
				this.returnTimeoutPeriod = val;
			} else if (pdu.getResult().getPositiveResult().getParLatencyLimit() != null) {
				if (pdu.getResult().getPositiveResult().getParLatencyLimit().getParameterValue().getOffline() != null) {
					this.latencyLimit = null;
				} else {
					int val = pdu.getResult().getPositiveResult().getParLatencyLimit().getParameterValue().getOnline()
							.intValue();
					this.latencyLimit = val;
				}
			} else if (pdu.getResult().getPositiveResult().getParReportingCycle() != null) {
				if (pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue()
						.getPeriodicReportingOff() != null) {
					this.reportingCycle = null; 
				} else {
					int val = pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue()
							.getPeriodicReportingOn().intValue();
					this.reportingCycle = val;
				}
			} else if (pdu.getResult().getPositiveResult().getParMinReportingCycle() != null) {
				if (pdu.getResult().getPositiveResult().getParMinReportingCycle().getParameterValue()
						!= null) {
					this.minReportingCycle = pdu.getResult().getPositiveResult().getParMinReportingCycle().getParameterValue().intValue(); 
				}
			} else if (pdu.getResult().getPositiveResult().getParReqFrameQuality() != null) {
				int code = pdu.getResult().getPositiveResult().getParReqFrameQuality().getParameterValue().intValue();
				this.requestedFrameQuality = RafRequestedFrameQualityEnum.fromCode(code);
			} else if (pdu.getResult().getPositiveResult().getParPermittedFrameQuality() != null) {
				this.permittedFrameQuality = new LinkedList<>();
				for(RequestedFrameQuality rfq : pdu.getResult().getPositiveResult().getParPermittedFrameQuality().getParameterValue().getRequestedFrameQuality()) {
					RafRequestedFrameQualityEnum reqs = RafRequestedFrameQualityEnum.fromCode(rfq.intValue());
					if(reqs != null) {
						this.permittedFrameQuality.add(reqs);
					}
				}
			} else {
				LOG.warning(getServiceInstanceIdentifier() + ": Get parameter return received, positive result but unknown/unsupported parameter found");
			}
		} else {
			// Dump warning with diagnostic
			LOG.warning(getServiceInstanceIdentifier() + ": Get parameter return received, negative result: "
					+ RafDiagnosticsStrings.getGetParameterDiagnostic(pdu.getResult().getNegativeResult()));

		}
		// Notify PDU
		notifyPduReceived(pdu, "GET-PARAMETER-RETURN", getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleRafGetParameterV1toV4Return(RafGetParameterReturnV1toV4 pdu) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND
				|| this.currentState == ServiceInstanceBindingStateEnum.BIND_PENDING
				|| this.currentState == ServiceInstanceBindingStateEnum.UNBIND_PENDING) {
			disconnect("Get parameter return received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, "GET-PARAMETER-RETURN", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Get parameter return received, but wrong credentials");
			notifyPduReceived(pdu, "GET-PARAMETER-RETURN", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Cancel timer task for operation
		int invokeId = pdu.getInvokeId().intValue();
		cancelReturnTimeout(invokeId);

		// If all fine (result positive), update configuration parameter and notify
		// PDU received
		if (pdu.getResult().getPositiveResult() != null) {
			if (pdu.getResult().getPositiveResult().getParBufferSize() != null) {
				int val = pdu.getResult().getPositiveResult().getParBufferSize().getParameterValue().intValue();
				this.transferBufferSize = val;
			} else if (pdu.getResult().getPositiveResult().getParDeliveryMode() != null) {
				DeliveryModeEnum val = DeliveryModeEnum.values()[pdu.getResult().getPositiveResult()
						.getParDeliveryMode().getParameterValue().intValue()];
				this.deliveryMode = val;
			} else if (pdu.getResult().getPositiveResult().getParReturnTimeout() != null) {
				int val = pdu.getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue();
				this.returnTimeoutPeriod = val;
			} else if (pdu.getResult().getPositiveResult().getParLatencyLimit() != null) {
				if (pdu.getResult().getPositiveResult().getParLatencyLimit().getParameterValue().getOffline() != null) {
					this.latencyLimit = null;
				} else {
					int val = pdu.getResult().getPositiveResult().getParLatencyLimit().getParameterValue().getOnline()
							.intValue();
					this.latencyLimit = val;
				}
			} else if (pdu.getResult().getPositiveResult().getParReportingCycle() != null) {
				if (pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue()
						.getPeriodicReportingOff() != null) { // this is the reporting cycle
					this.reportingCycle = null;
				} else {
					int val = pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue()
							.getPeriodicReportingOn().intValue();
					this.reportingCycle = val;
				}
			} else if (pdu.getResult().getPositiveResult().getParReqFrameQuality() != null) {
				int code = pdu.getResult().getPositiveResult().getParReqFrameQuality().getParameterValue().intValue();
				this.requestedFrameQuality = RafRequestedFrameQualityEnum.fromCode(code);
			} else {
				LOG.warning(getServiceInstanceIdentifier() + ": Get parameter return received, positive result but unknown/unsupported parameter found");
			}
		} else {
			// Dump warning with diagnostic
			LOG.warning(getServiceInstanceIdentifier() + ": Get parameter return received, negative result: "
					+ RafDiagnosticsStrings.getGetParameterDiagnostic(pdu.getResult().getNegativeResult()));

		}
		// Notify PDU
		notifyPduReceived(pdu, "GET-PARAMETER-RETURN", getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleRafStartReturn(RafStartReturn pdu) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.START_PENDING) {
			disconnect("Start return received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, "START-RETURN", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Start return received, but wrong credentials");
			notifyPduReceived(pdu, "START-RETURN", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Cancel timer task for operation
		int invokeId = pdu.getInvokeId().intValue();
		cancelReturnTimeout(invokeId);

		// If all fine (result positive), transition to new state: ACTIVE and notify
		// PDU received
		if (pdu.getResult().getPositiveResult() != null) {
			setServiceInstanceState(ServiceInstanceBindingStateEnum.ACTIVE);
		} else {
			LOG.warning(getServiceInstanceIdentifier() + ": Start return received, negative result: "
					+ RafDiagnosticsStrings.getStartDiagnostic(pdu.getResult().getNegativeResult()));
			// Reset requested frame quality
			this.requestedFrameQuality = null;
			// Set times
			this.startTime = null;
			this.endTime = null;
			
			// If problems (result negative), BOUND
			setServiceInstanceState(ServiceInstanceBindingStateEnum.READY);
		}
		// Notify PDU
		notifyPduReceived(pdu, "START-RETURN", getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleRafStopReturn(SleAcknowledgement pdu) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.STOP_PENDING) {
			disconnect("Stop return received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, "STOP-RETURN", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(pdu.getCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Stop return received, but wrong credentials");
			notifyPduReceived(pdu, "STOP-RETURN", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Cancel timer task for operation
		int invokeId = pdu.getInvokeId().intValue();
		cancelReturnTimeout(invokeId);

		// If all fine (result positive), transition to new state: BOUND and notify
		// PDU received
		if (pdu.getResult().getPositiveResult() != null) {
			setServiceInstanceState(ServiceInstanceBindingStateEnum.READY);
			// Reset requested frame quality
			this.requestedFrameQuality = null;
			// Set times
			this.startTime = null;
			this.endTime = null;
		} else {
			LOG.warning(getServiceInstanceIdentifier() + ": Stop return received, negative result: " + RafDiagnosticsStrings.getDiagnostic(pdu.getResult().getNegativeResult()));
			// If problems (result negative), ACTIVE
			setServiceInstanceState(ServiceInstanceBindingStateEnum.ACTIVE);
		}
		// Notify PDU
		notifyPduReceived(pdu, "STOP-RETURN", getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleRafStatusReport(RafStatusReportInvocation pdu) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND) {
			disconnect("Status report received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, "STATUS-REPORT", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(pdu.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Status report received, but wrong credentials");
			notifyPduReceived(pdu, "STATUS-REPORT", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Set the status report parameters
		this.carrierLockStatus = mapCarrierLockStatus(pdu.getCarrierLockStatus().intValue());
		this.subcarrierLockStatus = mapSubCarrierLockStatus(pdu.getSubcarrierLockStatus().intValue());
		this.symbolSyncLockStatus = mapSymbolSyncLockStatus(pdu.getSymbolSyncLockStatus().intValue());
		this.frameSyncLockStatus = mapFrameSyncLockStatus(pdu.getFrameSyncLockStatus().intValue());
		this.productionStatus = mapProductionStatus(pdu.getProductionStatus().intValue());
		this.errorFreeFrameNumber = pdu.getErrorFreeFrameNumber().intValue();
		this.deliveredFrameNumber = pdu.getDeliveredFrameNumber().intValue();

		// Notify PDU
		notifyPduReceived(pdu, "STATUS-REPORT", getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleRafTransferBuffer(RafTransferBuffer pdu) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.ACTIVE) {
			disconnect("Transfer buffer received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, "TRANSFER-BUFFER", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		for (FrameOrNotification fon : pdu.getFrameOrNotification()) {
			if (fon.getAnnotatedFrame() != null) {
				RafTransferDataInvocation tf = fon.getAnnotatedFrame();

				// Validate credentials
				// From the API configuration (remote peers) and SI configuration (remote peer),
				// check remote peer and check if authentication must be used.
				// If so, verify credentials.
				if(!authenticate(tf.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
					disconnect("Transfer data received, but wrong credentials");
					notifyPduReceived(pdu, "TRANSFER-DATA", getLastPduReceived());
					notifyStateUpdate();
					return;
				}

				// Notify PDU
				notifyPduReceived(tf, "TRANSFER-DATA", null);
			} else {
				RafSyncNotifyInvocation sn = fon.getSyncNotification();

				// Validate credentials
				// From the API configuration (remote peers) and SI configuration (remote peer),
				// check remote peer and check if authentication must be used.
				// If so, verify credentials.
				if(!authenticate(sn.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
					disconnect("Notify received, but wrong credentials");
					notifyPduReceived(pdu, "NOTIFY", getLastPduReceived());
					notifyStateUpdate();
					return;
				}

				if (sn.getNotification().getEndOfData() != null) {
					LOG.info(getServiceInstanceIdentifier() + ": End of data reported");
				} else if (sn.getNotification().getExcessiveDataBacklog() != null) {
					LOG.warning(getServiceInstanceIdentifier() + ": Data discarded due to excessive backlog");
				} else if (sn.getNotification().getLossFrameSync() != null) {
					this.carrierLockStatus = mapCarrierLockStatus(
							sn.getNotification().getLossFrameSync().getCarrierLockStatus().intValue());
					this.subcarrierLockStatus = mapSubCarrierLockStatus(
							sn.getNotification().getLossFrameSync().getSubcarrierLockStatus().intValue());
					this.symbolSyncLockStatus = mapSymbolSyncLockStatus(
							sn.getNotification().getLossFrameSync().getSymbolSyncLockStatus().intValue());
					LOG.warning(getServiceInstanceIdentifier() + ": Loss frame synchronisation");
				} else if (sn.getNotification().getProductionStatusChange() != null) {
					this.productionStatus = mapProductionStatus(
							sn.getNotification().getProductionStatusChange().intValue());
					LOG.info(
							getServiceInstanceIdentifier() + ": Production status changed to " + this.productionStatus);
				}
				// Notify PDU
				notifyPduReceived(sn, "NOTIFY", null);
			}
		}
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleRafStatusReportV1toV2(RafStatusReportInvocationV1toV2 pdu) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND) {
			disconnect("Status report received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, "STATUS-REPORT", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(pdu.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Status report received, but wrong credentials");
			notifyPduReceived(pdu, "STATUS-REPORT", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Set the status report parameters
		this.carrierLockStatus = mapCarrierLockStatus(pdu.getCarrierLockStatus().intValue());
		this.subcarrierLockStatus = mapSubCarrierLockStatus(pdu.getSubcarrierLockStatus().intValue());
		this.symbolSyncLockStatus = mapSymbolSyncLockStatus(pdu.getSymbolSyncLockStatus().intValue());
		this.frameSyncLockStatus = mapFrameSyncLockStatus(pdu.getFrameSyncLockStatus().intValue());
		this.productionStatus = mapProductionStatus(pdu.getProductionStatus().intValue());
		this.deliveredFrameNumber = pdu.getDeliveredFrameNumber().intValue();
		this.errorFreeFrameNumber = pdu.getErrorFreeFrameNumber().intValue();

		// Notify PDU
		notifyPduReceived(pdu, "STATUS-REPORT", getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleRafScheduleStatusReportReturn(SleScheduleStatusReportReturn pdu) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND) {
			disconnect("Schedule status report return received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, "SCHEDULE-STATUS-REPORT-RETURN", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Schedule status report return received, but wrong credentials");
			notifyPduReceived(pdu, "SCHEDULE-STATUS-REPORT-RETURN", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Cancel timer task for operation
		int invokeId = pdu.getInvokeId().intValue();
		cancelReturnTimeout(invokeId);

		// If all fine (result positive), set flag and notify
		// PDU received
		if (pdu.getResult().getPositiveResult() != null) {
			//
			LOG.info(getServiceInstanceIdentifier() + ": Schedule status report return received, positive result");
		} else {
			LOG.warning(getServiceInstanceIdentifier() + ": Schedule status report return received, negative result: "
					+ RafDiagnosticsStrings.getScheduleStatusReportDiagnostic(pdu.getResult().getNegativeResult()));
		}
		// Notify PDU
		notifyPduReceived(pdu, "SCHEDULE-STATUS-REPORT-RETURN", getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	private ProductionStatusEnum mapProductionStatus(int intValue) {
		switch (intValue) {
		case 0:
			return ProductionStatusEnum.RUNNING;
		case 1:
			return ProductionStatusEnum.INTERRUPTED;
		case 2:
			return ProductionStatusEnum.HALTED;
		default:
			return ProductionStatusEnum.UNKNOWN;
		}
	}

	private LockStatusEnum mapFrameSyncLockStatus(int intValue) {
		switch (intValue) {
		case 0:
			return LockStatusEnum.IN_LOCK;
		case 1:
			return LockStatusEnum.OUT_OF_LOCK;
		default:
			return LockStatusEnum.UNKNOWN;
		}
	}

	private LockStatusEnum mapSymbolSyncLockStatus(int intValue) {
		switch (intValue) {
		case 0:
			return LockStatusEnum.IN_LOCK;
		case 1:
			return LockStatusEnum.OUT_OF_LOCK;
		default:
			return LockStatusEnum.UNKNOWN;
		}
	}

	private LockStatusEnum mapSubCarrierLockStatus(int intValue) {
		switch (intValue) {
		case 0:
			return LockStatusEnum.IN_LOCK;
		case 1:
			return LockStatusEnum.OUT_OF_LOCK;
		case 2:
			return LockStatusEnum.NOT_IN_USE;
		default:
			return LockStatusEnum.UNKNOWN;
		}
	}

	private LockStatusEnum mapCarrierLockStatus(int intValue) {
		switch (intValue) {
		case 0:
			return LockStatusEnum.IN_LOCK;
		case 1:
			return LockStatusEnum.OUT_OF_LOCK;
		default:
			return LockStatusEnum.UNKNOWN;
		}
	}

	@Override
	protected ServiceInstanceState buildCurrentState() {
		RafServiceInstanceState state = new RafServiceInstanceState();
		copyCommonState(state);
		state.setCarrierLockStatus(carrierLockStatus);
		state.setDeliveryMode(deliveryMode);
		state.setFrameSyncLockStatus(frameSyncLockStatus);
		state.setLatencyLimit(latencyLimit);
		state.setMinReportingCycle(minReportingCycle);
		state.setDeliveredFrameNumber(deliveredFrameNumber);
		state.setErrorFreeFrameNumber(errorFreeFrameNumber);
		state.setPermittedFrameQuality(new ArrayList<>(permittedFrameQuality));
		state.setProductionStatus(productionStatus);
		state.setReportingCycle(reportingCycle);
		state.setRequestedFrameQuality(requestedFrameQuality);
		state.setSubcarrierLockStatus(subcarrierLockStatus);
		state.setSymbolSyncLockStatus(symbolSyncLockStatus);
		state.setTransferBufferSize(transferBufferSize);
		state.setReturnTimeoutPeriod(returnTimeoutPeriod);
		state.setStartTime(startTime);
		state.setEndTime(endTime);
		return state;
	}

	@Override
	protected Object decodePdu(byte[] pdu) throws IOException {
		return this.encDec.decode(pdu);
	}

	@Override
	protected byte[] encodePdu(BerType pdu) throws IOException {
		return this.encDec.encode(pdu);
	}

	@Override
	public ApplicationIdentifierEnum getApplicationIdentifier() {
		return ApplicationIdentifierEnum.RAF;
	}

	@Override
	protected void updateHandlersForVersion(int version) {
		this.encDec.useSleVersion(version);
	}

	@Override
	protected void resetState() {
		// Read from configuration, updated via GET_PARAMETER
		this.latencyLimit = getRafConfiguration().getLatencyLimit();
		this.permittedFrameQuality = getRafConfiguration().getPermittedFrameQuality();
		this.minReportingCycle = getRafConfiguration().getMinReportingCycle();
		this.returnTimeoutPeriod = getRafConfiguration().getReturnTimeoutPeriod();
		this.transferBufferSize = getRafConfiguration().getTransferBufferSize();
		this.deliveryMode = getRafConfiguration().getDeliveryMode();
		
		// Updated via START and GET_PARAMETER
		this.requestedFrameQuality = null;
		this.startTime = null;
		this.endTime = null;
		this.reportingCycle = null; // NULL if off, otherwise a value
		
		// Updated via STATUS_REPORT
		this.errorFreeFrameNumber = 0;
		this.deliveredFrameNumber = 0;
		this.frameSyncLockStatus = LockStatusEnum.UNKNOWN;
		this.symbolSyncLockStatus = LockStatusEnum.UNKNOWN;
		this.subcarrierLockStatus = LockStatusEnum.UNKNOWN;
		this.carrierLockStatus = LockStatusEnum.UNKNOWN;
		this.productionStatus = ProductionStatusEnum.UNKNOWN;
	}

	private RafServiceInstanceConfiguration getRafConfiguration() {
		return (RafServiceInstanceConfiguration) this.serviceInstanceConfiguration;
	}

}
