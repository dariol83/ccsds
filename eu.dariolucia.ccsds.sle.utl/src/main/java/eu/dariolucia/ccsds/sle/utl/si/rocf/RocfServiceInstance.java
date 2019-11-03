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

package eu.dariolucia.ccsds.sle.utl.si.rocf;

import com.beanit.jasn1.ber.types.BerInteger;
import com.beanit.jasn1.ber.types.BerNull;
import com.beanit.jasn1.ber.types.BerType;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.incoming.pdus.RocfGetParameterInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.incoming.pdus.RocfStartInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.outgoing.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.structures.*;
import eu.dariolucia.ccsds.sle.utl.config.PeerConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.rocf.RocfServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.encdec.RocfEncDec;
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
 * One object of this class represents an ROCF Service Instance.
 */
public class RocfServiceInstance extends ServiceInstance {

	private static final Logger LOG = Logger.getLogger(RocfServiceInstance.class.getName());

	// Read from configuration, updated via GET_PARAMETER
	private Integer latencyLimit; // NULL if offline, otherwise a value
	private List<GVCID> permittedGvcid;
	private List<Integer> permittedTcVcid;
	private List<RocfControlWordTypeEnum> permittedControlWordTypes;
	private List<RocfUpdateModeEnum> permittedUpdateModes;
	private Integer minReportingCycle;
	private int returnTimeoutPeriod;
	private int transferBufferSize;
	private DeliveryModeEnum deliveryMode = null;

	// Updated via START and GET_PARAMETER
	private GVCID requestedGvcid = null;
	private Integer requestedTcVcid = null;
	private RocfControlWordTypeEnum requestedControlWordType = null;
	private RocfUpdateModeEnum requestedUpdateMode = null;
	private Integer reportingCycle = null; // NULL if off, otherwise a value
	private Date startTime = null;
	private Date endTime = null;

	// Updated via STATUS_REPORT
	private int processedFrameNumber = 0;
	private int deliveredOcfsNumber = 0;
	private LockStatusEnum frameSyncLockStatus = LockStatusEnum.UNKNOWN;
	private LockStatusEnum symbolSyncLockStatus = LockStatusEnum.UNKNOWN;
	private LockStatusEnum subcarrierLockStatus = LockStatusEnum.UNKNOWN;
	private LockStatusEnum carrierLockStatus = LockStatusEnum.UNKNOWN;
	private ProductionStatusEnum productionStatus = ProductionStatusEnum.UNKNOWN;

	// Encoder/decoder
	private final RocfEncDec encDec = new RocfEncDec();

	public RocfServiceInstance(PeerConfiguration apiConfiguration,
                               RocfServiceInstanceConfiguration serviceInstanceConfiguration) {
		super(apiConfiguration, serviceInstanceConfiguration);
	}

	@Override
	protected void setup() {
		// Register handlers
		registerPduReceptionHandler(RocfStartReturn.class, this::handleRocfStartReturn);
		registerPduReceptionHandler(SleAcknowledgement.class, this::handleRocfStopReturn);
		registerPduReceptionHandler(SleScheduleStatusReportReturn.class,
				this::handleRocfScheduleStatusReportReturn);
		registerPduReceptionHandler(RocfStatusReportInvocation.class, this::handleRocfStatusReport);
		registerPduReceptionHandler(RocfTransferBuffer.class, this::handleRocfTransferBuffer);
		registerPduReceptionHandler(RocfGetParameterReturn.class, this::handleRocfGetParameterReturn);
		registerPduReceptionHandler(RocfGetParameterReturnV1toV4.class, this::handleRocfGetParameterV1toV4Return);
	}

	/**
	 * This method requests the transmission of a START operation.
	 *
	 * @param start the start time
	 * @param end the end time
	 * @param requestedGVCID2 the global virtual channel ID
	 * @param tcVcId the requested TC VC ID
	 * @param controlWordType the type of OCF to receive
	 * @param updateMode the requested update mode
	 */
	public void start(Date start, Date end, GVCID requestedGVCID2, Integer tcVcId, RocfControlWordTypeEnum controlWordType,
			RocfUpdateModeEnum updateMode) {
		dispatchFromUser(() -> doStart(start, end, requestedGVCID2, tcVcId, controlWordType, updateMode));
	}

	private void doStart(Date start, Date end, GVCID requestedGVCID2, Integer tcVcId,
			RocfControlWordTypeEnum controlWordType, RocfUpdateModeEnum updateMode) {
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
		RocfStartInvocation pdu = new RocfStartInvocation();
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
		// ControlWordType - TcVcId
		pdu.setControlWordType(new ControlWordType());
		if (controlWordType == RocfControlWordTypeEnum.CLCW) {
			pdu.getControlWordType().setClcw(new TcVcid());
			if (tcVcId != null) {
				pdu.getControlWordType().getClcw().setTcVcid(new VcId(tcVcId.longValue()));
			} else {
				pdu.getControlWordType().getClcw().setNoTcVC(new BerNull());
			}
		} else if (controlWordType == RocfControlWordTypeEnum.NO_CLCW) {
			pdu.getControlWordType().setNotClcw(new BerNull());
		} else if (controlWordType == RocfControlWordTypeEnum.ALL) {
			pdu.getControlWordType().setAllControlWords(new BerNull());
		}

		// Update mode
		pdu.setUpdateMode(new UpdateMode(updateMode.ordinal()));

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
			//
			this.requestedControlWordType = controlWordType;
			this.requestedUpdateMode = updateMode;
			this.requestedTcVcid = tcVcId;

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
	public void getParameter(RocfParameterEnum parameter) {
		dispatchFromUser(() -> doGetParameter(parameter));
	}

	private void doGetParameter(RocfParameterEnum parameter) {
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
		RocfGetParameterInvocation pdu = new RocfGetParameterInvocation();
		pdu.setInvokeId(new InvokeId(invokeId));
		//
		pdu.setRocfParameter(new RocfParameterName(parameter.getCode()));

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

	private void handleRocfGetParameterReturn(RocfGetParameterReturn pdu) {
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
		if (!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
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
			} else if (pdu.getResult().getPositiveResult().getParPermittedRprtTypeSet() != null) {
				List<RocfControlWordTypeEnum> theList = new LinkedList<>();
				for (ControlWordTypeNumber cwtn : pdu.getResult().getPositiveResult().getParPermittedRprtTypeSet()
						.getParameterValue().getControlWordTypeNumber()) {
					theList.add(RocfControlWordTypeEnum.values()[cwtn.intValue()]);
				}
				this.permittedControlWordTypes = theList;
			} else if (pdu.getResult().getPositiveResult().getParPermittedTcVcidSet() != null) {
				List<Integer> theList = new LinkedList<>();
				if (pdu.getResult().getPositiveResult().getParPermittedTcVcidSet().getParameterValue()
						.getTcVcids() != null) {
					for (VcId vcid : pdu.getResult().getPositiveResult().getParPermittedTcVcidSet().getParameterValue()
							.getTcVcids().getVcId()) {
						theList.add(vcid.intValue());
					}
				}
				this.permittedTcVcid = theList;
			} else if (pdu.getResult().getPositiveResult().getParPermittedUpdModeSet() != null) {
				List<RocfUpdateModeEnum> theList = new LinkedList<>();
				for (UpdateMode um : pdu.getResult().getPositiveResult().getParPermittedUpdModeSet().getParameterValue()
						.getUpdateMode()) {
					theList.add(RocfUpdateModeEnum.values()[um.intValue()]);
				}
				this.permittedUpdateModes = theList;
			} else if (pdu.getResult().getPositiveResult().getParReqControlWordType() != null) {
				this.requestedControlWordType = RocfControlWordTypeEnum.values()[pdu.getResult().getPositiveResult()
						.getParReqControlWordType().getParameterValue().intValue()];
			} else if (pdu.getResult().getPositiveResult().getParReqTcVcid() != null) {
				this.requestedTcVcid = pdu.getResult().getPositiveResult().getParReqTcVcid().getParameterValue()
						.getTcVcid() != null
								? pdu.getResult().getPositiveResult().getParReqTcVcid().getParameterValue().getTcVcid()
										.intValue()
								: null;
			} else if (pdu.getResult().getPositiveResult().getParReqUpdateMode() != null) {
				this.requestedUpdateMode = RocfUpdateModeEnum.values()[pdu.getResult().getPositiveResult().getParReqUpdateMode().getParameterValue().intValue()];
			} else {
				LOG.warning(getServiceInstanceIdentifier() + ": Get parameter return received, positive result but unknown/unsupported parameter found");
			}
		} else {
			// Dump warning with diagnostic
			LOG.warning(getServiceInstanceIdentifier() + ": Get parameter return received, negative result: "
					+ RocfDiagnosticsStrings.getGetParameterDiagnostic(pdu.getResult().getNegativeResult()));
		}
		// Notify PDU
		notifyPduReceived(pdu, GET_PARAMETER_RETURN_NAME, getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	private void handleRocfGetParameterV1toV4Return(RocfGetParameterReturnV1toV4 pdu) {
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
		if (!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
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
			} else if (pdu.getResult().getPositiveResult().getParPermittedRprtTypeSet() != null) {
				List<RocfControlWordTypeEnum> theList = new LinkedList<>();
				for (ControlWordTypeNumber cwtn : pdu.getResult().getPositiveResult().getParPermittedRprtTypeSet()
						.getParameterValue().getControlWordTypeNumber()) {
					theList.add(RocfControlWordTypeEnum.values()[cwtn.intValue()]);
				}
				this.permittedControlWordTypes = theList;
			} else if (pdu.getResult().getPositiveResult().getParPermittedTcVcidSet() != null) {
				List<Integer> theList = new LinkedList<>();
				if (pdu.getResult().getPositiveResult().getParPermittedTcVcidSet().getParameterValue()
						.getTcVcids() != null) {
					for (VcId vcid : pdu.getResult().getPositiveResult().getParPermittedTcVcidSet().getParameterValue()
							.getTcVcids().getVcId()) {
						theList.add(vcid.intValue());
					}
				}
				this.permittedTcVcid = theList;
			} else if (pdu.getResult().getPositiveResult().getParPermittedUpdModeSet() != null) {
				List<RocfUpdateModeEnum> theList = new LinkedList<>();
				for (UpdateMode um : pdu.getResult().getPositiveResult().getParPermittedUpdModeSet().getParameterValue()
						.getUpdateMode()) {
					theList.add(RocfUpdateModeEnum.values()[um.intValue()]);
				}
				this.permittedUpdateModes = theList;
			} else if (pdu.getResult().getPositiveResult().getParReqControlWordType() != null) {
				this.requestedControlWordType = RocfControlWordTypeEnum.values()[pdu.getResult().getPositiveResult()
						.getParReqControlWordType().getParameterValue().intValue()];
			} else if (pdu.getResult().getPositiveResult().getParReqTcVcid() != null) {
				this.requestedTcVcid = pdu.getResult().getPositiveResult().getParReqTcVcid().getParameterValue()
						.getTcVcid().getTcVcid() != null
						? pdu.getResult().getPositiveResult().getParReqTcVcid().getParameterValue().getTcVcid()
						.getTcVcid().intValue()
						: null;
			} else if (pdu.getResult().getPositiveResult().getParReqUpdateMode() != null) {
				this.requestedUpdateMode = RocfUpdateModeEnum.values()[pdu.getResult().getPositiveResult().getParReqUpdateMode().getParameterValue().intValue()];
			} else {
				LOG.warning(getServiceInstanceIdentifier() + ": Get parameter return received, positive result but unknown/unsupported parameter found");
			}
		} else {
			// Dump warning with diagnostic
			LOG.warning(getServiceInstanceIdentifier() + ": Get parameter return received, negative result: "
					+ RocfDiagnosticsStrings.getGetParameterDiagnostic(pdu.getResult().getNegativeResult()));
		}
		// Notify PDU
		notifyPduReceived(pdu, GET_PARAMETER_RETURN_NAME, getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	private void handleRocfStartReturn(RocfStartReturn pdu) {
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
		if (!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
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
			LOG.warning("Start return received, negative result: "
					+ RocfDiagnosticsStrings.getStartDiagnostic(pdu.getResult().getNegativeResult()));
			// Reset requested GVCID
			this.requestedGvcid = null;
			this.requestedControlWordType = null;
			this.requestedTcVcid = null;
			this.requestedUpdateMode = null;
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

	private void handleRocfStopReturn(SleAcknowledgement pdu) {
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
		if (!authenticate(pdu.getCredentials(), AuthenticationModeEnum.ALL)) {
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
			this.requestedControlWordType = null;
			this.requestedTcVcid = null;
			this.requestedUpdateMode = null;
			// Set times
			this.startTime = null;
			this.endTime = null;
		} else {
			LOG.warning(getServiceInstanceIdentifier() + ": Stop return received, negative result: "
					+ RocfDiagnosticsStrings.getDiagnostic(pdu.getResult().getNegativeResult()));
			// If problems (result negative), ACTIVE
			setServiceInstanceState(ServiceInstanceBindingStateEnum.ACTIVE);
		}
		// Notify PDU
		notifyPduReceived(pdu, STOP_RETURN_NAME, getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	private void handleRocfStatusReport(RocfStatusReportInvocation pdu) {
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
		if (!authenticate(pdu.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
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
		this.deliveredOcfsNumber = pdu.getDeliveredOcfsNumber().intValue();
		this.processedFrameNumber = pdu.getProcessedFrameNumber().intValue();

		// Notify PDU
		notifyPduReceived(pdu, STATUS_REPORT_NAME, getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	private void handleRocfTransferBuffer(RocfTransferBuffer pdu) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.ACTIVE && this.currentState != ServiceInstanceBindingStateEnum.STOP_PENDING) {
			disconnect("Transfer buffer received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, TRANSFER_BUFFER_NAME, getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		for (OcfOrNotification fon : pdu.getOcfOrNotification()) {
			if (fon.getAnnotatedOcf() != null) {
				RocfTransferDataInvocation tf = fon.getAnnotatedOcf();

				// Validate credentials
				// From the API configuration (remote peers) and SI configuration (remote peer),
				// check remote peer and check if authentication must be used.
				// If so, verify credentials.
				if (!authenticate(tf.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
					disconnect("Transfer data received, but wrong credentials");
					notifyPduReceived(pdu, TRANSFER_DATA_NAME, getLastPduReceived());
					notifyStateUpdate();
					return;
				}

				// Notify PDU
				notifyPduReceived(tf, TRANSFER_DATA_NAME, null);
			} else {
				RocfSyncNotifyInvocation sn = fon.getSyncNotification();

				// Validate credentials
				// From the API configuration (remote peers) and SI configuration (remote peer),
				// check remote peer and check if authentication must be used.
				// If so, verify credentials.
				if (!authenticate(sn.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
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

	private void handleRocfScheduleStatusReportReturn(SleScheduleStatusReportReturn pdu) {
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
		if (!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
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
					+ RocfDiagnosticsStrings.getScheduleStatusReportDiagnostic(pdu.getResult().getNegativeResult()));
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
		RocfServiceInstanceState state = new RocfServiceInstanceState();
		copyCommonState(state);
		state.setCarrierLockStatus(carrierLockStatus);
		state.setDeliveryMode(deliveryMode);
		state.setFrameSyncLockStatus(frameSyncLockStatus);
		state.setLatencyLimit(latencyLimit);
		state.setMinReportingCycle(minReportingCycle);
		state.setDeliveredOcfsNumber(deliveredOcfsNumber);
		state.setProcessedFrameNumber(processedFrameNumber);
		state.setPermittedGvcid(new ArrayList<>(permittedGvcid));
		state.setPermittedControlWordTypes(new ArrayList<>(permittedControlWordTypes));
		state.setPermittedTcVcid(new ArrayList<>(permittedTcVcid));
		state.setPermittedUpdateModes(new ArrayList<>(permittedUpdateModes));
		state.setProductionStatus(productionStatus);
		state.setReportingCycle(reportingCycle);
		state.setRequestedGvcid(requestedGvcid);
		state.setRequestedTcVcid(requestedTcVcid);
		state.setRequestedControlWordType(requestedControlWordType);
		state.setRequestedUpdateMode(requestedUpdateMode);
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
		return ApplicationIdentifierEnum.ROCF;
	}

	@Override
	protected void updateHandlersForVersion(int version) {
		this.encDec.useSleVersion(version);
	}

	@Override
	protected void resetState() {
		// Read from configuration, updated via GET_PARAMETER
		this.latencyLimit = getRocfConfiguration().getLatencyLimit();
		this.permittedGvcid = getRocfConfiguration().getPermittedGvcid();
		this.permittedTcVcid = getRocfConfiguration().getPermittedTcVcids();
		this.permittedControlWordTypes = getRocfConfiguration().getPermittedControlWordTypes();
		this.permittedUpdateModes = getRocfConfiguration().getPermittedUpdateModes();
		this.minReportingCycle = getRocfConfiguration().getMinReportingCycle();
		this.returnTimeoutPeriod = getRocfConfiguration().getReturnTimeoutPeriod();
		this.transferBufferSize = getRocfConfiguration().getTransferBufferSize();
		this.deliveryMode = getRocfConfiguration().getDeliveryMode();

		// Updated via START and GET_PARAMETER
		this.requestedGvcid = null;
		this.requestedControlWordType = null;
		this.requestedTcVcid = null;
		this.requestedUpdateMode = null;
		this.startTime = null;
		this.endTime = null;
		this.reportingCycle = null; // NULL if off, otherwise a value

		// Updated via STATUS_REPORT
		this.deliveredOcfsNumber = 0;
		this.processedFrameNumber = 0;
		this.frameSyncLockStatus = LockStatusEnum.UNKNOWN;
		this.symbolSyncLockStatus = LockStatusEnum.UNKNOWN;
		this.subcarrierLockStatus = LockStatusEnum.UNKNOWN;
		this.carrierLockStatus = LockStatusEnum.UNKNOWN;
		this.productionStatus = ProductionStatusEnum.UNKNOWN;
	}

	private RocfServiceInstanceConfiguration getRocfConfiguration() {
		return (RocfServiceInstanceConfiguration) this.serviceInstanceConfiguration;
	}
}
