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

import com.beanit.jasn1.ber.types.BerInteger;
import com.beanit.jasn1.ber.types.BerNull;
import com.beanit.jasn1.ber.types.BerType;
import com.beanit.jasn1.ber.types.string.BerVisibleString;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.incoming.pdus.CltuGetParameterInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.incoming.pdus.CltuStartInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.incoming.pdus.CltuThrowEventInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.incoming.pdus.CltuTransferDataInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.outgoing.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.*;
import eu.dariolucia.ccsds.sle.utl.config.PeerConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.cltu.CltuServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.encdec.CltuProviderEncDec;
import eu.dariolucia.ccsds.sle.utl.pdu.PduFactoryUtil;
import eu.dariolucia.ccsds.sle.utl.si.*;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuLastOk;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuLastProcessed;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuNotification;
import eu.dariolucia.ccsds.sle.utl.si.cltu.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One object of this class represents a CLTU Service Instance (provider role).
 */
public class CltuServiceInstanceProvider extends ServiceInstance {

    private static final Logger LOG = Logger.getLogger(CltuServiceInstanceProvider.class.getName());

    // Read from configuration, retrieved via GET_PARAMETER
    private Integer maxCltuLength;
    private Integer minCltuDelay;
    private boolean bitlockRequired;
    private boolean rfAvailableRequired;
    private Integer minReportingCycle;
    private int returnTimeoutPeriod;

    //
    private Integer acquisitionSequenceLength;
    private GVCID clcwGlobalVcId; // can be null -> not configured
    private String clcwPhysicalChannel; // can be null -> not configured
    private Long cltuIdentification;
    private Long eventInvocationIdentification = 0L;
    private Long modulationFrequency;
    private Integer modulationIndex;
    private CltuNotificationModeEnum notificationMode;
    private Integer plop1IdleSequenceLength;
    private CltuPlopInEffectEnum plopInEffect;
    private CltuProtocolAbortModeEnum protocolAbortMode;
    private Integer subcarrierToBitRateRatio;

    // Updated via START and GET_PARAMETER
    private Long firstCltuIdentification;
    private Integer reportingCycle = null; // NULL if off, otherwise a value

    // Requested via STATUS_REPORT, updated externally (therefore they are protected via separate lock)
    private CltuLastProcessed lastProcessed; // also from async notify
    private CltuLastOk lastOk; // also from async notify
    private CltuProductionStatusEnum productionStatus = CltuProductionStatusEnum.INTERRUPTED; // also from async notify
    private CltuUplinkStatusEnum uplinkStatus = CltuUplinkStatusEnum.UPLINK_STATUS_NOT_AVAILABLE; // also from async notify
    private Long nbCltuReceived = 0L;
    private Long nbCltuProcessed = 0L;
    private Long nbCltuRadiated = 0L;
    private Long bufferAvailable = 0L;

    private Date productionStatusOperationalTime = null;

    // Encoder/decoder
    private final CltuProviderEncDec encDec = new CltuProviderEncDec();

    // Status report scheduler
    private final AtomicReference<Timer> reportingScheduler = new AtomicReference<>();

    // Radiation notification request set
    private final Set<Long> radiationNotificationSet = new HashSet<>();

    // Operation extension handlers: they are called to drive the positive/negative response (where supported).
    // If it is not set, it means that there are no additional checks to be done.
    private final AtomicReference<Predicate<CltuStartInvocation>> startOperationHandler = new AtomicReference<>();
    // Return the remaining buffer if the CLTU is added to the buffer for processing, or a negative number if the CLTU must be discarded and was not added to the buffer.
    // The absolute value of the negative number is the specific diagnostic code to be sent back to the user, so make sure it is in line with the CCSDS specs.
    private final AtomicReference<Function<CltuTransferDataInvocation, Long>> transferDataOperationHandler = new AtomicReference<>();
    // Return null if the event invocation has been taken onboard, or a positive number if the throw event must be discarded and it will not be processed.
    // The value of the returned number is the specific diagnostic code to be sent back to the user, so make sure it is in line with the CCSDS specs.
    private final AtomicReference<Function<CltuThrowEventInvocation, Long>> throwEventOperationHandler = new AtomicReference<>();

    public CltuServiceInstanceProvider(PeerConfiguration apiConfiguration,
                                       CltuServiceInstanceConfiguration serviceInstanceConfiguration) {
        super(apiConfiguration, serviceInstanceConfiguration);
    }

    @Override
    protected void setup() {
        // Register handlers
        registerPduReceptionHandler(CltuStartInvocation.class, this::handleCltuStartInvocation);
        registerPduReceptionHandler(SleStopInvocation.class, this::handleCltuStopInvocation);
        registerPduReceptionHandler(SleScheduleStatusReportInvocation.class, this::handleCltuScheduleStatusReportInvocation);
        registerPduReceptionHandler(CltuGetParameterInvocation.class, this::handleCltuGetParameterInvocation);
        registerPduReceptionHandler(CltuTransferDataInvocation.class, this::handleCltuTransferDataInvocation);
        registerPduReceptionHandler(CltuThrowEventInvocation.class, this::handleCltuThrowEventInvocation);
    }

    public void setStartOperationHandler(Predicate<CltuStartInvocation> handler) {
        this.startOperationHandler.set(handler);
    }

    public void setTransferDataOperationHandler(Function<CltuTransferDataInvocation, Long> transferDataOperationHandler) {
        this.transferDataOperationHandler.set(transferDataOperationHandler);
    }

    public void setThrowEventOperationHandler(Function<CltuThrowEventInvocation, Long> throwEventOperationHandler) {
        this.throwEventOperationHandler.set(throwEventOperationHandler);
    }

    public void updateProductionStatus(CltuProductionStatusEnum productionStatus, CltuUplinkStatusEnum uplinkStatus, long bufferAvailable) {
        dispatchFromProvider(() -> doUpdateProductionStatus(productionStatus, uplinkStatus, bufferAvailable));
    }

    public void bufferEmpty(long bufferAvailable) {
        dispatchFromProvider(() -> doBufferEmpty(bufferAvailable));
    }

    public void cltuProgress(long cltuId, CltuStatusEnum status, Date radiationStartTime, Date radiationStopTime, long bufferAvailable) {
        dispatchFromProvider(() -> doCltuProgress(cltuId, status, radiationStartTime, radiationStopTime, bufferAvailable));
    }

    public void eventProgress(long eventId, boolean checksFailed, boolean completed) {
        dispatchFromProvider(() -> doEventProgress(eventId, checksFailed, completed));
    }

    private void doEventProgress(long eventId, boolean checksFailed, boolean completed) {
        if(checksFailed) {
            doSendAsyncNotify(CltuNotification.CltuNotificationTypeEnum.EVENT_CONDITION_EV_FALSE, eventId);
        } else if(completed) {
            doSendAsyncNotify(CltuNotification.CltuNotificationTypeEnum.ACTION_LIST_COMPLETED, eventId);
        } else {
            doSendAsyncNotify(CltuNotification.CltuNotificationTypeEnum.ACTION_LIST_NOT_COMPLETED, eventId);
        }
    }

    private void doCltuProgress(long cltuId, CltuStatusEnum status, Date radiationStartTime, Date radiationStopTime, long bufferAvailable) {
        this.bufferAvailable = bufferAvailable;
        // Save the progress
        this.lastProcessed = new CltuLastProcessed(cltuId, radiationStartTime, status);
        if(status == CltuStatusEnum.PRODUCTION_STARTED) {
            ++this.nbCltuProcessed;
        }
        // If radiated, good, update lastOk
        if(status == CltuStatusEnum.RADIATED) {
            this.lastOk = new CltuLastOk(cltuId, radiationStopTime);
            ++this.nbCltuRadiated;
        }
        // If radiation notification was request for this id, then send a notification
        if(status == CltuStatusEnum.RADIATED && radiationNotificationSet.remove(cltuId)) {
            doSendAsyncNotify(CltuNotification.CltuNotificationTypeEnum.CLTU_RADIATED, null);
        }
        // If this CLTU is expired, then send a notification
        if(status == CltuStatusEnum.EXPIRED) {
            doSendAsyncNotify(CltuNotification.CltuNotificationTypeEnum.SLDU_EXPIRED, null);
        }
    }

    private void doBufferEmpty(long bufferAvailable) {
        this.bufferAvailable = bufferAvailable;
        doSendAsyncNotify(CltuNotification.CltuNotificationTypeEnum.BUFFER_EMPTY, null);
    }

    private void doUpdateProductionStatus(CltuProductionStatusEnum productionStatus, CltuUplinkStatusEnum uplinkStatus, long bufferAvailable) {
        CltuProductionStatusEnum previousProductionStatus = this.productionStatus;
        this.productionStatus = productionStatus;
        this.uplinkStatus = uplinkStatus;
        this.bufferAvailable = bufferAvailable;
        if((getCurrentBindingState() == ServiceInstanceBindingStateEnum.ACTIVE || getCurrentBindingState() == ServiceInstanceBindingStateEnum.READY)
                && previousProductionStatus != this.productionStatus) {
            switch (this.productionStatus) {
                case HALTED:
                    doSendAsyncNotify(CltuNotification.CltuNotificationTypeEnum.PRODUCTION_HALTED, null);
                    break;
                case INTERRUPTED:
                    doSendAsyncNotify(CltuNotification.CltuNotificationTypeEnum.PRODUCTION_INTERRUPTED, null);
                    break;
                case OPERATIONAL:
                    this.productionStatusOperationalTime = new Date();
                    doSendAsyncNotify(CltuNotification.CltuNotificationTypeEnum.PRODUCTION_OPERATIONAL, null);
                    break;
                default:
                    // Do not do anything
                    break;
            }
        }
    }

    private void doSendAsyncNotify(CltuNotification.CltuNotificationTypeEnum notificationType, Long eventId) {
        clearError();

        if(LOG.isLoggable(Level.INFO)) {
            LOG.info(String.format("%s: Stopping status report", getServiceInstanceIdentifier()));
        }
        // Validate state
        if (this.currentState != ServiceInstanceBindingStateEnum.ACTIVE && this.currentState != ServiceInstanceBindingStateEnum.READY) {
            setError("Async. notify discarded, service instance is in state "
                    + this.currentState);
            notifyStateUpdate();
            return;
        }

        // Create the object
        CltuAsyncNotifyInvocation pdu = new CltuAsyncNotifyInvocation();

        // Add credentials
        // From the API configuration (remote peers) and SI configuration (responder
        // id), check remote peer and check if authentication must be used.
        Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL);
        if (creds == null) {
            // Error while generating credentials, set by generateCredentials()
            notifyPduSentError(pdu, SleOperationNames.NOTIFY_NAME, null);
            notifyStateUpdate();
            return;
        } else {
            pdu.setInvokerCredentials(creds);
        }

        // Last OK - Mandatory
        pdu.setCltuLastOk(new eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.CltuLastOk());
        if(this.lastOk != null) {
            pdu.getCltuLastOk().setCltuOk(new eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.CltuLastOk.CltuOk());
            pdu.getCltuLastOk().getCltuOk().setCltuIdentification(new CltuIdentification(this.lastOk.getCltuIdentification()));
            pdu.getCltuLastOk().getCltuOk().setStopRadiationTime(new Time());
            pdu.getCltuLastOk().getCltuOk().getStopRadiationTime().setCcsdsFormat(new TimeCCSDS(PduFactoryUtil.buildCDSTime(this.lastOk.getRadiationStopTime().getTime(), 0)));
        } else {
            pdu.getCltuLastOk().setNoCltuOk(new BerNull());
        }

        // Last Processed - Mandatory
        pdu.setCltuLastProcessed(new eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.CltuLastProcessed());
        if(this.lastProcessed != null) {
            pdu.getCltuLastProcessed().setCltuProcessed(new eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.CltuLastProcessed.CltuProcessed());
            pdu.getCltuLastProcessed().getCltuProcessed().setCltuIdentification(new CltuIdentification(this.lastProcessed.getCltuIdentification()));
            pdu.getCltuLastProcessed().getCltuProcessed().setCltuStatus(new CltuStatus(this.lastProcessed.getCltuStatus().ordinal()));
            pdu.getCltuLastProcessed().getCltuProcessed().setStartRadiationTime(new ConditionalTime());
            if(this.lastProcessed.getRadiationStartTime() == null) {
                pdu.getCltuLastProcessed().getCltuProcessed().getStartRadiationTime().setUndefined(new BerNull());
            } else {
                pdu.getCltuLastProcessed().getCltuProcessed().getStartRadiationTime().setKnown(new Time());
                pdu.getCltuLastProcessed().getCltuProcessed().getStartRadiationTime().getKnown().setCcsdsFormat(new TimeCCSDS(PduFactoryUtil.buildCDSTime(this.lastProcessed.getRadiationStartTime().getTime(), 0)));
            }
        } else {
            pdu.getCltuLastProcessed().setNoCltuProcessed(new BerNull());
        }

        // Production and uplink status - Mandatory
        pdu.setProductionStatus(new ProductionStatus(this.productionStatus.ordinal()));
        pdu.setUplinkStatus(new UplinkStatus(this.uplinkStatus.ordinal()));

        // Notification - Mandatory
        pdu.setCltuNotification(new eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.CltuNotification());
        switch(notificationType) {
            case ACTION_LIST_COMPLETED:
                pdu.getCltuNotification().setActionListCompleted(new EventInvocationId(eventId));
                break;
            case ACTION_LIST_NOT_COMPLETED:
                pdu.getCltuNotification().setActionListNotCompleted(new EventInvocationId(eventId));
                break;
            case EVENT_CONDITION_EV_FALSE:
                pdu.getCltuNotification().setEventConditionEvFalse(new EventInvocationId(eventId));
                break;
            case BUFFER_EMPTY:
                pdu.getCltuNotification().setBufferEmpty(new BerNull());
                break;
            case CLTU_RADIATED:
                pdu.getCltuNotification().setCltuRadiated(new BerNull());
                break;
            case SLDU_EXPIRED:
                pdu.getCltuNotification().setSlduExpired(new BerNull());
                break;
            case PRODUCTION_HALTED:
                pdu.getCltuNotification().setProductionHalted(new BerNull());
                break;
            case PRODUCTION_INTERRUPTED:
                pdu.getCltuNotification().setProductionInterrupted(new BerNull());
                break;
            case PRODUCTION_OPERATIONAL:
                pdu.getCltuNotification().setProductionOperational(new BerNull());
                break;
            default:
                throw new IllegalArgumentException("Type " + notificationType + " not supported");
        }

        boolean resultOk = encodeAndSend(null, pdu, SleOperationNames.NOTIFY_NAME);

        if (resultOk) {
            // Notify PDU
            notifyPduSent(pdu, SleOperationNames.NOTIFY_NAME, getLastPduSent());
            // Generate state and notify update
            notifyStateUpdate();
        }
    }

    private void handleCltuTransferDataInvocation(CltuTransferDataInvocation invocation) {
        clearError();

        // Validate state
        if (this.currentState != ServiceInstanceBindingStateEnum.ACTIVE) {
            setError("Transfer data received from user, but service instance is in state "
                    + this.currentState);
            notifyStateUpdate();
            peerAbort(PeerAbortReasonEnum.PROTOCOL_ERROR);
            return;
        }

        // Process the TRANSFER-DATA

        // Validate credentials
        // From the API configuration (remote peers) and SI configuration (remote peer),
        // check remote peer and check if authentication must be used.
        // If so, verify credentials.
        if (!authenticate(invocation.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
            disconnect("Transfer data invocation received, but wrong credentials");
            notifyPduReceived(invocation, SleOperationNames.TRANSFER_DATA_NAME, getLastPduReceived());
            notifyStateUpdate();
            return;
        }

        // Validate the request
        boolean permittedOk = true;
        // Expected CLTU identification
        if(invocation.getCltuIdentification().intValue() != this.cltuIdentification) {
            permittedOk = false;
        }
        // Latest transmission time
        if(invocation.getLatestTransmissionTime().getKnown() != null) {
            Date latestTransmissionTime = PduFactoryUtil.toDate(invocation.getLatestTransmissionTime());
            if(latestTransmissionTime.getTime() < new Date().getTime()) {
                permittedOk = false;
            }
        }

        Long newBufferAvailable = null;
        if (permittedOk) {
            // Ask the external handler if any
            Function<CltuTransferDataInvocation, Long> handler = this.transferDataOperationHandler.get(); // NOSONAR: null is a plausible value
            if (handler != null) {
                newBufferAvailable = handler.apply(invocation);
                permittedOk = newBufferAvailable != null && newBufferAvailable > 0;
            } else {
                // If there is no handler for a transfer data, the CLTU cannot be radiated, so fail by default
                permittedOk = false;
            }
        }

        CltuTransferDataReturn pdu = new CltuTransferDataReturn();
        pdu.setInvokeId(invocation.getInvokeId());
        pdu.setResult(new CltuTransferDataReturn.Result());
        if (permittedOk) {
            pdu.getResult().setPositiveResult(new BerNull());
            this.bufferAvailable = newBufferAvailable;
            ++this.cltuIdentification;
            ++this.nbCltuReceived;
        } else {
            pdu.getResult().setNegativeResult(new DiagnosticCltuTransferData());
            // If you reach this point, it means that either there was a previous fail, so newBufferAvailable is null, or that the provider returned a negative or zero number
            if(newBufferAvailable != null) {
                // Code from the provider implementation
                pdu.getResult().getNegativeResult().setSpecific(new BerInteger(Math.abs(newBufferAvailable)));
            } else {
                // Unable to process: not really according to the standard but good enough for testing
                pdu.getResult().getNegativeResult().setSpecific(new BerInteger(0));
            }
        }
        pdu.setCltuBufferAvailable(new BufferSize(this.bufferAvailable != null ? this.bufferAvailable : 0));
        pdu.setCltuIdentification(new CltuIdentification(this.cltuIdentification));
        // Add credentials
        // From the API configuration (remote peers) and SI configuration (responder
        // id), check remote peer and check if authentication must be used.
        Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL);
        if (creds == null) {
            // Error while generating credentials, set by generateCredentials()
            notifyPduSentError(pdu, SleOperationNames.TRANSFER_DATA_RETURN_NAME, null);
            notifyStateUpdate();
            return;
        } else {
            pdu.setPerformerCredentials(creds);
        }

        boolean resultOk = encodeAndSend(null, pdu, SleOperationNames.TRANSFER_DATA_RETURN_NAME);

        if (resultOk) {
            // Check if radiation notification is needed
            if(invocation.getSlduRadiationNotification().intValue() == 0) { // 0: produce notification, 1: do not produce notification
                this.radiationNotificationSet.add(invocation.getCltuIdentification().longValue());
            }
            // Notify PDU
            notifyPduSent(pdu, SleOperationNames.TRANSFER_DATA_RETURN_NAME, getLastPduSent());
            // Generate state and notify update
            notifyStateUpdate();
        }
    }

    private void handleCltuThrowEventInvocation(CltuThrowEventInvocation invocation) {
        clearError();

        // Validate state
        if (this.currentState != ServiceInstanceBindingStateEnum.ACTIVE && this.currentState != ServiceInstanceBindingStateEnum.READY) {
            setError("Throw event received from user, but service instance is in state "
                    + this.currentState);
            notifyStateUpdate();
            peerAbort(PeerAbortReasonEnum.PROTOCOL_ERROR);
            return;
        }

        // Process the THROW-EVENT

        // Validate credentials
        // From the API configuration (remote peers) and SI configuration (remote peer),
        // check remote peer and check if authentication must be used.
        // If so, verify credentials.
        if (!authenticate(invocation.getInvokerCredentials(), AuthenticationModeEnum.ALL)) {
            disconnect("Transfer data invocation received, but wrong credentials");
            notifyPduReceived(invocation, SleOperationNames.THROW_EVENT_NAME, getLastPduReceived());
            notifyStateUpdate();
            return;
        }

        // Validate the request
        boolean permittedOk = true;
        // Expected event identification
        if(invocation.getEventInvocationIdentification().intValue() != this.eventInvocationIdentification) {
            permittedOk = false;
        }

        Long returnCode = null;
        if (permittedOk) {
            // Ask the external handler if any
            Function<CltuThrowEventInvocation, Long> handler = this.throwEventOperationHandler.get(); // NOSONAR: null is a plausible value
            if (handler != null) {
                returnCode = handler.apply(invocation);
                permittedOk = returnCode == null;
            } else {
                // If there is no handler for a throw event, the throw event cannot be performed, so fail by default
                permittedOk = false;
            }
        }

        CltuThrowEventReturn pdu = new CltuThrowEventReturn();
        pdu.setInvokeId(invocation.getInvokeId());
        pdu.setResult(new CltuThrowEventReturn.Result());
        if (permittedOk) {
            pdu.getResult().setPositiveResult(new BerNull());
            ++this.eventInvocationIdentification;
        } else {
            pdu.getResult().setNegativeResult(new DiagnosticCltuThrowEvent());
            // If you reach this point, it means that either there was a previous fail, or that the provider returned a negative or zero number
            if(returnCode != null) {
                // Code from the provider implementation
                pdu.getResult().getNegativeResult().setSpecific(new BerInteger(Math.abs(returnCode)));
            } else {
                // Other reason: not really according to the standard but good enough for testing
                pdu.getResult().getNegativeResult().setCommon(new Diagnostics(127));
            }
        }
        pdu.setEventInvocationIdentification(new EventInvocationId(this.eventInvocationIdentification));
        // Add credentials
        // From the API configuration (remote peers) and SI configuration (responder
        // id), check remote peer and check if authentication must be used.
        Credentials creds = generateCredentials(getInitiatorIdentifier(), AuthenticationModeEnum.ALL);
        if (creds == null) {
            // Error while generating credentials, set by generateCredentials()
            notifyPduSentError(pdu, SleOperationNames.THROW_EVENT_RETURN_NAME, null);
            notifyStateUpdate();
            return;
        } else {
            pdu.setPerformerCredentials(creds);
        }

        boolean resultOk = encodeAndSend(null, pdu, SleOperationNames.THROW_EVENT_RETURN_NAME);

        if (resultOk) {
            // Notify PDU
            notifyPduSent(pdu, SleOperationNames.THROW_EVENT_RETURN_NAME, getLastPduSent());
            // Generate state and notify update
            notifyStateUpdate();
        }
    }

    private void handleCltuStartInvocation(CltuStartInvocation invocation) {
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

        // Validate the current production status
        boolean permittedOk = true;
        if(this.productionStatus == CltuProductionStatusEnum.HALTED || this.productionStatus == CltuProductionStatusEnum.INTERRUPTED) {
            permittedOk = false;
        }

        if (permittedOk) {
            // Ask the external handler if any
            Predicate<CltuStartInvocation> handler = this.startOperationHandler.get();
            if (handler != null) {
                permittedOk = handler.test(invocation);
            }
        }

        CltuStartReturn pdu = new CltuStartReturn();
        pdu.setInvokeId(invocation.getInvokeId());
        pdu.setResult(new CltuStartReturn.Result());
        if (permittedOk) {
            pdu.getResult().setPositiveResult(new CltuStartReturn.Result.PositiveResult());
            pdu.getResult().getPositiveResult().setStopRadiationTime(new ConditionalTime());
            pdu.getResult().getPositiveResult().getStopRadiationTime().setUndefined(new BerNull());
            pdu.getResult().getPositiveResult().setStartRadiationTime(new Time());
            if(this.productionStatusOperationalTime == null) {
                this.productionStatusOperationalTime = new Date();
            }
            pdu.getResult().getPositiveResult().getStartRadiationTime().setCcsdsFormat(new TimeCCSDS(PduFactoryUtil.buildCDSTime(this.productionStatusOperationalTime.getTime(), 0)));
        } else {
            pdu.getResult().setNegativeResult(new DiagnosticCltuStart());
            pdu.getResult().getNegativeResult().setSpecific(new BerInteger(1)); // Unable to comply: not really according to the standard, but good enough for testing
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
                // Set the first CLTU ID
                this.firstCltuIdentification = invocation.getFirstCltuIdentification().longValue();
                // Set the CLTU identification to wait for
                this.cltuIdentification = this.firstCltuIdentification;
                // Transition to new state: ACTIVE and notify PDU sent
                setServiceInstanceState(ServiceInstanceBindingStateEnum.ACTIVE);
            }
            // Notify PDU
            notifyPduSent(pdu, SleOperationNames.START_RETURN_NAME, getLastPduSent());
            // Generate state and notify update
            notifyStateUpdate();
        }
    }

    private void handleCltuStopInvocation(SleStopInvocation invocation) {
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

        dispatchFromProvider(() -> {
            boolean resultOk = encodeAndSend(null, pdu, SleOperationNames.STOP_RETURN_NAME);

            if (resultOk) {
                // If all fine, transition to new state: READY and notify PDU sent
                setServiceInstanceState(ServiceInstanceBindingStateEnum.READY);
                // Unset the first cltu identification
                this.firstCltuIdentification = null;
                // Notify PDU
                notifyPduSent(pdu, SleOperationNames.STOP_RETURN_NAME, getLastPduSent());
                // Generate state and notify update
                notifyStateUpdate();
            }
        });
    }

    private void handleCltuScheduleStatusReportInvocation(SleScheduleStatusReportInvocation invocation) {
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
        if(LOG.isLoggable(Level.INFO)) {
            LOG.info(String.format("%s: Stopping status report", getServiceInstanceIdentifier()));
        }
        this.reportingCycle = null;
        this.reportingScheduler.get().cancel();
        this.reportingScheduler.set(null);
    }

    private void handleCltuGetParameterInvocation(CltuGetParameterInvocation invocation) {
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
        if (getSleVersion() <= 3) {
            CltuGetParameterReturnV1toV3 pdu = new CltuGetParameterReturnV1toV3();
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
            pdu.setResult(new CltuGetParameterReturnV1toV3.Result());
            pdu.getResult().setPositiveResult(new CltuGetParameterV1toV3());
            if (invocation.getCltuParameter().intValue() == CltuParameterEnum.BIT_LOCK_REQUIRED.getCode()) {
                pdu.getResult().getPositiveResult().setParBitLockRequired(new CltuGetParameterV1toV3.ParBitLockRequired());
                pdu.getResult().getPositiveResult().getParBitLockRequired().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParBitLockRequired().setParameterValue(new IntPosShort(this.bitlockRequired ? 0 : 1)); // yes = 0, no = 1
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.RF_AVAILABLE_REQUIRED.getCode()) {
                pdu.getResult().getPositiveResult().setParRfAvailableRequired(new CltuGetParameterV1toV3.ParRfAvailableRequired());
                pdu.getResult().getPositiveResult().getParRfAvailableRequired().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParRfAvailableRequired().setParameterValue(new IntPosShort(this.rfAvailableRequired ? 0 : 1)); // yes = 0, no = 1
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.DELIVERY_MODE.getCode()) {
                pdu.getResult().getPositiveResult().setParDeliveryMode(new CltuGetParameterV1toV3.ParDeliveryMode());
                pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterValue(new CltuDeliveryMode(DeliveryModeEnum.FWD_ONLINE.ordinal())); // fixed to 3
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.EXPECTED_SLDU_IDENTIFICATION.getCode()) {
                pdu.getResult().getPositiveResult().setParCltuIdentification(new CltuGetParameterV1toV3.ParCltuIdentification());
                pdu.getResult().getPositiveResult().getParCltuIdentification().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParCltuIdentification().setParameterValue(new CltuIdentification(this.cltuIdentification == null ? 0 : this.cltuIdentification)); // if not specified, 0
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.EXPECTED_EVENT_INVOCATION_IDENTIFICATION.getCode()) {
                pdu.getResult().getPositiveResult().setParEventInvocationIdentification(new CltuGetParameterV1toV3.ParEventInvocationIdentification());
                pdu.getResult().getPositiveResult().getParEventInvocationIdentification().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParEventInvocationIdentification().setParameterValue(new EventInvocationId(this.eventInvocationIdentification == null ? 0 : this.eventInvocationIdentification)); // if not specified, 0
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.MAXIMUM_SLDU_LENGTH.getCode()) {
                pdu.getResult().getPositiveResult().setParMaximumCltuLength(new CltuGetParameterV1toV3.ParMaximumCltuLength());
                pdu.getResult().getPositiveResult().getParMaximumCltuLength().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParMaximumCltuLength().setParameterValue(new BerInteger(this.maxCltuLength == null ? 0 : this.maxCltuLength)); // Even if null-check protected, this is a mandatory service parameter
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.MODULATION_FREQUENCY.getCode()) {
                pdu.getResult().getPositiveResult().setParModulationFrequency(new CltuGetParameterV1toV3.ParModulationFrequency());
                pdu.getResult().getPositiveResult().getParModulationFrequency().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParModulationFrequency().setParameterValue(new ModulationFrequency(this.modulationFrequency == null ? 0 : this.modulationFrequency)); // Even if null-check protected, this is a mandatory service parameter
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.MODULATION_INDEX.getCode()) {
                pdu.getResult().getPositiveResult().setParModulationIndex(new CltuGetParameterV1toV3.ParModulationIndex());
                pdu.getResult().getPositiveResult().getParModulationIndex().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParModulationIndex().setParameterValue(new ModulationIndex(this.modulationIndex == null ? 0 : this.modulationIndex)); // Even if null-check protected, this is a mandatory service parameter
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.PLOP_IN_EFFECT.getCode()) {
                pdu.getResult().getPositiveResult().setParPlopInEffect(new CltuGetParameterV1toV3.ParPlopInEffect());
                pdu.getResult().getPositiveResult().getParPlopInEffect().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParPlopInEffect().setParameterValue(new BerInteger(this.plopInEffect == null ? 0 : this.plopInEffect.ordinal())); // Even if null-check protected, this is a mandatory service parameter
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.SUBCARRIER_TO_BITRATE_RATIO.getCode()) {
                pdu.getResult().getPositiveResult().setParSubcarrierToBitRateRatio(new CltuGetParameterV1toV3.ParSubcarrierToBitRateRatio());
                pdu.getResult().getPositiveResult().getParSubcarrierToBitRateRatio().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParSubcarrierToBitRateRatio().setParameterValue(new SubcarrierDivisor(this.subcarrierToBitRateRatio == null ? 0 : this.subcarrierToBitRateRatio)); // Even if null-check protected, this is a mandatory service parameter
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.REPORTING_CYCLE.getCode()) {
                pdu.getResult().getPositiveResult().setParReportingCycle(new CltuGetParameterV1toV3.ParReportingCycle());
                pdu.getResult().getPositiveResult().getParReportingCycle().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReportingCycle().setParameterValue(new CurrentReportingCycle());
                if (this.reportingScheduler.get() != null && this.reportingCycle != null) {
                    pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOn(new ReportingCycle(this.reportingCycle));
                } else {
                    pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOff(new BerNull());
                }
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.RETURN_TIMEOUT_PERIOD.getCode()) {
                pdu.getResult().getPositiveResult().setParReturnTimeout(new CltuGetParameterV1toV3.ParReturnTimeout());
                pdu.getResult().getPositiveResult().getParReturnTimeout().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReturnTimeout().setParameterValue(new TimeoutPeriod(this.returnTimeoutPeriod));
            } else {
                pdu.getResult().setPositiveResult(null);
                pdu.getResult().setNegativeResult(new DiagnosticCltuGetParameter());
                pdu.getResult().getNegativeResult().setSpecific(new BerInteger(0)); // unknownParameter
            }
            toSend = pdu;
        } else if (getSleVersion() == 4) {
            CltuGetParameterReturnV4 pdu = new CltuGetParameterReturnV4();
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
            pdu.setResult(new CltuGetParameterReturnV4.Result());
            pdu.getResult().setPositiveResult(new CltuGetParameterV4());
            if (invocation.getCltuParameter().intValue() == CltuParameterEnum.BIT_LOCK_REQUIRED.getCode()) {
                pdu.getResult().getPositiveResult().setParBitLockRequired(new CltuGetParameterV4.ParBitLockRequired());
                pdu.getResult().getPositiveResult().getParBitLockRequired().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParBitLockRequired().setParameterValue(new IntPosShort(this.bitlockRequired ? 0 : 1)); // yes = 0, no = 1
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.RF_AVAILABLE_REQUIRED.getCode()) {
                pdu.getResult().getPositiveResult().setParRfAvailableRequired(new CltuGetParameterV4.ParRfAvailableRequired());
                pdu.getResult().getPositiveResult().getParRfAvailableRequired().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParRfAvailableRequired().setParameterValue(new IntPosShort(this.rfAvailableRequired ? 0 : 1)); // yes = 0, no = 1
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.DELIVERY_MODE.getCode()) {
                pdu.getResult().getPositiveResult().setParDeliveryMode(new CltuGetParameterV4.ParDeliveryMode());
                pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterValue(new CltuDeliveryMode(DeliveryModeEnum.FWD_ONLINE.ordinal())); // fixed to 3
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.EXPECTED_SLDU_IDENTIFICATION.getCode()) {
                pdu.getResult().getPositiveResult().setParCltuIdentification(new CltuGetParameterV4.ParCltuIdentification());
                pdu.getResult().getPositiveResult().getParCltuIdentification().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParCltuIdentification().setParameterValue(new CltuIdentification(this.cltuIdentification == null ? 0 : this.cltuIdentification)); // if not specified, 0
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.EXPECTED_EVENT_INVOCATION_IDENTIFICATION.getCode()) {
                pdu.getResult().getPositiveResult().setParEventInvocationIdentification(new CltuGetParameterV4.ParEventInvocationIdentification());
                pdu.getResult().getPositiveResult().getParEventInvocationIdentification().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParEventInvocationIdentification().setParameterValue(new EventInvocationId(this.eventInvocationIdentification == null ? 0 : this.eventInvocationIdentification)); // if not specified, 0
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.MAXIMUM_SLDU_LENGTH.getCode()) {
                pdu.getResult().getPositiveResult().setParMaximumCltuLength(new CltuGetParameterV4.ParMaximumCltuLength());
                pdu.getResult().getPositiveResult().getParMaximumCltuLength().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParMaximumCltuLength().setParameterValue(new BerInteger(this.maxCltuLength == null ? 0 : this.maxCltuLength)); // Even if null-check protected, this is a mandatory service parameter
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.MODULATION_FREQUENCY.getCode()) {
                pdu.getResult().getPositiveResult().setParModulationFrequency(new CltuGetParameterV4.ParModulationFrequency());
                pdu.getResult().getPositiveResult().getParModulationFrequency().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParModulationFrequency().setParameterValue(new ModulationFrequency(this.modulationFrequency == null ? 0 : this.modulationFrequency)); // Even if null-check protected, this is a mandatory service parameter
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.MODULATION_INDEX.getCode()) {
                pdu.getResult().getPositiveResult().setParModulationIndex(new CltuGetParameterV4.ParModulationIndex());
                pdu.getResult().getPositiveResult().getParModulationIndex().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParModulationIndex().setParameterValue(new ModulationIndex(this.modulationIndex == null ? 0 : this.modulationIndex)); // Even if null-check protected, this is a mandatory service parameter
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.PLOP_IN_EFFECT.getCode()) {
                pdu.getResult().getPositiveResult().setParPlopInEffect(new CltuGetParameterV4.ParPlopInEffect());
                pdu.getResult().getPositiveResult().getParPlopInEffect().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParPlopInEffect().setParameterValue(new BerInteger(this.plopInEffect == null ? 0 : this.plopInEffect.ordinal())); // Even if null-check protected, this is a mandatory service parameter
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.SUBCARRIER_TO_BITRATE_RATIO.getCode()) {
                pdu.getResult().getPositiveResult().setParSubcarrierToBitRateRatio(new CltuGetParameterV4.ParSubcarrierToBitRateRatio());
                pdu.getResult().getPositiveResult().getParSubcarrierToBitRateRatio().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParSubcarrierToBitRateRatio().setParameterValue(new SubcarrierDivisor(this.subcarrierToBitRateRatio == null ? 0 : this.subcarrierToBitRateRatio)); // Even if null-check protected, this is a mandatory service parameter
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.REPORTING_CYCLE.getCode()) {
                pdu.getResult().getPositiveResult().setParReportingCycle(new CltuGetParameterV4.ParReportingCycle());
                pdu.getResult().getPositiveResult().getParReportingCycle().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReportingCycle().setParameterValue(new CurrentReportingCycle());
                if (this.reportingScheduler.get() != null && this.reportingCycle != null) {
                    pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOn(new ReportingCycle(this.reportingCycle));
                } else {
                    pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOff(new BerNull());
                }
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.RETURN_TIMEOUT_PERIOD.getCode()) {
                pdu.getResult().getPositiveResult().setParReturnTimeout(new CltuGetParameterV4.ParReturnTimeout());
                pdu.getResult().getPositiveResult().getParReturnTimeout().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReturnTimeout().setParameterValue(new TimeoutPeriod(this.returnTimeoutPeriod));
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.ACQUISITION_SEQUENCE_LENGTH.getCode()) {
                pdu.getResult().getPositiveResult().setParAcquisitionSequenceLength(new CltuGetParameterV4.ParAcquisitionSequenceLength());
                pdu.getResult().getPositiveResult().getParAcquisitionSequenceLength().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParAcquisitionSequenceLength().setParameterValue(new IntUnsignedShort(this.acquisitionSequenceLength == null ? 0 : this.acquisitionSequenceLength));
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.CLCW_PHYSICAL_CHANNEL.getCode()) {
                pdu.getResult().getPositiveResult().setParClcwPhysicalChannel(new CltuGetParameterV4.ParClcwPhysicalChannel());
                pdu.getResult().getPositiveResult().getParClcwPhysicalChannel().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParClcwPhysicalChannel().setParameterValue(new BerVisibleString(this.clcwPhysicalChannel == null ? "" : this.clcwPhysicalChannel));
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.MINIMUM_DELAY_TIME.getCode()) {
                pdu.getResult().getPositiveResult().setParMinimumDelayTime(new CltuGetParameterV4.ParMinimumDelayTime());
                pdu.getResult().getPositiveResult().getParMinimumDelayTime().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParMinimumDelayTime().setParameterValue(new Duration(this.minCltuDelay == null ? 0 : this.minCltuDelay));
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.NOTIFICATION_MODE.getCode()) {
                pdu.getResult().getPositiveResult().setParNotificationMode(new CltuGetParameterV4.ParNotificationMode());
                pdu.getResult().getPositiveResult().getParNotificationMode().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParNotificationMode().setParameterValue(new BerInteger(this.notificationMode == null ? CltuNotificationModeEnum.DEFERRED.ordinal() : this.notificationMode.ordinal()));
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.PLOP1_IDLE_SEQUENCE_LENGTH.getCode()) {
                pdu.getResult().getPositiveResult().setParPlop1IdleSequenceLength(new CltuGetParameterV4.ParPlop1IdleSequenceLength());
                pdu.getResult().getPositiveResult().getParPlop1IdleSequenceLength().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParPlop1IdleSequenceLength().setParameterValue(new IntUnsignedShort(this.plop1IdleSequenceLength == null ? 0 : this.plop1IdleSequenceLength));
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.PROTOCOL_ABORT_MODE.getCode()) {
                pdu.getResult().getPositiveResult().setParProtocolAbortMode(new CltuGetParameterV4.ParProtocolAbortMode());
                pdu.getResult().getPositiveResult().getParProtocolAbortMode().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParProtocolAbortMode().setParameterValue(new BerInteger(this.protocolAbortMode == null ? CltuProtocolAbortModeEnum.ABORT_MODE.ordinal() : this.protocolAbortMode.ordinal()));
            } else {
                pdu.getResult().setPositiveResult(null);
                pdu.getResult().setNegativeResult(new DiagnosticCltuGetParameter());
                pdu.getResult().getNegativeResult().setSpecific(new BerInteger(0)); // unknownParameter
            }
            toSend = pdu;
        } else {
            CltuGetParameterReturn pdu = new CltuGetParameterReturn();
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
            pdu.setResult(new CltuGetParameterReturn.Result());
            pdu.getResult().setPositiveResult(new CltuGetParameter());
            if (invocation.getCltuParameter().intValue() == CltuParameterEnum.BIT_LOCK_REQUIRED.getCode()) {
                pdu.getResult().getPositiveResult().setParBitLockRequired(new CltuGetParameter.ParBitLockRequired());
                pdu.getResult().getPositiveResult().getParBitLockRequired().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParBitLockRequired().setParameterValue(new IntPosShort(this.bitlockRequired ? 0 : 1)); // yes = 0, no = 1
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.RF_AVAILABLE_REQUIRED.getCode()) {
                pdu.getResult().getPositiveResult().setParRfAvailableRequired(new CltuGetParameter.ParRfAvailableRequired());
                pdu.getResult().getPositiveResult().getParRfAvailableRequired().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParRfAvailableRequired().setParameterValue(new IntPosShort(this.rfAvailableRequired ? 0 : 1)); // yes = 0, no = 1
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.DELIVERY_MODE.getCode()) {
                pdu.getResult().getPositiveResult().setParDeliveryMode(new CltuGetParameter.ParDeliveryMode());
                pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParDeliveryMode().setParameterValue(new CltuDeliveryMode(DeliveryModeEnum.FWD_ONLINE.ordinal())); // fixed to 3
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.EXPECTED_SLDU_IDENTIFICATION.getCode()) {
                pdu.getResult().getPositiveResult().setParCltuIdentification(new CltuGetParameter.ParCltuIdentification());
                pdu.getResult().getPositiveResult().getParCltuIdentification().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParCltuIdentification().setParameterValue(new CltuIdentification(this.cltuIdentification == null ? 0 : this.cltuIdentification)); // if not specified, 0
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.EXPECTED_EVENT_INVOCATION_IDENTIFICATION.getCode()) {
                pdu.getResult().getPositiveResult().setParEventInvocationIdentification(new CltuGetParameter.ParEventInvocationIdentification());
                pdu.getResult().getPositiveResult().getParEventInvocationIdentification().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParEventInvocationIdentification().setParameterValue(new EventInvocationId(this.eventInvocationIdentification == null ? 0 : this.eventInvocationIdentification)); // if not specified, 0
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.MAXIMUM_SLDU_LENGTH.getCode()) {
                pdu.getResult().getPositiveResult().setParMaximumCltuLength(new CltuGetParameter.ParMaximumCltuLength());
                pdu.getResult().getPositiveResult().getParMaximumCltuLength().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParMaximumCltuLength().setParameterValue(new BerInteger(this.maxCltuLength == null ? 0 : this.maxCltuLength)); // Even if null-check protected, this is a mandatory service parameter
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.MODULATION_FREQUENCY.getCode()) {
                pdu.getResult().getPositiveResult().setParModulationFrequency(new CltuGetParameter.ParModulationFrequency());
                pdu.getResult().getPositiveResult().getParModulationFrequency().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParModulationFrequency().setParameterValue(new ModulationFrequency(this.modulationFrequency == null ? 0 : this.modulationFrequency)); // Even if null-check protected, this is a mandatory service parameter
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.MODULATION_INDEX.getCode()) {
                pdu.getResult().getPositiveResult().setParModulationIndex(new CltuGetParameter.ParModulationIndex());
                pdu.getResult().getPositiveResult().getParModulationIndex().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParModulationIndex().setParameterValue(new ModulationIndex(this.modulationIndex == null ? 0 : this.modulationIndex)); // Even if null-check protected, this is a mandatory service parameter
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.PLOP_IN_EFFECT.getCode()) {
                pdu.getResult().getPositiveResult().setParPlopInEffect(new CltuGetParameter.ParPlopInEffect());
                pdu.getResult().getPositiveResult().getParPlopInEffect().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParPlopInEffect().setParameterValue(new BerInteger(this.plopInEffect == null ? 0 : this.plopInEffect.ordinal())); // Even if null-check protected, this is a mandatory service parameter
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.SUBCARRIER_TO_BITRATE_RATIO.getCode()) {
                pdu.getResult().getPositiveResult().setParSubcarrierToBitRateRatio(new CltuGetParameter.ParSubcarrierToBitRateRatio());
                pdu.getResult().getPositiveResult().getParSubcarrierToBitRateRatio().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParSubcarrierToBitRateRatio().setParameterValue(new SubcarrierDivisor(this.subcarrierToBitRateRatio == null ? 0 : this.subcarrierToBitRateRatio)); // Even if null-check protected, this is a mandatory service parameter
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.REPORTING_CYCLE.getCode()) {
                pdu.getResult().getPositiveResult().setParReportingCycle(new CltuGetParameter.ParReportingCycle());
                pdu.getResult().getPositiveResult().getParReportingCycle().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReportingCycle().setParameterValue(new CurrentReportingCycle());
                if (this.reportingScheduler.get() != null && this.reportingCycle != null) {
                    pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOn(new ReportingCycle(this.reportingCycle));
                } else {
                    pdu.getResult().getPositiveResult().getParReportingCycle().getParameterValue().setPeriodicReportingOff(new BerNull());
                }
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.RETURN_TIMEOUT_PERIOD.getCode()) {
                pdu.getResult().getPositiveResult().setParReturnTimeout(new CltuGetParameter.ParReturnTimeout());
                pdu.getResult().getPositiveResult().getParReturnTimeout().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParReturnTimeout().setParameterValue(new TimeoutPeriod(this.returnTimeoutPeriod));
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.ACQUISITION_SEQUENCE_LENGTH.getCode()) {
                pdu.getResult().getPositiveResult().setParAcquisitionSequenceLength(new CltuGetParameter.ParAcquisitionSequenceLength());
                pdu.getResult().getPositiveResult().getParAcquisitionSequenceLength().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParAcquisitionSequenceLength().setParameterValue(new IntUnsignedShort(this.acquisitionSequenceLength == null ? 0 : this.acquisitionSequenceLength));
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.CLCW_GLOBAL_VCID.getCode()) {
                pdu.getResult().getPositiveResult().setParClcwGlobalVcId(new CltuGetParameter.ParClcwGlobalVcId());
                pdu.getResult().getPositiveResult().getParClcwGlobalVcId().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParClcwGlobalVcId().setParameterValue(new ClcwGvcId());
                if(this.clcwGlobalVcId == null) {
                    pdu.getResult().getPositiveResult().getParClcwGlobalVcId().getParameterValue().setNotConfigured(new BerNull());
                } else {
                    pdu.getResult().getPositiveResult().getParClcwGlobalVcId().getParameterValue().setCongigured(new GvcId());
                    pdu.getResult().getPositiveResult().getParClcwGlobalVcId().getParameterValue().getCongigured().setSpacecraftId(new BerInteger(this.clcwGlobalVcId.getSpacecraftId()));
                    pdu.getResult().getPositiveResult().getParClcwGlobalVcId().getParameterValue().getCongigured().setVersionNumber(new BerInteger(this.clcwGlobalVcId.getTransferFrameVersionNumber()));
                    pdu.getResult().getPositiveResult().getParClcwGlobalVcId().getParameterValue().getCongigured().setVcId(new GvcId.VcId());
                    if(this.clcwGlobalVcId.getVirtualChannelId() == null) {
                        pdu.getResult().getPositiveResult().getParClcwGlobalVcId().getParameterValue().getCongigured().getVcId().setMasterChannel(new BerNull());
                    } else {
                        pdu.getResult().getPositiveResult().getParClcwGlobalVcId().getParameterValue().getCongigured().getVcId().setVirtualChannel(new VcId(this.clcwGlobalVcId.getVirtualChannelId()));
                    }
                }
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.CLCW_PHYSICAL_CHANNEL.getCode()) {
                pdu.getResult().getPositiveResult().setParClcwPhysicalChannel(new CltuGetParameter.ParClcwPhysicalChannel());
                pdu.getResult().getPositiveResult().getParClcwPhysicalChannel().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParClcwPhysicalChannel().setParameterValue(new ClcwPhysicalChannel());
                if(this.clcwPhysicalChannel == null) {
                    pdu.getResult().getPositiveResult().getParClcwPhysicalChannel().getParameterValue().setNotConfigured(new BerNull());
                } else {
                    pdu.getResult().getPositiveResult().getParClcwPhysicalChannel().getParameterValue().setConfigured(new BerVisibleString(this.clcwPhysicalChannel));
                }
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.MINIMUM_DELAY_TIME.getCode()) {
                pdu.getResult().getPositiveResult().setParMinimumDelayTime(new CltuGetParameter.ParMinimumDelayTime());
                pdu.getResult().getPositiveResult().getParMinimumDelayTime().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParMinimumDelayTime().setParameterValue(new Duration(this.minCltuDelay == null ? 0 : this.minCltuDelay));
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.NOTIFICATION_MODE.getCode()) {
                pdu.getResult().getPositiveResult().setParNotificationMode(new CltuGetParameter.ParNotificationMode());
                pdu.getResult().getPositiveResult().getParNotificationMode().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParNotificationMode().setParameterValue(new BerInteger(this.notificationMode == null ? CltuNotificationModeEnum.DEFERRED.ordinal() : this.notificationMode.ordinal()));
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.PLOP1_IDLE_SEQUENCE_LENGTH.getCode()) {
                pdu.getResult().getPositiveResult().setParPlop1IdleSequenceLength(new CltuGetParameter.ParPlop1IdleSequenceLength());
                pdu.getResult().getPositiveResult().getParPlop1IdleSequenceLength().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParPlop1IdleSequenceLength().setParameterValue(new IntUnsignedShort(this.plop1IdleSequenceLength == null ? 0 : this.plop1IdleSequenceLength));
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.PROTOCOL_ABORT_MODE.getCode()) {
                pdu.getResult().getPositiveResult().setParProtocolAbortMode(new CltuGetParameter.ParProtocolAbortMode());
                pdu.getResult().getPositiveResult().getParProtocolAbortMode().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParProtocolAbortMode().setParameterValue(new BerInteger(this.protocolAbortMode == null ? CltuProtocolAbortModeEnum.ABORT_MODE.ordinal() : this.protocolAbortMode.ordinal()));
            } else if (invocation.getCltuParameter().intValue() == CltuParameterEnum.MIN_REPORTING_CYCLE.getCode()) {
                pdu.getResult().getPositiveResult().setParMinReportingCycle(new CltuGetParameter.ParMinReportingCycle());
                pdu.getResult().getPositiveResult().getParMinReportingCycle().setParameterName(new ParameterName(invocation.getCltuParameter().intValue()));
                pdu.getResult().getPositiveResult().getParMinReportingCycle().setParameterValue(new IntPosShort(this.minReportingCycle == null ? 0 : this.minReportingCycle));
            } else {
                pdu.getResult().setPositiveResult(null);
                pdu.getResult().setNegativeResult(new DiagnosticCltuGetParameter());
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
        if (!immediate && this.reportingScheduler.get() == null) {
            return;
        }

        CltuStatusReportInvocation pdu = new CltuStatusReportInvocation();
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
        pdu.setCltuBufferAvailable(new BufferSize(this.bufferAvailable == null ? 0 : this.bufferAvailable));
        pdu.setCltuLastOk(new eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.CltuLastOk());
        if(this.lastOk == null) {
            pdu.getCltuLastOk().setNoCltuOk(new BerNull());
        } else {
            pdu.getCltuLastOk().setCltuOk(new eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.CltuLastOk.CltuOk());
            pdu.getCltuLastOk().getCltuOk().setCltuIdentification(new CltuIdentification(this.lastOk.getCltuIdentification()));
            pdu.getCltuLastOk().getCltuOk().setStopRadiationTime(new Time());
            pdu.getCltuLastOk().getCltuOk().getStopRadiationTime().setCcsdsFormat(new TimeCCSDS(PduFactoryUtil.buildCDSTime(this.lastOk.getRadiationStopTime().getTime(), 0)));
        }
        pdu.setCltuLastProcessed(new eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.CltuLastProcessed());
        if(this.lastProcessed == null) {
            pdu.getCltuLastProcessed().setNoCltuProcessed(new BerNull());
        } else {
            pdu.getCltuLastProcessed().setCltuProcessed(new eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.CltuLastProcessed.CltuProcessed());
            pdu.getCltuLastProcessed().getCltuProcessed().setCltuIdentification(new CltuIdentification(this.lastProcessed.getCltuIdentification()));
            pdu.getCltuLastProcessed().getCltuProcessed().setCltuStatus(new CltuStatus(this.lastProcessed.getCltuStatus().ordinal()));
            pdu.getCltuLastProcessed().getCltuProcessed().setStartRadiationTime(new ConditionalTime());
            if(this.lastProcessed.getRadiationStartTime() == null) {
                pdu.getCltuLastProcessed().getCltuProcessed().getStartRadiationTime().setUndefined(new BerNull());
            } else {
                pdu.getCltuLastProcessed().getCltuProcessed().getStartRadiationTime().setKnown(new Time());
                pdu.getCltuLastProcessed().getCltuProcessed().getStartRadiationTime().getKnown().setCcsdsFormat(new TimeCCSDS(PduFactoryUtil.buildCDSTime(this.lastProcessed.getRadiationStartTime().getTime(), 0)));
            }
        }
        pdu.setCltuProductionStatus(new ProductionStatus(this.productionStatus.ordinal()));
        pdu.setUplinkStatus(new UplinkStatus(this.uplinkStatus.ordinal()));
        pdu.setNumberOfCltusProcessed(new NumberOfCltusProcessed(this.nbCltuProcessed));
        pdu.setNumberOfCltusRadiated(new NumberOfCltusRadiated(this.nbCltuRadiated));
        pdu.setNumberOfCltusReceived(new NumberOfCltusReceived(this.nbCltuReceived));

        boolean resultOk = encodeAndSend(null, pdu, SleOperationNames.STATUS_REPORT_NAME);

        if (resultOk) {
            // Notify PDU
            notifyPduSent(pdu, SleOperationNames.STATUS_REPORT_NAME, getLastPduSent());
            // Generate state and notify update
            notifyStateUpdate();
        }
    }

    @Override
    protected ServiceInstanceState buildCurrentState() {
        CltuServiceInstanceState state = new CltuServiceInstanceState();
        copyCommonState(state);
        state.setMaxCltuLength(this.maxCltuLength);
        state.setMinCltuDelay(this.minCltuDelay);
        state.setBitlockRequired(this.bitlockRequired);
        state.setRfAvailableRequired(this.rfAvailableRequired);
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
        state.setSubcarrierToBitRateRation(this.subcarrierToBitRateRatio);
        state.setDeliveryMode(DeliveryModeEnum.FWD_ONLINE);

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
        state.setNotification(null);
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
        this.protocolAbortMode = getCltuConfiguration().getProtocolAbortMode();
        this.minReportingCycle = getCltuConfiguration().getMinReportingCycle();
        this.reportingCycle = null;
        this.returnTimeoutPeriod = getCltuConfiguration().getReturnTimeoutPeriod();

        this.acquisitionSequenceLength = getCltuConfiguration().getAcquisitionSequenceLength();
        this.clcwGlobalVcId = getCltuConfiguration().getClcwGlobalVcid(); // can be null -> not configured
        this.clcwPhysicalChannel = getCltuConfiguration().getClcwPhysicalChannel(); // can be null -> not configured
        this.cltuIdentification = null;
        this.eventInvocationIdentification = 0L;
        this.modulationFrequency = getCltuConfiguration().getModulationFrequency();
        this.modulationIndex = getCltuConfiguration().getModulationIndex();
        this.notificationMode = getCltuConfiguration().getNotificationMode();
        this.plop1IdleSequenceLength = getCltuConfiguration().getPlop1idleSequenceLength();
        this.plopInEffect = getCltuConfiguration().getPlopInEffect();
        this.subcarrierToBitRateRatio = getCltuConfiguration().getSubcarrierToBitrateRatio();

        // START
        this.firstCltuIdentification = null;
        // STATUS_REPORT
        this.lastProcessed = null; // also from async notify
        this.lastOk = null; // also from async notify
        this.productionStatus = CltuProductionStatusEnum.INTERRUPTED; // also from async notify
        this.uplinkStatus = CltuUplinkStatusEnum.UPLINK_STATUS_NOT_AVAILABLE; // also from async notify
        this.nbCltuReceived = 0L;
        this.nbCltuProcessed = 0L;
        this.nbCltuRadiated = 0L;
        this.bufferAvailable = 0L;

        this.radiationNotificationSet.clear();
    }

    private CltuServiceInstanceConfiguration getCltuConfiguration() {
        return (CltuServiceInstanceConfiguration) this.serviceInstanceConfiguration;
    }

    @Override
    protected boolean isUserSide() {
        return false;
    }
}
