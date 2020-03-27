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

package eu.dariolucia.ccsds.sle.utl.server;

import com.beanit.jasn1.ber.types.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.*;
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
import eu.dariolucia.ccsds.sle.utl.si.rcf.RcfParameterEnum;
import eu.dariolucia.ccsds.sle.utl.si.rcf.RcfServiceInstanceState;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * One object of this class represents an RCF Service Instance (provider role).
 */
public class RcfServiceInstanceProvider extends ServiceInstance {

    private static final Logger LOG = Logger.getLogger(RcfServiceInstanceProvider.class.getName());

    // Read from configuration, retrieved via GET_PARAMETER
    private Integer latencyLimit; // NULL if offline, otherwise a value
    private List<GVCID> permittedGvcids;
    private Integer minReportingCycle;
    private int returnTimeoutPeriod;
    private int transferBufferSize;
    private DeliveryModeEnum deliveryMode = null;

    // Updated via START and GET_PARAMETER
    private volatile GVCID requestedGvcid = null;
    private Integer reportingCycle = null; // NULL if off, otherwise a value
    private volatile Date startTime = null;
    private volatile Date endTime = null;

    // Requested via STATUS_REPORT, updated externally (therefore they are protected via separate lock)
    private final ReentrantLock statusMutex = new ReentrantLock();
    private volatile int deliveredFrameNumber = 0;
    private LockStatusEnum frameSyncLockStatus = LockStatusEnum.OUT_OF_LOCK;
    private LockStatusEnum symbolSyncLockStatus = LockStatusEnum.OUT_OF_LOCK;
    private LockStatusEnum subcarrierLockStatus = LockStatusEnum.OUT_OF_LOCK;
    private LockStatusEnum carrierLockStatus = LockStatusEnum.OUT_OF_LOCK;
    private ProductionStatusEnum productionStatus = ProductionStatusEnum.HALTED;

    // Encoder/decoder
    private final RcfProviderEncDec encDec = new RcfProviderEncDec();

    // Status report scheduler
    private volatile Timer reportingScheduler = null;

    // Transfer buffer under construction
    private final ReentrantLock bufferMutex = new ReentrantLock();
    private final Condition bufferChangedCondition = bufferMutex.newCondition();
    private RcfTransferBuffer bufferUnderConstruction = null;
    private boolean bufferUnderTransmission = false;
    private boolean bufferActive = false;

    // Latency timer
    private final Timer latencyTimer = new Timer();
    private volatile TimerTask pendingLatencyTimeout = null;

    // Operation extension handlers: they are called to drive the positive/negative response (where supported)
    private volatile Function<RcfStartInvocation, Boolean> startOperationHandler;

    public RcfServiceInstanceProvider(PeerConfiguration apiConfiguration,
                                      RcfServiceInstanceConfiguration serviceInstanceConfiguration) {
        super(apiConfiguration, serviceInstanceConfiguration);
    }

    @Override
    protected void setup() {
        // Register handlers
        registerPduReceptionHandler(RcfStartInvocation.class, this::handleRcfStartInvocation);
        registerPduReceptionHandler(SleStopInvocation.class, this::handleRcfStopInvocation);
        registerPduReceptionHandler(SleScheduleStatusReportInvocation.class, this::handleRcfScheduleStatusReportInvocation);
        registerPduReceptionHandler(RcfGetParameterInvocation.class, this::handleRcfGetParameterInvocation);
    }

    public void setStartOperationHandler(Function<RcfStartInvocation, Boolean> handler) {
        this.startOperationHandler = handler;
    }

    public void updateProductionStatus(Instant time, LockStatusEnum carrier, LockStatusEnum subCarrier, LockStatusEnum symbol, LockStatusEnum frame, ProductionStatusEnum productionStatus) {
        ProductionStatusEnum previousProductionStatus;
        LockStatusEnum previousFrameLockStatus;
        this.statusMutex.lock();
        try {
            previousProductionStatus = this.productionStatus;
            previousFrameLockStatus = this.frameSyncLockStatus;
            this.carrierLockStatus = carrier;
            this.subcarrierLockStatus = subCarrier;
            this.symbolSyncLockStatus = symbol;
            this.frameSyncLockStatus = frame;
            this.productionStatus = productionStatus;
        } finally {
            this.statusMutex.unlock();
        }
        boolean currentBufferActive;
        this.bufferMutex.lock();
        currentBufferActive = this.bufferActive;
        this.bufferMutex.unlock();

        if (previousFrameLockStatus == LockStatusEnum.IN_LOCK && frame != LockStatusEnum.IN_LOCK) {
            // We lost the frame lock, we need to send a notification
            if (currentBufferActive && this.deliveryMode != DeliveryModeEnum.OFFLINE) {
                sendLossFrameNotification(time, carrier, subCarrier, symbol);
            }
        }
        if (previousProductionStatus != productionStatus) {
            // We changed production status, we need to send a notification
            if (currentBufferActive && this.deliveryMode != DeliveryModeEnum.OFFLINE) {
                sendProductionStatusChangeNotification(productionStatus);
            }
        }
    }

    private boolean sendProductionStatusChangeNotification(ProductionStatusEnum productionStatus) {
        this.bufferMutex.lock();
        try {
            // If the state is not correct, say bye
            if (!this.bufferActive) {
                return false;
            }
            // Here we check immediately if the buffer is full: if so, we send it
            checkBuffer(false);
            // Add the PDU to the buffer, there must be free space by algorithm implementation
            addProductionStatusChangeNotification(productionStatus);
            checkBuffer(true); // We send it immediately
            return true;
        } finally {
            this.bufferChangedCondition.signalAll();
            this.bufferMutex.unlock();
        }
    }

    private boolean sendLossFrameNotification(Instant time, LockStatusEnum carrierLockStatus, LockStatusEnum subcarrierLockStatus, LockStatusEnum symbolSyncLockStatus) {
        this.bufferMutex.lock();
        try {
            // If the state is not correct, say bye
            if (!this.bufferActive) {
                return false;
            }
            // Here we check immediately if the buffer is full: if so, we send it
            checkBuffer(false);
            // Add the PDU to the buffer, there must be free space by algorithm implementation
            addLossFrameSyncNotification(time, carrierLockStatus, subcarrierLockStatus, symbolSyncLockStatus);
            checkBuffer(true); // We send it immediately
            return true;
        } finally {
            this.bufferMutex.unlock();
        }
    }


    public boolean dataDiscarded() {
        this.bufferMutex.lock();
        try {
            // If the state is not correct, say bye
            if (!this.bufferActive) {
                return false;
            }
            // Here we check immediately if the buffer is full: if so, we send it
            checkBuffer(false);
            // Add the PDU to the buffer, there must be free space by algorithm implementation
            addDataDiscardedNotification();
            checkBuffer(false); // No need to send it immediately
            return true;
        } finally {
            this.bufferMutex.unlock();
        }
    }

    public boolean endOfData() {
        this.bufferMutex.lock();
        try {
            // If the state is not correct, say bye
            if (!this.bufferActive) {
                return false;
            }
            // Here we check immediately if the buffer is full: if so, we send it
            checkBuffer(false);
            // Add the PDU to the buffer, there must be free space by algorithm implementation
            addEndOfDataNotification();
            checkBuffer(true); // We send it immediately
            return true;
        } finally {
            this.bufferMutex.unlock();
        }
    }

    public boolean transferData(byte[] spaceDataUnit, int linkContinuity, Instant earthReceiveTime, boolean isPico, String antennaId, boolean globalAntennaId, byte[] privateAnnotations) {
        this.bufferMutex.lock();
        try {
            // If the state is not correct, say bye
            if (!this.bufferActive) {
                return false;
            }
            // Here we check immediately if the buffer is full: if so, we send it
            checkBuffer(false);
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
            checkBuffer(false);
            return true;
        } finally {
            this.bufferMutex.unlock();
        }
    }

    private boolean match(GVCID requestedGvcid, byte[] spaceDataUnit) {
        ByteBuffer bb = ByteBuffer.wrap(spaceDataUnit);
        short header = bb.getShort();
        // Extract TFVN, VCID and SCID from the frame
        int tfvn = ((header & 0xC000) & 0xFFFF) >>> 14;
        int scid = -1;
        int vcid = -1;
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

    // Under sync on this.bufferMutex
    private void checkBuffer(boolean forceSend) {
        if(!bufferActive) {
            return;
        }
        if (this.bufferUnderConstruction == null || this.bufferUnderConstruction.getFrameOrNotification().isEmpty()) {
            // No data, nothing to do
            return;
        }
        if (this.bufferUnderConstruction.getFrameOrNotification().size() == this.transferBufferSize || forceSend) {
            // Stop the latency timer
            stopLatencyTimer();
            // Try to send the buffer: replace the buffer with a new one
            RcfTransferBuffer bufferToSend = this.bufferUnderConstruction;
            this.bufferUnderConstruction = new RcfTransferBuffer();
            boolean discarded = trySendBuffer(bufferToSend);
            // If discarded, add a sync notification about it
            if (discarded) {
                addDataDiscardedNotification();
            }
            // I do not call this recursively, we are under lock: if there transfer buffer is set with 1 item, anyway go out
            // and at the next call, it will be sent ... or not

            // Start the latency timer
            startLatencyTimer();

            // Signal the change
            this.bufferChangedCondition.signalAll();
        }
    }

    private void startLatencyTimer() {
        if (this.latencyLimit == null) {
            return; // No timer
        }

        stopLatencyTimer();

        this.pendingLatencyTimeout = new TimerTask() {
            @Override
            public void run() {
                if (pendingLatencyTimeout == this) {
                    // Elapsed, send the buffer if needed
                    latencyElapsed();
                }
            }
        };
        this.latencyTimer.schedule(this.pendingLatencyTimeout, this.latencyLimit * 1000L);
    }

    private void latencyElapsed() {
        // Add the PDU to the buffer, there must be free space by algorithm implementation
        this.bufferMutex.lock();
        try {
            checkBuffer(true);
        } finally {
            this.bufferMutex.unlock();
        }
    }

    private void stopLatencyTimer() {
        if (this.pendingLatencyTimeout != null) {
            this.pendingLatencyTimeout.cancel();
            this.pendingLatencyTimeout = null;
        }
    }

    // Under sync on this.bufferMutex
    private boolean trySendBuffer(RcfTransferBuffer bufferToSend) {
        // Here we check the delivery mode
        if (getRcfConfiguration().getDeliveryMode() == DeliveryModeEnum.TIMELY_ONLINE) {
            // Timely mode: if there is a buffer in transmission, you have to discard the buffer
            if (this.bufferUnderTransmission) {
                return true;
            }
        } else {
            // Complete mode: wait
            while (this.bufferActive && this.bufferUnderTransmission) {
                try {
                    this.bufferChangedCondition.await();
                } catch (InterruptedException e) {
                    // Buffer discarded
                    return true;
                }
            }
        }
        if (bufferActive) {
            // Set transmission flag
            this.bufferUnderTransmission = true;
            // Send the buffer
            dispatchFromProvider(() -> doHandleRcfTransferBufferInvocation(bufferToSend));
            // Not discarded
            return false;
        } else {
            return true;
        }
    }

    private void doHandleRcfTransferBufferInvocation(RcfTransferBuffer bufferToSend) {
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
            this.bufferMutex.lock();
            this.bufferUnderTransmission = false;
            this.bufferChangedCondition.signalAll();
            this.bufferMutex.unlock();
            // Notify PDU
            notifyPduSent(bufferToSend, SleOperationNames.TRANSFER_BUFFER_NAME, getLastPduSent());
            // Generate state and notify update
            notifyStateUpdate();
        }
    }

    // Under sync on this.bufferMutex
    private void addProductionStatusChangeNotification(ProductionStatusEnum productionStatus) {
        if(!bufferActive || bufferUnderConstruction == null) {
            return;
        }
        RcfSyncNotifyInvocation in = new RcfSyncNotifyInvocation();
        in.setNotification(new Notification());
        in.getNotification().setProductionStatusChange(new RcfProductionStatus(productionStatus.ordinal()));
        finalizeAndAddNotification(in);
    }

    // Under sync on this.bufferMutex
    private void addDataDiscardedNotification() {
        if(!bufferActive || bufferUnderConstruction == null) {
            return;
        }
        RcfSyncNotifyInvocation in = new RcfSyncNotifyInvocation();
        in.setNotification(new Notification());
        in.getNotification().setExcessiveDataBacklog(new BerNull());
        finalizeAndAddNotification(in);
    }

    // Under sync on this.bufferMutex
    private void addLossFrameSyncNotification(Instant time, LockStatusEnum carrierLockStatus, LockStatusEnum subcarrierLockStatus, LockStatusEnum symbolSyncLockStatus) {
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
    private void addEndOfDataNotification() {
        if(!bufferActive || bufferUnderConstruction == null) {
            return;
        }
        RcfSyncNotifyInvocation in = new RcfSyncNotifyInvocation();
        in.setNotification(new Notification());
        in.getNotification().setEndOfData(new BerNull());
        finalizeAndAddNotification(in);
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
        ++this.deliveredFrameNumber;
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
            Function<RcfStartInvocation, Boolean> handler = this.startOperationHandler;
            if (handler != null) {
                permittedOk = handler.apply(invocation);
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
                // Set times
                this.startTime = PduFactoryUtil.toDate(invocation.getStartTime());
                this.endTime = PduFactoryUtil.toDate(invocation.getStopTime());
                // Activate capability to send frames and notifications
                this.bufferMutex.lock();
                try {
                    this.bufferActive = true;
                    this.bufferUnderTransmission = false;
                    this.bufferUnderConstruction = new RcfTransferBuffer();
                    this.bufferChangedCondition.signalAll();
                } finally {
                    this.bufferMutex.unlock();
                }
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

    private void handleRcfStopInvocation(SleStopInvocation invocation) {
        dispatchFromProvider(() -> doHandleRcfStopInvocation(invocation));
    }

    private void doHandleRcfStopInvocation(SleStopInvocation invocation) {
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
        if (!authenticate(invocation.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
            disconnect("Stop invocation received, but wrong credentials");
            notifyPduReceived(invocation, SleOperationNames.STOP_NAME, getLastPduReceived());
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
            notifyPduSentError(pdu, SleOperationNames.STOP_RETURN_NAME, null);
            notifyStateUpdate();
            return;
        } else {
            pdu.setCredentials(creds);
        }

        // Stop the ability to add transfer frames: pending buffers in the executor queue will still be processed
        // and sent. As soon as the STOP-RETURN is sent, the state will go to READY and pending buffers will be discarded.
        this.bufferMutex.lock();
        try {
            this.bufferActive = false;
            this.bufferChangedCondition.signalAll();
        } finally {
            this.bufferMutex.unlock();
        }

        dispatchFromProvider(() -> {
            boolean resultOk = encodeAndSend(null, pdu, SleOperationNames.STOP_RETURN_NAME);

            if (resultOk) {
                // Stop the latency timer
                stopLatencyTimer();
                // Schedule this last part in the management thread
                this.bufferMutex.lock();
                try {
                    this.bufferActive = false;
                    this.bufferUnderTransmission = false;
                    this.bufferUnderConstruction = null;
                    this.bufferChangedCondition.signalAll();
                } finally {
                    this.bufferMutex.unlock();
                }

                // If all fine, transition to new state: READY and notify PDU sent
                setServiceInstanceState(ServiceInstanceBindingStateEnum.READY);
                // Set the requested GVCID
                this.requestedGvcid = null;
                // Set times
                this.startTime = null;
                this.endTime = null;
                // Notify PDU
                notifyPduSent(pdu, SleOperationNames.STOP_RETURN_NAME, getLastPduSent());
                // Generate state and notify update
                notifyStateUpdate();
            }
        });
    }

    private void handleRcfScheduleStatusReportInvocation(SleScheduleStatusReportInvocation invocation) {
        dispatchFromProvider(() -> doHandleRcfScheduleStatusReportInvocation(invocation));
    }

    private void doHandleRcfScheduleStatusReportInvocation(SleScheduleStatusReportInvocation invocation) {
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
        if (!authenticate(invocation.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
            disconnect("Schedule status report received, but wrong credentials");
            notifyPduReceived(invocation, SleOperationNames.SCHEDULE_STATUS_REPORT_NAME, getLastPduReceived());
            notifyStateUpdate();
            return;
        }

        SleScheduleStatusReportReturn pdu = new SleScheduleStatusReportReturn();
        pdu.setInvokeId(invocation.getInvokeId());
        pdu.setResult(new SleScheduleStatusReportReturn.Result());

        if (invocation.getReportRequestType().getImmediately() != null) {
            sendStatusReport(true);
            pdu.getResult().setPositiveResult(new BerNull());
        } else if (invocation.getReportRequestType().getStop() != null) {
            if (this.reportingScheduler != null) {
                stopStatusReport();
                pdu.getResult().setPositiveResult(new BerNull());
            } else {
                pdu.getResult().setNegativeResult(new DiagnosticScheduleStatusReport());
                pdu.getResult().getNegativeResult().setSpecific(new BerInteger(1)); // Already stopped
            }
        } else if (invocation.getReportRequestType().getPeriodically() != null) {
            int period = invocation.getReportRequestType().getPeriodically().intValue();
            if (this.minReportingCycle == null || period > this.minReportingCycle) {
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
            notifyPduSentError(pdu, SleOperationNames.SCHEDULE_STATUS_REPORT_RETURN_NAME, null);
            notifyStateUpdate();
            return;
        } else {
            pdu.setPerformerCredentials(creds);
        }

        boolean resultOk = encodeAndSend(null, pdu, SleOperationNames.SCHEDULE_STATUS_REPORT_RETURN_NAME);

        if (resultOk) {
            // Notify PDU
            notifyPduSent(pdu, SleOperationNames.SCHEDULE_STATUS_REPORT_RETURN_NAME, getLastPduSent());
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
                if (reportingScheduler != null) {
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
                if (this.reportingScheduler != null && this.reportingCycle != null) {
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
                if (this.reportingScheduler != null && this.reportingCycle != null) {
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

    private void sendStatusReport(boolean immediate) {
        if (!immediate && this.reportingScheduler == null) {
            return;
        }
        if (getSleVersion() == 1) {
            RcfStatusReportInvocationV1 pdu = new RcfStatusReportInvocationV1();
            // Add credentials
            Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL);
            if (creds == null) {
                // Error while generating credentials, set by generateCredentials()
                notifyPduSentError(pdu, SleOperationNames.STATUS_REPORT_NAME, null);
                notifyStateUpdate();
                return;
            } else {
                pdu.setInvokerCredentials(creds);
            }
            //
            this.statusMutex.lock();
            try {
                pdu.setCarrierLockStatus(new LockStatus(this.carrierLockStatus.ordinal()));
                pdu.setFrameSyncLockStatus(new LockStatus(this.frameSyncLockStatus.ordinal()));
                pdu.setDeliveredFrameNumber(new IntUnsignedLong(this.deliveredFrameNumber));
                pdu.setProductionStatus(new RcfProductionStatus(this.productionStatus.ordinal()));
                pdu.setSubcarrierLockStatus(new LockStatus(this.subcarrierLockStatus.ordinal()));
                pdu.setSymbolSyncLockStatus(new LockStatus(this.symbolSyncLockStatus.ordinal()));
            } finally {
                this.statusMutex.unlock();
            }
            boolean resultOk = encodeAndSend(null, pdu, SleOperationNames.STATUS_REPORT_NAME);

            if (resultOk) {
                // Notify PDU
                notifyPduSent(pdu, SleOperationNames.STATUS_REPORT_NAME, getLastPduSent());
                // Generate state and notify update
                notifyStateUpdate();
            }
        } else {
            RcfStatusReportInvocation pdu = new RcfStatusReportInvocation();
            // Add credentials
            Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL);
            if (creds == null) {
                // Error while generating credentials, set by generateCredentials()
                notifyPduSentError(pdu, SleOperationNames.STATUS_REPORT_NAME, null);
                notifyStateUpdate();
                return;
            } else {
                pdu.setInvokerCredentials(creds);
            }
            //
            this.statusMutex.lock();
            try {
                pdu.setCarrierLockStatus(new CarrierLockStatus(this.carrierLockStatus.ordinal()));
                pdu.setFrameSyncLockStatus(new FrameSyncLockStatus(this.frameSyncLockStatus.ordinal()));
                pdu.setDeliveredFrameNumber(new IntUnsignedLong(this.deliveredFrameNumber));
                pdu.setProductionStatus(new RcfProductionStatus(this.productionStatus.ordinal()));
                pdu.setSubcarrierLockStatus(new LockStatus(this.subcarrierLockStatus.ordinal()));
                pdu.setSymbolSyncLockStatus(new SymbolLockStatus(this.symbolSyncLockStatus.ordinal()));
            } finally {
                this.statusMutex.unlock();
            }
            boolean resultOk = encodeAndSend(null, pdu, SleOperationNames.STATUS_REPORT_NAME);

            if (resultOk) {
                // Notify PDU
                notifyPduSent(pdu, SleOperationNames.STATUS_REPORT_NAME, getLastPduSent());
                // Generate state and notify update
                notifyStateUpdate();
            }
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
        state.setNumFramesDelivered(deliveredFrameNumber);
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
        stopLatencyTimer();

        this.bufferMutex.lock();
        try {
            this.bufferActive = false;
            this.bufferUnderTransmission = false;
            this.bufferUnderConstruction = null;
            this.bufferChangedCondition.signalAll();
        } finally {
            this.bufferMutex.unlock();
        }
        // Read from configuration, updated via GET_PARAMETER
        this.latencyLimit = getRcfConfiguration().getLatencyLimit();
        this.permittedGvcids = getRcfConfiguration().getPermittedGvcid();
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
        this.deliveredFrameNumber = 0;
        this.statusMutex.lock();
        try {
            this.frameSyncLockStatus = LockStatusEnum.UNKNOWN;
            this.symbolSyncLockStatus = LockStatusEnum.UNKNOWN;
            this.subcarrierLockStatus = LockStatusEnum.UNKNOWN;
            this.carrierLockStatus = LockStatusEnum.UNKNOWN;
            this.productionStatus = ProductionStatusEnum.UNKNOWN;
        } finally {
            this.statusMutex.unlock();
        }
    }

    private RcfServiceInstanceConfiguration getRcfConfiguration() {
        return (RcfServiceInstanceConfiguration) this.serviceInstanceConfiguration;
    }

    @Override
    protected boolean isUserSide() {
        return false;
    }
}
