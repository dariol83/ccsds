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

package eu.dariolucia.ccsds.sle.utl.si.rcf;

import com.beanit.jasn1.ber.types.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.ReportingCycle;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.incoming.pdus.RcfGetParameterInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.incoming.pdus.RcfStartInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.outgoing.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.structures.*;
import eu.dariolucia.ccsds.sle.utl.config.PeerConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.rcf.RcfServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.encdec.RcfProviderEncDec;
import eu.dariolucia.ccsds.sle.utl.pdu.PduFactoryUtil;
import eu.dariolucia.ccsds.sle.utl.pdu.PduStringUtil;
import eu.dariolucia.ccsds.sle.utl.si.*;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * One object of this class represents an RCF Service Instance (provider role).
 */
public class RcfServiceInstanceProvider extends ReturnServiceInstanceProvider<RcfProviderEncDec, RcfTransferBuffer, RcfServiceInstanceConfiguration> {

    // Read from configuration, retrieved via GET_PARAMETER
    private List<GVCID> permittedGvcids;

    // Updated via START and GET_PARAMETER
    private volatile GVCID requestedGvcid = null; // NOSONAR not expected to be changed

    // Requested via STATUS_REPORT, updated externally (therefore they are protected via separate lock)
    private final AtomicInteger deliveredFrameNumber = new AtomicInteger();

    // Operation extension handlers: they are called to drive the positive/negative response (where supported)
    private volatile Predicate<RcfStartInvocation> startOperationHandler; // NOSONAR function pointer

    public RcfServiceInstanceProvider(PeerConfiguration apiConfiguration,
                                      RcfServiceInstanceConfiguration serviceInstanceConfiguration) {
        super(apiConfiguration, serviceInstanceConfiguration, new RcfProviderEncDec());
    }

    @Override
    protected void doCustomSetup() {
        // Register handlers
        registerPduReceptionHandler(RcfStartInvocation.class, this::handleRcfStartInvocation);
        registerPduReceptionHandler(RcfGetParameterInvocation.class, this::handleRcfGetParameterInvocation);
    }

    public void setStartOperationHandler(Predicate<RcfStartInvocation> handler) {
        this.startOperationHandler = handler;
    }

    @Override
    protected boolean checkAndAddTransferData(byte[] spaceDataUnit, int quality, int linkContinuity, Instant earthReceiveTime, boolean isPico, String antennaId, boolean globalAntennaId, byte[] privateAnnotations) {
        // If quality is not GOOD, bye
        if(quality != FRAME_QUALITY_GOOD) {
            return false;
        }
        // If GVCID is not matching, say bye
        if (!match(this.requestedGvcid, spaceDataUnit)) {
            return false;
        }
        // If ERT is not matching, say bye
        if (this.startTime != null && earthReceiveTime.getEpochSecond() < this.startTime.getTime() / 1000) {
            return false;
        }
        if (this.endTime != null && earthReceiveTime.getEpochSecond() > this.endTime.getTime() / 1000) {
            return false;
        }
        // Add the PDU to the buffer, there must be free space by algorithm implementation
        addTransferData(spaceDataUnit, linkContinuity, earthReceiveTime, isPico, antennaId, globalAntennaId, privateAnnotations);
        return true;
    }

    private boolean match(GVCID requestedGvcid, byte[] spaceDataUnit) {
        ByteBuffer bb = ByteBuffer.wrap(spaceDataUnit);
        short header = bb.getShort();
        // Extract TFVN, VCID and SCID from the frame
        int tfvn = ((header & 0xC000) & 0xFFFF) >>> 14;
        int scid;
        int vcid;
        if(tfvn == 0) {
            // TM
            scid = ((header & 0x3FF0) & 0xFFFF) >>> 4;
            vcid = ((header & 0x000E) & 0xFFFF) >>> 1;
        } else {
            // AOS
            scid = ((header & 0x3FC0) & 0xFFFF) >>> 6;
            vcid = ((header & 0x003F) & 0xFFFF);
        }
        return requestedGvcid.getTransferFrameVersionNumber() == tfvn &&
                requestedGvcid.getSpacecraftId() == scid &&
                (requestedGvcid.getVirtualChannelId() == null || requestedGvcid.getVirtualChannelId() == vcid);
    }

    @Override
    protected RcfTransferBuffer createCurrentBuffer() {
        return new RcfTransferBuffer();
    }

    @Override
    protected int getCurrentBufferItems(RcfTransferBuffer bufferUnderConstruction) {
        return super.bufferUnderConstruction != null ? super.bufferUnderConstruction.getFrameOrNotification().size() : 0;
    }

    @Override
    protected boolean isCurrentBufferEmpty(RcfTransferBuffer bufferUnderConstruction) {
        return super.bufferUnderConstruction == null || super.bufferUnderConstruction.getFrameOrNotification().isEmpty();
    }

    @Override
    protected void doHandleTransferBufferInvocation(RcfTransferBuffer bufferToSend) {
        clearError();

        // Validate state
        if (this.currentState != ServiceInstanceBindingStateEnum.ACTIVE) {
            setError("Transfer buffer in transmission discarded, service instance is in state "
                    + this.currentState);
            notifyStateUpdate();
            return;
        }

        boolean resultOk = encodeAndSend(null, bufferToSend, SleOperationNames.TRANSFER_BUFFER_NAME);

        if (resultOk) {
            // Clear buffer transmission flag
            clearBufferTransmissionFlag();
            // Notify PDU
            notifyPduSent(bufferToSend, SleOperationNames.TRANSFER_BUFFER_NAME, getLastPduSent());
            // Generate state and notify update
            notifyStateUpdate();
        }
    }

    @Override
    protected DeliveryModeEnum getConfiguredDeliveryMode() {
        return getRcfConfiguration().getDeliveryMode();
    }

    // Under sync on this.bufferMutex
    @Override
    protected void addProductionStatusChangeNotification(ProductionStatusEnum productionStatus) {
        if(!bufferActive || bufferUnderConstruction == null) {
            return;
        }
        RcfSyncNotifyInvocation in = new RcfSyncNotifyInvocation();
        in.setNotification(new Notification());
        in.getNotification().setProductionStatusChange(new RcfProductionStatus(productionStatus.ordinal()));
        finalizeAndAddNotification(in);
    }

    // Under sync on this.bufferMutex
    @Override
    protected void addDataDiscardedNotification() {
        if(!bufferActive || bufferUnderConstruction == null) {
            return;
        }
        RcfSyncNotifyInvocation in = new RcfSyncNotifyInvocation();
        in.setNotification(new Notification());
        in.getNotification().setExcessiveDataBacklog(new BerNull());
        finalizeAndAddNotification(in);
    }

    // Under sync on this.bufferMutex
    @Override
    protected void addLossFrameSyncNotification(Instant time, LockStatusEnum carrierLockStatus, LockStatusEnum subcarrierLockStatus, LockStatusEnum symbolSyncLockStatus) {
        if(!bufferActive || bufferUnderConstruction == null) {
            return;
        }
        RcfSyncNotifyInvocation in = new RcfSyncNotifyInvocation();
        in.setNotification(new Notification());
        in.getNotification().setLossFrameSync(new LockStatusReport());
        in.getNotification().getLossFrameSync().setCarrierLockStatus(new CarrierLockStatus(carrierLockStatus.ordinal()));
        in.getNotification().getLossFrameSync().setSubcarrierLockStatus(new LockStatus(subcarrierLockStatus.ordinal()));
        in.getNotification().getLossFrameSync().setSymbolSyncLockStatus(new SymbolLockStatus(symbolSyncLockStatus.ordinal()));
        in.getNotification().getLossFrameSync().setTime(new Time());
        in.getNotification().getLossFrameSync().getTime().setCcsdsFormat(new TimeCCSDS(PduFactoryUtil.buildCDSTime(time.toEpochMilli(), (time.getNano() % 1000000) / 1000)));
        finalizeAndAddNotification(in);
    }

    // Under sync on this.bufferMutex
    @Override
    protected void addEndOfDataNotification() {
        if(!bufferActive || bufferUnderConstruction == null) {
            return;
        }
        RcfSyncNotifyInvocation in = new RcfSyncNotifyInvocation();
        in.setNotification(new Notification());
        in.getNotification().setEndOfData(new BerNull());
        finalizeAndAddNotification(in);
    }

    @Override
    protected void resetStartArgumentsOnStop() {
        this.requestedGvcid = null;
    }

    private void finalizeAndAddNotification(RcfSyncNotifyInvocation in) {
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
    private void addTransferData(byte[] spaceDataUnit, int linkContinuity, Instant earthReceiveTime, boolean isPico, String antennaId, boolean globalAntennaId, byte[] privateAnnotations) {
        if(!bufferActive || bufferUnderConstruction == null) {
            return;
        }
        RcfTransferDataInvocation td = new RcfTransferDataInvocation();
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
        td.setPrivateAnnotation(new RcfTransferDataInvocation.PrivateAnnotation());
        if (privateAnnotations == null || privateAnnotations.length == 0) {
            td.getPrivateAnnotation().setNull(new BerNull());
        } else {
            td.getPrivateAnnotation().setNotNull(new BerOctetString(privateAnnotations));
        }
        // Data link continuity
        td.setDataLinkContinuity(new BerInteger(linkContinuity));

        // Add credentials
        // From the API configuration (remote peers) and SI configuration (responder
        // id), check remote peer and check if authentication must be used.
        Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL);
        td.setInvokerCredentials(creds);

        FrameOrNotification fon = new FrameOrNotification();
        fon.setAnnotatedFrame(td);
        this.bufferUnderConstruction.getFrameOrNotification().add(fon);
        // Assume delivered
        this.deliveredFrameNumber.incrementAndGet();
    }

    private void handleRcfStartInvocation(RcfStartInvocation invocation) {
        dispatchFromProvider(() -> doHandleRcfStartInvocation(invocation));
    }

    private void doHandleRcfStartInvocation(RcfStartInvocation invocation) {
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

        // Validate the requested GVCID
        GVCID rfq = new GVCID(invocation.getRequestedGvcId().getSpacecraftId().intValue(),
                invocation.getRequestedGvcId().getVersionNumber().intValue(),
                invocation.getRequestedGvcId().getVcId().getMasterChannel() != null ? null : invocation.getRequestedGvcId().getVcId().getVirtualChannel().intValue());
        boolean permittedOk = false;
        if (permittedGvcids.contains(rfq)) {
            permittedOk = true;
        }

        if (permittedOk) {
            // Ask the external handler if any
            Predicate<RcfStartInvocation> handler = this.startOperationHandler;
            if (handler != null) {
                permittedOk = handler.test(invocation);
            }
        }

        RcfStartReturn pdu = new RcfStartReturn();
        pdu.setInvokeId(invocation.getInvokeId());
        pdu.setResult(new RcfStartReturn.Result());
        if (permittedOk) {
            pdu.getResult().setPositiveResult(new BerNull());
        } else {
            pdu.getResult().setNegativeResult(new DiagnosticRcfStart());
            pdu.getResult().getNegativeResult().setSpecific(new BerInteger(1)); // Unable to comply
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
                // Set the requested GVCID
                this.requestedGvcid = rfq;
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

    private void handleRcfGetParameterInvocation(RcfGetParameterInvocation invocation) {
        dispatchFromProvider(() -> doHandleRcfGetParameterInvocation(invocation));
    }

    private void doHandleRcfGetParameterInvocation(RcfGetParameterInvocation invocation) {
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
            RcfGetParameterReturnV1toV4 pdu = new RcfGetParameterReturnV1toV4();
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
            pdu.setResult(new RcfGetParameterReturnV1toV4.Result());
            pdu.getResult().setPositiveResult(new RcfGetParameterV1toV4());
            if (invocation.getRcfParameter().intValue() == RcfParameterEnum.BUFFER_SIZE.getCode()) {
                pdu.getResult().getPositiveResult().setParBufferSize(new RcfGetParameterV1toV4.ParBufferSize());
                pdu.getResult().getPositiveResult().getParBufferSize().setParameterName(new ParameterName(invocation.getRcfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParBufferSize().setParameterValue(new IntPosShort(this.transferBufferSize));
            } else if (invocation.getRcfParameter().intValue() == RcfParameterEnum.DELIVERY_MODE.getCode()) {
                pdu.getResult().getPositiveResult().setParDeliveryMode(new RcfGetParameterV1toV4.ParDeliveryMode());
                pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterName(new ParameterName(invocation.getRcfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterValue(new RcfDeliveryMode(this.deliveryMode.ordinal()));
            } else if (invocation.getRcfParameter().intValue() == RcfParameterEnum.LATENCY_LIMIT.getCode()) {
                pdu.getResult().getPositiveResult().setParLatencyLimit(new RcfGetParameterV1toV4.ParLatencyLimit());
                pdu.getResult().getPositiveResult().getParLatencyLimit().setParameterName(new ParameterName(invocation.getRcfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParLatencyLimit().setParameterValue(new RcfGetParameterV1toV4.ParLatencyLimit.ParameterValue());
                pdu.getResult().getPositiveResult().getParLatencyLimit().getParameterValue().setOnline(new IntPosShort(this.latencyLimit));
            } else if (invocation.getRcfParameter().intValue() == RcfParameterEnum.REPORTING_CYCLE.getCode()) {
                pdu.getResult().getPositiveResult().setParReportingCycle(new RcfGetParameterV1toV4.ParReportingCycle());
                pdu.getResult().getPositiveResult().getParReportingCycle().setParameterName(new ParameterName(invocation.getRcfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReportingCycle().setParameterValue(new CurrentReportingCycle());
                if (this.reportingScheduler.get() != null && this.reportingCycle != null) {
                    pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOn(new ReportingCycle(this.reportingCycle));
                } else {
                    pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOff(new BerNull());
                }
            } else if (invocation.getRcfParameter().intValue() == RcfParameterEnum.REQUESTED_GVCID.getCode()) {
                pdu.getResult().getPositiveResult().setParReqGvcId(new RcfGetParameterV1toV4.ParReqGvcId());
                pdu.getResult().getPositiveResult().getParReqGvcId().setParameterName(new ParameterName(invocation.getRcfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReqGvcId().setParameterValue(new RequestedGvcId());
                if (this.requestedGvcid != null) {
                    pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().setGvcid(new GvcId());
                    pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().getGvcid().setSpacecraftId(new BerInteger(this.requestedGvcid.getSpacecraftId()));
                    pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().getGvcid().setVersionNumber(new BerInteger(this.requestedGvcid.getTransferFrameVersionNumber()));
                    pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().getGvcid().setVcId(new GvcId.VcId());
                    if(this.requestedGvcid.getVirtualChannelId() == null) {
                        pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().getGvcid().getVcId().setMasterChannel(new BerNull());
                    } else {
                        pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().getGvcid().getVcId().setVirtualChannel(new VcId(this.requestedGvcid.getVirtualChannelId()));
                    }
                } else {
                    pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().setUndefined(new BerNull());
                }
            } else if (invocation.getRcfParameter().intValue() == RcfParameterEnum.RETURN_TIMEOUT_PERIOD.getCode()) {
                pdu.getResult().getPositiveResult().setParReturnTimeout(new RcfGetParameterV1toV4.ParReturnTimeout());
                pdu.getResult().getPositiveResult().getParReturnTimeout().setParameterName(new ParameterName(invocation.getRcfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReturnTimeout().setParameterValue(new TimeoutPeriod(this.returnTimeoutPeriod));
            } else if (invocation.getRcfParameter().intValue() == RcfParameterEnum.PERMITTED_GVCID_SET.getCode()) {
                pdu.getResult().getPositiveResult().setParPermittedGvcidSet(new RcfGetParameterV1toV4.ParPermittedGvcidSet());
                pdu.getResult().getPositiveResult().getParPermittedGvcidSet().setParameterName(new ParameterName(invocation.getRcfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParPermittedGvcidSet().setParameterValue(new GvcIdSetV1toV4());
                for (GVCID permitted : this.permittedGvcids) {
                    MasterChannelCompositionV1toV4 entry = new MasterChannelCompositionV1toV4();
                    entry.setSpacecraftId(new BerInteger(permitted.getSpacecraftId()));
                    entry.setVersionNumber(new BerInteger(permitted.getTransferFrameVersionNumber()));
                    entry.setMcOrVcList(new MasterChannelCompositionV1toV4.McOrVcList());
                    if(permitted.getVirtualChannelId() == null) {
                        entry.getMcOrVcList().setMasterChannel(new BerNull());
                    } else {
                        entry.getMcOrVcList().setVcList(new MasterChannelCompositionV1toV4.McOrVcList.VcList());
                        entry.getMcOrVcList().getVcList().getVcId().add(new VcId(permitted.getVirtualChannelId()));
                    }
                    pdu.getResult().getPositiveResult().getParPermittedGvcidSet().getParameterValue().getMasterChannelCompositionV1toV4().add(entry);
                }
            } else {
                pdu.getResult().setPositiveResult(null);
                pdu.getResult().setNegativeResult(new DiagnosticRcfGet());
                pdu.getResult().getNegativeResult().setSpecific(new BerInteger(0)); // unknownParameter
            }
            toSend = pdu;
        } else {
            RcfGetParameterReturn pdu = new RcfGetParameterReturn();
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
            pdu.setResult(new RcfGetParameterReturn.Result());
            pdu.getResult().setPositiveResult(new RcfGetParameter());
            if (invocation.getRcfParameter().intValue() == RcfParameterEnum.BUFFER_SIZE.getCode()) {
                pdu.getResult().getPositiveResult().setParBufferSize(new RcfGetParameter.ParBufferSize());
                pdu.getResult().getPositiveResult().getParBufferSize().setParameterName(new ParameterName(invocation.getRcfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParBufferSize().setParameterValue(new IntPosShort(this.transferBufferSize));
            } else if (invocation.getRcfParameter().intValue() == RcfParameterEnum.DELIVERY_MODE.getCode()) {
                pdu.getResult().getPositiveResult().setParDeliveryMode(new RcfGetParameter.ParDeliveryMode());
                pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterName(new ParameterName(invocation.getRcfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterValue(new RcfDeliveryMode(this.deliveryMode.ordinal()));
            } else if (invocation.getRcfParameter().intValue() == RcfParameterEnum.LATENCY_LIMIT.getCode()) {
                pdu.getResult().getPositiveResult().setParLatencyLimit(new RcfGetParameter.ParLatencyLimit());
                pdu.getResult().getPositiveResult().getParLatencyLimit().setParameterName(new ParameterName(invocation.getRcfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParLatencyLimit().setParameterValue(new RcfGetParameter.ParLatencyLimit.ParameterValue());
                pdu.getResult().getPositiveResult().getParLatencyLimit().getParameterValue().setOnline(new IntPosShort(this.latencyLimit));
            } else if (invocation.getRcfParameter().intValue() == RcfParameterEnum.REPORTING_CYCLE.getCode()) {
                pdu.getResult().getPositiveResult().setParReportingCycle(new RcfGetParameter.ParReportingCycle());
                pdu.getResult().getPositiveResult().getParReportingCycle().setParameterName(new ParameterName(invocation.getRcfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReportingCycle().setParameterValue(new CurrentReportingCycle());
                if (this.reportingScheduler.get() != null && this.reportingCycle != null) {
                    pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOn(new ReportingCycle(this.reportingCycle));
                } else {
                    pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOff(new BerNull());
                }
            } else if (invocation.getRcfParameter().intValue() == RcfParameterEnum.REQUESTED_GVCID.getCode()) {
                pdu.getResult().getPositiveResult().setParReqGvcId(new RcfGetParameter.ParReqGvcId());
                pdu.getResult().getPositiveResult().getParReqGvcId().setParameterName(new ParameterName(invocation.getRcfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReqGvcId().setParameterValue(new RequestedGvcId());
                if (this.requestedGvcid != null) {
                    pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().setGvcid(new GvcId());
                    pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().getGvcid().setSpacecraftId(new BerInteger(this.requestedGvcid.getSpacecraftId()));
                    pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().getGvcid().setVersionNumber(new BerInteger(this.requestedGvcid.getTransferFrameVersionNumber()));
                    pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().getGvcid().setVcId(new GvcId.VcId());
                    if(this.requestedGvcid.getVirtualChannelId() == null) {
                        pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().getGvcid().getVcId().setMasterChannel(new BerNull());
                    } else {
                        pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().getGvcid().getVcId().setVirtualChannel(new VcId(this.requestedGvcid.getVirtualChannelId()));
                    }
                } else {
                    pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().setUndefined(new BerNull());
                }
            } else if (invocation.getRcfParameter().intValue() == RcfParameterEnum.RETURN_TIMEOUT_PERIOD.getCode()) {
                pdu.getResult().getPositiveResult().setParReturnTimeout(new RcfGetParameter.ParReturnTimeout());
                pdu.getResult().getPositiveResult().getParReturnTimeout().setParameterName(new ParameterName(invocation.getRcfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReturnTimeout().setParameterValue(new TimeoutPeriod(this.returnTimeoutPeriod));
            } else if (invocation.getRcfParameter().intValue() == RcfParameterEnum.MIN_REPORTING_CYCLE.getCode()) {
                pdu.getResult().getPositiveResult().setParMinReportingCycle(new RcfGetParameter.ParMinReportingCycle());
                pdu.getResult().getPositiveResult().getParMinReportingCycle().setParameterName(new ParameterName(invocation.getRcfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParMinReportingCycle().setParameterValue(new IntPosShort(this.minReportingCycle != null ? this.minReportingCycle : 0));
            } else if (invocation.getRcfParameter().intValue() == RcfParameterEnum.PERMITTED_GVCID_SET.getCode()) {
                pdu.getResult().getPositiveResult().setParPermittedGvcidSet(new RcfGetParameter.ParPermittedGvcidSet());
                pdu.getResult().getPositiveResult().getParPermittedGvcidSet().setParameterName(new ParameterName(invocation.getRcfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParPermittedGvcidSet().setParameterValue(new GvcIdSet());
                for (GVCID permitted : this.permittedGvcids) {
                    MasterChannelComposition entry = new MasterChannelComposition();
                    entry.setSpacecraftId(new BerInteger(permitted.getSpacecraftId()));
                    entry.setVersionNumber(new BerInteger(permitted.getTransferFrameVersionNumber()));
                    entry.setMcOrVcList(new MasterChannelComposition.McOrVcList());
                    if(permitted.getVirtualChannelId() == null) {
                        entry.getMcOrVcList().setMasterChannel(new BerNull());
                    } else {
                        entry.getMcOrVcList().setVcList(new MasterChannelComposition.McOrVcList.VcList());
                        entry.getMcOrVcList().getVcList().getVcId().add(new VcId(permitted.getVirtualChannelId()));
                    }
                    pdu.getResult().getPositiveResult().getParPermittedGvcidSet().getParameterValue().getMasterChannelComposition().add(entry);
                }
            } else {
                pdu.getResult().setPositiveResult(null);
                pdu.getResult().setNegativeResult(new DiagnosticRcfGet());
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
        if (getSleVersion() == 1) {
            RcfStatusReportInvocationV1 pdu = new RcfStatusReportInvocationV1();
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
                pdu.setProductionStatus(new RcfProductionStatus(this.productionStatus.ordinal()));
                pdu.setSubcarrierLockStatus(new LockStatus(this.subcarrierLockStatus.ordinal()));
                pdu.setSymbolSyncLockStatus(new LockStatus(this.symbolSyncLockStatus.ordinal()));
            } finally {
                this.statusMutex.unlock();
            }
            return pdu;
        } else {
            RcfStatusReportInvocation pdu = new RcfStatusReportInvocation();
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
                pdu.setProductionStatus(new RcfProductionStatus(this.productionStatus.ordinal()));
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
        RcfServiceInstanceState state = new RcfServiceInstanceState();
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
        state.setNumFramesDelivered(deliveredFrameNumber.get());
        state.setPermittedGvcid(new ArrayList<>(permittedGvcids));
        state.setReportingCycle(reportingCycle);
        state.setRequestedGvcid(requestedGvcid);
        state.setTransferBufferSize(transferBufferSize);
        state.setReturnTimeoutPeriod(returnTimeoutPeriod);
        state.setStartTime(startTime);
        state.setEndTime(endTime);
        return state;
    }

    @Override
    public ApplicationIdentifierEnum getApplicationIdentifier() {
        return ApplicationIdentifierEnum.RCF;
    }

    @Override
    protected void doResetState() {
        this.latencyLimit = getRcfConfiguration().getLatencyLimit();
        this.permittedGvcids = getRcfConfiguration().getPermittedGvcid();
        this.minReportingCycle = getRcfConfiguration().getMinReportingCycle();
        this.returnTimeoutPeriod = getRcfConfiguration().getReturnTimeoutPeriod();
        this.transferBufferSize = getRcfConfiguration().getTransferBufferSize();
        this.requestedGvcid = null;
        this.deliveredFrameNumber.set(0);
    }

    private RcfServiceInstanceConfiguration getRcfConfiguration() {
        return (RcfServiceInstanceConfiguration) this.serviceInstanceConfiguration;
    }
}
