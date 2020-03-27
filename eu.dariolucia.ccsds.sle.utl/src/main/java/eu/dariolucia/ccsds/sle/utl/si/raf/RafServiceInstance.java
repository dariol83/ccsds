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
import eu.dariolucia.ccsds.sle.utl.encdec.RafUserEncDec;
import eu.dariolucia.ccsds.sle.utl.pdu.PduFactoryUtil;
import eu.dariolucia.ccsds.sle.utl.si.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static eu.dariolucia.ccsds.sle.utl.si.SleOperationNames.*;

/**
 * One object of this class represents an RAF Service Instance.
 */
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
	private final RafUserEncDec encDec = new RafUserEncDec();

	public RafServiceInstance(PeerConfiguration apiConfiguration,
                              RafServiceInstanceConfiguration serviceInstanceConfiguration) {
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
	}

	/**
	 * This method requests the transmission of a START operation.
	 *
	 * @param start the start time
	 * @param end the end time
	 * @param frameQuality the frame quality
	 */
	public void start(Date start, Date end, RafRequestedFrameQualityEnum frameQuality) {
		dispatchFromUser(() -> doStart(start, end, frameQuality));
	}

	private void doStart(Date start, Date end, RafRequestedFrameQualityEnum frameQuality) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.READY) {
			notifyInternalError("Start requested, but service instance is in state "
					+ this.currentState);
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
			pduTransmissionError(pdu, START_NAME, null);
			return;
		} else {
			pdu.setInvokerCredentials(creds);
		}

		boolean resultOk = encodeAndSend(invokeId, pdu, START_NAME);

		if (resultOk) {
			// If all fine, transition to new state: START_PENDING and notify PDU sent
			setServiceInstanceState(ServiceInstanceBindingStateEnum.START_PENDING);
			// Set the requested GVCID
			this.requestedFrameQuality = frameQuality;
			// Set times
			this.startTime = start;
			this.endTime = end;
			// Notify PDU
			pduTransmissionOk(pdu, START_NAME);
		}
	}

	/**
	 * This method requests the transmission of a STOP operation.
	 */
	public void stop() {
		dispatchFromUser(this::doStop);
	}

	private void doStop() {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.ACTIVE) {
			notifyInternalError("Stop requested, but service instance is in state "
					+ this.currentState);
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
			pduTransmissionError(pdu, STOP_NAME, null);
			return;
		} else {
			pdu.setInvokerCredentials(creds);
		}

		boolean resultOk = encodeAndSend(invokeId, pdu, STOP_NAME);
		
		if (resultOk) {
			// If all fine, transition to new state: STOP_PENDING and notify PDU sent
			setServiceInstanceState(ServiceInstanceBindingStateEnum.STOP_PENDING);
			pduTransmissionOk(pdu, STOP_NAME);
		}
	}

	/**
	 * This method requests the transmission of a SCHEDULE-STATUS-REPORT operation.
	 *
	 * @param isStop true if the scheduled report shall be stopped, false otherwise
	 * @param period (evaluated only if isStop is set to false) the report period in seconds, or null to ask for an immediate report
	 */
	public void scheduleStatusReport(boolean isStop, Integer period) {
		dispatchFromUser(() -> doScheduleStatusReport(isStop, period));
	}

	private void doScheduleStatusReport(boolean isStop, Integer period) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND
				|| this.currentState == ServiceInstanceBindingStateEnum.BIND_PENDING
				|| this.currentState == ServiceInstanceBindingStateEnum.UNBIND_PENDING) {
			notifyInternalError("Schedule status report requested, but service instance is in state " + this.currentState);
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
			pdu.getReportRequestType().setPeriodically(new ReportingCycle(period));
		} else {
			pdu.getReportRequestType().setImmediately(new BerNull());
		}

		// Add credentials
		// From the API configuration (remote peers) and SI configuration (responder
		// id), check remote peer and check if authentication must be used.
		Credentials creds = generateCredentials(getResponderIdentifier(), AuthenticationModeEnum.ALL);
		if (creds == null) {
			// Error while generating credentials, set by generateCredentials()
			pduTransmissionError(pdu, SCHEDULE_STATUS_REPORT_NAME, null);
			return;
		} else {
			pdu.setInvokerCredentials(creds);
		}

		boolean resultOk = encodeAndSend(invokeId, pdu, SCHEDULE_STATUS_REPORT_NAME);

		if (resultOk) {
			// If all fine, notify PDU sent
			pduTransmissionOk(pdu, SCHEDULE_STATUS_REPORT_NAME);
		}
	}

	/**
	 * This method requests the transmission of a GET-PARAMETER operation.
	 *
	 * @param parameter the parameter to retrieve
	 */
	public void getParameter(RafParameterEnum parameter) {
		dispatchFromUser(() -> doGetParameter(parameter));
	}

	private void doGetParameter(RafParameterEnum parameter) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND
				|| this.currentState == ServiceInstanceBindingStateEnum.BIND_PENDING
				|| this.currentState == ServiceInstanceBindingStateEnum.UNBIND_PENDING) {
			notifyInternalError("Get parameter requested, but service instance is in state "
					+ this.currentState);
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
			pduTransmissionError(pdu, GET_PARAMETER_NAME, null);
			return;
		} else {
			pdu.setInvokerCredentials(creds);
		}

		boolean resultOk = encodeAndSend(invokeId, pdu, GET_PARAMETER_NAME);

		if (resultOk) {
			// If all fine, notify PDU sent
			pduTransmissionOk(pdu, GET_PARAMETER_NAME);
		}
	}

	private void handleRafGetParameterReturn(RafGetParameterReturn pdu) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND
				|| this.currentState == ServiceInstanceBindingStateEnum.BIND_PENDING
				|| this.currentState == ServiceInstanceBindingStateEnum.UNBIND_PENDING) {
			pduReceptionProcessingError("Get parameter return received, but service instance is in state " + this.currentState, pdu, GET_PARAMETER_RETURN_NAME);
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
			pduReceptionProcessingError("Get parameter return received, but wrong credentials", pdu, GET_PARAMETER_RETURN_NAME);
			return;
		}

		// Cancel timer task for operation
		int invokeId = pdu.getInvokeId().intValue();
		cancelReturnTimeout(invokeId);

		// If all fine (result positive), update configuration parameter and notify
		// PDU received
		if (pdu.getResult().getPositiveResult() != null) {
			if (pdu.getResult().getPositiveResult().getParBufferSize() != null) {
				this.transferBufferSize = pdu.getResult().getPositiveResult().getParBufferSize().getParameterValue().intValue();
			} else if (pdu.getResult().getPositiveResult().getParDeliveryMode() != null) {
				this.deliveryMode = DeliveryModeEnum.values()[pdu.getResult().getPositiveResult()
						.getParDeliveryMode().getParameterValue().intValue()];
			} else if (pdu.getResult().getPositiveResult().getParReturnTimeout() != null) {
				this.returnTimeoutPeriod = pdu.getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue();
			} else if (pdu.getResult().getPositiveResult().getParLatencyLimit() != null) {
				if (pdu.getResult().getPositiveResult().getParLatencyLimit().getParameterValue().getOffline() != null) {
					this.latencyLimit = null;
				} else {
					this.latencyLimit = pdu.getResult().getPositiveResult().getParLatencyLimit().getParameterValue().getOnline()
							.intValue();
				}
			} else if (pdu.getResult().getPositiveResult().getParReportingCycle() != null) {
				if (pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue()
						.getPeriodicReportingOff() != null) {
					this.reportingCycle = null; 
				} else {
					this.reportingCycle = pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue()
							.getPeriodicReportingOn().intValue();
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
					this.permittedFrameQuality.add(reqs);
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
		pduReceptionOk(pdu, GET_PARAMETER_RETURN_NAME);
	}

	private void handleRafGetParameterV1toV4Return(RafGetParameterReturnV1toV4 pdu) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND
				|| this.currentState == ServiceInstanceBindingStateEnum.BIND_PENDING
				|| this.currentState == ServiceInstanceBindingStateEnum.UNBIND_PENDING) {
			pduReceptionProcessingError("Get parameter return received, but service instance is in state " + this.currentState, pdu, GET_PARAMETER_RETURN_NAME);
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
			pduReceptionProcessingError("Get parameter return received, but wrong credentials", pdu, GET_PARAMETER_RETURN_NAME);
			return;
		}

		// Cancel timer task for operation
		int invokeId = pdu.getInvokeId().intValue();
		cancelReturnTimeout(invokeId);

		// If all fine (result positive), update configuration parameter and notify
		// PDU received
		if (pdu.getResult().getPositiveResult() != null) {
			if (pdu.getResult().getPositiveResult().getParBufferSize() != null) {
				this.transferBufferSize = pdu.getResult().getPositiveResult().getParBufferSize().getParameterValue().intValue();
			} else if (pdu.getResult().getPositiveResult().getParDeliveryMode() != null) {
				this.deliveryMode = DeliveryModeEnum.values()[pdu.getResult().getPositiveResult()
						.getParDeliveryMode().getParameterValue().intValue()];
			} else if (pdu.getResult().getPositiveResult().getParReturnTimeout() != null) {
				this.returnTimeoutPeriod = pdu.getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue();
			} else if (pdu.getResult().getPositiveResult().getParLatencyLimit() != null) {
				if (pdu.getResult().getPositiveResult().getParLatencyLimit().getParameterValue().getOffline() != null) {
					this.latencyLimit = null;
				} else {
					this.latencyLimit = pdu.getResult().getPositiveResult().getParLatencyLimit().getParameterValue().getOnline()
							.intValue();
				}
			} else if (pdu.getResult().getPositiveResult().getParReportingCycle() != null) {
				if (pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue()
						.getPeriodicReportingOff() != null) { // this is the reporting cycle
					this.reportingCycle = null;
				} else {
					this.reportingCycle = pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue()
							.getPeriodicReportingOn().intValue();
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
		pduReceptionOk(pdu, GET_PARAMETER_RETURN_NAME);
	}

	private void handleRafStartReturn(RafStartReturn pdu) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.START_PENDING) {
			pduReceptionProcessingError("Start return received, but service instance is in state " + this.currentState, pdu, START_RETURN_NAME);
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
			pduReceptionProcessingError("Start return received, but wrong credentials", pdu, START_RETURN_NAME);
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
		pduReceptionOk(pdu, START_RETURN_NAME);
	}

	private void handleRafStopReturn(SleAcknowledgement pdu) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.STOP_PENDING) {
			pduReceptionProcessingError("Stop return received, but service instance is in state " + this.currentState, pdu, STOP_RETURN_NAME);
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(pdu.getCredentials(), AuthenticationModeEnum.ALL)) {
			pduReceptionProcessingError("Stop return received, but wrong credentials", pdu, STOP_RETURN_NAME);
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
		pduReceptionOk(pdu, STOP_RETURN_NAME);
	}

	private void handleRafStatusReport(RafStatusReportInvocation pdu) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND) {
			pduReceptionProcessingError("Status report received, but service instance is in state " + this.currentState, pdu, STATUS_REPORT_NAME);
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(pdu.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
			pduReceptionProcessingError("Status report received, but wrong credentials", pdu, STATUS_REPORT_NAME);
			return;
		}

		// Set the status report parameters
		this.carrierLockStatus = mapLockStatus(pdu.getCarrierLockStatus().intValue());
		this.subcarrierLockStatus = mapLockStatus(pdu.getSubcarrierLockStatus().intValue());
		this.symbolSyncLockStatus = mapLockStatus(pdu.getSymbolSyncLockStatus().intValue());
		this.frameSyncLockStatus = mapLockStatus(pdu.getFrameSyncLockStatus().intValue());
		this.productionStatus = mapProductionStatus(pdu.getProductionStatus().intValue());
		this.errorFreeFrameNumber = pdu.getErrorFreeFrameNumber().intValue();
		this.deliveredFrameNumber = pdu.getDeliveredFrameNumber().intValue();

		// Notify PDU
		pduReceptionOk(pdu, STATUS_REPORT_NAME);
	}

	private void handleRafTransferBuffer(RafTransferBuffer pdu) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.ACTIVE && this.currentState != ServiceInstanceBindingStateEnum.STOP_PENDING) {
			pduReceptionProcessingError("Transfer buffer received, but service instance is in state " + this.currentState, pdu, TRANSFER_BUFFER_NAME);
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
					pduReceptionProcessingError("Transfer data received, but wrong credentials", pdu, TRANSFER_DATA_NAME);
					return;
				}

				// Notify PDU
				notifyPduReceived(tf, TRANSFER_DATA_NAME, null);
			} else {
				RafSyncNotifyInvocation sn = fon.getSyncNotification();

				// Validate credentials
				// From the API configuration (remote peers) and SI configuration (remote peer),
				// check remote peer and check if authentication must be used.
				// If so, verify credentials.
				if(!authenticate(sn.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
					pduReceptionProcessingError("Notify received, but wrong credentials", pdu, NOTIFY_NAME);
					return;
				}

				if (sn.getNotification().getEndOfData() != null) {
					LOG.info(getServiceInstanceIdentifier() + ": End of data reported");
				} else if (sn.getNotification().getExcessiveDataBacklog() != null) {
					LOG.warning(getServiceInstanceIdentifier() + ": Data discarded due to excessive backlog");
				} else if (sn.getNotification().getLossFrameSync() != null) {
					this.carrierLockStatus = mapLockStatus(
							sn.getNotification().getLossFrameSync().getCarrierLockStatus().intValue());
					this.subcarrierLockStatus = mapLockStatus(
							sn.getNotification().getLossFrameSync().getSubcarrierLockStatus().intValue());
					this.symbolSyncLockStatus = mapLockStatus(
							sn.getNotification().getLossFrameSync().getSymbolSyncLockStatus().intValue());
					this.frameSyncLockStatus = LockStatusEnum.OUT_OF_LOCK;
					LOG.warning(getServiceInstanceIdentifier() + ": Loss frame synchronisation");
				} else if (sn.getNotification().getProductionStatusChange() != null) {
					this.productionStatus = mapProductionStatus(
							sn.getNotification().getProductionStatusChange().intValue());
					if(LOG.isLoggable(Level.INFO)) {
						LOG.info(String.format("%s: Production status changed to %s", getServiceInstanceIdentifier(), this.productionStatus));
					}
				}
				// Notify PDU
				notifyPduReceived(sn, NOTIFY_NAME, null);
			}
		}
		// Generate state and notify update
		notifyStateUpdate();
	}

	private void handleRafStatusReportV1toV2(RafStatusReportInvocationV1toV2 pdu) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND) {
			pduReceptionProcessingError("Status report received, but service instance is in state " + this.currentState, pdu, STATUS_REPORT_NAME);
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(pdu.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
			pduReceptionProcessingError("Status report received, but wrong credentials", pdu, STATUS_REPORT_NAME);
			return;
		}

		// Set the status report parameters
		this.carrierLockStatus = mapLockStatus(pdu.getCarrierLockStatus().intValue());
		this.subcarrierLockStatus = mapLockStatus(pdu.getSubcarrierLockStatus().intValue());
		this.symbolSyncLockStatus = mapLockStatus(pdu.getSymbolSyncLockStatus().intValue());
		this.frameSyncLockStatus = mapLockStatus(pdu.getFrameSyncLockStatus().intValue());
		this.productionStatus = mapProductionStatus(pdu.getProductionStatus().intValue());
		this.deliveredFrameNumber = pdu.getDeliveredFrameNumber().intValue();
		this.errorFreeFrameNumber = pdu.getErrorFreeFrameNumber().intValue();

		// Notify PDU
		pduReceptionOk(pdu, STATUS_REPORT_NAME);
	}

	private void handleRafScheduleStatusReportReturn(SleScheduleStatusReportReturn pdu) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND) {
			pduReceptionProcessingError("Schedule status report return received, but service instance is in state " + this.currentState, pdu, SCHEDULE_STATUS_REPORT_RETURN_NAME);
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
			pduReceptionProcessingError("Schedule status report return received, but wrong credentials", pdu, SCHEDULE_STATUS_REPORT_RETURN_NAME);
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
		pduReceptionOk(pdu, SCHEDULE_STATUS_REPORT_RETURN_NAME);
	}

	private ProductionStatusEnum mapProductionStatus(int intValue) {
		return ProductionStatusEnum.fromCode(intValue);
	}

	private LockStatusEnum mapLockStatus(int intValue) {
		return LockStatusEnum.fromCode(intValue);
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
