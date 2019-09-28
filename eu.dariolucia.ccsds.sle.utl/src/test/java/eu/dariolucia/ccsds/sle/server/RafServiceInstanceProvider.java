/*
 *   Copyright (c) 2019 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.sle.server;

import com.beanit.jasn1.ber.types.BerInteger;
import com.beanit.jasn1.ber.types.BerNull;
import com.beanit.jasn1.ber.types.BerType;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.Credentials;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.IntPosShort;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.IntUnsignedLong;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.ParameterName;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.incoming.pdus.RafGetParameterInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.incoming.pdus.RafStartInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.structures.*;
import eu.dariolucia.ccsds.sle.utl.config.PeerConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.raf.RafServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.pdu.PduFactoryUtil;
import eu.dariolucia.ccsds.sle.utl.si.*;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafParameterEnum;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafRequestedFrameQualityEnum;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafServiceInstanceState;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * One object of this class represents an RAF Service Instance (provider role).
 */
public class RafServiceInstanceProvider extends ServiceInstance {

	private static final Logger LOG = Logger.getLogger(RafServiceInstanceProvider.class.getName());

	// Read from configuration, retrieved via GET_PARAMETER
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

	// Requested via STATUS_REPORT, updated externally
	private int errorFreeFrameNumber = 0;
	private int deliveredFrameNumber = 0;
	private LockStatusEnum frameSyncLockStatus = LockStatusEnum.OUT_OF_LOCK;
	private LockStatusEnum symbolSyncLockStatus = LockStatusEnum.OUT_OF_LOCK;
	private LockStatusEnum subcarrierLockStatus = LockStatusEnum.OUT_OF_LOCK;
	private LockStatusEnum carrierLockStatus = LockStatusEnum.OUT_OF_LOCK;
	private ProductionStatusEnum productionStatus = ProductionStatusEnum.HALTED;

	// Encoder/decoder
	private final RafProviderEncDec encDec = new RafProviderEncDec();

	// Status report scheduler
	private volatile Timer reportingScheduler = null;

	public RafServiceInstanceProvider(PeerConfiguration apiConfiguration,
                                      RafServiceInstanceConfiguration serviceInstanceConfiguration) {
		super(apiConfiguration, serviceInstanceConfiguration);
	}

	@Override
	protected void setup() {
		// Register handlers
		registerPduReceptionHandler(RafStartInvocation.class, this::handleRafStartInvocation);
		registerPduReceptionHandler(SleStopInvocation.class, this::handleRafStopInvocation);
		registerPduReceptionHandler(SleScheduleStatusReportInvocation.class, this::handleRafScheduleStatusReportInvocation);
		registerPduReceptionHandler(RafGetParameterInvocation.class, this::handleRafGetParameterInvocation);

		// Update status parameters from configuration
		resetState();
	}

	private void handleRafStartInvocation(RafStartInvocation invocation) {
		dispatchFromProvider(() -> doHandleRafStartInvocation(invocation));
	}

	private void doHandleRafStartInvocation(RafStartInvocation invocation) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.READY) {
			setError("Start received from user, but service instance is in state "
					+ this.currentState);
			notifyStateUpdate();
			peerAbort(PeerAbortReasonEnum.PROTOCOL_ERROR);
			return;
		}

		// Process the START

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(invocation.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Start invocation received, but wrong credentials");
			notifyPduReceived(invocation, "START", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Validate the requested frame quality
		RequestedFrameQuality rfq = invocation.getRequestedFrameQuality();
		boolean permittedOk = false;
		for(RafRequestedFrameQualityEnum permitted : this.permittedFrameQuality) {
			if(permitted.getCode() == rfq.intValue()) {
				permittedOk = true;
				break;
			}
		}

		RafStartReturn pdu = new RafStartReturn();
		pdu.setInvokeId(invocation.getInvokeId());
		pdu.setResult(new RafStartReturn.Result());
		if(permittedOk) {
			pdu.getResult().setPositiveResult(new BerNull());
		} else {
			pdu.getResult().setNegativeResult(new DiagnosticRafStart());
			pdu.getResult().getNegativeResult().setSpecific(new BerInteger(1)); // Unable to comply
		}
		// Add credentials
		// From the API configuration (remote peers) and SI configuration (responder
		// id), check remote peer and check if authentication must be used.
		Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL);
		if (creds == null) {
			// Error while generating credentials, set by generateCredentials()
			notifyPduSentError(pdu, "START-RETURN", null);
			notifyStateUpdate();
			return;
		} else {
			pdu.setPerformerCredentials(creds);
		}

		boolean resultOk = encodeAndSend(null, pdu, "START-RETURN");

		// TODO: activate capability to send frames and notifications

		if (resultOk) {
			// If all fine, transition to new state: ACTIVE and notify PDU sent
			setServiceInstanceState(ServiceInstanceBindingStateEnum.ACTIVE);
			// Set the requested frame quality
			this.requestedFrameQuality = RafRequestedFrameQualityEnum.fromCode(invocation.getRequestedFrameQuality().intValue());
			// Set times
			this.startTime = PduFactoryUtil.toDate(invocation.getStartTime());
			this.endTime = PduFactoryUtil.toDate(invocation.getStopTime());
			// Notify PDU
			notifyPduSent(pdu, "START-RETURN", getLastPduSent());
			// Generate state and notify update
			notifyStateUpdate();
		}
	}

	private void handleRafStopInvocation(SleStopInvocation invocation) {
		dispatchFromProvider(() -> doHandleRafStopInvocation(invocation));
	}

	private void doHandleRafStopInvocation(SleStopInvocation invocation) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.ACTIVE) {
			setError("Stop received from user, but service instance is in state "
					+ this.currentState);
			notifyStateUpdate();
			peerAbort(PeerAbortReasonEnum.PROTOCOL_ERROR);
			return;
		}

		// Process the STOP

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(invocation.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Stop invocation received, but wrong credentials");
			notifyPduReceived(invocation, "STOP", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		SleAcknowledgement pdu = new SleAcknowledgement();
		pdu.setInvokeId(invocation.getInvokeId());
		pdu.setResult(new SleAcknowledgement.Result());
		pdu.getResult().setPositiveResult(new BerNull());

		// Add credentials
		// From the API configuration (remote peers) and SI configuration (responder
		// id), check remote peer and check if authentication must be used.
		Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL);
		if (creds == null) {
			// Error while generating credentials, set by generateCredentials()
			notifyPduSentError(pdu, "STOP-RETURN", null);
			notifyStateUpdate();
			return;
		} else {
			pdu.setCredentials(creds);
		}

		// TODO: stop the ability to add transfer frames
		// TODO: schedule this last part in the management thread

		dispatchFromProvider(() -> {
			boolean resultOk = encodeAndSend(null, pdu, "STOP-RETURN");

			if (resultOk) {
				// If all fine, transition to new state: READY and notify PDU sent
				setServiceInstanceState(ServiceInstanceBindingStateEnum.READY);
				// Set the requested frame quality
				this.requestedFrameQuality = null;
				// Set times
				this.startTime = null;
				this.endTime = null;
				// Notify PDU
				notifyPduSent(pdu, "STOP-RETURN", getLastPduSent());
				// Generate state and notify update
				notifyStateUpdate();
			}
		});
	}

	private void handleRafScheduleStatusReportInvocation(SleScheduleStatusReportInvocation invocation) {
		dispatchFromProvider(() -> doHandleRafScheduleStatusReportInvocation(invocation));
	}

	private void doHandleRafScheduleStatusReportInvocation(SleScheduleStatusReportInvocation invocation) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.READY && this.currentState != ServiceInstanceBindingStateEnum.ACTIVE) {
			setError("Schedule status report received from user, but service instance is in state "
					+ this.currentState);
			notifyStateUpdate();
			peerAbort(PeerAbortReasonEnum.PROTOCOL_ERROR);
			return;
		}

		// Process the SCHEDULE-STATUS-REPORT

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(invocation.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Schedule status report received, but wrong credentials");
			notifyPduReceived(invocation, "SCHEDULE-STATUS-REPORT", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		SleScheduleStatusReportReturn pdu = new SleScheduleStatusReportReturn();
		pdu.setInvokeId(invocation.getInvokeId());
		pdu.setResult(new SleScheduleStatusReportReturn.Result());

		if(invocation.getReportRequestType().getImmediately() != null) {
			sendStatusReport(true);
			pdu.getResult().setPositiveResult(new BerNull());
		} else if(invocation.getReportRequestType().getStop() != null) {
			if(this.reportingScheduler != null) {
				stopStatusReport();
				pdu.getResult().setPositiveResult(new BerNull());
			} else {
				pdu.getResult().setNegativeResult(new DiagnosticScheduleStatusReport());
				pdu.getResult().getNegativeResult().setSpecific(new BerInteger(1)); // Already stopped
			}
		} else if(invocation.getReportRequestType().getPeriodically() != null) {
			int period = invocation.getReportRequestType().getPeriodically().intValue();
			if(this.minReportingCycle == null || period > this.minReportingCycle) {
				startStatusReport(period);
				pdu.getResult().setPositiveResult(new BerNull());
			} else {
				pdu.getResult().setNegativeResult(new DiagnosticScheduleStatusReport());
				pdu.getResult().getNegativeResult().setSpecific(new BerInteger(2)); // Invalid reporting cycle
			}
		}

		// Add credentials
		// From the API configuration (remote peers) and SI configuration (responder
		// id), check remote peer and check if authentication must be used.
		Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL);
		if (creds == null) {
			// Error while generating credentials, set by generateCredentials()
			notifyPduSentError(pdu, "SCHEDULE-STATUS-REPORT-RETURN", null);
			notifyStateUpdate();
			return;
		} else {
			pdu.setPerformerCredentials(creds);
		}

		boolean resultOk = encodeAndSend(null, pdu, "SCHEDULE-STATUS-REPORT-RETURN");

		if (resultOk) {
			// Notify PDU
			notifyPduSent(pdu, "SCHEDULE-STATUS-REPORT-RETURN", getLastPduSent());
			// Generate state and notify update
			notifyStateUpdate();
		}
	}

	private void startStatusReport(int period) {
		this.reportingCycle = period;
		this.reportingScheduler = new Timer();
		this.reportingScheduler.schedule(new TimerTask() {
			@Override
			public void run() {
				if(reportingScheduler != null) {
					dispatchFromProvider(() -> sendStatusReport(false));
				}
			}
		}, 0, period * 1000);
	}

	private void stopStatusReport() {
		this.reportingCycle = null;
		this.reportingScheduler.cancel();
		this.reportingScheduler = null;
	}

	private void handleRafGetParameterInvocation(RafGetParameterInvocation invocation) {
		dispatchFromProvider(() -> doHandleRafGetParameterInvocation(invocation));
	}

	private void doHandleRafGetParameterInvocation(RafGetParameterInvocation invocation) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.READY && this.currentState != ServiceInstanceBindingStateEnum.ACTIVE) {
			setError("Get parameter received from user, but service instance is in state "
					+ this.currentState);
			notifyStateUpdate();
			peerAbort(PeerAbortReasonEnum.PROTOCOL_ERROR);
			return;
		}

		// Process the GET-PARAMETER

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(invocation.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Get parameter received, but wrong credentials");
			notifyPduReceived(invocation, "GET-PARAMETER", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		BerType toSend = null;
		if(getSleVersion() <= 4) {
			RafGetParameterReturnV1toV4 pdu = new RafGetParameterReturnV1toV4();
			pdu.setInvokeId(invocation.getInvokeId());

			// Add credentials
			// From the API configuration (remote peers) and SI configuration (responder
			// id), check remote peer and check if authentication must be used.
			Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL);
			if (creds == null) {
				// Error while generating credentials, set by generateCredentials()
				notifyPduSentError(pdu, "GET-PARAMETER-RETURN", null);
				notifyStateUpdate();
				return;
			} else {
				pdu.setPerformerCredentials(creds);
			}
			// Prepare for positive response
			pdu.setResult(new RafGetParameterReturnV1toV4.Result());
			pdu.getResult().setPositiveResult(new RafGetParameterV1toV4());
			if(invocation.getRafParameter().intValue() == RafParameterEnum.BUFFER_SIZE.getCode()) {
				pdu.getResult().getPositiveResult().setParBufferSize(new RafGetParameterV1toV4.ParBufferSize());
				pdu.getResult().getPositiveResult().getParBufferSize().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
				pdu.getResult().getPositiveResult().getParBufferSize().setParameterValue(new IntPosShort(this.transferBufferSize));
			} else if(invocation.getRafParameter().intValue() == RafParameterEnum.DELIVERY_MODE.getCode()) {
				pdu.getResult().getPositiveResult().setParDeliveryMode(new RafGetParameterV1toV4.ParDeliveryMode());
				pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
				pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterValue(new RafDeliveryMode(this.deliveryMode.ordinal()));
			} else if(invocation.getRafParameter().intValue() == RafParameterEnum.LATENCY_LIMIT.getCode()) {
				pdu.getResult().getPositiveResult().setParLatencyLimit(new RafGetParameterV1toV4.ParLatencyLimit());
				pdu.getResult().getPositiveResult().getParLatencyLimit().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
				pdu.getResult().getPositiveResult().getParLatencyLimit().setParameterValue(new RafGetParameterV1toV4.ParLatencyLimit.ParameterValue());
				pdu.getResult().getPositiveResult().getParLatencyLimit().getParameterValue().setOnline(new IntPosShort(this.latencyLimit));
			} else if(invocation.getRafParameter().intValue() == RafParameterEnum.REPORTING_CYCLE.getCode()) {
				pdu.getResult().getPositiveResult().setParReportingCycle(new RafGetParameterV1toV4.ParReportingCycle());
				pdu.getResult().getPositiveResult().getParReportingCycle().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
				pdu.getResult().getPositiveResult().getParReportingCycle().setParameterValue(new CurrentReportingCycle());
				if(this.reportingScheduler != null && this.reportingCycle != null) {
					pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOn(new ReportingCycle(this.reportingCycle));
				} else {
					pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOff(new BerNull());
				}
			} else if(invocation.getRafParameter().intValue() == RafParameterEnum.REQUESTED_FRAME_QUALITY.getCode()) {
				pdu.getResult().getPositiveResult().setParReqFrameQuality(new RafGetParameterV1toV4.ParReqFrameQuality());
				pdu.getResult().getPositiveResult().getParReqFrameQuality().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
				if(this.requestedFrameQuality != null) {
					pdu.getResult().getPositiveResult().getParReqFrameQuality().setParameterValue(new BerInteger(this.requestedFrameQuality.getCode()));
				} else {
					pdu.getResult().getPositiveResult().getParReqFrameQuality().setParameterValue(new BerInteger(0)); // as per standard
				}
			} else if(invocation.getRafParameter().intValue() == RafParameterEnum.RETURN_TIMEOUT_PERIOD.getCode()) {
				pdu.getResult().getPositiveResult().setParReturnTimeout(new RafGetParameterV1toV4.ParReturnTimeout());
				pdu.getResult().getPositiveResult().getParReturnTimeout().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
				pdu.getResult().getPositiveResult().getParReturnTimeout().setParameterValue(new TimeoutPeriod(this.returnTimeoutPeriod));
			} else {
				pdu.getResult().setPositiveResult(null);
				pdu.getResult().setNegativeResult(new DiagnosticRafGet());
				pdu.getResult().getNegativeResult().setSpecific(new BerInteger(0)); // unknownParameter
			}
			toSend = pdu;
		} else {
			RafGetParameterReturn pdu = new RafGetParameterReturn();
			pdu.setInvokeId(invocation.getInvokeId());

			// Add credentials
			// From the API configuration (remote peers) and SI configuration (responder
			// id), check remote peer and check if authentication must be used.
			Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL);
			if (creds == null) {
				// Error while generating credentials, set by generateCredentials()
				notifyPduSentError(pdu, "GET-PARAMETER-RETURN", null);
				notifyStateUpdate();
				return;
			} else {
				pdu.setPerformerCredentials(creds);
			}
			// Prepare for positive response
			pdu.setResult(new RafGetParameterReturn.Result());
			pdu.getResult().setPositiveResult(new RafGetParameter());
			if(invocation.getRafParameter().intValue() == RafParameterEnum.BUFFER_SIZE.getCode()) {
				pdu.getResult().getPositiveResult().setParBufferSize(new RafGetParameter.ParBufferSize());
				pdu.getResult().getPositiveResult().getParBufferSize().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
				pdu.getResult().getPositiveResult().getParBufferSize().setParameterValue(new IntPosShort(this.transferBufferSize));
			} else if(invocation.getRafParameter().intValue() == RafParameterEnum.DELIVERY_MODE.getCode()) {
				pdu.getResult().getPositiveResult().setParDeliveryMode(new RafGetParameter.ParDeliveryMode());
				pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
				pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterValue(new RafDeliveryMode(this.deliveryMode.ordinal()));
			} else if(invocation.getRafParameter().intValue() == RafParameterEnum.LATENCY_LIMIT.getCode()) {
				pdu.getResult().getPositiveResult().setParLatencyLimit(new RafGetParameter.ParLatencyLimit());
				pdu.getResult().getPositiveResult().getParLatencyLimit().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
				pdu.getResult().getPositiveResult().getParLatencyLimit().setParameterValue(new RafGetParameter.ParLatencyLimit.ParameterValue());
				pdu.getResult().getPositiveResult().getParLatencyLimit().getParameterValue().setOnline(new IntPosShort(this.latencyLimit));
			} else if(invocation.getRafParameter().intValue() == RafParameterEnum.REPORTING_CYCLE.getCode()) {
				pdu.getResult().getPositiveResult().setParReportingCycle(new RafGetParameter.ParReportingCycle());
				pdu.getResult().getPositiveResult().getParReportingCycle().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
				pdu.getResult().getPositiveResult().getParReportingCycle().setParameterValue(new CurrentReportingCycle());
				if(this.reportingScheduler != null && this.reportingCycle != null) {
					pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOn(new ReportingCycle(this.reportingCycle));
				} else {
					pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOff(new BerNull());
				}
			} else if(invocation.getRafParameter().intValue() == RafParameterEnum.REQUESTED_FRAME_QUALITY.getCode()) {
				pdu.getResult().getPositiveResult().setParReqFrameQuality(new RafGetParameter.ParReqFrameQuality());
				pdu.getResult().getPositiveResult().getParReqFrameQuality().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
				if(this.requestedFrameQuality != null) {
					pdu.getResult().getPositiveResult().getParReqFrameQuality().setParameterValue(new BerInteger(this.requestedFrameQuality.getCode()));
				} else {
					pdu.getResult().getPositiveResult().getParReqFrameQuality().setParameterValue(new BerInteger(0)); // as per standard
				}
			} else if(invocation.getRafParameter().intValue() == RafParameterEnum.RETURN_TIMEOUT_PERIOD.getCode()) {
				pdu.getResult().getPositiveResult().setParReturnTimeout(new RafGetParameter.ParReturnTimeout());
				pdu.getResult().getPositiveResult().getParReturnTimeout().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
				pdu.getResult().getPositiveResult().getParReturnTimeout().setParameterValue(new TimeoutPeriod(this.returnTimeoutPeriod));
			} else if(invocation.getRafParameter().intValue() == RafParameterEnum.MIN_REPORTING_CYCLE.getCode()) {
				pdu.getResult().getPositiveResult().setParMinReportingCycle(new RafGetParameter.ParMinReportingCycle());
				pdu.getResult().getPositiveResult().getParMinReportingCycle().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
				if(this.minReportingCycle != null) {
					pdu.getResult().getPositiveResult().getParMinReportingCycle().setParameterValue(new IntPosShort(this.minReportingCycle));
				} else {
					pdu.getResult().getPositiveResult().getParMinReportingCycle().setParameterValue(new IntPosShort(0));
				}
			} else if(invocation.getRafParameter().intValue() == RafParameterEnum.PERMITTED_FRAME_QUALITY.getCode()) {
				pdu.getResult().getPositiveResult().setParPermittedFrameQuality(new RafGetParameter.ParPermittedFrameQuality());
				pdu.getResult().getPositiveResult().getParPermittedFrameQuality().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
				pdu.getResult().getPositiveResult().getParPermittedFrameQuality().setParameterValue(new PermittedFrameQualitySet());
				for(RafRequestedFrameQualityEnum permitted : this.permittedFrameQuality) {
					pdu.getResult().getPositiveResult().getParPermittedFrameQuality().getParameterValue().getRequestedFrameQuality().add(new RequestedFrameQuality(permitted.getCode()));
				}
			} else {
				pdu.getResult().setPositiveResult(null);
				pdu.getResult().setNegativeResult(new DiagnosticRafGet());
				pdu.getResult().getNegativeResult().setSpecific(new BerInteger(0)); // unknownParameter
			}
			toSend = pdu;
		}

		boolean resultOk = encodeAndSend(null, toSend, "GET-PARAMETER-RETURN");

		if (resultOk) {
			// Notify PDU
			notifyPduSent(toSend, "GET-PARAMETER-RETURN", getLastPduSent());
			// Generate state and notify update
			notifyStateUpdate();
		}
	}

	private void sendStatusReport(boolean immediate) {
		if(!immediate && this.reportingScheduler == null) {
			return;
		}
		if(getSleVersion() <= 2) {
			RafStatusReportInvocationV1toV2 pdu = new RafStatusReportInvocationV1toV2();
			// Add credentials
			Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL);
			if (creds == null) {
				// Error while generating credentials, set by generateCredentials()
				notifyPduSentError(pdu, "STATUS-REPORT", null);
				notifyStateUpdate();
				return;
			} else {
				pdu.setInvokerCredentials(creds);
			}
			//
			pdu.setCarrierLockStatus(new LockStatus(this.carrierLockStatus.ordinal()));
			pdu.setFrameSyncLockStatus(new LockStatus(this.frameSyncLockStatus.ordinal()));
			pdu.setDeliveredFrameNumber(new IntUnsignedLong(this.deliveredFrameNumber));
			pdu.setErrorFreeFrameNumber(new IntUnsignedLong(this.errorFreeFrameNumber));
			pdu.setProductionStatus(new RafProductionStatus(this.productionStatus.ordinal()));
			pdu.setSubcarrierLockStatus(new LockStatus(this.subcarrierLockStatus.ordinal()));
			pdu.setSymbolSyncLockStatus(new LockStatus(this.symbolSyncLockStatus.ordinal()));

			boolean resultOk = encodeAndSend(null, pdu, "STATUS-REPORT");

			if (resultOk) {
				// Notify PDU
				notifyPduSent(pdu, "STATUS-REPORT", getLastPduSent());
				// Generate state and notify update
				notifyStateUpdate();
			}
		} else {
			RafStatusReportInvocation pdu = new RafStatusReportInvocation();
			// Add credentials
			Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL);
			if (creds == null) {
				// Error while generating credentials, set by generateCredentials()
				notifyPduSentError(pdu, "STATUS-REPORT", null);
				notifyStateUpdate();
				return;
			} else {
				pdu.setInvokerCredentials(creds);
			}
			//
			pdu.setCarrierLockStatus(new CarrierLockStatus(this.carrierLockStatus.ordinal()));
			pdu.setFrameSyncLockStatus(new FrameSyncLockStatus(this.frameSyncLockStatus.ordinal()));
			pdu.setDeliveredFrameNumber(new IntUnsignedLong(this.deliveredFrameNumber));
			pdu.setErrorFreeFrameNumber(new IntUnsignedLong(this.errorFreeFrameNumber));
			pdu.setProductionStatus(new RafProductionStatus(this.productionStatus.ordinal()));
			pdu.setSubcarrierLockStatus(new LockStatus(this.subcarrierLockStatus.ordinal()));
			pdu.setSymbolSyncLockStatus(new SymbolLockStatus(this.symbolSyncLockStatus.ordinal()));

			boolean resultOk = encodeAndSend(null, pdu, "STATUS-REPORT");

			if (resultOk) {
				// Notify PDU
				notifyPduSent(pdu, "STATUS-REPORT", getLastPduSent());
				// Generate state and notify update
				notifyStateUpdate();
			}
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
