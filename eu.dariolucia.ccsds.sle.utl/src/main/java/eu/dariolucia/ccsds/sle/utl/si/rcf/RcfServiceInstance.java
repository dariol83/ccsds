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

package eu.dariolucia.ccsds.sle.utl.si.rcf;

import com.beanit.jasn1.ber.types.BerInteger;
import com.beanit.jasn1.ber.types.BerNull;
import com.beanit.jasn1.ber.types.BerType;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.incoming.pdus.RcfGetParameterInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.incoming.pdus.RcfStartInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.outgoing.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.structures.*;
import eu.dariolucia.ccsds.sle.utl.config.PeerConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.rcf.RcfServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.encdec.RcfEncDec;
import eu.dariolucia.ccsds.sle.utl.pdu.PduFactoryUtil;
import eu.dariolucia.ccsds.sle.utl.si.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One object of this class represents an RCF Service Instance.
 */
public class RcfServiceInstance extends ServiceInstance {

	private static final Logger LOG = Logger.getLogger(RcfServiceInstance.class.getName());

	private static final String START_NAME = "START";
	private static final String STOP_NAME = "STOP";
	private static final String SCHEDULE_STATUS_REPORT_NAME = "SCHEDULE-STATUS-REPORT";
	private static final String GET_PARAMETER_NAME = "GET-PARAMETER";
	private static final String GET_PARAMETER_RETURN_NAME = "GET-PARAMETER-RETURN";
	private static final String START_RETURN_NAME = "START-RETURN";
	private static final String STOP_RETURN_NAME = "STOP-RETURN";
	private static final String STATUS_REPORT_NAME = "STATUS-REPORT";
	private static final String TRANSFER_BUFFER_NAME = "TRANSFER-BUFFER";
	private static final String TRANSFER_DATA_NAME = "TRANSFER-DATA";
	private static final String NOTIFY_NAME = "NOTIFY";
	private static final String SCHEDULE_STATUS_REPORT_RETURN_NAME = "SCHEDULE-STATUS-REPORT-RETURN";

	// Read from configuration, updated via GET_PARAMETER
	private Integer latencyLimit; // NULL if offline, otherwise a value
	private List<GVCID> permittedGvcid;
	private Integer minReportingCycle;
	private int returnTimeoutPeriod;
	private int transferBufferSize;
	private DeliveryModeEnum deliveryMode = null;
	
	// Updated via START and GET_PARAMETER
	private GVCID requestedGvcid = null;
	private Integer reportingCycle = null; // NULL if off, otherwise a value
	private Date startTime = null;
	private Date endTime = null;
	
	// Updated via STATUS_REPORT
	private int numFramesDelivered = 0;
	private LockStatusEnum frameSyncLockStatus = LockStatusEnum.UNKNOWN;
	private LockStatusEnum symbolSyncLockStatus = LockStatusEnum.UNKNOWN;
	private LockStatusEnum subcarrierLockStatus = LockStatusEnum.UNKNOWN;
	private LockStatusEnum carrierLockStatus = LockStatusEnum.UNKNOWN;
	private ProductionStatusEnum productionStatus = ProductionStatusEnum.UNKNOWN;

	// Encoder/decoder
	private final RcfEncDec encDec = new RcfEncDec();

	public RcfServiceInstance(PeerConfiguration apiConfiguration,
                              RcfServiceInstanceConfiguration serviceInstanceConfiguration) {
		super(apiConfiguration, serviceInstanceConfiguration);
	}

	@Override
	protected void setup() {
		// Register handlers
		registerPduReceptionHandler(RcfStartReturn.class, this::handleRcfStartReturn);
		registerPduReceptionHandler(SleAcknowledgement.class, this::handleRcfStopReturn);
		registerPduReceptionHandler(SleScheduleStatusReportReturn.class, this::handleRcfScheduleStatusReportReturn);
		registerPduReceptionHandler(RcfStatusReportInvocation.class, this::handleRcfStatusReport);
		registerPduReceptionHandler(RcfStatusReportInvocationV1.class, this::handleRcfStatusReportV1);
		registerPduReceptionHandler(RcfTransferBuffer.class, this::handleRcfTransferBuffer);
		registerPduReceptionHandler(RcfGetParameterReturn.class, this::handleRcfGetParameterReturn);
		registerPduReceptionHandler(RcfGetParameterReturnV1toV4.class, this::handleRcfGetParameterV1toV4Return);
	}

	/**
	 * This method requests the transmission of a START operation.
	 *
	 * @param start the start time
	 * @param end the end time
	 * @param requestedGVCID2 the global virtual channel ID
	 */
	public void start(Date start, Date end, GVCID requestedGVCID2) {
		dispatchFromUser(() -> doStart(start, end, requestedGVCID2));
	}

	private void doStart(Date start, Date end, GVCID requestedGVCID2) {
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
		RcfStartInvocation pdu = new RcfStartInvocation();
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
		// GVCID
		pdu.setRequestedGvcId(new GvcId());
		pdu.getRequestedGvcId().setVersionNumber(new BerInteger(requestedGVCID2.getTransferFrameVersionNumber()));
		pdu.getRequestedGvcId().setSpacecraftId(new BerInteger(requestedGVCID2.getSpacecraftId()));
		pdu.getRequestedGvcId().setVcId(new GvcId.VcId());
		if (requestedGVCID2.getVirtualChannelId() == null) {
			pdu.getRequestedGvcId().getVcId().setMasterChannel(new BerNull());
		} else {
			pdu.getRequestedGvcId().getVcId()
					.setVirtualChannel(new VcId(requestedGVCID2.getVirtualChannelId()));
		}

		// Add credentials
		// From the API configuration (remote peers) and SI configuration (responder
		// id), check remote peer and check if authentication must be used.
		Credentials creds = generateCredentials(getResponderIdentifier(), AuthenticationModeEnum.ALL);
		if (creds == null) {
			// Error while generating credentials, set by generateCredentials()
			notifyPduSentError(pdu, START_NAME, null);
			notifyStateUpdate();
			return;
		} else {
			pdu.setInvokerCredentials(creds);
		}

		boolean resultOk = encodeAndSend(invokeId, pdu, START_NAME);

		if (resultOk) {
			// If all fine, transition to new state: START_PENDING and notify PDU sent
			setServiceInstanceState(ServiceInstanceBindingStateEnum.START_PENDING);
			// Set the requested GVCID
			this.requestedGvcid = requestedGVCID2;
			// Set times
			this.startTime = start;
			this.endTime = end;
			// Notify PDU
			notifyPduSent(pdu, START_NAME, getLastPduSent());

			// Generate state and notify update
			notifyStateUpdate();
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
			notifyPduSentError(pdu, STOP_NAME, null);
			notifyStateUpdate();
			return;
		} else {
			pdu.setInvokerCredentials(creds);
		}

		boolean resultOk = encodeAndSend(invokeId, pdu, STOP_NAME);
		
		if (resultOk) {
			// If all fine, transition to new state: STOP_PENDING and notify PDU sent
			setServiceInstanceState(ServiceInstanceBindingStateEnum.STOP_PENDING);
			notifyPduSent(pdu, STOP_NAME, getLastPduSent());
			// Generate state and notify update
			notifyStateUpdate();
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
			notifyPduSentError(pdu, SCHEDULE_STATUS_REPORT_NAME, null);
			notifyStateUpdate();
			return;
		} else {
			pdu.setInvokerCredentials(creds);
		}

		boolean resultOk = encodeAndSend(invokeId, pdu, SCHEDULE_STATUS_REPORT_NAME);

		if (resultOk) {
			// If all fine, notify PDU sent
			notifyPduSent(pdu, SCHEDULE_STATUS_REPORT_NAME, getLastPduSent());

			// Generate state and notify update
			notifyStateUpdate();
		}
	}

	/**
	 * This method requests the transmission of a GET-PARAMETER operation.
	 *
	 * @param parameter the parameter to retrieve
	 */
	public void getParameter(RcfParameterEnum parameter) {
		dispatchFromUser(() -> doGetParameter(parameter));
	}

	private void doGetParameter(RcfParameterEnum parameter) {
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
		RcfGetParameterInvocation pdu = new RcfGetParameterInvocation();
		pdu.setInvokeId(new InvokeId(invokeId));
		//
		pdu.setRcfParameter(new RcfParameterName(parameter.getCode()));

		// Add credentials
		// From the API configuration (remote peers) and SI configuration (responder
		// id), check remote peer and check if authentication must be used.
		Credentials creds = generateCredentials(getResponderIdentifier(), AuthenticationModeEnum.ALL);
		if (creds == null) {
			// Error while generating credentials, set by generateCredentials()
			notifyPduSentError(pdu, GET_PARAMETER_NAME, null);
			notifyStateUpdate();
			return;
		} else {
			pdu.setInvokerCredentials(creds);
		}

		boolean resultOk = encodeAndSend(invokeId, pdu, GET_PARAMETER_NAME);

		if (resultOk) {
			// If all fine, notify PDU sent
			notifyPduSent(pdu, GET_PARAMETER_NAME, getLastPduSent());

			// Generate state and notify update
			notifyStateUpdate();
		}
	}

	protected void handleRcfGetParameterReturn(RcfGetParameterReturn pdu) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND
				|| this.currentState == ServiceInstanceBindingStateEnum.BIND_PENDING
				|| this.currentState == ServiceInstanceBindingStateEnum.UNBIND_PENDING) {
			disconnect("Get parameter return received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, GET_PARAMETER_RETURN_NAME, getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Get parameter return received, but wrong credentials");
			notifyPduReceived(pdu, GET_PARAMETER_RETURN_NAME, getLastPduReceived());
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
			} else if (pdu.getResult().getPositiveResult().getParReqGvcId() != null) {
				if (pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().getUndefined() != null) {
					this.requestedGvcid = null;
				} else {
					GvcId gvcid = pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().getGvcid();
					this.requestedGvcid = new GVCID(gvcid.getSpacecraftId().intValue(),
							gvcid.getVersionNumber().intValue(), gvcid.getVcId().getVirtualChannel() == null ? null
									: gvcid.getVcId().getVirtualChannel().intValue());
				}
			} else if (pdu.getResult().getPositiveResult().getParPermittedGvcidSet() != null) {
				List<GVCID> theList = new LinkedList<>();
				for (MasterChannelComposition mcc : pdu.getResult().getPositiveResult().getParPermittedGvcidSet()
						.getParameterValue().getMasterChannelComposition()) {
					int scId = mcc.getSpacecraftId().intValue();
					int tfvn = mcc.getVersionNumber().intValue();
					if (mcc.getMcOrVcList().getMasterChannel() != null) {
						theList.add(new GVCID(scId, tfvn, null));
					} else {
						for (VcId vcid : mcc.getMcOrVcList().getVcList().getVcId()) {
							theList.add(new GVCID(scId, tfvn, vcid.intValue()));
						}
					}
				}
				this.permittedGvcid = theList;
			} else {
				LOG.warning(getServiceInstanceIdentifier() + ": Get parameter return received, positive result but unknown/unsupported parameter found");
			}
		} else {
			// Dump warning with diagnostic
			LOG.warning(getServiceInstanceIdentifier() + ": Get parameter return received, negative result: "
					+ RcfDiagnosticsStrings.getGetParameterDiagnostic(pdu.getResult().getNegativeResult()));

		}
		// Notify PDU
		notifyPduReceived(pdu, GET_PARAMETER_RETURN_NAME, getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleRcfGetParameterV1toV4Return(RcfGetParameterReturnV1toV4 pdu) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND
				|| this.currentState == ServiceInstanceBindingStateEnum.BIND_PENDING
				|| this.currentState == ServiceInstanceBindingStateEnum.UNBIND_PENDING) {
			disconnect("Get parameter return received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, GET_PARAMETER_RETURN_NAME, getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Get parameter return received, but wrong credentials");
			notifyPduReceived(pdu, GET_PARAMETER_RETURN_NAME, getLastPduReceived());
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
			} else if (pdu.getResult().getPositiveResult().getParReqGvcId() != null) {
				this.requestedGvcid = new GVCID(
						pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().getSpacecraftId()
								.intValue(),
						pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().getVersionNumber()
								.intValue(),
						pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().getVcId()
								.getVirtualChannel() == null ? null
										: pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue()
												.getVcId().getVirtualChannel().intValue());
			} else if (pdu.getResult().getPositiveResult().getParPermittedGvcidSet() != null) {
				List<GVCID> theList = new LinkedList<>();
				for (MasterChannelCompositionV1toV4 mcc : pdu.getResult().getPositiveResult().getParPermittedGvcidSet()
						.getParameterValue().getMasterChannelCompositionV1toV4()) {
					int scId = mcc.getSpacecraftId().intValue();
					int tfvn = mcc.getVersionNumber().intValue();
					if (mcc.getMcOrVcList().getMasterChannel() != null) {
						theList.add(new GVCID(scId, tfvn, null));
					} else {
						for (VcId vcid : mcc.getMcOrVcList().getVcList().getVcId()) {
							theList.add(new GVCID(scId, tfvn, vcid.intValue()));
						}
					}
				}
				this.permittedGvcid = theList;
			} else {
				LOG.warning(getServiceInstanceIdentifier() + ": Get parameter return received, positive result but unknown/unsupported parameter found");
			}
		} else {
			// Dump warning with diagnostic
			LOG.warning(getServiceInstanceIdentifier() + ": Get parameter return received, negative result: "
					+ RcfDiagnosticsStrings.getGetParameterDiagnostic(pdu.getResult().getNegativeResult()));

		}
		// Notify PDU
		notifyPduReceived(pdu, GET_PARAMETER_RETURN_NAME, getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleRcfStartReturn(RcfStartReturn pdu) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.START_PENDING) {
			disconnect("Start return received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, START_RETURN_NAME, getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Start return received, but wrong credentials");
			notifyPduReceived(pdu, START_RETURN_NAME, getLastPduReceived());
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
					+ RcfDiagnosticsStrings.getStartDiagnostic(pdu.getResult().getNegativeResult()));
			// Reset requested GVCID
			this.requestedGvcid = null;
			// Set times
			this.startTime = null;
			this.endTime = null;
			
			// If problems (result negative), BOUND
			setServiceInstanceState(ServiceInstanceBindingStateEnum.READY);
		}
		// Notify PDU
		notifyPduReceived(pdu, START_RETURN_NAME, getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleRcfStopReturn(SleAcknowledgement pdu) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.STOP_PENDING) {
			disconnect("Stop return received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, STOP_RETURN_NAME, getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(pdu.getCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Stop return received, but wrong credentials");
			notifyPduReceived(pdu, STOP_RETURN_NAME, getLastPduReceived());
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
			// Reset requested GVCID
			this.requestedGvcid = null;
			// Set times
			this.startTime = null;
			this.endTime = null;
		} else {
			LOG.warning(getServiceInstanceIdentifier() + ": Stop return received, negative result: " + RcfDiagnosticsStrings.getDiagnostic(pdu.getResult().getNegativeResult()));
			// If problems (result negative), ACTIVE
			setServiceInstanceState(ServiceInstanceBindingStateEnum.ACTIVE);
		}
		// Notify PDU
		notifyPduReceived(pdu, STOP_RETURN_NAME, getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleRcfStatusReport(RcfStatusReportInvocation pdu) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND) {
			disconnect("Status report received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, STATUS_REPORT_NAME, getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(pdu.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Status report received, but wrong credentials");
			notifyPduReceived(pdu, STATUS_REPORT_NAME, getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Set the status report parameters
		this.carrierLockStatus = mapCarrierLockStatus(pdu.getCarrierLockStatus().intValue());
		this.subcarrierLockStatus = mapSubCarrierLockStatus(pdu.getSubcarrierLockStatus().intValue());
		this.symbolSyncLockStatus = mapSymbolSyncLockStatus(pdu.getSymbolSyncLockStatus().intValue());
		this.frameSyncLockStatus = mapFrameSyncLockStatus(pdu.getFrameSyncLockStatus().intValue());
		this.productionStatus = mapProductionStatus(pdu.getProductionStatus().intValue());
		this.numFramesDelivered = pdu.getDeliveredFrameNumber().intValue();

		// Notify PDU
		notifyPduReceived(pdu, STATUS_REPORT_NAME, getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleRcfTransferBuffer(RcfTransferBuffer pdu) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.ACTIVE && this.currentState != ServiceInstanceBindingStateEnum.STOP_PENDING) {
			disconnect("Transfer buffer received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, TRANSFER_BUFFER_NAME, getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		for (FrameOrNotification fon : pdu.getFrameOrNotification()) {
			if (fon.getAnnotatedFrame() != null) {
				RcfTransferDataInvocation tf = fon.getAnnotatedFrame();

				// Validate credentials
				// From the API configuration (remote peers) and SI configuration (remote peer),
				// check remote peer and check if authentication must be used.
				// If so, verify credentials.
				if(!authenticate(tf.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
					disconnect("Transfer data received, but wrong credentials");
					notifyPduReceived(pdu, TRANSFER_DATA_NAME, getLastPduReceived());
					notifyStateUpdate();
					return;
				}

				// Notify PDU
				notifyPduReceived(tf, TRANSFER_DATA_NAME, null);
			} else {
				RcfSyncNotifyInvocation sn = fon.getSyncNotification();

				// Validate credentials
				// From the API configuration (remote peers) and SI configuration (remote peer),
				// check remote peer and check if authentication must be used.
				// If so, verify credentials.
				if(!authenticate(sn.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
					disconnect("Notify received, but wrong credentials");
					notifyPduReceived(pdu, NOTIFY_NAME, getLastPduReceived());
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

	protected void handleRcfStatusReportV1(RcfStatusReportInvocationV1 pdu) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND) {
			disconnect("Status report received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, STATUS_REPORT_NAME, getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(pdu.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Status report received, but wrong credentials");
			notifyPduReceived(pdu, STATUS_REPORT_NAME, getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Set the status report parameters
		this.carrierLockStatus = mapCarrierLockStatus(pdu.getCarrierLockStatus().intValue());
		this.subcarrierLockStatus = mapSubCarrierLockStatus(pdu.getSubcarrierLockStatus().intValue());
		this.symbolSyncLockStatus = mapSymbolSyncLockStatus(pdu.getSymbolSyncLockStatus().intValue());
		this.frameSyncLockStatus = mapFrameSyncLockStatus(pdu.getFrameSyncLockStatus().intValue());
		this.productionStatus = mapProductionStatus(pdu.getProductionStatus().intValue());
		this.numFramesDelivered = pdu.getDeliveredFrameNumber().intValue();

		// Notify PDU
		notifyPduReceived(pdu, STATUS_REPORT_NAME, getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleRcfScheduleStatusReportReturn(SleScheduleStatusReportReturn pdu) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND) {
			disconnect("Schedule status report return received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, SCHEDULE_STATUS_REPORT_RETURN_NAME, getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if(!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Schedule status report return received, but wrong credentials");
			notifyPduReceived(pdu, SCHEDULE_STATUS_REPORT_RETURN_NAME, getLastPduReceived());
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
					+ RcfDiagnosticsStrings.getScheduleStatusReportDiagnostic(pdu.getResult().getNegativeResult()));
		}
		// Notify PDU
		notifyPduReceived(pdu, SCHEDULE_STATUS_REPORT_RETURN_NAME, getLastPduReceived());
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
		RcfServiceInstanceState state = new RcfServiceInstanceState();
		copyCommonState(state);
		state.setCarrierLockStatus(carrierLockStatus);
		state.setDeliveryMode(deliveryMode);
		state.setFrameSyncLockStatus(frameSyncLockStatus);
		state.setLatencyLimit(latencyLimit);
		state.setMinReportingCycle(minReportingCycle);
		state.setNumFramesDelivered(numFramesDelivered);
		state.setPermittedGvcid(new ArrayList<>(permittedGvcid));
		state.setProductionStatus(productionStatus);
		state.setReportingCycle(reportingCycle);
		state.setRequestedGvcid(requestedGvcid);
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
		return ApplicationIdentifierEnum.RCF;
	}

	@Override
	protected void updateHandlersForVersion(int version) {
		this.encDec.useSleVersion(version);
	}

	@Override
	protected void resetState() {
		// Read from configuration, updated via GET_PARAMETER
		this.latencyLimit = getRcfConfiguration().getLatencyLimit();
		this.permittedGvcid = getRcfConfiguration().getPermittedGvcid();
		this.minReportingCycle = getRcfConfiguration().getMinReportingCycle();
		this.returnTimeoutPeriod = getRcfConfiguration().getReturnTimeoutPeriod();
		this.transferBufferSize = getRcfConfiguration().getTransferBufferSize();
		this.deliveryMode = getRcfConfiguration().getDeliveryMode();
		
		// Updated via START and GET_PARAMETER
		this.requestedGvcid = null;
		this.startTime = null;
		this.endTime = null;
		this.reportingCycle = null; // NULL if off, otherwise a value
		
		// Updated via STATUS_REPORT
		this.numFramesDelivered = 0;
		this.frameSyncLockStatus = LockStatusEnum.UNKNOWN;
		this.symbolSyncLockStatus = LockStatusEnum.UNKNOWN;
		this.subcarrierLockStatus = LockStatusEnum.UNKNOWN;
		this.carrierLockStatus = LockStatusEnum.UNKNOWN;
		this.productionStatus = ProductionStatusEnum.UNKNOWN;
	}

	private RcfServiceInstanceConfiguration getRcfConfiguration() {
		return (RcfServiceInstanceConfiguration) this.serviceInstanceConfiguration;
	}
}
