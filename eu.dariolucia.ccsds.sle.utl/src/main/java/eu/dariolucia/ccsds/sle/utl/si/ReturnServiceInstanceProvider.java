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

package eu.dariolucia.ccsds.sle.utl.si;

import com.beanit.jasn1.ber.types.BerInteger;
import com.beanit.jasn1.ber.types.BerNull;
import com.beanit.jasn1.ber.types.BerType;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.Credentials;
import eu.dariolucia.ccsds.sle.utl.config.PeerConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.ServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.encdec.CommonEncDec;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One object of this class represents an return Service Instance (provider role).
 */
public abstract class ReturnServiceInstanceProvider<T extends CommonEncDec, K extends BerType, U extends ServiceInstanceConfiguration> extends ServiceInstance {

    private static final Logger LOG = Logger.getLogger(ReturnServiceInstanceProvider.class.getName());

    public static final int FRAME_QUALITY_GOOD = 0;
    public static final int FRAME_QUALITY_ERRED = 1;
    public static final int FRAME_QUALITY_UNDETERMINED = 2;

    // Read from configuration, retrieved via GET_PARAMETER
    protected Integer latencyLimit; // NULL if offline, otherwise a value
    protected Integer minReportingCycle;
    protected int returnTimeoutPeriod;
    protected int transferBufferSize;
    protected DeliveryModeEnum deliveryMode = null;

    // Updated via START and GET_PARAMETER
    protected Integer reportingCycle = null; // NULL if off, otherwise a value
    protected volatile Date startTime = null; // NOSONAR no manipulation expected
    protected volatile Date endTime = null; // NOSONAR no manipulation expected

    // Requested via STATUS_REPORT, updated externally (therefore they are protected via separate lock)
    protected final ReentrantLock statusMutex = new ReentrantLock();
    protected LockStatusEnum frameSyncLockStatus = LockStatusEnum.OUT_OF_LOCK;
    protected LockStatusEnum symbolSyncLockStatus = LockStatusEnum.OUT_OF_LOCK;
    protected LockStatusEnum subcarrierLockStatus = LockStatusEnum.OUT_OF_LOCK;
    protected LockStatusEnum carrierLockStatus = LockStatusEnum.OUT_OF_LOCK;
    protected ProductionStatusEnum productionStatus = ProductionStatusEnum.HALTED;

    // Encoder/decoder
    protected final T encDec;

    // Status report scheduler
    protected final AtomicReference<Timer> reportingScheduler = new AtomicReference<>();

    // Transfer buffer under construction
    protected final ReentrantLock bufferMutex = new ReentrantLock();
    protected final Condition bufferChangedCondition = bufferMutex.newCondition();
    protected K bufferUnderConstruction = null;
    protected boolean bufferUnderTransmission = false;
    protected boolean bufferActive = false;

    // Latency timer
    protected final Timer latencyTimer = new Timer();
    protected final AtomicReference<TimerTask> pendingLatencyTimeout = new AtomicReference<>();

    protected ReturnServiceInstanceProvider(PeerConfiguration apiConfiguration,
                                         U serviceInstanceConfiguration, T encoderDecoder) {
        super(apiConfiguration, serviceInstanceConfiguration);
        this.encDec = encoderDecoder;
        this.deliveryMode = getConfiguredDeliveryMode();
    }

    @Override
    protected void setup() {
        // Register handlers
        registerPduReceptionHandler(SleStopInvocation.class, this::handleRafStopInvocation);
        registerPduReceptionHandler(SleScheduleStatusReportInvocation.class, this::handleRafScheduleStatusReportInvocation);
        doCustomSetup();
    }

    protected abstract void doCustomSetup();

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

        // We lost the frame lock, we need to send a notification
        if (previousFrameLockStatus == LockStatusEnum.IN_LOCK && frame != LockStatusEnum.IN_LOCK &&
                currentBufferActive && this.deliveryMode != DeliveryModeEnum.OFFLINE) {
            sendLossFrameNotification(time, carrier, subCarrier, symbol);
        }
        // We changed production status, we need to send a notification
        if (previousProductionStatus != productionStatus && currentBufferActive && this.deliveryMode != DeliveryModeEnum.OFFLINE) {
            sendProductionStatusChangeNotification(productionStatus);
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

    public boolean transferData(byte[] spaceDataUnit, int quality, int linkContinuity, Instant earthReceiveTime, boolean isPico, String antennaId, boolean globalAntennaId, byte[] privateAnnotations) { // NOSONAR: creation of an additional object is avoided for performance reasons
        this.bufferMutex.lock();
        try {
            // If the state is not correct, say bye
            if (!this.bufferActive) {
                return false;
            }
            // Here we check immediately if the buffer is full: if so, we send it
            checkBuffer(false);

            boolean result = checkAndAddTransferData(spaceDataUnit, quality, linkContinuity, earthReceiveTime,isPico, antennaId, globalAntennaId, privateAnnotations);
            if(result) {
                checkBuffer(false);
            }
            return result;
        } finally {
            this.bufferMutex.unlock();
        }
    }

    // Under sync on this.bufferMutex
    protected abstract boolean checkAndAddTransferData(byte[] spaceDataUnit, int quality, int linkContinuity, Instant earthReceiveTime, boolean isPico, String antennaId, boolean globalAntennaId, byte[] privateAnnotations); // NOSONAR for performance reasons, creation of a single argument object is avoided

    // Under sync on this.bufferMutex
    private void checkBuffer(boolean forceSend) {
        if(!bufferActive) {
            return;
        }
        if (isCurrentBufferEmpty(this.bufferUnderConstruction)) {
            // No data, nothing to do
            return;
        }
        if (getCurrentBufferItems(this.bufferUnderConstruction) == this.transferBufferSize || forceSend) {
            // Stop the latency timer
            stopLatencyTimer();
            // Try to send the buffer: replace the buffer with a new one
            K bufferToSend = this.bufferUnderConstruction;
            this.bufferUnderConstruction = createCurrentBuffer();
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

    protected abstract K createCurrentBuffer();

    protected abstract int getCurrentBufferItems(K bufferUnderConstruction);

    protected abstract boolean isCurrentBufferEmpty(K bufferUnderConstruction);

    protected void doHandleTransferBufferInvocation(K bufferToSend) {
        clearError();

        try {
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
            } else {
                LOG.log(Level.SEVERE, String.format("%s: Transfer buffer sending problem", getServiceInstanceIdentifier()));
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, String.format("%s: Exception detected when handling transfer buffer invocation", getServiceInstanceIdentifier()), e);
        }
    }

    protected void startLatencyTimer() {
        if (this.latencyLimit == null) {
            return; // No timer
        }

        stopLatencyTimer();

        this.pendingLatencyTimeout.set(new TimerTask() {
            @Override
            public void run() {
                if (pendingLatencyTimeout.get() == this) {
                    // Elapsed, send the buffer if needed
                    latencyElapsed();
                }
            }
        });
        this.latencyTimer.schedule(this.pendingLatencyTimeout.get(), this.latencyLimit * 1000L);
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

    protected void stopLatencyTimer() {
        if (this.pendingLatencyTimeout.get() != null) {
            this.pendingLatencyTimeout.get().cancel();
            this.pendingLatencyTimeout.set(null);
        }
    }

    // Under sync on this.bufferMutex
    private boolean trySendBuffer(K bufferToSend) {
        if(LOG.isLoggable(Level.FINER)) {
            LOG.finer(String.format("%s: Transfer buffer about to be sent", getServiceInstanceIdentifier()));
        }
        // Here we check the delivery mode
        if (getConfiguredDeliveryMode() == DeliveryModeEnum.TIMELY_ONLINE) {
            // Timely mode: if there is a buffer in transmission, you have to discard the buffer
            if (this.bufferUnderTransmission) {
                if(LOG.isLoggable(Level.FINER)) {
                    LOG.finer(String.format("%s: Transfer buffer pending transmission in online timely delivery mode, new transfer buffer discarded", getServiceInstanceIdentifier()));
                }
                return true;
            }
        } else {
            // Complete mode: wait
            while (this.bufferActive && this.bufferUnderTransmission) {
                if(LOG.isLoggable(Level.FINER)) {
                    LOG.finer(String.format("%s: Transfer buffer pending transmission in online complete delivery mode, waiting for transmission", getServiceInstanceIdentifier()));
                }
                try {
                    this.bufferChangedCondition.await();
                } catch (InterruptedException e) { // NOSONAR: sorry to say, but this rule is pointless, to be disabled in the profile
                    Thread.interrupted();
                    if(LOG.isLoggable(Level.FINER)) {
                        LOG.finer(String.format("%s: Interrupted exception in online complete delivery mode received, new transfer buffer discarded", getServiceInstanceIdentifier()));
                    }
                    // Buffer discarded
                    return true;
                }
            }
        }
        if (bufferActive) {
            // Set transmission flag
            this.bufferUnderTransmission = true;
            //
            if(LOG.isLoggable(Level.FINER)) {
                LOG.finer(String.format("%s: Sending transfer buffer to the owner thread for dispatching to proxy", getServiceInstanceIdentifier()));
            }
            // Send the buffer
            dispatchFromProvider(() -> doHandleTransferBufferInvocation(bufferToSend));
            // Not discarded
            return false;
        } else {
            return true;
        }
    }

    protected void clearBufferTransmissionFlag() {
        this.bufferMutex.lock();
        this.bufferUnderTransmission = false;
        this.bufferChangedCondition.signalAll();
        this.bufferMutex.unlock();
    }

    protected abstract DeliveryModeEnum getConfiguredDeliveryMode();

    // Under sync on this.bufferMutex
    protected abstract void addProductionStatusChangeNotification(ProductionStatusEnum productionStatus);

    // Under sync on this.bufferMutex
    protected abstract void addDataDiscardedNotification();

    // Under sync on this.bufferMutex
    protected abstract void addLossFrameSyncNotification(Instant time, LockStatusEnum carrierLockStatus, LockStatusEnum subcarrierLockStatus, LockStatusEnum symbolSyncLockStatus);

    // Under sync on this.bufferMutex
    protected abstract void addEndOfDataNotification();

    // To be called in the start handling method
    protected void initialiseTransferBufferActivation(Date startTime, Date stopTime) {
        // Set times
        this.startTime = startTime;
        this.endTime = stopTime;
        // Activate capability to send frames and notifications
        this.bufferMutex.lock();
        try {
            this.bufferActive = true;
            this.bufferUnderTransmission = false;
            this.bufferUnderConstruction = createCurrentBuffer();
            this.bufferChangedCondition.signalAll();
        } finally {
            this.bufferMutex.unlock();
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
                resetStartArgumentsOnStop();
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

    protected abstract void resetStartArgumentsOnStop();

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
            if (this.reportingScheduler.get() != null) {
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
        if(LOG.isLoggable(Level.INFO)) {
            LOG.info(String.format("%s: Scheduling status report with period %d", getServiceInstanceIdentifier(), period));
        }
        this.reportingCycle = period;
        this.reportingScheduler.set(new Timer());
        this.reportingScheduler.get().schedule(new TimerTask() {
            @Override
            public void run() {
                if (reportingScheduler.get() != null) {
                    dispatchFromProvider(() -> sendStatusReport(false));
                }
            }
        }, 0, period * 1000L);
    }

    private void stopStatusReport() {
        if(LOG.isLoggable(Level.FINER)) {
            LOG.finer(String.format("%s: Stopping status report", getServiceInstanceIdentifier()));
        }
        this.reportingCycle = null;
        Timer scheduleTimer = reportingScheduler.getAndSet(null);
        if (scheduleTimer != null) {
            scheduleTimer.cancel();
        }
    }

    private void sendStatusReport(boolean immediate) {
        if (!immediate && this.reportingScheduler.get() == null) {
            return;
        }
        BerType pdu = buildStatusReportPdu();
        if(pdu == null) {
            return;
        }

        boolean resultOk = encodeAndSend(null, pdu, SleOperationNames.STATUS_REPORT_NAME);

        if (resultOk) {
            // Notify PDU
            notifyPduSent(pdu, SleOperationNames.STATUS_REPORT_NAME, getLastPduSent());
            // Generate state and notify update
            notifyStateUpdate();
        }
    }

    protected abstract BerType buildStatusReportPdu();

    // buildCurrentState is type-dependant

    @Override
    protected Object decodePdu(byte[] pdu) throws IOException {
        return this.encDec.decode(pdu);
    }

    @Override
    protected byte[] encodePdu(BerType pdu) throws IOException {
        return this.encDec.encode(pdu);
    }

    @Override
    protected void updateHandlersForVersion(int version) {
        this.encDec.useSleVersion(version);
    }

    @Override
    protected void resetState() {
        stopStatusReport();
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
        doResetState();

        // Updated via START and GET_PARAMETER
        this.startTime = null;
        this.endTime = null;
        this.reportingCycle = null; // NULL if off, otherwise a value

        // Status parameters are not updated here
    }

    protected abstract void doResetState();

    @Override
    protected boolean isUserSide() {
        return false;
    }
}
