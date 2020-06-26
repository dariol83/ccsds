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

package eu.dariolucia.ccsds.sle.utl.si.raf;

import com.beanit.jasn1.ber.types.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.ReportingCycle;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.incoming.pdus.RafGetParameterInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.incoming.pdus.RafStartInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.structures.*;
import eu.dariolucia.ccsds.sle.utl.config.PeerConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.raf.RafServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.encdec.RafProviderEncDec;
import eu.dariolucia.ccsds.sle.utl.pdu.PduFactoryUtil;
import eu.dariolucia.ccsds.sle.utl.pdu.PduStringUtil;
import eu.dariolucia.ccsds.sle.utl.si.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * One object of this class represents an RAF Service Instance (provider role).
 */
public class RafServiceInstanceProvider extends ReturnServiceInstanceProvider<RafProviderEncDec, RafTransferBuffer, RafServiceInstanceConfiguration> {

    // RAF type specific, read from configuration, retrieved via GET_PARAMETER
    private List<RafRequestedFrameQualityEnum> permittedFrameQuality;

    // RAF type specific, updated via START and GET_PARAMETER
    private volatile RafRequestedFrameQualityEnum requestedFrameQuality = null; // NOSONAR immutable object

    // RAF type specific, requested via STATUS_REPORT, updated externally (therefore they are protected via separate lock)
    private final AtomicInteger errorFreeFrameNumber = new AtomicInteger();
    private final AtomicInteger deliveredFrameNumber = new AtomicInteger();

    // Operation extension handlers: they are called to drive the positive/negative response (where supported)
    private volatile Function<RafStartInvocation, RafStartResult> startOperationHandler; // NOSONAR function pointer

    public RafServiceInstanceProvider(PeerConfiguration apiConfiguration,
                                      RafServiceInstanceConfiguration serviceInstanceConfiguration) {
        super(apiConfiguration, serviceInstanceConfiguration, new RafProviderEncDec());
    }

    @Override
    protected void doCustomSetup() {
        // Register handlers
        registerPduReceptionHandler(RafStartInvocation.class, this::handleRafStartInvocation);
        registerPduReceptionHandler(RafGetParameterInvocation.class, this::handleRafGetParameterInvocation);
    }

    public void setStartOperationHandler(Function<RafStartInvocation, RafStartResult>  handler) {
        this.startOperationHandler = handler;
    }

    @Override
    protected boolean checkAndAddTransferData(byte[] spaceDataUnit, int quality, int linkContinuity, Instant earthReceiveTime, boolean isPico, String antennaId, boolean globalAntennaId, byte[] privateAnnotations) {
        // If quality is not matching, say bye
        if (this.requestedFrameQuality != RafRequestedFrameQualityEnum.ALL_FRAMES) {
            if (this.requestedFrameQuality == RafRequestedFrameQualityEnum.GOOD_FRAMES_ONLY && quality != FRAME_QUALITY_GOOD) {
                return false;
            }
            if (this.requestedFrameQuality == RafRequestedFrameQualityEnum.BAD_FRAMES_ONLY && quality != FRAME_QUALITY_ERRED) {
                return false;
            }
        }
        // If ERT is not matching, say bye
        if (this.startTime != null && earthReceiveTime.getEpochSecond() < this.startTime.getTime() / 1000) {
            return false;
        }
        if (this.endTime != null && earthReceiveTime.getEpochSecond() > this.endTime.getTime() / 1000) {
            return false;
        }
        // Add the PDU to the buffer, there must be free space by algorithm implementation
        addTransferData(spaceDataUnit, quality, linkContinuity, earthReceiveTime, isPico, antennaId, globalAntennaId, privateAnnotations);
        return true;
    }

    @Override
    protected RafTransferBuffer createCurrentBuffer() {
        return new RafTransferBuffer();
    }

    @Override
    protected int getCurrentBufferItems(RafTransferBuffer bufferUnderConstruction) {
        return super.bufferUnderConstruction != null ? super.bufferUnderConstruction.getFrameOrNotification().size() : 0;
    }

    @Override
    protected boolean isCurrentBufferEmpty(RafTransferBuffer bufferUnderConstruction) {
        return super.bufferUnderConstruction == null || super.bufferUnderConstruction.getFrameOrNotification().isEmpty();
    }

    @Override
    protected DeliveryModeEnum getConfiguredDeliveryMode() {
        return getRafConfiguration().getDeliveryMode();
    }

    // Under sync on this.bufferMutex
    @Override
    protected void addProductionStatusChangeNotification(ProductionStatusEnum productionStatus) {
        if(bufferActive && bufferUnderConstruction != null) {
            RafSyncNotifyInvocation in = new RafSyncNotifyInvocation();
            in.setNotification(new Notification());
            in.getNotification().setProductionStatusChange(new RafProductionStatus(productionStatus.ordinal()));
            finalizeAndAddNotification(in);
        }
    }

    // Under sync on this.bufferMutex
    @Override
    protected void addDataDiscardedNotification() {
        if(bufferActive && bufferUnderConstruction != null) {
            RafSyncNotifyInvocation in = new RafSyncNotifyInvocation();
            in.setNotification(new Notification());
            in.getNotification().setExcessiveDataBacklog(new BerNull());
            finalizeAndAddNotification(in);
        }
    }

    // Under sync on this.bufferMutex
    @Override
    protected void addLossFrameSyncNotification(Instant time, LockStatusEnum carrierLockStatus, LockStatusEnum subcarrierLockStatus, LockStatusEnum symbolSyncLockStatus) {
        if(bufferActive && bufferUnderConstruction != null) {
            RafSyncNotifyInvocation in = new RafSyncNotifyInvocation();
            in.setNotification(new Notification());
            in.getNotification().setLossFrameSync(new LockStatusReport());
            in.getNotification().getLossFrameSync().setCarrierLockStatus(new CarrierLockStatus(carrierLockStatus.ordinal()));
            in.getNotification().getLossFrameSync().setSubcarrierLockStatus(new LockStatus(subcarrierLockStatus.ordinal()));
            in.getNotification().getLossFrameSync().setSymbolSyncLockStatus(new SymbolLockStatus(symbolSyncLockStatus.ordinal()));
            in.getNotification().getLossFrameSync().setTime(new Time());
            in.getNotification().getLossFrameSync().getTime().setCcsdsFormat(new TimeCCSDS(PduFactoryUtil.buildCDSTime(time.toEpochMilli(), (time.getNano() % 1000000) / 1000)));
            finalizeAndAddNotification(in);
        }
    }

    // Under sync on this.bufferMutex
    @Override
    protected void addEndOfDataNotification() {
        if(bufferActive && bufferUnderConstruction != null) {
            RafSyncNotifyInvocation in = new RafSyncNotifyInvocation();
            in.setNotification(new Notification());
            in.getNotification().setEndOfData(new BerNull());
            finalizeAndAddNotification(in);
        }
    }

    private void finalizeAndAddNotification(RafSyncNotifyInvocation in) {
        // Add credentials
        // From the API configuration (remote peers) and SI configuration (responder
        // id), check remote peer and check if authentication must be used.
        Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL);
        in.setInvokerCredentials(creds);

        FrameOrNotification fon = new FrameOrNotification();
        fon.setSyncNotification(in);
        this.bufferUnderConstruction.getFrameOrNotification().add(fon);
    }

    // Under sync on this.bufferMutex
    private void addTransferData(byte[] spaceDataUnit, int quality, int linkContinuity, Instant earthReceiveTime, boolean isPico, String antennaId, boolean globalAntennaId, byte[] privateAnnotations) { // NOSONAR: direct derivation from the rule on transferData() method.
        if(bufferActive && bufferUnderConstruction != null) {
            RafTransferDataInvocation td = new RafTransferDataInvocation();
            // Antenna ID
            td.setAntennaId(new AntennaId());
            if (globalAntennaId) {
                td.getAntennaId().setGlobalForm(new BerObjectIdentifier(PduStringUtil.fromOIDString(antennaId)));
            } else {
                td.getAntennaId().setLocalForm(new BerOctetString(PduStringUtil.fromHexDump(antennaId)));
            }
            // Data
            td.setData(new SpaceLinkDataUnit(spaceDataUnit));
            // Time
            td.setEarthReceiveTime(new Time());
            if (isPico) {
                td.getEarthReceiveTime().setCcsdsPicoFormat(new TimeCCSDSpico(PduFactoryUtil.buildCDSTimePico(earthReceiveTime.toEpochMilli(), (earthReceiveTime.getNano() % 1000000) * 1000L)));
            } else {
                td.getEarthReceiveTime().setCcsdsFormat(new TimeCCSDS(PduFactoryUtil.buildCDSTime(earthReceiveTime.toEpochMilli(), (earthReceiveTime.getNano() % 1000000) / 1000)));
            }
            // Private annotations
            td.setPrivateAnnotation(new RafTransferDataInvocation.PrivateAnnotation());
            if (privateAnnotations == null || privateAnnotations.length == 0) {
                td.getPrivateAnnotation().setNull(new BerNull());
            } else {
                td.getPrivateAnnotation().setNotNull(new BerOctetString(privateAnnotations));
            }
            // Data link continuity
            td.setDataLinkContinuity(new BerInteger(linkContinuity));
            // Quality
            td.setDeliveredFrameQuality(new FrameQuality(quality));

            // Add credentials
            // From the API configuration (remote peers) and SI configuration (responder
            // id), check remote peer and check if authentication must be used.
            Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL);
            td.setInvokerCredentials(creds);

            FrameOrNotification fon = new FrameOrNotification();
            fon.setAnnotatedFrame(td);
            this.bufferUnderConstruction.getFrameOrNotification().add(fon);

            // Assumed delivered
            this.deliveredFrameNumber.incrementAndGet();
            if (quality == FRAME_QUALITY_GOOD) {
                this.errorFreeFrameNumber.incrementAndGet();
            }
        }
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
        if (!authenticate(invocation.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
            disconnect("Start invocation received, but wrong credentials");
            notifyPduReceived(invocation, SleOperationNames.START_NAME, getLastPduReceived());
            notifyStateUpdate();
            return;
        }

        // Validate the requested frame quality
        RequestedFrameQuality rfq = invocation.getRequestedFrameQuality();
        RafStartResult startResult = RafStartResult.noError();
        boolean permittedOk = false;
        for (RafRequestedFrameQualityEnum permitted : this.permittedFrameQuality) {
            if (permitted.getCode() == rfq.intValue()) {
                permittedOk = true;
                break;
            }
        }

        if (permittedOk) {
            // Ask the external handler if any
            Function<RafStartInvocation, RafStartResult> handler = this.startOperationHandler;
            if (handler != null) {
                startResult = handler.apply(invocation);
                permittedOk = !startResult.isError();
            }
        } else {
            startResult = RafStartResult.errorSpecific(RafStartDiagnosticsEnum.UNABLE_TO_COMPLY);
        }

        RafStartReturn pdu = new RafStartReturn();
        pdu.setInvokeId(invocation.getInvokeId());
        pdu.setResult(new RafStartReturn.Result());
        if (permittedOk) {
            pdu.getResult().setPositiveResult(new BerNull());
        } else {
            pdu.getResult().setNegativeResult(new DiagnosticRafStart());
            if(startResult.getCommon() != null) {
                pdu.getResult().getNegativeResult().setCommon(new Diagnostics(startResult.getCommon().getCode()));
            } else {
                pdu.getResult().getNegativeResult().setSpecific(new BerInteger(startResult.getSpecific().getCode()));
            }
        }
        // Add credentials
        // From the API configuration (remote peers) and SI configuration (responder
        // id), check remote peer and check if authentication must be used.
        Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL);
        if (creds == null) {
            // Error while generating credentials, set by generateCredentials()
            notifyPduSentError(pdu, SleOperationNames.START_RETURN_NAME, null);
            notifyStateUpdate();
            return;
        } else {
            pdu.setPerformerCredentials(creds);
        }

        boolean resultOk = encodeAndSend(null, pdu, SleOperationNames.START_RETURN_NAME);

        if (resultOk) {
            if (permittedOk) {
                // Set the requested frame quality
                this.requestedFrameQuality = RafRequestedFrameQualityEnum.fromCode(invocation.getRequestedFrameQuality().intValue());
                // Init start activation
                initialiseTransferBufferActivation(PduFactoryUtil.toDate(invocation.getStartTime()), PduFactoryUtil.toDate(invocation.getStopTime()));
                // Start the latency timer
                startLatencyTimer();
                // Transition to new state: ACTIVE and notify PDU sent
                setServiceInstanceState(ServiceInstanceBindingStateEnum.ACTIVE);
            }
            // Notify PDU
            notifyPduSent(pdu, SleOperationNames.START_RETURN_NAME, getLastPduSent());
            // Generate state and notify update
            notifyStateUpdate();
        }
    }

    @Override
    protected void resetStartArgumentsOnStop() {
        // Set the requested frame quality
        this.requestedFrameQuality = null;
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
        if (!authenticate(invocation.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
            disconnect("Get parameter received, but wrong credentials");
            notifyPduReceived(invocation, SleOperationNames.GET_PARAMETER_NAME, getLastPduReceived());
            notifyStateUpdate();
            return;
        }

        BerType toSend;
        if (getSleVersion() <= 4) {
            RafGetParameterReturnV1toV4 pdu = new RafGetParameterReturnV1toV4();
            pdu.setInvokeId(invocation.getInvokeId());

            // Add credentials
            // From the API configuration (remote peers) and SI configuration (responder
            // id), check remote peer and check if authentication must be used.
            Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL);
            if (creds == null) {
                // Error while generating credentials, set by generateCredentials()
                notifyPduSentError(pdu, SleOperationNames.GET_PARAMETER_RETURN_NAME, null);
                notifyStateUpdate();
                return;
            } else {
                pdu.setPerformerCredentials(creds);
            }
            // Prepare for positive response
            pdu.setResult(new RafGetParameterReturnV1toV4.Result());
            pdu.getResult().setPositiveResult(new RafGetParameterV1toV4());
            if (invocation.getRafParameter().intValue() == RafParameterEnum.BUFFER_SIZE.getCode()) {
                pdu.getResult().getPositiveResult().setParBufferSize(new RafGetParameterV1toV4.ParBufferSize());
                pdu.getResult().getPositiveResult().getParBufferSize().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
                pdu.getResult().getPositiveResult().getParBufferSize().setParameterValue(new IntPosShort(this.transferBufferSize));
            } else if (invocation.getRafParameter().intValue() == RafParameterEnum.DELIVERY_MODE.getCode()) {
                pdu.getResult().getPositiveResult().setParDeliveryMode(new RafGetParameterV1toV4.ParDeliveryMode());
                pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
                pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterValue(new RafDeliveryMode(this.deliveryMode.ordinal()));
            } else if (invocation.getRafParameter().intValue() == RafParameterEnum.LATENCY_LIMIT.getCode()) {
                pdu.getResult().getPositiveResult().setParLatencyLimit(new RafGetParameterV1toV4.ParLatencyLimit());
                pdu.getResult().getPositiveResult().getParLatencyLimit().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
                pdu.getResult().getPositiveResult().getParLatencyLimit().setParameterValue(new RafGetParameterV1toV4.ParLatencyLimit.ParameterValue());
                pdu.getResult().getPositiveResult().getParLatencyLimit().getParameterValue().setOnline(new IntPosShort(this.latencyLimit));
            } else if (invocation.getRafParameter().intValue() == RafParameterEnum.REPORTING_CYCLE.getCode()) {
                pdu.getResult().getPositiveResult().setParReportingCycle(new RafGetParameterV1toV4.ParReportingCycle());
                pdu.getResult().getPositiveResult().getParReportingCycle().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReportingCycle().setParameterValue(new CurrentReportingCycle());
                if (this.reportingScheduler.get() != null && this.reportingCycle != null) {
                    pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOn(new ReportingCycle(this.reportingCycle));
                } else {
                    pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOff(new BerNull());
                }
            } else if (invocation.getRafParameter().intValue() == RafParameterEnum.REQUESTED_FRAME_QUALITY.getCode()) {
                pdu.getResult().getPositiveResult().setParReqFrameQuality(new RafGetParameterV1toV4.ParReqFrameQuality());
                pdu.getResult().getPositiveResult().getParReqFrameQuality().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
                if (this.requestedFrameQuality != null) {
                    pdu.getResult().getPositiveResult().getParReqFrameQuality().setParameterValue(new BerInteger(this.requestedFrameQuality.getCode()));
                } else {
                    pdu.getResult().getPositiveResult().getParReqFrameQuality().setParameterValue(new BerInteger(0)); // as per standard
                }
            } else if (invocation.getRafParameter().intValue() == RafParameterEnum.RETURN_TIMEOUT_PERIOD.getCode()) {
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
                notifyPduSentError(pdu, SleOperationNames.GET_PARAMETER_RETURN_NAME, null);
                notifyStateUpdate();
                return;
            } else {
                pdu.setPerformerCredentials(creds);
            }
            // Prepare for positive response
            pdu.setResult(new RafGetParameterReturn.Result());
            pdu.getResult().setPositiveResult(new RafGetParameter());
            if (invocation.getRafParameter().intValue() == RafParameterEnum.BUFFER_SIZE.getCode()) {
                pdu.getResult().getPositiveResult().setParBufferSize(new RafGetParameter.ParBufferSize());
                pdu.getResult().getPositiveResult().getParBufferSize().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
                pdu.getResult().getPositiveResult().getParBufferSize().setParameterValue(new IntPosShort(this.transferBufferSize));
            } else if (invocation.getRafParameter().intValue() == RafParameterEnum.DELIVERY_MODE.getCode()) {
                pdu.getResult().getPositiveResult().setParDeliveryMode(new RafGetParameter.ParDeliveryMode());
                pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
                pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterValue(new RafDeliveryMode(this.deliveryMode.ordinal()));
            } else if (invocation.getRafParameter().intValue() == RafParameterEnum.LATENCY_LIMIT.getCode()) {
                pdu.getResult().getPositiveResult().setParLatencyLimit(new RafGetParameter.ParLatencyLimit());
                pdu.getResult().getPositiveResult().getParLatencyLimit().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
                pdu.getResult().getPositiveResult().getParLatencyLimit().setParameterValue(new RafGetParameter.ParLatencyLimit.ParameterValue());
                pdu.getResult().getPositiveResult().getParLatencyLimit().getParameterValue().setOnline(new IntPosShort(this.latencyLimit));
            } else if (invocation.getRafParameter().intValue() == RafParameterEnum.REPORTING_CYCLE.getCode()) {
                pdu.getResult().getPositiveResult().setParReportingCycle(new RafGetParameter.ParReportingCycle());
                pdu.getResult().getPositiveResult().getParReportingCycle().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReportingCycle().setParameterValue(new CurrentReportingCycle());
                if (this.reportingScheduler.get() != null && this.reportingCycle != null) {
                    pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOn(new ReportingCycle(this.reportingCycle));
                } else {
                    pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOff(new BerNull());
                }
            } else if (invocation.getRafParameter().intValue() == RafParameterEnum.REQUESTED_FRAME_QUALITY.getCode()) {
                pdu.getResult().getPositiveResult().setParReqFrameQuality(new RafGetParameter.ParReqFrameQuality());
                pdu.getResult().getPositiveResult().getParReqFrameQuality().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
                if (this.requestedFrameQuality != null) {
                    pdu.getResult().getPositiveResult().getParReqFrameQuality().setParameterValue(new BerInteger(this.requestedFrameQuality.getCode()));
                } else {
                    pdu.getResult().getPositiveResult().getParReqFrameQuality().setParameterValue(new BerInteger(0)); // as per standard
                }
            } else if (invocation.getRafParameter().intValue() == RafParameterEnum.RETURN_TIMEOUT_PERIOD.getCode()) {
                pdu.getResult().getPositiveResult().setParReturnTimeout(new RafGetParameter.ParReturnTimeout());
                pdu.getResult().getPositiveResult().getParReturnTimeout().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReturnTimeout().setParameterValue(new TimeoutPeriod(this.returnTimeoutPeriod));
            } else if (invocation.getRafParameter().intValue() == RafParameterEnum.MIN_REPORTING_CYCLE.getCode()) {
                pdu.getResult().getPositiveResult().setParMinReportingCycle(new RafGetParameter.ParMinReportingCycle());
                pdu.getResult().getPositiveResult().getParMinReportingCycle().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
                pdu.getResult().getPositiveResult().getParMinReportingCycle().setParameterValue(new IntPosShort(Objects.requireNonNullElse(this.minReportingCycle, 0)));
            } else if (invocation.getRafParameter().intValue() == RafParameterEnum.PERMITTED_FRAME_QUALITY.getCode()) {
                pdu.getResult().getPositiveResult().setParPermittedFrameQuality(new RafGetParameter.ParPermittedFrameQuality());
                pdu.getResult().getPositiveResult().getParPermittedFrameQuality().setParameterName(new ParameterName(invocation.getRafParameter().intValue()));
                pdu.getResult().getPositiveResult().getParPermittedFrameQuality().setParameterValue(new PermittedFrameQualitySet());
                for (RafRequestedFrameQualityEnum permitted : this.permittedFrameQuality) {
                    pdu.getResult().getPositiveResult().getParPermittedFrameQuality().getParameterValue().getRequestedFrameQuality().add(new RequestedFrameQuality(permitted.getCode()));
                }
            } else {
                pdu.getResult().setPositiveResult(null);
                pdu.getResult().setNegativeResult(new DiagnosticRafGet());
                pdu.getResult().getNegativeResult().setSpecific(new BerInteger(0)); // unknownParameter
            }
            toSend = pdu;
        }

        boolean resultOk = encodeAndSend(null, toSend, SleOperationNames.GET_PARAMETER_RETURN_NAME);

        if (resultOk) {
            // Notify PDU
            notifyPduSent(toSend, SleOperationNames.GET_PARAMETER_RETURN_NAME, getLastPduSent());
            // Generate state and notify update
            notifyStateUpdate();
        }
    }

    @Override
    protected BerType buildStatusReportPdu() {
        if (getSleVersion() <= 2) {
            RafStatusReportInvocationV1toV2 pdu = new RafStatusReportInvocationV1toV2();
            // Add credentials
            Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL);
            if (creds == null) {
                // Error while generating credentials, set by generateCredentials()
                notifyPduSentError(pdu, SleOperationNames.STATUS_REPORT_NAME, null);
                notifyStateUpdate();
                return null;
            } else {
                pdu.setInvokerCredentials(creds);
            }
            //
            this.statusMutex.lock();
            try {
                pdu.setCarrierLockStatus(new LockStatus(this.carrierLockStatus.ordinal()));
                pdu.setFrameSyncLockStatus(new LockStatus(this.frameSyncLockStatus.ordinal()));
                pdu.setDeliveredFrameNumber(new IntUnsignedLong(this.deliveredFrameNumber.get()));
                pdu.setErrorFreeFrameNumber(new IntUnsignedLong(this.errorFreeFrameNumber.get()));
                pdu.setProductionStatus(new RafProductionStatus(this.productionStatus.ordinal()));
                pdu.setSubcarrierLockStatus(new LockStatus(this.subcarrierLockStatus.ordinal()));
                pdu.setSymbolSyncLockStatus(new LockStatus(this.symbolSyncLockStatus.ordinal()));
            } finally {
                this.statusMutex.unlock();
            }
            return pdu;
        } else {
            RafStatusReportInvocation pdu = new RafStatusReportInvocation();
            // Add credentials
            Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL);
            if (creds == null) {
                // Error while generating credentials, set by generateCredentials()
                notifyPduSentError(pdu, SleOperationNames.STATUS_REPORT_NAME, null);
                notifyStateUpdate();
                return null;
            } else {
                pdu.setInvokerCredentials(creds);
            }
            //
            this.statusMutex.lock();
            try {
                pdu.setCarrierLockStatus(new CarrierLockStatus(this.carrierLockStatus.ordinal()));
                pdu.setFrameSyncLockStatus(new FrameSyncLockStatus(this.frameSyncLockStatus.ordinal()));
                pdu.setDeliveredFrameNumber(new IntUnsignedLong(this.deliveredFrameNumber.get()));
                pdu.setErrorFreeFrameNumber(new IntUnsignedLong(this.errorFreeFrameNumber.get()));
                pdu.setProductionStatus(new RafProductionStatus(this.productionStatus.ordinal()));
                pdu.setSubcarrierLockStatus(new LockStatus(this.subcarrierLockStatus.ordinal()));
                pdu.setSymbolSyncLockStatus(new SymbolLockStatus(this.symbolSyncLockStatus.ordinal()));
            } finally {
                this.statusMutex.unlock();
            }
            return pdu;
        }
    }

    @Override
    protected ServiceInstanceState buildCurrentState() {
        RafServiceInstanceState state = new RafServiceInstanceState();
        copyCommonState(state);
        this.statusMutex.lock();
        try {
            state.setCarrierLockStatus(carrierLockStatus);
            state.setFrameSyncLockStatus(frameSyncLockStatus);
            state.setSubcarrierLockStatus(subcarrierLockStatus);
            state.setSymbolSyncLockStatus(symbolSyncLockStatus);
            state.setProductionStatus(productionStatus);
        } finally {
            this.statusMutex.unlock();
        }
        state.setDeliveryMode(deliveryMode);
        state.setLatencyLimit(latencyLimit);
        state.setMinReportingCycle(minReportingCycle);
        state.setDeliveredFrameNumber(deliveredFrameNumber.get());
        state.setErrorFreeFrameNumber(errorFreeFrameNumber.get());
        state.setPermittedFrameQuality(new ArrayList<>(permittedFrameQuality));
        state.setReportingCycle(reportingCycle);
        state.setRequestedFrameQuality(requestedFrameQuality);
        state.setTransferBufferSize(transferBufferSize);
        state.setReturnTimeoutPeriod(returnTimeoutPeriod);
        state.setStartTime(startTime);
        state.setEndTime(endTime);
        return state;
    }

    @Override
    public ApplicationIdentifierEnum getApplicationIdentifier() {
        return ApplicationIdentifierEnum.RAF;
    }

    @Override
    protected void doResetState() {
        this.latencyLimit = getRafConfiguration().getLatencyLimit();
        this.minReportingCycle = getRafConfiguration().getMinReportingCycle();
        this.returnTimeoutPeriod = getRafConfiguration().getReturnTimeoutPeriod();
        this.transferBufferSize = getRafConfiguration().getTransferBufferSize();
        this.permittedFrameQuality = getRafConfiguration().getPermittedFrameQuality();
        this.requestedFrameQuality = null;
        this.errorFreeFrameNumber.set(0);
        this.deliveredFrameNumber.set(0);
    }

    private RafServiceInstanceConfiguration getRafConfiguration() {
        return (RafServiceInstanceConfiguration) this.serviceInstanceConfiguration;
    }
}
