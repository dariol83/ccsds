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

package eu.dariolucia.ccsds.sle.utl.si.rocf;

import com.beanit.jasn1.ber.types.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.ReportingCycle;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.incoming.pdus.RocfGetParameterInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.incoming.pdus.RocfStartInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.outgoing.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.structures.*;
import eu.dariolucia.ccsds.sle.utl.config.PeerConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.rocf.RocfServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.encdec.RocfProviderEncDec;
import eu.dariolucia.ccsds.sle.utl.pdu.PduFactoryUtil;
import eu.dariolucia.ccsds.sle.utl.pdu.PduStringUtil;
import eu.dariolucia.ccsds.sle.utl.si.*;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * One object of this class represents an ROCF Service Instance (provider role).
 */
public class RocfServiceInstanceProvider extends ReturnServiceInstanceProvider<RocfProviderEncDec, RocfTransferBuffer, RocfServiceInstanceConfiguration> {

    // Read from configuration, retrieved via GET_PARAMETER
    private List<GVCID> permittedGvcids;
    private List<Integer> permittedTcVcid;
    private List<RocfControlWordTypeEnum> permittedControlWordTypes;
    private List<RocfUpdateModeEnum> permittedUpdateModes;

    // Updated via START and GET_PARAMETER
    private volatile GVCID requestedGvcid = null; // NOSONAR not expected to be changed
    private volatile Integer requestedTcVcid = null;
    private volatile RocfControlWordTypeEnum requestedControlWordType = null; // NOSONAR enumeration is immutable
    private volatile RocfUpdateModeEnum requestedUpdateMode = null; // NOSONAR enumeration is immutable

    // Requested via STATUS_REPORT, updated externally (therefore they are protected via separate lock)
    private final AtomicInteger processedFrameNumber = new AtomicInteger();
    private final AtomicInteger deliveredOcfsNumber = new AtomicInteger();

    // Last recorded OCF per TC VC ID
    private final Map<Integer, Integer> tcVcId2lastClcw = new HashMap<>();

    // Operation extension handlers: they are called to drive the positive/negative response (where supported)
    private volatile Predicate<RocfStartInvocation> startOperationHandler; // NOSONAR function pointer

    public RocfServiceInstanceProvider(PeerConfiguration apiConfiguration,
                                       RocfServiceInstanceConfiguration serviceInstanceConfiguration) {
        super(apiConfiguration, serviceInstanceConfiguration, new RocfProviderEncDec());
    }

    @Override
    protected void doCustomSetup() {
        // Register handlers
        registerPduReceptionHandler(RocfStartInvocation.class, this::handleRocfStartInvocation);
        registerPduReceptionHandler(RocfGetParameterInvocation.class, this::handleRocfGetParameterInvocation);
    }

    public void setStartOperationHandler(Predicate<RocfStartInvocation> handler) {
        this.startOperationHandler = handler;
    }

    @Override
    protected boolean checkAndAddTransferData(byte[] spaceDataUnit, int quality, int linkContinuity, Instant earthReceiveTime, boolean isPico, String antennaId, boolean globalAntennaId, byte[] privateAnnotations) {
        // If quality is not GOOD, bye
        if(quality != FRAME_QUALITY_GOOD) {
            return false;
        }
        // If ERT is not matching, say bye
        if (this.startTime != null && earthReceiveTime.getEpochSecond() < this.startTime.getTime() / 1000) {
            return false;
        }
        if (this.endTime != null && earthReceiveTime.getEpochSecond() > this.endTime.getTime() / 1000) {
            return false;
        }
        // If GVCID is not matching, say bye
        if (!match(this.requestedGvcid, spaceDataUnit)) {
            return false;
        }
        // So we are processing the frame
        this.processedFrameNumber.incrementAndGet();
        // Extract and check the OCF (selected TC VC ID, on-change/continuous, report type)
        int ocfAsInt = ByteBuffer.wrap(spaceDataUnit, spaceDataUnit.length - 4, 4).getInt();
        boolean isClcw = (ocfAsInt & 0x80000000) == 0;
        // CLCW and requesting no CLCW -> skip
        if(this.requestedControlWordType == RocfControlWordTypeEnum.NO_CLCW && isClcw) {
            return false;
        }
        // Not a CLCW and requesting CLCW only -> skip
        if(this.requestedControlWordType == RocfControlWordTypeEnum.CLCW && !isClcw) {
            return false;
        }
        // If it is not a CLCW, we need to ignore the TC VC ID and the update mode (we use -1 for that)
        int tcVcId = !isClcw ? -1 : (ocfAsInt & 0x00FC0000) >>> 18;
        // Check if the TC VC ID is the requested one (only is it is a CLCW)
        if(isClcw && this.requestedTcVcid != null && this.requestedTcVcid != tcVcId) {
            return false;
        }
        // Change based or continuous?
        if(this.requestedUpdateMode == RocfUpdateModeEnum.CHANGE_BASED) {
            Integer lastOcf = this.tcVcId2lastClcw.get(tcVcId);
            if (lastOcf != null &&
                    lastOcf == ocfAsInt) {
                return false;
            }
        }
        // Remember the OCF
        this.tcVcId2lastClcw.put(tcVcId, ocfAsInt);
        // Add the PDU to the buffer, there must be free space by algorithm implementation
        addTransferData(Arrays.copyOfRange(spaceDataUnit, spaceDataUnit.length - 4, spaceDataUnit.length), linkContinuity, earthReceiveTime, isPico, antennaId, globalAntennaId, privateAnnotations);
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
    protected RocfTransferBuffer createCurrentBuffer() {
        return new RocfTransferBuffer();
    }

    @Override
    protected int getCurrentBufferItems(RocfTransferBuffer bufferUnderConstruction) {
        return super.bufferUnderConstruction != null ? super.bufferUnderConstruction.getOcfOrNotification().size() : 0;
    }

    @Override
    protected boolean isCurrentBufferEmpty(RocfTransferBuffer bufferUnderConstruction) {
        return super.bufferUnderConstruction == null || super.bufferUnderConstruction.getOcfOrNotification().isEmpty();
    }

    @Override
    protected DeliveryModeEnum getConfiguredDeliveryMode() {
        return getRocfConfiguration().getDeliveryMode();
    }

    // Under sync on this.bufferMutex
    @Override
    protected void addProductionStatusChangeNotification(ProductionStatusEnum productionStatus) {
        if(bufferActive && bufferUnderConstruction != null) {
            RocfSyncNotifyInvocation in = new RocfSyncNotifyInvocation();
            in.setNotification(new Notification());
            in.getNotification().setProductionStatusChange(new RocfProductionStatus(productionStatus.ordinal()));
            finalizeAndAddNotification(in);
        }
    }

    // Under sync on this.bufferMutex
    @Override
    protected void addDataDiscardedNotification() {
        if(bufferActive && bufferUnderConstruction != null) {
            RocfSyncNotifyInvocation in = new RocfSyncNotifyInvocation();
            in.setNotification(new Notification());
            in.getNotification().setExcessiveDataBacklog(new BerNull());
            finalizeAndAddNotification(in);
        }
    }

    // Under sync on this.bufferMutex
    @Override
    protected void addLossFrameSyncNotification(Instant time, LockStatusEnum carrierLockStatus, LockStatusEnum subcarrierLockStatus, LockStatusEnum symbolSyncLockStatus) {
        if(bufferActive && bufferUnderConstruction != null) {
            RocfSyncNotifyInvocation in = new RocfSyncNotifyInvocation();
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
            RocfSyncNotifyInvocation in = new RocfSyncNotifyInvocation();
            in.setNotification(new Notification());
            in.getNotification().setEndOfData(new BerNull());
            finalizeAndAddNotification(in);
        }
    }

    @Override
    protected void resetStartArgumentsOnStop() {
        // Set the requested parameters
        this.requestedGvcid = null;
        this.requestedTcVcid = null;
        this.requestedControlWordType = null;
        this.requestedUpdateMode = null;
    }

    private void finalizeAndAddNotification(RocfSyncNotifyInvocation in) {
        // Add credentials
        // From the API configuration (remote peers) and SI configuration (responder
        // id), check remote peer and check if authentication must be used.
        Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL);
        in.setInvokerCredentials(creds);

        OcfOrNotification fon = new OcfOrNotification();
        fon.setSyncNotification(in);
        this.bufferUnderConstruction.getOcfOrNotification().add(fon);
    }

    // Under sync on this.bufferMutex
    private void addTransferData(byte[] spaceDataUnit, int linkContinuity, Instant earthReceiveTime, boolean isPico, String antennaId, boolean globalAntennaId, byte[] privateAnnotations) {
        if(bufferActive && bufferUnderConstruction != null) {
            RocfTransferDataInvocation td = new RocfTransferDataInvocation();
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
            td.setPrivateAnnotation(new RocfTransferDataInvocation.PrivateAnnotation());
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

            OcfOrNotification fon = new OcfOrNotification();
            fon.setAnnotatedOcf(td);
            this.bufferUnderConstruction.getOcfOrNotification().add(fon);
            // Assumed delivered
            this.deliveredOcfsNumber.incrementAndGet();
        }
    }

    private void handleRocfStartInvocation(RocfStartInvocation invocation) {
        dispatchFromProvider(() -> doHandleRocfStartInvocation(invocation));
    }

    private void doHandleRocfStartInvocation(RocfStartInvocation invocation) {
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
        boolean permittedOk = true;
        if (!permittedGvcids.contains(rfq)) {
            permittedOk = false;
        }

        // Validate the requested OCF type
        RocfControlWordTypeEnum reqControlWordType;
        if(invocation.getControlWordType().getAllControlWords() != null) {
            reqControlWordType = RocfControlWordTypeEnum.ALL;
        } else if(invocation.getControlWordType().getClcw() != null) {
            reqControlWordType = RocfControlWordTypeEnum.CLCW;
        } else {
            reqControlWordType = RocfControlWordTypeEnum.NO_CLCW;
        }
        if(!permittedControlWordTypes.contains(reqControlWordType)) {
            permittedOk = false;
        }

        // Validate the TC VC ID
        if(reqControlWordType == RocfControlWordTypeEnum.CLCW) {
            if(invocation.getControlWordType().getClcw().getNoTcVC() != null && !this.permittedTcVcid.isEmpty()) {
                permittedOk = false;
            }
            if(invocation.getControlWordType().getClcw().getTcVcid() != null && !this.permittedTcVcid.contains(invocation.getControlWordType().getClcw().getTcVcid().intValue())) {
                permittedOk = false;
            }
        }

        // Validate the update type
        RocfUpdateModeEnum reqUptMode = RocfUpdateModeEnum.values()[invocation.getUpdateMode().intValue()];
        if(!permittedUpdateModes.contains(reqUptMode)) {
            permittedOk = false;
        }

        if (permittedOk) {
            // Ask the external handler if any
            Predicate<RocfStartInvocation> handler = this.startOperationHandler;
            if (handler != null) {
                permittedOk = handler.test(invocation);
            }
        }

        RocfStartReturn pdu = new RocfStartReturn();
        pdu.setInvokeId(invocation.getInvokeId());
        pdu.setResult(new RocfStartReturn.Result());
        if (permittedOk) {
            pdu.getResult().setPositiveResult(new BerNull());
        } else {
            pdu.getResult().setNegativeResult(new DiagnosticRocfStart());
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
                // Set the requested items
                this.requestedGvcid = rfq;
                this.requestedUpdateMode = reqUptMode;
                this.requestedControlWordType = reqControlWordType;
                this.requestedTcVcid = null;
                if(reqControlWordType == RocfControlWordTypeEnum.CLCW && invocation.getControlWordType().getClcw().getTcVcid() != null) {
                    this.requestedTcVcid = invocation.getControlWordType().getClcw().getTcVcid().intValue();
                }
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

    private void handleRocfGetParameterInvocation(RocfGetParameterInvocation invocation) {
        dispatchFromProvider(() -> doHandleRocfGetParameterInvocation(invocation));
    }

    private void doHandleRocfGetParameterInvocation(RocfGetParameterInvocation invocation) {
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
            RocfGetParameterReturnV1toV4 pdu = new RocfGetParameterReturnV1toV4();
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
            pdu.setResult(new RocfGetParameterReturnV1toV4.Result());
            pdu.getResult().setPositiveResult(new RocfGetParameterV1toV4());
            if (invocation.getRocfParameter().intValue() == RocfParameterEnum.BUFFER_SIZE.getCode()) {
                pdu.getResult().getPositiveResult().setParBufferSize(new RocfGetParameterV1toV4.ParBufferSize());
                pdu.getResult().getPositiveResult().getParBufferSize().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParBufferSize().setParameterValue(new IntPosShort(this.transferBufferSize));
            } else if (invocation.getRocfParameter().intValue() == RocfParameterEnum.DELIVERY_MODE.getCode()) {
                pdu.getResult().getPositiveResult().setParDeliveryMode(new RocfGetParameterV1toV4.ParDeliveryMode());
                pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterValue(new RocfDeliveryMode(this.deliveryMode.ordinal()));
            } else if (invocation.getRocfParameter().intValue() == RocfParameterEnum.LATENCY_LIMIT.getCode()) {
                pdu.getResult().getPositiveResult().setParLatencyLimit(new RocfGetParameterV1toV4.ParLatencyLimit());
                pdu.getResult().getPositiveResult().getParLatencyLimit().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParLatencyLimit().setParameterValue(new RocfGetParameterV1toV4.ParLatencyLimit.ParameterValue());
                pdu.getResult().getPositiveResult().getParLatencyLimit().getParameterValue().setOnline(new IntPosShort(this.latencyLimit));
            } else if (invocation.getRocfParameter().intValue() == RocfParameterEnum.REPORTING_CYCLE.getCode()) {
                pdu.getResult().getPositiveResult().setParReportingCycle(new RocfGetParameterV1toV4.ParReportingCycle());
                pdu.getResult().getPositiveResult().getParReportingCycle().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReportingCycle().setParameterValue(new CurrentReportingCycle());
                if (this.reportingScheduler.get() != null && this.reportingCycle != null) {
                    pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOn(new ReportingCycle(this.reportingCycle));
                } else {
                    pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOff(new BerNull());
                }
            } else if (invocation.getRocfParameter().intValue() == RocfParameterEnum.REQUESTED_GVCID.getCode()) {
                pdu.getResult().getPositiveResult().setParReqGvcId(new RocfGetParameterV1toV4.ParReqGvcId());
                pdu.getResult().getPositiveResult().getParReqGvcId().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReqGvcId().setParameterValue(new RequestedGvcIdV1toV4());
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
            } else if (invocation.getRocfParameter().intValue() == RocfParameterEnum.RETURN_TIMEOUT_PERIOD.getCode()) {
                pdu.getResult().getPositiveResult().setParReturnTimeout(new RocfGetParameterV1toV4.ParReturnTimeout());
                pdu.getResult().getPositiveResult().getParReturnTimeout().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReturnTimeout().setParameterValue(new TimeoutPeriod(this.returnTimeoutPeriod));
            } else if (invocation.getRocfParameter().intValue() == RocfParameterEnum.PERMITTED_GVCID_SET.getCode()) {
                pdu.getResult().getPositiveResult().setParPermittedGvcidSet(new RocfGetParameterV1toV4.ParPermittedGvcidSet());
                pdu.getResult().getPositiveResult().getParPermittedGvcidSet().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParPermittedGvcidSet().setParameterValue(new GvcIdSetV1toV4());
                for (GVCID permitted : this.permittedGvcids) {
                    MasterChannelCompositionV1toV4 entry = new MasterChannelCompositionV1toV4();
                    entry.setSpacecraftId(new BerInteger(permitted.getSpacecraftId()));
                    entry.setVersionNumber(new BerInteger(permitted.getTransferFrameVersionNumber()));
                    entry.setMcOrVcList(new MasterChannelCompositionV1toV4.McOrVcList());
                    if (permitted.getVirtualChannelId() == null) {
                        entry.getMcOrVcList().setMasterChannel(new BerNull());
                    } else {
                        entry.getMcOrVcList().setVcList(new MasterChannelCompositionV1toV4.McOrVcList.VcList());
                        entry.getMcOrVcList().getVcList().getVcId().add(new VcId(permitted.getVirtualChannelId()));
                    }
                    pdu.getResult().getPositiveResult().getParPermittedGvcidSet().getParameterValue().getMasterChannelCompositionV1toV4().add(entry);
                }
            } else if(invocation.getRocfParameter().intValue() == RocfParameterEnum.PERMITTED_CONTROL_WORD_TYPE_SET.getCode()) {
                pdu.getResult().getPositiveResult().setParPermittedRprtTypeSet(new RocfGetParameterV1toV4.ParPermittedRprtTypeSet());
                pdu.getResult().getPositiveResult().getParPermittedRprtTypeSet().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParPermittedRprtTypeSet().setParameterValue(new RocfGetParameterV1toV4.ParPermittedRprtTypeSet.ParameterValue());
                for(RocfControlWordTypeEnum en : this.permittedControlWordTypes) {
                    pdu.getResult().getPositiveResult().getParPermittedRprtTypeSet().getParameterValue().getControlWordTypeNumber().add(new ControlWordTypeNumber(en.ordinal()));
                }
            } else if(invocation.getRocfParameter().intValue() == RocfParameterEnum.PERMITTED_TC_VCID_SET.getCode()) {
                pdu.getResult().getPositiveResult().setParPermittedTcVcidSet(new RocfGetParameterV1toV4.ParPermittedTcVcidSet());
                pdu.getResult().getPositiveResult().getParPermittedTcVcidSet().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParPermittedTcVcidSet().setParameterValue(new TcVcidSet());
                if(this.permittedTcVcid == null || this.permittedTcVcid.isEmpty()) {
                    pdu.getResult().getPositiveResult().getParPermittedTcVcidSet().getParameterValue().setNoTcVC(new BerNull());
                } else {
                    pdu.getResult().getPositiveResult().getParPermittedTcVcidSet().getParameterValue().setTcVcids(new TcVcidSet.TcVcids());
                    for (int i : this.permittedTcVcid) {
                        pdu.getResult().getPositiveResult().getParPermittedTcVcidSet().getParameterValue().getTcVcids().getVcId().add(new VcId(i));
                    }
                }
            } else if(invocation.getRocfParameter().intValue() == RocfParameterEnum.PERMITTED_UPDATE_MODE_SET.getCode()) {
                pdu.getResult().getPositiveResult().setParPermittedUpdModeSet(new RocfGetParameterV1toV4.ParPermittedUpdModeSet());
                pdu.getResult().getPositiveResult().getParPermittedUpdModeSet().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParPermittedUpdModeSet().setParameterValue(new RocfGetParameterV1toV4.ParPermittedUpdModeSet.ParameterValue());
                for (RocfUpdateModeEnum en : this.permittedUpdateModes) {
                    pdu.getResult().getPositiveResult().getParPermittedUpdModeSet().getParameterValue().getUpdateMode().add(new UpdateMode(en.ordinal()));
                }
            } else if(invocation.getRocfParameter().intValue() == RocfParameterEnum.REQUESTED_CONTROL_WORD_TYPE.getCode()) {
                pdu.getResult().getPositiveResult().setParReqControlWordType(new RocfGetParameterV1toV4.ParReqControlWordType());
                pdu.getResult().getPositiveResult().getParReqControlWordType().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReqControlWordType().setParameterValue(new RequestedControlWordTypeNumberV1toV4(this.requestedControlWordType == null ? this.permittedControlWordTypes.get(0).ordinal() : this.requestedControlWordType.ordinal()));
            } else if(invocation.getRocfParameter().intValue() == RocfParameterEnum.REQUESTED_TC_VCID.getCode()) {
                pdu.getResult().getPositiveResult().setParReqTcVcid(new RocfGetParameterV1toV4.ParReqTcVcid());
                pdu.getResult().getPositiveResult().getParReqTcVcid().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReqTcVcid().setParameterValue(new RequestedTcVcidV1toV4());
                if(this.requestedControlWordType == RocfControlWordTypeEnum.CLCW) {
                    pdu.getResult().getPositiveResult().getParReqTcVcid().getParameterValue().setTcVcid(new TcVcid());
                    if(this.requestedTcVcid == null) {
                        pdu.getResult().getPositiveResult().getParReqTcVcid().getParameterValue().getTcVcid().setNoTcVC(new BerNull());
                    } else {
                        pdu.getResult().getPositiveResult().getParReqTcVcid().getParameterValue().getTcVcid().setTcVcid(new VcId(this.requestedTcVcid));
                    }
                } else {
                    pdu.getResult().getPositiveResult().getParReqTcVcid().getParameterValue().setUndefined(new BerNull());
                }
            } else if(invocation.getRocfParameter().intValue() == RocfParameterEnum.REQUESTED_UPDATE_MODE.getCode()) {
                pdu.getResult().getPositiveResult().setParReqUpdateMode(new RocfGetParameterV1toV4.ParReqUpdateMode());
                pdu.getResult().getPositiveResult().getParReqUpdateMode().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReqUpdateMode().setParameterValue(new RequestedUpdateModeV1toV4(this.requestedUpdateMode == null ? 3 : this.requestedUpdateMode.ordinal())); // 3 is undefined in old specs
            } else {
                pdu.getResult().setPositiveResult(null);
                pdu.getResult().setNegativeResult(new DiagnosticRocfGet());
                pdu.getResult().getNegativeResult().setSpecific(new BerInteger(0)); // unknownParameter
            }
            toSend = pdu;
        } else {
            RocfGetParameterReturn pdu = new RocfGetParameterReturn();
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
            pdu.setResult(new RocfGetParameterReturn.Result());
            pdu.getResult().setPositiveResult(new RocfGetParameter());
            if (invocation.getRocfParameter().intValue() == RocfParameterEnum.BUFFER_SIZE.getCode()) {
                pdu.getResult().getPositiveResult().setParBufferSize(new RocfGetParameter.ParBufferSize());
                pdu.getResult().getPositiveResult().getParBufferSize().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParBufferSize().setParameterValue(new IntPosShort(this.transferBufferSize));
            } else if (invocation.getRocfParameter().intValue() == RocfParameterEnum.DELIVERY_MODE.getCode()) {
                pdu.getResult().getPositiveResult().setParDeliveryMode(new RocfGetParameter.ParDeliveryMode());
                pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterValue(new RocfDeliveryMode(this.deliveryMode.ordinal()));
            } else if (invocation.getRocfParameter().intValue() == RocfParameterEnum.LATENCY_LIMIT.getCode()) {
                pdu.getResult().getPositiveResult().setParLatencyLimit(new RocfGetParameter.ParLatencyLimit());
                pdu.getResult().getPositiveResult().getParLatencyLimit().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParLatencyLimit().setParameterValue(new RocfGetParameter.ParLatencyLimit.ParameterValue());
                pdu.getResult().getPositiveResult().getParLatencyLimit().getParameterValue().setOnline(new IntPosShort(this.latencyLimit));
            } else if (invocation.getRocfParameter().intValue() == RocfParameterEnum.REPORTING_CYCLE.getCode()) {
                pdu.getResult().getPositiveResult().setParReportingCycle(new RocfGetParameter.ParReportingCycle());
                pdu.getResult().getPositiveResult().getParReportingCycle().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReportingCycle().setParameterValue(new CurrentReportingCycle());
                if (this.reportingScheduler.get() != null && this.reportingCycle != null) {
                    pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOn(new ReportingCycle(this.reportingCycle));
                } else {
                    pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOff(new BerNull());
                }
            } else if (invocation.getRocfParameter().intValue() == RocfParameterEnum.REQUESTED_GVCID.getCode()) {
                pdu.getResult().getPositiveResult().setParReqGvcId(new RocfGetParameter.ParReqGvcId());
                pdu.getResult().getPositiveResult().getParReqGvcId().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReqGvcId().setParameterValue(new RequestedGvcId());
                if (this.requestedGvcid != null) {
                    pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().setSpacecraftId(new BerInteger(this.requestedGvcid.getSpacecraftId()));
                    pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().setVersionNumber(new BerInteger(this.requestedGvcid.getTransferFrameVersionNumber()));
                    pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().setVcId(new GvcId.VcId());
                    if(this.requestedGvcid.getVirtualChannelId() == null) {
                        pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().getVcId().setMasterChannel(new BerNull());
                    } else {
                        pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().getVcId().setVirtualChannel(new VcId(this.requestedGvcid.getVirtualChannelId()));
                    }
                } else {
                    pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().setSpacecraftId(new BerInteger(this.permittedGvcids.get(0).getSpacecraftId()));
                    pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().setVersionNumber(new BerInteger(this.permittedGvcids.get(0).getTransferFrameVersionNumber()));
                    pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().setVcId(new GvcId.VcId());
                    if(this.permittedGvcids.get(0).getVirtualChannelId() == null) {
                        pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().getVcId().setMasterChannel(new BerNull());
                    } else {
                        pdu.getResult().getPositiveResult().getParReqGvcId().getParameterValue().getVcId().setVirtualChannel(new VcId(this.permittedGvcids.get(0).getVirtualChannelId()));
                    }
                }
            } else if (invocation.getRocfParameter().intValue() == RocfParameterEnum.RETURN_TIMEOUT_PERIOD.getCode()) {
                pdu.getResult().getPositiveResult().setParReturnTimeout(new RocfGetParameter.ParReturnTimeout());
                pdu.getResult().getPositiveResult().getParReturnTimeout().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReturnTimeout().setParameterValue(new TimeoutPeriod(this.returnTimeoutPeriod));
            } else if (invocation.getRocfParameter().intValue() == RocfParameterEnum.MIN_REPORTING_CYCLE.getCode()) {
                pdu.getResult().getPositiveResult().setParMinReportingCycle(new RocfGetParameter.ParMinReportingCycle());
                pdu.getResult().getPositiveResult().getParMinReportingCycle().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParMinReportingCycle().setParameterValue(new IntPosShort(this.minReportingCycle != null ? this.minReportingCycle : 0));
            } else if (invocation.getRocfParameter().intValue() == RocfParameterEnum.PERMITTED_GVCID_SET.getCode()) {
                pdu.getResult().getPositiveResult().setParPermittedGvcidSet(new RocfGetParameter.ParPermittedGvcidSet());
                pdu.getResult().getPositiveResult().getParPermittedGvcidSet().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
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
            } else if(invocation.getRocfParameter().intValue() == RocfParameterEnum.PERMITTED_CONTROL_WORD_TYPE_SET.getCode()) {
                pdu.getResult().getPositiveResult().setParPermittedRprtTypeSet(new RocfGetParameter.ParPermittedRprtTypeSet());
                pdu.getResult().getPositiveResult().getParPermittedRprtTypeSet().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParPermittedRprtTypeSet().setParameterValue(new RocfGetParameter.ParPermittedRprtTypeSet.ParameterValue());
                for(RocfControlWordTypeEnum en : this.permittedControlWordTypes) {
                    pdu.getResult().getPositiveResult().getParPermittedRprtTypeSet().getParameterValue().getControlWordTypeNumber().add(new ControlWordTypeNumber(en.ordinal()));
                }
            } else if(invocation.getRocfParameter().intValue() == RocfParameterEnum.PERMITTED_TC_VCID_SET.getCode()) {
                pdu.getResult().getPositiveResult().setParPermittedTcVcidSet(new RocfGetParameter.ParPermittedTcVcidSet());
                pdu.getResult().getPositiveResult().getParPermittedTcVcidSet().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParPermittedTcVcidSet().setParameterValue(new TcVcidSet());
                if(this.permittedTcVcid == null || this.permittedTcVcid.isEmpty()) {
                    pdu.getResult().getPositiveResult().getParPermittedTcVcidSet().getParameterValue().setNoTcVC(new BerNull());
                } else {
                    pdu.getResult().getPositiveResult().getParPermittedTcVcidSet().getParameterValue().setTcVcids(new TcVcidSet.TcVcids());
                    for (int i : this.permittedTcVcid) {
                        pdu.getResult().getPositiveResult().getParPermittedTcVcidSet().getParameterValue().getTcVcids().getVcId().add(new VcId(i));
                    }
                }
            } else if(invocation.getRocfParameter().intValue() == RocfParameterEnum.PERMITTED_UPDATE_MODE_SET.getCode()) {
                pdu.getResult().getPositiveResult().setParPermittedUpdModeSet(new RocfGetParameter.ParPermittedUpdModeSet());
                pdu.getResult().getPositiveResult().getParPermittedUpdModeSet().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParPermittedUpdModeSet().setParameterValue(new RocfGetParameter.ParPermittedUpdModeSet.ParameterValue());
                for (RocfUpdateModeEnum en : this.permittedUpdateModes) {
                    pdu.getResult().getPositiveResult().getParPermittedUpdModeSet().getParameterValue().getUpdateMode().add(new UpdateMode(en.ordinal()));
                }
            } else if(invocation.getRocfParameter().intValue() == RocfParameterEnum.REQUESTED_CONTROL_WORD_TYPE.getCode()) {
                pdu.getResult().getPositiveResult().setParReqControlWordType(new RocfGetParameter.ParReqControlWordType());
                pdu.getResult().getPositiveResult().getParReqControlWordType().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReqControlWordType().setParameterValue(new RequestedControlWordTypeNumber(this.requestedControlWordType == null ? this.permittedControlWordTypes.get(0).ordinal() : this.requestedControlWordType.ordinal()));
            } else if(invocation.getRocfParameter().intValue() == RocfParameterEnum.REQUESTED_TC_VCID.getCode()) {
                pdu.getResult().getPositiveResult().setParReqTcVcid(new RocfGetParameter.ParReqTcVcid());
                pdu.getResult().getPositiveResult().getParReqTcVcid().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReqTcVcid().setParameterValue(new RequestedTcVcid());
                if(this.requestedTcVcid == null) {
                    pdu.getResult().getPositiveResult().getParReqTcVcid().getParameterValue().setNoTcVC(new BerNull());
                } else {
                    pdu.getResult().getPositiveResult().getParReqTcVcid().getParameterValue().setTcVcid(new VcId(this.requestedTcVcid));
                }
            } else if(invocation.getRocfParameter().intValue() == RocfParameterEnum.REQUESTED_UPDATE_MODE.getCode()) {
                pdu.getResult().getPositiveResult().setParReqUpdateMode(new RocfGetParameter.ParReqUpdateMode());
                pdu.getResult().getPositiveResult().getParReqUpdateMode().setParameterName(new ParameterName(invocation.getRocfParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReqUpdateMode().setParameterValue(new RequestedUpdateMode(this.requestedUpdateMode == null ? this.permittedUpdateModes.get(0).ordinal() : this.requestedUpdateMode.ordinal()));
            } else {
                pdu.getResult().setPositiveResult(null);
                pdu.getResult().setNegativeResult(new DiagnosticRocfGet());
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
        RocfStatusReportInvocation pdu = new RocfStatusReportInvocation();
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
            pdu.setDeliveredOcfsNumber(new IntUnsignedLong(this.deliveredOcfsNumber.get()));
            pdu.setProcessedFrameNumber(new IntUnsignedLong(this.processedFrameNumber.get()));
            pdu.setProductionStatus(new RocfProductionStatus(this.productionStatus.ordinal()));
            pdu.setSubcarrierLockStatus(new LockStatus(this.subcarrierLockStatus.ordinal()));
            pdu.setSymbolSyncLockStatus(new SymbolLockStatus(this.symbolSyncLockStatus.ordinal()));
        } finally {
            this.statusMutex.unlock();
        }
        return pdu;
    }

    @Override
    protected ServiceInstanceState buildCurrentState() {
        RocfServiceInstanceState state = new RocfServiceInstanceState();
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
        state.setDeliveredOcfsNumber(deliveredOcfsNumber.get());
        state.setProcessedFrameNumber(processedFrameNumber.get());
        state.setPermittedGvcid(new ArrayList<>(permittedGvcids));
        state.setPermittedControlWordTypes(new ArrayList<>(permittedControlWordTypes));
        state.setPermittedTcVcid(new ArrayList<>(permittedTcVcid));
        state.setPermittedUpdateModes(new ArrayList<>(permittedUpdateModes));
        state.setRequestedControlWordType(requestedControlWordType);
        state.setRequestedGvcid(requestedGvcid);
        state.setRequestedUpdateMode(requestedUpdateMode);
        state.setRequestedTcVcid(requestedTcVcid);
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
        return ApplicationIdentifierEnum.ROCF;
    }

    @Override
    protected void doResetState() {
        this.latencyLimit = getRocfConfiguration().getLatencyLimit();
        this.permittedGvcids = getRocfConfiguration().getPermittedGvcid();
        this.permittedUpdateModes = getRocfConfiguration().getPermittedUpdateModes();
        this.permittedTcVcid = getRocfConfiguration().getPermittedTcVcids();
        this.permittedControlWordTypes = getRocfConfiguration().getPermittedControlWordTypes();
        this.minReportingCycle = getRocfConfiguration().getMinReportingCycle();
        this.returnTimeoutPeriod = getRocfConfiguration().getReturnTimeoutPeriod();
        this.transferBufferSize = getRocfConfiguration().getTransferBufferSize();
        this.requestedGvcid = null;
        this.requestedUpdateMode = null;
        this.requestedTcVcid = null;
        this.requestedControlWordType = null;
        this.processedFrameNumber.set(0);
        this.deliveredOcfsNumber.set(0);
    }

    private RocfServiceInstanceConfiguration getRocfConfiguration() {
        return (RocfServiceInstanceConfiguration) this.serviceInstanceConfiguration;
    }
}
