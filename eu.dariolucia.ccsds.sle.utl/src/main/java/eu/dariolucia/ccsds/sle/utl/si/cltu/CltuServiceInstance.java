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

package eu.dariolucia.ccsds.sle.utl.si.cltu;

import com.beanit.jasn1.ber.types.BerNull;
import com.beanit.jasn1.ber.types.BerOctetString;
import com.beanit.jasn1.ber.types.BerType;
import com.beanit.jasn1.ber.types.string.BerVisibleString;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.incoming.pdus.CltuGetParameterInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.incoming.pdus.CltuStartInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.incoming.pdus.CltuThrowEventInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.incoming.pdus.CltuTransferDataInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.outgoing.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.CltuLastOk.CltuOk;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.CltuLastProcessed.CltuProcessed;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.*;
import eu.dariolucia.ccsds.sle.utl.config.PeerConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.cltu.CltuServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.encdec.CltuEncDec;
import eu.dariolucia.ccsds.sle.utl.pdu.PduFactoryUtil;
import eu.dariolucia.ccsds.sle.utl.si.*;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuNotification.CltuNotificationTypeEnum;

import java.io.IOException;
import java.util.Date;
import java.util.logging.Logger;

public class CltuServiceInstance extends ServiceInstance {

	private static final Logger LOG = Logger.getLogger(CltuServiceInstance.class.getName());

	// Configuration or GET_PARAMETER
	private Integer maxCltuLength;
	private Integer minCltuDelay;
	private boolean bitlockRequired;
	private boolean rfAvailableRequired;
	private boolean protocolAbortClearEnabled;
	private Integer minReportingCycle;
	private Integer reportingCycle;
	private int returnTimeoutPeriod;

	//
	private Integer acquisitionSequenceLength;
	private GVCID clcwGlobalVcId; // can be null -> not configured
	private String clcwPhysicalChannel; // can be null -> not configured
	private Long cltuIdentification;
	private Long eventInvocationIdentification;
	private Long modulationFrequency;
	private Integer modulationIndex;
	private CltuNotificationModeEnum notificationMode;
	private Integer plop1IdleSequenceLength;
	private CltuPlopInEffectEnum plopInEffect;
	private CltuProtocolAbortModeEnum protocolAbortMode;
	private Integer subcarrierToBitRateRation;
	private DeliveryModeEnum deliveryMode;
	// START
	private Long firstCltuIdentification;
	// STATUS_REPORT
	private CltuLastProcessed lastProcessed; // also from async notify
	private CltuLastOk lastOk; // also from async notify
	private CltuProductionStatusEnum productionStatus; // also from async notify
	private CltuUplinkStatusEnum uplinkStatus; // also from async notify
	private Long nbCltuReceived;
	private Long nbCltuProcessed;
	private Long nbCltuRadiated;
	private Long bufferAvailable;
	// ASYNC_NOTIFY
	private CltuNotification notification;

	// Encoder/decoder
	private final CltuEncDec encDec = new CltuEncDec();

	public CltuServiceInstance(PeerConfiguration apiConfiguration,
                               CltuServiceInstanceConfiguration serviceInstanceConfiguration) throws Exception {
		super(apiConfiguration, serviceInstanceConfiguration);
	}

	@Override
	protected void setup() {
		// Register handlers
		registerPduReceptionHandler(CltuStartReturn.class, this::handleCltuStartReturn);
		registerPduReceptionHandler(SleAcknowledgement.class, this::handleCltuStopReturn);
		registerPduReceptionHandler(SleScheduleStatusReportReturn.class,
				this::handleCltuScheduleStatusReportReturn);
		registerPduReceptionHandler(CltuStatusReportInvocation.class, this::handleCltuStatusReport);
		registerPduReceptionHandler(CltuGetParameterReturn.class, this::handleCltuGetParameterReturn);
		registerPduReceptionHandler(CltuGetParameterReturnV1toV3.class, this::handleCltuGetParameterV1toV3Return);
		registerPduReceptionHandler(CltuGetParameterReturnV4.class, this::handleCltuGetParameterV4Return);
		registerPduReceptionHandler(CltuThrowEventReturn.class, this::handleCltuThrowEventReturn);
		registerPduReceptionHandler(CltuTransferDataReturn.class, this::handleCltuTransferDataReturn);
		registerPduReceptionHandler(CltuAsyncNotifyInvocation.class, this::handleCltuAsyncNotify);

		// Update status parameters from configuration
		resetState();
	}

	public void start(Long firstCltuId) {
		dispatchFromUser(() -> doStart(firstCltuId));
	}

	private void doStart(Long firstCltuId) {
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
		if (firstCltuId == null) {
			setError("Start requested, but first CLTU ID is null, not compatible with selected SLE version");
			notifyStateUpdate();
			return;
		}

		CltuStartInvocation pdu = new CltuStartInvocation();
		pdu.setInvokeId(new InvokeId(invokeId));
		// GVCID
		pdu.setFirstCltuIdentification(new CltuIdentification(firstCltuId));

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
			// Set the first cltu identification
			this.firstCltuIdentification = firstCltuId;
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

	public void scheduleStatusReport(boolean isStop, Integer period) throws Exception {
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

	public void transferData(long cltuIdentificationId, Date earliestTxTime, Date latestTxTime, long durationMicrosec,
			boolean produceNotification, byte[] data) throws Exception {
		dispatchFromUser(() -> doTransferData(cltuIdentificationId, earliestTxTime, latestTxTime, durationMicrosec,
				produceNotification, data));
	}

	private void doTransferData(long cltuIdentificationId, Date earliestTxTime, Date latestTxTime,
			long durationMicrosec, boolean produceNotification, byte[] data) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.ACTIVE) {
			setError("Transfer data requested, but service instance is in state "
					+ this.currentState);
			notifyStateUpdate();
			return;
		}

		int invokeId = this.invokeIdSequencer.incrementAndGet();

		// Create operation
		CltuTransferDataInvocation pdu = new CltuTransferDataInvocation();
		pdu.setInvokeId(new InvokeId(invokeId));
		//
		pdu.setCltuIdentification(new CltuIdentification(cltuIdentificationId));
		pdu.setEarliestTransmissionTime(new ConditionalTime());
		if (earliestTxTime != null) {
			pdu.getEarliestTransmissionTime().setKnown(new Time());
			pdu.getEarliestTransmissionTime().getKnown()
					.setCcsdsFormat(new TimeCCSDS(PduFactoryUtil.buildCDSTime(earliestTxTime.getTime(), 0)));
		} else {
			pdu.getEarliestTransmissionTime().setUndefined(new BerNull());
		}
		if (latestTxTime != null) {
			pdu.getLatestTransmissionTime().setKnown(new Time());
			pdu.getLatestTransmissionTime().getKnown()
					.setCcsdsFormat(new TimeCCSDS(PduFactoryUtil.buildCDSTime(latestTxTime.getTime(), 0)));
		} else {
			pdu.getLatestTransmissionTime().setUndefined(new BerNull());
		}
		pdu.setDelayTime(new Duration(durationMicrosec));
		pdu.setSlduRadiationNotification(new SlduStatusNotification(produceNotification ? 0 : 1));
		pdu.setCltuData(new CltuData(data));

		// Add credentials
		// From the API configuration (remote peers) and SI configuration (responder
		// id), check remote peer and check if authentication must be used.
		Credentials creds = generateCredentials(getResponderIdentifier(), AuthenticationModeEnum.ALL);
		if (creds == null) {
			// Error while generating credentials, set by generateCredentials()
			notifyPduSentError(pdu, "TRANSFER-DATA", null);
			notifyStateUpdate();
			return;
		} else {
			pdu.setInvokerCredentials(creds);
		}

		boolean resultOk = encodeAndSend(invokeId, pdu, "TRANSFER-DATA");

		if (resultOk) {
			// If all fine, notify PDU sent
			notifyPduSent(pdu, "TRANSFER-DATA", getLastPduSent());

			// Generate state and notify update
			notifyStateUpdate();
		}
	}

	public void throwEvent(long eventInvocationId, int eventId, byte[] eventQualifier) throws Exception {
		dispatchFromUser(() -> doThrowEvent(eventInvocationId, eventId, eventQualifier));
	}

	private void doThrowEvent(long eventInvocationId, int eventId, byte[] eventQualifier) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND
				|| this.currentState == ServiceInstanceBindingStateEnum.BIND_PENDING
				|| this.currentState == ServiceInstanceBindingStateEnum.UNBIND_PENDING) {
			setError("Throw event requested, but service instance is in state "
					+ this.currentState);
			notifyStateUpdate();
			return;
		}

		int invokeId = this.invokeIdSequencer.incrementAndGet();

		// Create operation
		CltuThrowEventInvocation pdu = new CltuThrowEventInvocation();
		pdu.setInvokeId(new InvokeId(invokeId));
		//
		pdu.setEventInvocationIdentification(new EventInvocationId(eventInvocationId));
		pdu.setEventIdentifier(new IntPosShort(eventId));
		pdu.setEventQualifier(new BerOctetString(eventQualifier));

		// Add credentials
		// From the API configuration (remote peers) and SI configuration (responder
		// id), check remote peer and check if authentication must be used.
		Credentials creds = generateCredentials(getResponderIdentifier(), AuthenticationModeEnum.ALL);
		if (creds == null) {
			// Error while generating credentials, set by generateCredentials()
			notifyPduSentError(pdu, "THROW-EVENT", null);
			notifyStateUpdate();
			return;
		} else {
			pdu.setInvokerCredentials(creds);
		}

		boolean resultOk = encodeAndSend(invokeId, pdu, "THROW-EVENT");

		if (resultOk) {
			// If all fine, notify PDU sent
			notifyPduSent(pdu, "THROW-EVENT", getLastPduSent());

			// Generate state and notify update
			notifyStateUpdate();
		}
	}

	public void getParameter(CltuParameterEnum parameter) throws Exception {
		dispatchFromUser(() -> doGetParameter(parameter));
	}

	private void doGetParameter(CltuParameterEnum parameter) {
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
		CltuGetParameterInvocation pdu = new CltuGetParameterInvocation();
		pdu.setInvokeId(new InvokeId(invokeId));
		//
		pdu.setCltuParameter(new CltuParameterName(parameter.getCode()));

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

	protected void handleCltuGetParameterReturn(CltuGetParameterReturn pdu) {
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
		if (!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
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
			if (pdu.getResult().getPositiveResult().getParAcquisitionSequenceLength() != null) {
				int val = pdu.getResult().getPositiveResult().getParAcquisitionSequenceLength().getParameterValue()
						.intValue();
				this.acquisitionSequenceLength = val;
			} else if (pdu.getResult().getPositiveResult().getParBitLockRequired() != null) {
				this.bitlockRequired = pdu.getResult().getPositiveResult().getParBitLockRequired().getParameterValue()
						.intValue() == 0;
			} else if (pdu.getResult().getPositiveResult().getParClcwGlobalVcId() != null) {
				GvcId val = pdu.getResult().getPositiveResult().getParClcwGlobalVcId().getParameterValue()
						.getCongigured();
				if (val != null) {
					this.clcwGlobalVcId = new GVCID(val.getSpacecraftId().intValue(), val.getVersionNumber().intValue(),
							val.getVcId().getVirtualChannel() != null ? val.getVcId().getVirtualChannel().intValue()
									: null);
				} else {
					this.clcwGlobalVcId = null;
				}
			} else if (pdu.getResult().getPositiveResult().getParClcwPhysicalChannel() != null) {
				BerVisibleString val = pdu.getResult().getPositiveResult().getParClcwPhysicalChannel()
						.getParameterValue().getConfigured();
				if (val != null) {
					this.clcwPhysicalChannel = val.toString();
				} else {
					this.clcwPhysicalChannel = null;
				}
			} else if (pdu.getResult().getPositiveResult().getParDeliveryMode() != null) {
				DeliveryModeEnum val = DeliveryModeEnum.values()[pdu.getResult().getPositiveResult()
						.getParDeliveryMode().getParameterValue().intValue()];
				this.deliveryMode = val;
			} else if (pdu.getResult().getPositiveResult().getParCltuIdentification() != null) {
				this.cltuIdentification = pdu.getResult().getPositiveResult().getParCltuIdentification()
						.getParameterValue().longValue();
			} else if (pdu.getResult().getPositiveResult().getParEventInvocationIdentification() != null) {
				this.eventInvocationIdentification = pdu.getResult().getPositiveResult()
						.getParEventInvocationIdentification().getParameterValue().longValue();
			} else if (pdu.getResult().getPositiveResult().getParMaximumCltuLength() != null) {
				this.maxCltuLength = pdu.getResult().getPositiveResult().getParMaximumCltuLength().getParameterValue()
						.intValue();
			} else if (pdu.getResult().getPositiveResult().getParMinimumDelayTime() != null) {
				this.minCltuDelay = pdu.getResult().getPositiveResult().getParMinimumDelayTime().getParameterValue()
						.intValue();
			} else if (pdu.getResult().getPositiveResult().getParMinReportingCycle() != null) {
				if (pdu.getResult().getPositiveResult().getParMinReportingCycle().getParameterValue() != null) {
					this.minReportingCycle = pdu.getResult().getPositiveResult().getParMinReportingCycle()
							.getParameterValue().intValue();
				}
			} else if (pdu.getResult().getPositiveResult().getParModulationFrequency() != null) {
				this.modulationFrequency = pdu.getResult().getPositiveResult().getParModulationFrequency()
						.getParameterValue().longValue();
			} else if (pdu.getResult().getPositiveResult().getParModulationIndex() != null) {
				this.modulationIndex = pdu.getResult().getPositiveResult().getParModulationIndex().getParameterValue()
						.intValue();
			} else if (pdu.getResult().getPositiveResult().getParNotificationMode() != null) {
				CltuNotificationModeEnum val = CltuNotificationModeEnum.values()[pdu.getResult().getPositiveResult()
						.getParNotificationMode().getParameterValue().intValue()];
				this.notificationMode = val;
			} else if (pdu.getResult().getPositiveResult().getParPlop1IdleSequenceLength() != null) {
				this.plop1IdleSequenceLength = pdu.getResult().getPositiveResult().getParPlop1IdleSequenceLength()
						.getParameterValue().intValue();
			} else if (pdu.getResult().getPositiveResult().getParPlopInEffect() != null) {
				CltuPlopInEffectEnum val = CltuPlopInEffectEnum.values()[pdu.getResult().getPositiveResult()
						.getParPlopInEffect().getParameterValue().intValue()];
				this.plopInEffect = val;
			} else if (pdu.getResult().getPositiveResult().getParProtocolAbortMode() != null) {
				CltuProtocolAbortModeEnum val = CltuProtocolAbortModeEnum.values()[pdu.getResult().getPositiveResult()
						.getParProtocolAbortMode().getParameterValue().intValue()];
				this.protocolAbortMode = val;
			} else if (pdu.getResult().getPositiveResult().getParReportingCycle() != null) {
				if (pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue()
						.getPeriodicReportingOff() != null) {
					this.reportingCycle = null;
				} else {
					int val = pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue()
							.getPeriodicReportingOn().intValue();
					this.reportingCycle = val;
				}
			} else if (pdu.getResult().getPositiveResult().getParReturnTimeout() != null) {
				int val = pdu.getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue();
				this.returnTimeoutPeriod = val;
			} else if (pdu.getResult().getPositiveResult().getParRfAvailableRequired() != null) {
				this.rfAvailableRequired = pdu.getResult().getPositiveResult().getParRfAvailableRequired()
						.getParameterValue().intValue() == 0;
			} else if (pdu.getResult().getPositiveResult().getParSubcarrierToBitRateRatio() != null) {
				this.subcarrierToBitRateRation = pdu.getResult().getPositiveResult().getParSubcarrierToBitRateRatio()
						.getParameterValue().intValue();
			} else {
				LOG.warning(getServiceInstanceIdentifier() + ": Get parameter return received, positive result but unknown/unsupported parameter found");
			}
		} else {
			// Dump warning with diagnostic
			LOG.warning(getServiceInstanceIdentifier() + ": Get parameter return received, negative result: "
					+ CltuDiagnosticsStrings.getGetParameterDiagnostic(pdu.getResult().getNegativeResult()));

		}
		// Notify PDU
		notifyPduReceived(pdu, "GET-PARAMETER-RETURN", getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleCltuGetParameterV1toV3Return(CltuGetParameterReturnV1toV3 pdu) {
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
		if (!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
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
			if (pdu.getResult().getPositiveResult().getParBitLockRequired() != null) {
				this.bitlockRequired = pdu.getResult().getPositiveResult().getParBitLockRequired().getParameterValue()
						.intValue() == 0;
			} else if (pdu.getResult().getPositiveResult().getParDeliveryMode() != null) {
				DeliveryModeEnum val = DeliveryModeEnum.values()[pdu.getResult().getPositiveResult()
						.getParDeliveryMode().getParameterValue().intValue()];
				this.deliveryMode = val;
			} else if (pdu.getResult().getPositiveResult().getParCltuIdentification() != null) {
				this.cltuIdentification = pdu.getResult().getPositiveResult().getParCltuIdentification()
						.getParameterValue().longValue();
			} else if (pdu.getResult().getPositiveResult().getParEventInvocationIdentification() != null) {
				this.eventInvocationIdentification = pdu.getResult().getPositiveResult()
						.getParEventInvocationIdentification().getParameterValue().longValue();
			} else if (pdu.getResult().getPositiveResult().getParMaximumCltuLength() != null) {
				this.maxCltuLength = pdu.getResult().getPositiveResult().getParMaximumCltuLength().getParameterValue()
						.intValue();
			} else if (pdu.getResult().getPositiveResult().getParModulationFrequency() != null) {
				this.modulationFrequency = pdu.getResult().getPositiveResult().getParModulationFrequency()
						.getParameterValue().longValue();
			} else if (pdu.getResult().getPositiveResult().getParModulationIndex() != null) {
				this.modulationIndex = pdu.getResult().getPositiveResult().getParModulationIndex().getParameterValue()
						.intValue();
			} else if (pdu.getResult().getPositiveResult().getParPlopInEffect() != null) {
				CltuPlopInEffectEnum val = CltuPlopInEffectEnum.values()[pdu.getResult().getPositiveResult()
						.getParPlopInEffect().getParameterValue().intValue()];
				this.plopInEffect = val;
			} else if (pdu.getResult().getPositiveResult().getParReportingCycle() != null) {
				if (pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue()
						.getPeriodicReportingOff() != null) {
					this.reportingCycle = null;
				} else {
					int val = pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue()
							.getPeriodicReportingOn().intValue();
					this.reportingCycle = val;
				}
			} else if (pdu.getResult().getPositiveResult().getParReturnTimeout() != null) {
				int val = pdu.getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue();
				this.returnTimeoutPeriod = val;
			} else if (pdu.getResult().getPositiveResult().getParRfAvailableRequired() != null) {
				this.rfAvailableRequired = pdu.getResult().getPositiveResult().getParRfAvailableRequired()
						.getParameterValue().intValue() == 0;
			} else if (pdu.getResult().getPositiveResult().getParSubcarrierToBitRateRatio() != null) {
				this.subcarrierToBitRateRation = pdu.getResult().getPositiveResult().getParSubcarrierToBitRateRatio()
						.getParameterValue().intValue();
			} else {
				LOG.warning(getServiceInstanceIdentifier() + ": Get parameter return received, positive result but unknown/unsupported parameter found");
			}
		} else {
			// Dump warning with diagnostic
			LOG.warning(getServiceInstanceIdentifier() + ": Get parameter return received, negative result: "
					+ CltuDiagnosticsStrings.getGetParameterDiagnostic(pdu.getResult().getNegativeResult()));

		}
		// Notify PDU
		notifyPduReceived(pdu, "GET-PARAMETER-RETURN", getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleCltuGetParameterV4Return(CltuGetParameterReturnV4 pdu) {
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
		if (!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
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
			if (pdu.getResult().getPositiveResult().getParAcquisitionSequenceLength() != null) {
				int val = pdu.getResult().getPositiveResult().getParAcquisitionSequenceLength().getParameterValue()
						.intValue();
				this.acquisitionSequenceLength = val;
			} else if (pdu.getResult().getPositiveResult().getParBitLockRequired() != null) {
				this.bitlockRequired = pdu.getResult().getPositiveResult().getParBitLockRequired().getParameterValue()
						.intValue() == 0;
			} else if (pdu.getResult().getPositiveResult().getParClcwPhysicalChannel() != null) {
				BerVisibleString val = pdu.getResult().getPositiveResult().getParClcwPhysicalChannel()
						.getParameterValue();
				if (val != null) {
					this.clcwPhysicalChannel = val.toString();
				} else {
					this.clcwPhysicalChannel = null;
				}
			} else if (pdu.getResult().getPositiveResult().getParDeliveryMode() != null) {
				DeliveryModeEnum val = DeliveryModeEnum.values()[pdu.getResult().getPositiveResult()
						.getParDeliveryMode().getParameterValue().intValue()];
				this.deliveryMode = val;
			} else if (pdu.getResult().getPositiveResult().getParCltuIdentification() != null) {
				this.cltuIdentification = pdu.getResult().getPositiveResult().getParCltuIdentification()
						.getParameterValue().longValue();
			} else if (pdu.getResult().getPositiveResult().getParEventInvocationIdentification() != null) {
				this.eventInvocationIdentification = pdu.getResult().getPositiveResult()
						.getParEventInvocationIdentification().getParameterValue().longValue();
			} else if (pdu.getResult().getPositiveResult().getParMaximumCltuLength() != null) {
				this.maxCltuLength = pdu.getResult().getPositiveResult().getParMaximumCltuLength().getParameterValue()
						.intValue();
			} else if (pdu.getResult().getPositiveResult().getParMinimumDelayTime() != null) {
				this.minCltuDelay = pdu.getResult().getPositiveResult().getParMinimumDelayTime().getParameterValue()
						.intValue();
			} else if (pdu.getResult().getPositiveResult().getParModulationFrequency() != null) {
				this.modulationFrequency = pdu.getResult().getPositiveResult().getParModulationFrequency()
						.getParameterValue().longValue();
			} else if (pdu.getResult().getPositiveResult().getParModulationIndex() != null) {
				this.modulationIndex = pdu.getResult().getPositiveResult().getParModulationIndex().getParameterValue()
						.intValue();
			} else if (pdu.getResult().getPositiveResult().getParNotificationMode() != null) {
				CltuNotificationModeEnum val = CltuNotificationModeEnum.values()[pdu.getResult().getPositiveResult()
						.getParNotificationMode().getParameterValue().intValue()];
				this.notificationMode = val;
			} else if (pdu.getResult().getPositiveResult().getParPlop1IdleSequenceLength() != null) {
				this.plop1IdleSequenceLength = pdu.getResult().getPositiveResult().getParPlop1IdleSequenceLength()
						.getParameterValue().intValue();
			} else if (pdu.getResult().getPositiveResult().getParPlopInEffect() != null) {
				CltuPlopInEffectEnum val = CltuPlopInEffectEnum.values()[pdu.getResult().getPositiveResult()
						.getParPlopInEffect().getParameterValue().intValue()];
				this.plopInEffect = val;
			} else if (pdu.getResult().getPositiveResult().getParProtocolAbortMode() != null) {
				CltuProtocolAbortModeEnum val = CltuProtocolAbortModeEnum.values()[pdu.getResult().getPositiveResult()
						.getParProtocolAbortMode().getParameterValue().intValue()];
				this.protocolAbortMode = val;
			} else if (pdu.getResult().getPositiveResult().getParReportingCycle() != null) {
				if (pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue()
						.getPeriodicReportingOff() != null) {
					this.reportingCycle = null;
				} else {
					int val = pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue()
							.getPeriodicReportingOn().intValue();
					this.reportingCycle = val;
				}
			} else if (pdu.getResult().getPositiveResult().getParReturnTimeout() != null) {
				int val = pdu.getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue();
				this.returnTimeoutPeriod = val;
			} else if (pdu.getResult().getPositiveResult().getParRfAvailableRequired() != null) {
				this.rfAvailableRequired = pdu.getResult().getPositiveResult().getParRfAvailableRequired()
						.getParameterValue().intValue() == 0;
			} else if (pdu.getResult().getPositiveResult().getParSubcarrierToBitRateRatio() != null) {
				this.subcarrierToBitRateRation = pdu.getResult().getPositiveResult().getParSubcarrierToBitRateRatio()
						.getParameterValue().intValue();
			} else {
				LOG.warning(getServiceInstanceIdentifier() + ": Get parameter return received, positive result but unknown/unsupported parameter found");
			}
		} else {
			// Dump warning with diagnostic
			LOG.warning(getServiceInstanceIdentifier() + ": Get parameter return received, negative result: "
					+ CltuDiagnosticsStrings.getGetParameterDiagnostic(pdu.getResult().getNegativeResult()));

		}
		// Notify PDU
		notifyPduReceived(pdu, "GET-PARAMETER-RETURN", getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleCltuStartReturn(CltuStartReturn pdu) {
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
		if (!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
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
					+ CltuDiagnosticsStrings.getStartDiagnostic(pdu.getResult().getNegativeResult()));
			// Reset requested GVCID
			this.firstCltuIdentification = null;

			// If problems (result negative), BOUND
			setServiceInstanceState(ServiceInstanceBindingStateEnum.READY);
		}
		// Notify PDU
		notifyPduReceived(pdu, "START-RETURN", getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleCltuStopReturn(SleAcknowledgement pdu) {
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
		if (!authenticate(pdu.getCredentials(), AuthenticationModeEnum.ALL)) {
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
			// Reset requested GVCID
			this.firstCltuIdentification = null;
		} else {
			LOG.warning(getServiceInstanceIdentifier() + ": Stop return received, negative result: "
					+ CltuDiagnosticsStrings.getDiagnostic(pdu.getResult().getNegativeResult()));
			// If problems (result negative), ACTIVE
			setServiceInstanceState(ServiceInstanceBindingStateEnum.ACTIVE);
		}
		// Notify PDU
		notifyPduReceived(pdu, "STOP-RETURN", getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleCltuStatusReport(CltuStatusReportInvocation pdu) {
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
		if (!authenticate(pdu.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Status report received, but wrong credentials");
			notifyPduReceived(pdu, "STATUS-REPORT", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Set the status report parameters
		this.lastProcessed = mapCltuLastProcessed(pdu.getCltuLastProcessed().getCltuProcessed());
		this.lastOk = mapCltuLastOk(pdu.getCltuLastOk().getCltuOk());
		this.productionStatus = mapProductionStatus(pdu.getCltuProductionStatus().intValue());
		this.uplinkStatus = mapUplinkStatus(pdu.getUplinkStatus().intValue());
		this.nbCltuReceived = pdu.getNumberOfCltusReceived().longValue();
		this.nbCltuProcessed = pdu.getNumberOfCltusProcessed().longValue();
		this.nbCltuRadiated = pdu.getNumberOfCltusRadiated().longValue();
		this.bufferAvailable = pdu.getCltuBufferAvailable().longValue();

		// Notify PDU
		notifyPduReceived(pdu, "STATUS-REPORT", getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleCltuTransferDataReturn(CltuTransferDataReturn pdu) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.ACTIVE) {
			disconnect("Transfer data return received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, "TRANSFER-DATA-RETURN", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if (!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Transfer data return received, but wrong credentials");
			notifyPduReceived(pdu, "TRANSFER-DATA-RETURN", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Cancel timer task for operation
		int invokeId = pdu.getInvokeId().intValue();
		cancelReturnTimeout(invokeId);

		this.bufferAvailable = pdu.getCltuBufferAvailable().longValue();
		this.cltuIdentification = pdu.getCltuIdentification().longValue();

		// If all fine (result positive), set flag and notify
		// PDU received
		if (pdu.getResult().getPositiveResult() != null) {
			//
			LOG.info(getServiceInstanceIdentifier() + ": Transfer data return (" + this.cltuIdentification + ") received, positive result");
		} else {
			LOG.warning(getServiceInstanceIdentifier() + ": Transfer data return (" + this.cltuIdentification + ") received, negative result: "
					+ CltuDiagnosticsStrings.getTransferDataDiagnostic(pdu.getResult().getNegativeResult()));
		}
		// Notify PDU
		notifyPduReceived(pdu, "TRANSFER-DATA-RETURN", getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleCltuAsyncNotify(CltuAsyncNotifyInvocation pdu) {
		clearError();

		// Validate state
		if (this.currentState != ServiceInstanceBindingStateEnum.ACTIVE
				&& this.currentState != ServiceInstanceBindingStateEnum.READY) {
			disconnect("Async notify received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, "ASYNC-NOTIFY", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if (!authenticate(pdu.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Async notify received, but wrong credentials");
			notifyPduReceived(pdu, "ASYNC-NOTIFY", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		this.lastProcessed = mapCltuLastProcessed(pdu.getCltuLastProcessed().getCltuProcessed());
		this.lastOk = mapCltuLastOk(pdu.getCltuLastOk().getCltuOk());
		this.productionStatus = mapProductionStatus(pdu.getProductionStatus().intValue());
		this.uplinkStatus = mapUplinkStatus(pdu.getUplinkStatus().intValue());
		this.notification = mapNotification(pdu.getCltuNotification());

		// Notify PDU
		notifyPduReceived(pdu, "ASYNC-NOTIFY", getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleCltuThrowEventReturn(CltuThrowEventReturn pdu) {
		clearError();

		// Validate state
		if (this.currentState == ServiceInstanceBindingStateEnum.UNBOUND) {
			disconnect("Throw event return received, but service instance is in state " + this.currentState);
			notifyPduReceived(pdu, "THROW-EVENT-RETURN", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Validate credentials
		// From the API configuration (remote peers) and SI configuration (remote peer),
		// check remote peer and check if authentication must be used.
		// If so, verify credentials.
		if (!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
			disconnect("Throw event return received, but wrong credentials");
			notifyPduReceived(pdu, "THROW-EVENT-RETURN", getLastPduReceived());
			notifyStateUpdate();
			return;
		}

		// Cancel timer task for operation
		int invokeId = pdu.getInvokeId().intValue();
		cancelReturnTimeout(invokeId);

		this.eventInvocationIdentification = pdu.getEventInvocationIdentification().longValue();
		// If all fine (result positive), set flag and notify
		// PDU received
		if (pdu.getResult().getPositiveResult() != null) {
			//
			LOG.info("Throw event return (" + this.eventInvocationIdentification + ") received, positive result");
		} else {
			LOG.warning("Throw event return (" + this.eventInvocationIdentification + ") received, negative result: "
					+ CltuDiagnosticsStrings.getThrowEventDiagnostic(pdu.getResult().getNegativeResult()));
		}
		// Notify PDU
		notifyPduReceived(pdu, "THROW-EVENT-RETURN", getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	protected void handleCltuScheduleStatusReportReturn(SleScheduleStatusReportReturn pdu) {
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
		if (!authenticate(pdu.getPerformerCredentials(), AuthenticationModeEnum.ALL)) {
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
					+ CltuDiagnosticsStrings.getScheduleStatusReportDiagnostic(pdu.getResult().getNegativeResult()));
		}
		// Notify PDU
		notifyPduReceived(pdu, "SCHEDULE-STATUS-REPORT-RETURN", getLastPduReceived());
		// Generate state and notify update
		notifyStateUpdate();
	}

	private CltuProductionStatusEnum mapProductionStatus(int intValue) {
		return CltuProductionStatusEnum.values()[intValue];
	}

	private CltuUplinkStatusEnum mapUplinkStatus(int intValue) {
		return CltuUplinkStatusEnum.values()[intValue];
	}

	private CltuLastOk mapCltuLastOk(CltuOk obj) {
		if (obj == null) {
			return null;
		} else {
			Date stopTime = null;
			if (obj.getStopRadiationTime() != null) {
				if (obj.getStopRadiationTime().getCcsdsFormat() != null) {
					byte[] time = obj.getStopRadiationTime().getCcsdsFormat().value;
					stopTime = new Date(PduFactoryUtil.buildTimeMillis(time)[0]);
				} else {
					byte[] time = obj.getStopRadiationTime().getCcsdsPicoFormat().value;
					stopTime = new Date(PduFactoryUtil.buildTimeMillisPico(time)[0]);
				}
			}
			return new CltuLastOk(obj.getCltuIdentification().longValue(), stopTime);
		}
	}

	private CltuLastProcessed mapCltuLastProcessed(CltuProcessed obj) {
		if (obj == null) {
			return null;
		} else {
			Date startTime = null;
			if (obj.getStartRadiationTime() != null) {
				if (obj.getStartRadiationTime().getKnown() != null) {
					if (obj.getStartRadiationTime().getKnown().getCcsdsFormat() != null) {
						byte[] time = obj.getStartRadiationTime().getKnown().getCcsdsFormat().value;
						startTime = new Date(PduFactoryUtil.buildTimeMillis(time)[0]);
					} else {
						byte[] time = obj.getStartRadiationTime().getKnown().getCcsdsPicoFormat().value;
						startTime = new Date(PduFactoryUtil.buildTimeMillisPico(time)[0]);
					}
				}
			}
			;
			return new CltuLastProcessed(obj.getCltuIdentification().longValue(), startTime,
					CltuStatusEnum.values()[obj.getCltuStatus().intValue()]);
		}
	}

	private CltuNotification mapNotification(
			eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.CltuNotification cltuNotification) {
		if (cltuNotification.getActionListCompleted() != null) {
			return new CltuNotification(CltuNotificationTypeEnum.actionListCompleted,
					cltuNotification.getActionListCompleted().longValue());
		}
		if (cltuNotification.getActionListNotCompleted() != null) {
			return new CltuNotification(CltuNotificationTypeEnum.actionListNotCompleted,
					cltuNotification.getActionListNotCompleted().longValue());
		}
		if (cltuNotification.getBufferEmpty() != null) {
			return new CltuNotification(CltuNotificationTypeEnum.bufferEmpty, null);
		}
		if (cltuNotification.getCltuRadiated() != null) {
			return new CltuNotification(CltuNotificationTypeEnum.cltuRadiated, null);
		}
		if (cltuNotification.getEventConditionEvFalse() != null) {
			return new CltuNotification(CltuNotificationTypeEnum.eventConditionEvFalse,
					cltuNotification.getEventConditionEvFalse().longValue());
		}
		if (cltuNotification.getProductionHalted() != null) {
			return new CltuNotification(CltuNotificationTypeEnum.productionHalted, null);
		}
		if (cltuNotification.getProductionInterrupted() != null) {
			return new CltuNotification(CltuNotificationTypeEnum.productionInterrupted, null);
		}
		if (cltuNotification.getProductionOperational() != null) {
			return new CltuNotification(CltuNotificationTypeEnum.productionOperational, null);
		}
		if (cltuNotification.getSlduExpired() != null) {
			return new CltuNotification(CltuNotificationTypeEnum.slduExpired, null);
		}
		LOG.severe(getServiceInstanceIdentifier() + ": Inspection of CLTU notification failed in recognising the notified information");
		return null;
	}

	@Override
	protected ServiceInstanceState buildCurrentState() {
		CltuServiceInstanceState state = new CltuServiceInstanceState();
		copyCommonState(state);
		state.setMaxCltuLength(this.maxCltuLength);
		state.setMinCltuDelay(this.minCltuDelay);
		state.setBitlockRequired(this.bitlockRequired);
		state.setRfAvailableRequired(this.rfAvailableRequired);
		state.setProtocolAbortClearEnabled(this.protocolAbortClearEnabled);
		state.setMinReportingCycle(this.minReportingCycle);
		state.setReportingCycle(this.reportingCycle);
		state.setReturnTimeoutPeriod(this.returnTimeoutPeriod);

		state.setAcquisitionSequenceLength(this.acquisitionSequenceLength);
		state.setClcwGlobalVcId(this.clcwGlobalVcId); // can be null -> not configured
		state.setClcwPhysicalChannel(this.clcwPhysicalChannel); // can be null -> not configured
		state.setCltuIdentification(this.cltuIdentification);
		state.setEventInvocationIdentification(this.eventInvocationIdentification);
		state.setModulationFrequency(this.modulationFrequency);
		state.setModulationIndex(this.modulationIndex);
		state.setNotificationMode(this.notificationMode);
		state.setPlop1IdleSequenceLength(this.plop1IdleSequenceLength);
		state.setPlopInEffect(this.plopInEffect);
		state.setProtocolAbortMode(this.protocolAbortMode);
		state.setSubcarrierToBitRateRation(this.subcarrierToBitRateRation);
		state.setDeliveryMode(this.deliveryMode);

		// START
		state.setFirstCltuIdentification(this.firstCltuIdentification);
		// STATUS_REPORT
		state.setLastProcessed(this.lastProcessed); // also from async notify
		state.setLastOk(this.lastOk); // also from async notify
		state.setProductionStatus(this.productionStatus); // also from async notify
		state.setUplinkStatus(this.uplinkStatus); // also from async notify
		state.setNbCltuReceived(this.nbCltuReceived);
		state.setNbCltuProcessed(this.nbCltuProcessed);
		state.setNbCltuRadiated(this.nbCltuRadiated);
		state.setBufferAvailable(this.bufferAvailable);
		// ASYNC_NOTIFY
		state.setNotification(this.notification);
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
		return ApplicationIdentifierEnum.CLTU;
	}

	@Override
	protected void updateHandlersForVersion(int version) {
		this.encDec.useSleVersion(version);
	}

	@Override
	protected void resetState() {
		// Read from configuration, updated via GET_PARAMETER
		this.maxCltuLength = getCltuConfiguration().getMaxCltuLength();
		this.minCltuDelay = getCltuConfiguration().getMinCltuDelay();
		this.bitlockRequired = getCltuConfiguration().isBitlockRequired();
		this.rfAvailableRequired = getCltuConfiguration().isRfAvailableRequired();
		this.protocolAbortClearEnabled = getCltuConfiguration().isProtocolAbortClearEnabled();
		this.minReportingCycle = getCltuConfiguration().getMinReportingCycle();
		this.reportingCycle = null;
		this.returnTimeoutPeriod = getCltuConfiguration().getReturnTimeoutPeriod();

		this.acquisitionSequenceLength = null;
		this.clcwGlobalVcId = null; // can be null -> not configured
		this.clcwPhysicalChannel = null; // can be null -> not configured
		this.cltuIdentification = null;
		this.eventInvocationIdentification = null;
		this.modulationFrequency = null;
		this.modulationIndex = null;
		this.notificationMode = null;
		this.plop1IdleSequenceLength = null;
		this.plopInEffect = null;
		this.protocolAbortMode = null;
		this.subcarrierToBitRateRation = null;
		this.deliveryMode = DeliveryModeEnum.FWD_ONLINE;

		// START
		this.firstCltuIdentification = null;
		// STATUS_REPORT
		this.lastProcessed = null; // also from async notify
		this.lastOk = null; // also from async notify
		this.productionStatus = null; // also from async notify
		this.uplinkStatus = null; // also from async notify
		this.nbCltuReceived = null;
		this.nbCltuProcessed = null;
		this.nbCltuRadiated = null;
		this.bufferAvailable = null;
		// ASYNC_NOTIFY
		this.notification = null;
	}

	private CltuServiceInstanceConfiguration getCltuConfiguration() {
		return (CltuServiceInstanceConfiguration) this.serviceInstanceConfiguration;
	}

}
