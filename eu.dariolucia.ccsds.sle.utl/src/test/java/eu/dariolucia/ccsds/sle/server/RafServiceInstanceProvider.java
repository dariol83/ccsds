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

import com.beanit.jasn1.ber.types.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.incoming.pdus.RafGetParameterInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.incoming.pdus.RafStartInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.structures.*;
import eu.dariolucia.ccsds.sle.utl.config.PeerConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.raf.RafServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.pdu.PduFactoryUtil;
import eu.dariolucia.ccsds.sle.utl.pdu.PduStringUtil;
import eu.dariolucia.ccsds.sle.utl.si.*;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafParameterEnum;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafRequestedFrameQualityEnum;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafServiceInstanceState;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * One object of this class represents an RAF Service Instance (provider role).
 */
public class RafServiceInstanceProvider extends ServiceInstance {

    private static final Logger LOG = Logger.getLogger(RafServiceInstanceProvider.class.getName());

    public static final int FRAME_QUALITY_GOOD = 0;
    public static final int FRAME_QUALITY_ERRED = 1;
    public static final int FRAME_QUALITY_UNDETERMINED = 2;

    // Read from configuration, retrieved via GET_PARAMETER
    private Integer latencyLimit; // NULL if offline, otherwise a value
    private List<RafRequestedFrameQualityEnum> permittedFrameQuality;
    private Integer minReportingCycle;
    private int returnTimeoutPeriod;
    private int transferBufferSize;
    private DeliveryModeEnum deliveryMode = null;

    // Updated via START and GET_PARAMETER
    private volatile RafRequestedFrameQualityEnum requestedFrameQuality = null;
    private Integer reportingCycle = null; // NULL if off, otherwise a value
    private volatile Date startTime = null;
    private volatile Date endTime = null;

    // Requested via STATUS_REPORT, updated externally (therefore they are protected via separate lock)
    private final ReentrantLock statusMutex = new ReentrantLock();
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

    // Transfer buffer under construction
    private final ReentrantLock bufferMutex = new ReentrantLock();
    private final Condition bufferChangedCondition = bufferMutex.newCondition();
    private RafTransferBuffer bufferUnderConstruction = null;
    private boolean bufferUnderTransmission = false;
    private boolean bufferActive = false;

    // Latency timer
    private final Timer latencyTimer = new Timer();
    private volatile TimerTask pendingLatencyTimeout = null;

    // Operation extension handlers: they are called to drive the positive/negative response (where supported)
    private volatile Function<RafStartInvocation, Boolean> startOperationHandler;

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
    }

    public void setStartOperationHandler(Function<RafStartInvocation, Boolean> handler) {
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

    public boolean transferData(byte[] spaceDataUnit, int quality, int linkContinuity, Instant earthReceiveTime, boolean isPico, String antennaId, boolean globalAntennaId, byte[] privateAnnotations) {
        this.bufferMutex.lock();
        try {
            // If the state is not correct, say bye
            if (!this.bufferActive) {
                return false;
            }
            // Here we check immediately if the buffer is full: if so, we send it
            checkBuffer(false);
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
            checkBuffer(false);
            return true;
        } finally {
            this.bufferMutex.unlock();
        }
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
            RafTransferBuffer bufferToSend = this.bufferUnderConstruction;
            this.bufferUnderConstruction = new RafTransferBuffer();
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
    private boolean trySendBuffer(RafTransferBuffer bufferToSend) {
        // Here we check the delivery mode
        if (getRafConfiguration().getDeliveryMode() == DeliveryModeEnum.TIMELY_ONLINE) {
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
            dispatchFromProvider(() -> doHandleRafTransferBufferInvocation(bufferToSend));
            // Not discarded
            return false;
        } else {
            return true;
        }
    }

    private void doHandleRafTransferBufferInvocation(RafTransferBuffer bufferToSend) {
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
        RafSyncNotifyInvocation in = new RafSyncNotifyInvocation();
        in.setNotification(new Notification());
        in.getNotification().setProductionStatusChange(new RafProductionStatus(productionStatus.ordinal()));
        finalizeAndAddNotification(in);
    }

    // Under sync on this.bufferMutex
    private void addDataDiscardedNotification() {
        if(!bufferActive || bufferUnderConstruction == null) {
            return;
        }
        RafSyncNotifyInvocation in = new RafSyncNotifyInvocation();
        in.setNotification(new Notification());
        in.getNotification().setExcessiveDataBacklog(new BerNull());
        finalizeAndAddNotification(in);
    }

    // Under sync on this.bufferMutex
    private void addLossFrameSyncNotification(Instant time, LockStatusEnum carrierLockStatus, LockStatusEnum subcarrierLockStatus, LockStatusEnum symbolSyncLockStatus) {
        if(!bufferActive || bufferUnderConstruction == null) {
            return;
        }
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

    // Under sync on this.bufferMutex
    private void addEndOfDataNotification() {
        if(!bufferActive || bufferUnderConstruction == null) {
            return;
        }
        RafSyncNotifyInvocation in = new RafSyncNotifyInvocation();
        in.setNotification(new Notification());
        in.getNotification().setEndOfData(new BerNull());
        finalizeAndAddNotification(in);
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
    private void addTransferData(byte[] spaceDataUnit, int quality, int linkContinuity, Instant earthReceiveTime, boolean isPico, String antennaId, boolean globalAntennaId, byte[] privateAnnotations) {
        if(!bufferActive || bufferUnderConstruction == null) {
            return;
        }
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
        boolean permittedOk = false;
        for (RafRequestedFrameQualityEnum permitted : this.permittedFrameQuality) {
            if (permitted.getCode() == rfq.intValue()) {
                permittedOk = true;
                break;
            }
        }

        if (permittedOk) {
            // Ask the external handler if any
            Function<RafStartInvocation, Boolean> handler = this.startOperationHandler;
            if (handler != null) {
                permittedOk = handler.apply(invocation);
            }
        }

        RafStartReturn pdu = new RafStartReturn();
        pdu.setInvokeId(invocation.getInvokeId());
        pdu.setResult(new RafStartReturn.Result());
        if (permittedOk) {
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
                // Set times
                this.startTime = PduFactoryUtil.toDate(invocation.getStartTime());
                this.endTime = PduFactoryUtil.toDate(invocation.getStopTime());
                // Activate capability to send frames and notifications
                this.bufferMutex.lock();
                try {
                    this.bufferActive = true;
                    this.bufferUnderTransmission = false;
                    this.bufferUnderConstruction = new RafTransferBuffer();
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
                // Set the requested frame quality
                this.requestedFrameQuality = null;
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
                if (this.reportingScheduler != null && this.reportingCycle != null) {
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
                if (this.reportingScheduler != null && this.reportingCycle != null) {
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
                if (this.minReportingCycle != null) {
                    pdu.getResult().getPositiveResult().getParMinReportingCycle().setParameterValue(new IntPosShort(this.minReportingCycle));
                } else {
                    pdu.getResult().getPositiveResult().getParMinReportingCycle().setParameterValue(new IntPosShort(0));
                }
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

    private void sendStatusReport(boolean immediate) {
        if (!immediate && this.reportingScheduler == null) {
            return;
        }
        if (getSleVersion() <= 2) {
            RafStatusReportInvocationV1toV2 pdu = new RafStatusReportInvocationV1toV2();
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
                pdu.setErrorFreeFrameNumber(new IntUnsignedLong(this.errorFreeFrameNumber));
                pdu.setProductionStatus(new RafProductionStatus(this.productionStatus.ordinal()));
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
            RafStatusReportInvocation pdu = new RafStatusReportInvocation();
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
                pdu.setErrorFreeFrameNumber(new IntUnsignedLong(this.errorFreeFrameNumber));
                pdu.setProductionStatus(new RafProductionStatus(this.productionStatus.ordinal()));
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
        state.setDeliveredFrameNumber(deliveredFrameNumber);
        state.setErrorFreeFrameNumber(errorFreeFrameNumber);
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

    private RafServiceInstanceConfiguration getRafConfiguration() {
        return (RafServiceInstanceConfiguration) this.serviceInstanceConfiguration;
    }

    @Override
    protected boolean isUserSide() {
        return false;
    }
}
