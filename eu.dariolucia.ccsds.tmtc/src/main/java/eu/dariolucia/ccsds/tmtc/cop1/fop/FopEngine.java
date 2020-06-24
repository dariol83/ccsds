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

package eu.dariolucia.ccsds.tmtc.cop1.fop;

import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.ocf.pdu.Clcw;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * This class implements the FOP side of the COP-1 protocol, as defined by CCSDS 232.1-B-2 Cor. 1.
 *
 * This class is thread-safe.
 */
@SuppressWarnings("StatementWithEmptyBody")
public class FopEngine {

    private final int virtualChannelId;
    private final Supplier<Integer> nextVirtualChannelFrameCounterGetter;
    private final Consumer<Integer> nextVirtualChannelFrameCounterSetter;
    private final Supplier<TcTransferFrame> bcFrameUnlockFactory;
    private final IntFunction<TcTransferFrame> bcFrameSetVrFactory;

    private final ExecutorService fopExecutor;

    private volatile Thread confinementThread; // NOSONAR only used for reference equality comparison

    private final ExecutorService lowLevelExecutor;

    private final List<IFopObserver> observers = new CopyOnWriteArrayList<>();

    private final AtomicReference<FopOperationStatus> pendingAcceptRejectFrame = new AtomicReference<>();

    private final AtomicReference<Object[]> pendingInitAd = new AtomicReference<>();

    /**
     * This is the variable encapsulating what the standard calls 'Lower Level Procedures' or 'Lower Procedures'. In this implementation,
     * this is a function called asynchronously by the lowLevelExecutor, which returns true (frame accepted) or false
     * (frame rejected). There is no abort() operation defined in this implementation.
     */
    private final Function<TcTransferFrame, Boolean> output;

    private final Timer fopTimer;

    private TimerTask currentTimer;


    // ---------------------------------------------------------------------------------------------------------
    // FOP variables as per CCSDS 232.1-B-2 Cor. 1, section 5.1
    // ---------------------------------------------------------------------------------------------------------

    /**
     * This variable represents the state of FOP-1 for the specific Virtual Channel.
     */
    private AbstractFopState state;

    /*
     * The Transmitter_Frame_Sequence_Number, V(S), contains the value of the Frame Sequence
     * Number, N(S), to be put in the Transfer Frame Primary Header of the next Type-AD
     * Transfer Frame to be transmitted.
     * In this implementation, this information is in the tcVc object: tcVc.getNextVirtualChannelFrameCounter()
     */

    /**
     * When Type-AD FDUs are received from the Higher Procedures, they shall be held in the
     * Wait_Queue until they can be accepted by FOP-1. The Wait_Queue has a maximum
     * capacity of one FDU.
     * The Wait_Queue and 'Accept Response to Request to Transfer FDU' form the primary
     * mechanism by which flow control as seen by the Higher Procedures is governed. When an
     * FDU is on the Wait_Queue, this means that the Higher Procedures have not yet received an
     * 'Accept Response' for the corresponding 'Request to Transfer FDU'.
     */
    private AtomicReference<TcTransferFrame> waitQueue = new AtomicReference<>(null);
    /**
     * Whether or not a ‘Transmit Request for Frame’ can be sent for AD. If true, means READY.
     */
    private volatile boolean adOutReadyFlag = true;
    /**
     * Whether or not a ‘Transmit Request for Frame’ can be sent for BD. If true, means READY.
     */
    private volatile boolean bdOutReadyFlag = true;
    /**
     * Whether or not a ‘Transmit Request for Frame’ can be sent for BC. If true, means READY.
     */
    private volatile boolean bcOutReadyFlag = true;
    /**
     * The Sent_Queue is a Virtual Channel data structure in which the master copy of all Type-AD
     * and Type-BC Transfer Frames on a Virtual Channel is held between the time a copy of the
     * Transfer Frame is first passed to the Lower Procedures for transmission, and the time the
     * FOP-1 has finished processing the Transfer Frame.
     */
    private Queue<TransferFrameStatus> sentQueue = new LinkedList<>();
    /**
     * The Expected_Acknowledgement_Frame_Sequence_Number, NN(R), contains the Frame
     * Sequence Number of the oldest unacknowledged AD Frame, which is on the Sent_Queue.
     * This value is often equal to the value of N(R) from the previous CLCW on that Virtual
     * Channel.
     */
    private int expectedAckFrameSequenceNumber; // NN(R)
    /**
     * Whenever a Type-AD or Type-BC Transfer Frame is transmitted, the Timer shall be started
     * or restarted with an initial value of Timer_Initial_Value (T1_Initial).
     */
    private int timerInitialValue;
    /**
     * The Transmission_Limit holds a value which represents the maximum number of times the
     * first Transfer Frame on the Sent_Queue may be transmitted. This includes the first
     * 'transmission' and any subsequent 'retransmissions' of the Transfer Frame.
     */
    private int transmissionLimit;
    /**
     * The Timeout_Type variable may take one of two values, '0' or '1'.
     * It specifies the action to be performed when both the Timer expires and the
     * Transmission_Count has reached the Transmission_Limit.
     */
    private int timeoutType; // TT
    /**
     * The Transmission_Count variable is used to count the number of transmissions of the first
     * Transfer Frame on the Sent_Queue. The Transmission_Count shall be incremented each
     * time the first Transfer Frame is retransmitted.
     */
    private int transmissionCount;
    /**
     * The Suspend_State variable may take one of five values, from '0' to
     * '4'. It records the state that FOP-1 was in when the AD Service was suspended. This is the state to
     * which FOP-1 will return should the AD Service be resumed. If SS = 0, the AD Service is deemed not suspended.
     */
    private int suspendState; // SS
    /**
     * The FOP Sliding Window is a mechanism which limits the number of Transfer Frames which
     * can be transmitted ahead of the last acknowledged Transfer Frame, i.e., before a CLCW
     * report is received which updates the status of acknowledged Transfer Frames. This is done
     * to prevent sending a new Transfer Frame with the same sequence number as a rejected
     * Transfer Frame.
     */
    private int fopSlidingWindow; // K

    /**
     * Constructor of the FOP engine.
     *
     * Being this class fully decoupled from the way {@link TcTransferFrame} are constructed and forwarded to the encoding
     * layer, the constructor accepts functional objects for the operations to be delegated to the external interfaces.
     *
     * @param virtualChannelId the TC virtual channel ID controlled by this FOP entity
     * @param nextVirtualChannelFrameCounterGetter a {@link Supplier} function to retrieve the next virtual channel frame counter
     * @param nextVirtualChannelFrameCounterSetter a {@link Consumer} function to set the next virtual channel frame counter
     * @param bcFrameUnlockFactory a {@link Function} to build a BC frame for FARM unlock
     * @param bcFrameSetVrFactory a {@link Function} to build a BC frame for FARM Set_V(R)
     * @param output a {@link Consumer} function to forward {@link TcTransferFrame} as output of the FOP engine
     */
    public FopEngine(int virtualChannelId, Supplier<Integer> nextVirtualChannelFrameCounterGetter, Consumer<Integer> nextVirtualChannelFrameCounterSetter, Supplier<TcTransferFrame> bcFrameUnlockFactory, IntFunction<TcTransferFrame> bcFrameSetVrFactory, Function<TcTransferFrame, Boolean> output) {
        this.virtualChannelId = virtualChannelId;
        this.nextVirtualChannelFrameCounterGetter = nextVirtualChannelFrameCounterGetter;
        this.nextVirtualChannelFrameCounterSetter = nextVirtualChannelFrameCounterSetter;
        this.bcFrameUnlockFactory = bcFrameUnlockFactory;
        this.bcFrameSetVrFactory = bcFrameSetVrFactory;
        this.output = output;
        this.fopExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("FOP Entity Processor for TC VC " + virtualChannelId);
            FopEngine.this.confinementThread = t;
            return t;
        });
        this.lowLevelExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("FOP Entity Low Level for TC VC " + virtualChannelId);
            return t;
        });
        //
        this.state = new S6FopState(this); // In principle, the ‘Initial’ State is the first state entered by the state machine for a particular Virtual Channel.
        //
        this.fopTimer = new Timer("FOP TC VC " + virtualChannelId + " Timer");
    }

    /**
     * Register an {@link IFopObserver} as listener to FOP state changes, alerts and suspends notifications, as well as
     * transfer frame and directive notifications.
     *
     * @param observer the observer to register
     */
    public void register(IFopObserver observer) {
        this.observers.add(observer);
    }

    /**
     * Deregister an {@link IFopObserver} listener.
     *
     * @param observer the observer to deregister
     */
    public void deregister(IFopObserver observer) {
        this.observers.remove(observer);
    }

    // ---------------------------------------------------------------------------------------------------------
    // FOP-1 public operations as per CCSDS definition for event injection
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Request the FOP engine to execute a COP-1 directive.
     *
     * @param tag the request tag, which will be returned in the callback on the {@link IFopObserver} interface
     * @param directive the directive ID
     * @param qualifier the directive qualifier: if not meaningful, this argument can assume any value
     */
    public void directive(Object tag, FopDirective directive, int qualifier) {
        fopExecutor.execute(() -> processDirective(tag, directive, qualifier));
    }

    /**
     * Request the FOP engine to transmit a AD or BD frame. This operation allows simple flow control, as the method will
     * return when the frame will be either accepted or rejected by the FOP engine.
     *
     * The use of this operation is a convenience operation and shall not be mixed with the other transmit operation. The
     * behaviour of this class if the two operations are mixed is undefined.
     *
     * @param frame the frame to transmit
     * @param timeoutMillis the timeout in milliseconds waiting for acceptance or rejection of the request
     * @return true if the request was accepted, false it is was rejected or the timeout expired
     * @throws InterruptedException in case the invoking thread is interrupted
     */
    public boolean transmit(TcTransferFrame frame, int timeoutMillis) throws InterruptedException {
        long expirationTime = System.currentTimeMillis()+ timeoutMillis;
        synchronized (pendingAcceptRejectFrame) {
            if(pendingAcceptRejectFrame.get() != null) {
                throw new IllegalStateException("Internal state error, pendingAcceptRejectFrame must be null at this stage");
            }
            transmit(frame);
            while(pendingAcceptRejectFrame.get() == null) {
                pendingAcceptRejectFrame.wait(timeoutMillis);
                if(System.currentTimeMillis() >= expirationTime) {
                    break;
                }
            }
            FopOperationStatus operationResult = pendingAcceptRejectFrame.getAndSet(null);
            return operationResult == FopOperationStatus.ACCEPT_RESPONSE;
        }
    }

    /**
     * Request the FOP engine to transmit a AD or BD frame. This operation is fully asynchronous and does not implement
     * any flow control mechanism, as per COP-1 standard. Users of this method shall wait for the acceptance or rejection
     * of the frame as reported by the callback method on the {@link IFopObserver} interface.
     *
     * The use of this operation shall not be mixed with the other transmit operation. The behaviour of this class if
     * the two operations are mixed is undefined.
     *
     * @param frame the frame to transmit
     */
    public void transmit(TcTransferFrame frame) {
        switch(frame.getFrameType()) {
            case AD:
                fopExecutor.execute(() -> processAdFrame(frame));
            break;
            case BD:
                fopExecutor.execute(() -> processBdFrame(frame));
            break;
            default:
                throw new IllegalArgumentException("TC Transfer Frame detected unsupported type for transmit operation: " + frame.getFrameType());
        }
    }

    /**
     * Inform the FOP entity about the arrival of a new CLCW. The CLCW is processed only if reports COP-1 in effect, and
     * matches the TC VC specified at FOP engine construction time.
     *
     * @param clcw the CLCW to process
     */
    public void clcw(Clcw clcw) {
        if(clcw.getCopInEffect() == Clcw.CopEffectType.COP1 && clcw.getVirtualChannelId() == virtualChannelId) {
            fopExecutor.execute(() -> processClcw(clcw));
        }
    }

    /**
     * Dispose the FOP entity. This operation has the following effects:
     * <ul>
     *     <li>the timer is cancelled</li>
     *     <li>the wait and the sent queues are purged and related notifications are provided</li>
     *     <li>the internal executors are shutdown</li>
     * </ul>
     *
     * Calling other methods after that this method is invoked will likely cause the raising of {@link java.lang.reflect.InvocationTargetException}
     * due to the fact that the executors are shut down.
     */
    public void dispose() {
        fopExecutor.submit(() -> {
            cancelTimer();
            purgeWaitQueue();
            purgeSentQueue();
        });
        this.fopExecutor.shutdown();
        try {
            this.fopExecutor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.lowLevelExecutor.shutdownNow();
    }

    // ---------------------------------------------------------------------------------------------------------
    // FOP-1 actions defined as per CCSDS definition. All these actions are performed by the state transitions
    // and executed by the fopExecutor service. Thread access is enforced to avoid misuse.
    // ---------------------------------------------------------------------------------------------------------

    /**
     * This action includes clearing the Sent_Queue by generating a 'Negative Confirm Response to Request to
     * Transfer FDU' for each Transfer Frame on the queue and deleting the Transfer Frame.
     *
     * Ref. 5.2.2
     */
    void purgeSentQueue() {
        checkThreadAccess();
        for(TransferFrameStatus tfs : this.sentQueue) {
            if(tfs.getFrame().getFrameType() == TcTransferFrame.FrameType.BC && this.pendingInitAd.get() != null) {
                // Directive: not on the standard
                Object[] directive = this.pendingInitAd.getAndSet(null);
                observers.forEach(o -> o.directiveNotification(FopOperationStatus.NEGATIVE_CONFIRM, directive[0], (FopDirective) directive[1], (int) directive[2]));
            } else {
                // Frame
                observers.forEach(o -> o.transferNotification(FopOperationStatus.NEGATIVE_CONFIRM, tfs.getFrame()));
            }
        }
        this.sentQueue.clear();
    }

    /**
     * This action includes clearing the Wait_Queue and generating a 'Reject Response to Request to Transfer
     * FDU' for the queued FDU.
     *
     * Ref. 5.2.3
     */
    void purgeWaitQueue() {
        checkThreadAccess();
        if(this.waitQueue.get() != null) {
            reject(this.waitQueue.get());
        }
        this.waitQueue.set(null);
    }

    /**
     * This action includes all the functions necessary to prepare a Type-AD Transfer Frame for
     * transmission.
     *
     * Ref. 5.2.4
     *
     * @param frame the frame to send
     */
    void transmitTypeAdFrame(TcTransferFrame frame) {
        checkThreadAccess();
        // a) // Not needed, handled by tcVc object
        boolean sentQueueWasEmpty = this.sentQueue.isEmpty(); // in preparation for c)
        this.sentQueue.add(new TransferFrameStatus(frame)); // b)
        if(sentQueueWasEmpty) {
            this.transmissionCount = 1; // c)
        }
        restartTimer(); // d)
        setAdOutReadyFlag(false); // e)
        lowLevelExecutor.execute(() -> forwardToOutput(frame)); // f)
    }

    /**
     * This action includes all the functions necessary to prepare a Type-BC Transfer Frame for
     * transmission.
     *
     * Ref. 5.2.5
     *
     * @param frame the frame to send
     */
    void transmitTypeBcFrame(TcTransferFrame frame) {
        checkThreadAccess();
        this.sentQueue.add(new TransferFrameStatus(frame)); // a)
        this.transmissionCount = 1; // b)
        restartTimer(); // c)
        setBcOutReadyFlag(false); // d)
        lowLevelExecutor.execute(() -> forwardToOutput(frame)); // e)
    }

    /**
     * This action includes all the functions necessary to prepare a Type-BD Transfer Frame for
     * transmission.
     *
     * Ref. 5.2.6
     *
     * @param frame the frame to send
     */
    void transmitTypeBdFrame(TcTransferFrame frame) {
        checkThreadAccess();
        setBdOutReadyFlag(false); // a)
        lowLevelExecutor.execute(() -> forwardToOutput(frame)); // b)
    }

    /**
     * Ref. 5.2.7 for AD
     */
    void initiateAdRetransmission() {
        checkThreadAccess();
        // a) Abort request to lower procedures not provided
        this.transmissionCount++; // b)
        restartTimer(); // c)
        this.sentQueue.stream().filter(o -> o.getFrame().getFrameType() == TcTransferFrame.FrameType.AD).forEach(o -> o.setToBeRetransmitted(true)); // d)
    }

    /**
     * Ref. 5.2.7 for BC
     */
    void initiateBcRetransmission() {
        checkThreadAccess();
        // a) Abort request to lower procedures not provided
        this.transmissionCount++; // b)
        restartTimer(); // c)
        this.sentQueue.stream().filter(o -> o.getFrame().getFrameType() == TcTransferFrame.FrameType.BC).forEach(o -> o.setToBeRetransmitted(true)); // d)
    }

    /**
     * Ref. 5.2.8
     *
     * @param clcw the CLCW
     */
    void removeAckFramesFromSentQueue(Clcw clcw) {
        checkThreadAccess();
        boolean acked = true;
        while(acked && !this.sentQueue.isEmpty()) {
            TransferFrameStatus next = this.sentQueue.element(); // Inspect the first frame
            // No BC frames should be present here
            if(next.getFrame().getFrameType() == TcTransferFrame.FrameType.BC) {
                throw new IllegalStateException("No BC frames should be present in the sent queue when calling removeAckFramesFromSentQueue()");
            }
            // The frame is acked if its frame counter is lower than the CLCW reported value (mod 256)
            // taking into account the FOP sliding window.
            // Compute the set of values that actually acknowledge the frame
            int currentNNR = next.getFrame().getVirtualChannelFrameCount();
            Set<Integer> expander = new HashSet<>();
            for(int i = 0; i < fopSlidingWindow; ++i) {
                expander.add((currentNNR + 1 + i) % 256);
            }
            // If the set contains the clcw report, all fine
            if(expander.contains((int) clcw.getReportValue())) {
                // Acked - Confirm
                confirm(next.getFrame()); // a)
                this.sentQueue.remove();
                // Update NN(R)
                this.expectedAckFrameSequenceNumber = (this.expectedAckFrameSequenceNumber + 1) % 256; // b)
                // Reset transmission count
                this.transmissionCount = 1; // c)
                //
                acked = true;
            } else {
                // Not acked yet
                acked = false;
            }
        }
    }

    /**
     * Ref. 5.2.9
     */
    void lookForDirective() {
        checkThreadAccess();
        if(!bcOutReadyFlag) { // a)
            // If not, no further processing can be performed for retransmitting the Type-BC Transfer Frame until
            // a 'BC_Accept' Response is received from the Lower Procedures for the outstanding 'Transmit
            // Request for (BC) Frame', setting the BC_Out_Flag to 'Ready'. // NOSONAR not a block of code
        } else { // b)
            Optional<TransferFrameStatus> optBcFrame = this.sentQueue.stream().filter(o -> o.getFrame().getFrameType() == TcTransferFrame.FrameType.BC).findFirst();
            if(optBcFrame.isPresent() && optBcFrame.get().isToBeRetransmitted()) {
                setBcOutReadyFlag(false);
                optBcFrame.get().setToBeRetransmitted(false); // Not specified by the state machine, but if this is not done, the accept() of the lower procedure will trigger again a lookDirective that will send this
                lowLevelExecutor.execute(() -> forwardToOutput(optBcFrame.get().getFrame()));
            }
        }
    }

    /**
     * Ref. 5.2.10
     */
    void lookForFrame() {
        checkThreadAccess();
        if(!adOutReadyFlag) { // a)
            // If not, no further processing can be performed for transmitting Type-AD Transfer Frames. In fact, when an 'AD_Accept'
            // Response is received from the Lower Procedures for the outstanding 'Transmit Request for (AD) Frame', FOP-1 will set // NOSONAR not a block of code
            // the AD_Out_Flag to 'Ready' and execute a new 'Look for FDU'.
        } else {
            // Checking if a Type-AD Transfer Frame on the Sent_Queue is flagged 'To_Be_Retransmitted'. If so, the flag
            // is set to 'Not_Ready' and a copy of the first such AD Transfer Frame is passed to the Lower Procedures as a parameter
            // of a 'Transmit Request for (AD) Frame' and the To_Be_Retransmitted_Flag for that Transfer Frame is reset. // NOSONAR not a block of code
            Optional<TransferFrameStatus> optAdFrame = this.sentQueue.stream().filter(o -> o.getFrame().getFrameType() == TcTransferFrame.FrameType.AD).filter(TransferFrameStatus::isToBeRetransmitted).findFirst();
            if(optAdFrame.isPresent()) { // b)
                optAdFrame.get().setToBeRetransmitted(false);
                setAdOutReadyFlag(false);
                lowLevelExecutor.execute(() -> forwardToOutput(optAdFrame.get().getFrame()));
            } else { // c)
                // If no Type-AD Transfer Frame is marked 'To_Be_Retransmitted', checking if both V(S) < NN(R) + K and
                // a Type-AD FDU is available on the Wait_Queue. If so, the FDU is removed from the Wait_Queue, an
                // 'Accept Response to Request to Transfer FDU' is passed to the Higher Layer, and the 'Transmit Type-AD Frame'
                // action for the FDU is performed.
                if(waitQueue.get() != null && waitQueue.get().getFrameType() == TcTransferFrame.FrameType.AD && nextVirtualChannelFrameCounterGetter.get() < (expectedAckFrameSequenceNumber + fopSlidingWindow)) {
                    TcTransferFrame toTransmit = waitQueue.getAndSet(null);
                    accept(toTransmit);
                    transmitTypeAdFrame(toTransmit);
                } else { // d)
                    // If no FDU is available on the Wait_Queue, no further processing is performed.
                }
            }
        }
    }

    /**
     * Ref. 5.2.11
     *
     * @param frame the frame to notify as accepted
     */
    void accept(TcTransferFrame frame) {
        checkThreadAccess();
        synchronized (pendingAcceptRejectFrame) {
            pendingAcceptRejectFrame.set(FopOperationStatus.ACCEPT_RESPONSE);
            pendingAcceptRejectFrame.notifyAll();
        }
        observers.forEach(o -> o.transferNotification(FopOperationStatus.ACCEPT_RESPONSE, frame));
    }

    /**
     * Ref. 5.2.11
     *
     * @param tag the directive tag to report as accepted
     * @param directive the directive type
     * @param qualifier the qualifier
     */
    void accept(Object tag, FopDirective directive, int qualifier) {
        checkThreadAccess();
        observers.forEach(o -> o.directiveNotification(FopOperationStatus.ACCEPT_RESPONSE, tag, directive, qualifier));
    }

    /**
     * Ref. 5.2.12
     *
     * @param frame the frame to notify as rejected
     */
    void reject(TcTransferFrame frame) {
        checkThreadAccess();
        synchronized (pendingAcceptRejectFrame) {
            pendingAcceptRejectFrame.set(FopOperationStatus.REJECT_RESPONSE);
            pendingAcceptRejectFrame.notifyAll();
        }
        observers.forEach(o -> o.transferNotification(FopOperationStatus.REJECT_RESPONSE, frame));
    }

    /**
     * Ref. 5.2.12
     *
     * @param tag the directive tag to report as rejected
     * @param directive the directive type
     * @param qualifier the qualifier
     */
    void reject(Object tag, FopDirective directive, int qualifier) {
        checkThreadAccess();
        observers.forEach(o -> o.directiveNotification(FopOperationStatus.REJECT_RESPONSE, tag, directive, qualifier));
    }

    /**
     * Utility method to discriminate between frame or directive. To be called ONLY by the state machine handlers.
     *
     * @param fopEvent the event linked to the action to reject
     */
    void reject(FopEvent fopEvent) {
        checkThreadAccess();
        if(fopEvent.getFrame() != null) {
            reject(fopEvent.getFrame());
        } else if(fopEvent.getDirectiveId() != null) {
            reject(fopEvent.getDirectiveTag(), fopEvent.getDirectiveId(), fopEvent.getDirectiveQualifier());
        } else {
            throw new IllegalArgumentException("FOP event " + fopEvent + " not recognised for reject");
        }
    }

    void confirm(TcTransferFrame frame) {
        checkThreadAccess();
        observers.forEach(o -> o.transferNotification(FopOperationStatus.POSIIVE_CONFIRM, frame));
    }

    void confirm(Object tag, FopDirective directive, int qualifier) {
        checkThreadAccess();
        observers.forEach(o -> o.directiveNotification(FopOperationStatus.POSIIVE_CONFIRM, tag, directive, qualifier));
    }

    void addToWaitQueue(FopEvent fopEvent) {
        checkThreadAccess();
        this.waitQueue.set(fopEvent.getFrame());
    }

    /**
     * Ref. 5.2.14
     */
    void initialise() {
        checkThreadAccess();
        purgeSentQueue(); // a)
        purgeWaitQueue(); // b)
        this.transmissionCount = 1; // c)
        this.suspendState = 0; // d)
    }

    /**
     * Ref. 5.2.15
     *
     * @param code the alert code
     */
    void alert(FopAlertCode code) {
        checkThreadAccess();
        cancelTimer(); // a)
        purgeSentQueue(); // b)
        purgeWaitQueue(); // c)
        // d) not understood: if the wait and the sent queues are purged, there is no other frame stored in the FOP engine at this stage
        observers.forEach(o -> o.alert(code)); // e)
    }

    /**
     * Ref. 5.2.16
     */
    void suspend() {
        checkThreadAccess();
        observers.forEach(IFopObserver::suspend);
    }

    /**
     * Ref. 5.2.17
     */
    void resume() {
        checkThreadAccess();
        restartTimer(); // a)
        this.suspendState = 0; // b)
    }

    void restartTimer() {
        checkThreadAccess();
        synchronized (fopTimer) {
            if(currentTimer != null) {
                currentTimer.cancel();
                currentTimer = null;
            }
            currentTimer = new TimerTask() {
                @Override
                public void run() {
                    timerExpired(this);
                }
            };
            fopTimer.schedule(currentTimer, this.timerInitialValue * 1000L);
        }
    }

    void cancelTimer() {
        checkThreadAccess();
        synchronized (fopTimer) {
            if(currentTimer != null) {
                currentTimer.cancel();
                currentTimer = null;
            }
        }
    }

    void setFopSlidingWindow(int fopSlidingWindow) {
        checkThreadAccess();
        this.fopSlidingWindow = fopSlidingWindow;
    }

    void setT1Initial(int t1initial) {
        checkThreadAccess();
        this.timerInitialValue = t1initial;
    }

    void setTransmissionLimit(int limit) {
        checkThreadAccess();
        this.transmissionLimit = limit;
    }

    void setTimeoutType(int type) {
        checkThreadAccess();
        this.timeoutType = type;
    }

    void setAdOutReadyFlag(boolean flag) {
        checkThreadAccess();
        this.adOutReadyFlag = flag;
    }

    void setBcOutReadyFlag(boolean flag) {
        checkThreadAccess();
        this.bcOutReadyFlag = flag;
    }

    void setBdOutReadyFlag(boolean flag) {
        checkThreadAccess();
        this.bdOutReadyFlag = flag;
    }

    void transmitTypeBcFrameUnlock() {
        checkThreadAccess();
        TcTransferFrame frame = bcFrameUnlockFactory.get();
        transmitTypeBcFrame(frame);
    }

    void transmitTypeBcFrameSetVr(int vr) {
        checkThreadAccess();
        TcTransferFrame frame = bcFrameSetVrFactory.apply(vr);
        transmitTypeBcFrame(frame);
    }

    void setSuspendState(int suspendState) {
        checkThreadAccess();
        this.suspendState = suspendState;
    }

    void prepareForSetVr(int vstarr) {
        checkThreadAccess();
        nextVirtualChannelFrameCounterSetter.accept(vstarr);
        this.expectedAckFrameSequenceNumber = vstarr;
    }

    void setVs(int vstars) {
        checkThreadAccess();
        nextVirtualChannelFrameCounterSetter.accept(vstars);
        this.expectedAckFrameSequenceNumber = vstars;
    }

    void registerPendingInitAd(Object directiveTag, FopDirective directiveId, int directiveQualifier) {
        checkThreadAccess();
        this.pendingInitAd.set(new Object[] {directiveTag, directiveId, directiveQualifier});
    }

    void confirmPendingInitAdWithClcw(Clcw clcw) {
        checkThreadAccess();
        Object[] pendingDirective = this.pendingInitAd.getAndSet(null);
        if(pendingDirective != null) {
            // Set the V(S) and NN(R) to the value in the CLCW?
            setVs(clcw.getReportValue());
            confirm(pendingDirective[0], (FopDirective) pendingDirective[1], (int) pendingDirective[2]);
        }
    }

    void confirmPendingInitAdWithBcFrame() {
        checkThreadAccess();
        Object[] pendingDirective = this.pendingInitAd.getAndSet(null);
        if(pendingDirective != null) {
            confirm(pendingDirective[0], (FopDirective) pendingDirective[1], (int) pendingDirective[2]);
        }
    }

    void releaseBcFrame() {
        checkThreadAccess();
        Optional<TransferFrameStatus> optBcFrame = this.sentQueue.stream().filter(o -> o.getFrame().getFrameType() == TcTransferFrame.FrameType.BC).findFirst();
        optBcFrame.ifPresent(transferFrameStatus -> this.sentQueue.remove(transferFrameStatus));
    }

    // ---------------------------------------------------------------------------------------------------------
    // FOP-1 methods performed by the fopExecutor (thread confinement)
    // ---------------------------------------------------------------------------------------------------------

    private void processClcw(Clcw clcw) {
        FopEvent event;
        if(!clcw.isLockoutFlag()) {
            // Lockout == 0
            if(clcw.getReportValue() == nextVirtualChannelFrameCounterGetter.get()) {
                // Valid N(R) and all outstanding type AD frames acknowledged
                if(!clcw.isRetransmitFlag()) {
                    // Retransmit == 0
                    if(!clcw.isWaitFlag()) {
                        // Wait == 0
                        if(clcw.getReportValue() == this.expectedAckFrameSequenceNumber) {
                            // N(R) == NN(R)
                            event = new FopEvent(FopEvent.EventNumber.E1, clcw, this.suspendState);
                        } else {
                            // N(R) != NN(R)
                            event = new FopEvent(FopEvent.EventNumber.E2, clcw, this.suspendState);
                        }
                    } else {
                        // Wait == 1
                        event = new FopEvent(FopEvent.EventNumber.E3, clcw, this.suspendState);
                    }
                } else {
                    // Retransmit == 1
                    event = new FopEvent(FopEvent.EventNumber.E4, clcw, this.suspendState);
                }
            } else if(lessThan(clcw.getReportValue(), nextVirtualChannelFrameCounterGetter.get(), fopSlidingWindow) && (clcw.getReportValue() == this.expectedAckFrameSequenceNumber || greaterThan(clcw.getReportValue(), this.expectedAckFrameSequenceNumber, fopSlidingWindow))) {
                // Valid N(R) and some outstanding type AD frames not yet acknowledged
                if(!clcw.isRetransmitFlag()) {
                    // Retransmit == 0
                    if(!clcw.isWaitFlag()) {
                        // Wait == 0
                        if(clcw.getReportValue() == this.expectedAckFrameSequenceNumber) {
                            // N(R) == NN(R)
                            event = new FopEvent(FopEvent.EventNumber.E5, clcw, this.suspendState);
                        } else {
                            // N(R) != NN(R)
                            event = new FopEvent(FopEvent.EventNumber.E6, clcw, this.suspendState);
                        }
                    } else {
                        // Wait == 1
                        event = new FopEvent(FopEvent.EventNumber.E7, clcw, this.suspendState);
                    }
                } else {
                    // Retransmit == 1
                    if(this.transmissionLimit == 1) {
                        // Transmission limit == 1
                        event = new FopEvent(clcw.getReportValue() != this.expectedAckFrameSequenceNumber ? FopEvent.EventNumber.E101 : FopEvent.EventNumber.E102, clcw, this.suspendState);
                    } else {
                        // Transmission limit > 1 (cannot be <= 0)
                        if(clcw.getReportValue() != this.expectedAckFrameSequenceNumber) {
                            // N(R) != NN(R)
                            event = new FopEvent(!clcw.isWaitFlag() ? FopEvent.EventNumber.E8 : FopEvent.EventNumber.E9, clcw, this.suspendState);
                        } else {
                            // N(R) == NN(R)
                            if(this.transmissionCount < this.transmissionLimit) {
                                // Transmission count < Transmission limit
                                event = new FopEvent(!clcw.isWaitFlag() ? FopEvent.EventNumber.E10 : FopEvent.EventNumber.E11, clcw, this.suspendState);
                            } else {
                                // Transmission count >= Transmission limit
                                event = new FopEvent(!clcw.isWaitFlag() ? FopEvent.EventNumber.E12 : FopEvent.EventNumber.E103, clcw, this.suspendState);
                            }
                        }
                    }
                }
            } else {
                // Invalid N(R)
                event = new FopEvent(FopEvent.EventNumber.E13, clcw, this.suspendState);
            }
        } else {
            // Lockout == 1
            event = new FopEvent(FopEvent.EventNumber.E14, clcw, this.suspendState);
        }
        applyStateTransition(event);
    }

    /**
     * Perform comparison to check if num is less than otherNum mod 256, given the provided window.
     * Basically, the window tells how much otherNum can be greater than num, including wrap around.
     *
     * @param num the first term to compare
     * @param otherNum the second term to compare
     * @param window the window size
     * @return true if num is less than otherNum, otherwise false
     */
    private boolean lessThan(int num, int otherNum, int window) {
        // Expand num to window elements
        Set<Integer> expandedSet = new HashSet<>();
        for(int i = 0; i < window; ++i) {
            expandedSet.add((num + 1 + i) % 256);
        }
        // If otherNum is within the expanded set, return true, else return false
        return expandedSet.contains(otherNum);
    }

    /**
     * Perform comparison to check if num is greater than otherNum mod 256, given the provided window.
     * Basically, the window tells how much num can be greater than otherNum, including wrap around.
     *
     * @param num the first term to compare
     * @param otherNum the second term to compare
     * @param window the window size
     * @return true if num is greater than otherNum, otherwise false
     */
    private boolean greaterThan(int num, int otherNum, int window) {
        // Expand otherNum to window elements
        Set<Integer> expandedSet = new HashSet<>();
        for(int i = 0; i < window; ++i) {
            expandedSet.add((otherNum + 1 + i) % 256);
        }
        // If num is within the expanded set, return true, else return false
        return expandedSet.contains(num);
    }

    private void processTimerExpired() {
        FopEvent event;
        if(this.transmissionCount < this.transmissionLimit) {
            // Transmission count < Transmission limit
            event = new FopEvent(timeoutType == 0 ? FopEvent.EventNumber.E16 : FopEvent.EventNumber.E104, this.suspendState);
        } else {
            // Transmission count >= Transmission limit
            event = new FopEvent(timeoutType == 0 ? FopEvent.EventNumber.E17 : FopEvent.EventNumber.E18, this.suspendState);
        }
        applyStateTransition(event);
    }

    private void processBdFrame(TcTransferFrame frame) {
        FopEvent event;
        if(bdOutReadyFlag) {
            //
            event = new FopEvent(FopEvent.EventNumber.E21, frame, this.suspendState);
        } else {
            //
            event = new FopEvent(FopEvent.EventNumber.E22, frame, this.suspendState);
        }
        applyStateTransition(event);
    }

    private void processAdFrame(TcTransferFrame frame) {
        FopEvent event;
        if(waitQueue.get() == null) {
            // Wait queue empty
            event = new FopEvent(FopEvent.EventNumber.E19, frame, this.suspendState);
        } else {
            // Wait queue not empty
            event = new FopEvent(FopEvent.EventNumber.E20, frame, this.suspendState);
        }
        applyStateTransition(event);
    }

    private void processDirective(Object tag, FopDirective directive, int qualifier) {
        FopEvent event;
        switch(directive) {
            case INIT_AD_WITHOUT_CLCW:
                event = new FopEvent(FopEvent.EventNumber.E23, tag, directive, qualifier, this.suspendState);
                break;
            case INIT_AD_WITH_CLCW:
                event = new FopEvent(FopEvent.EventNumber.E24, tag, directive, qualifier, this.suspendState);
                break;
            case INIT_AD_WITH_UNLOCK:
                event = new FopEvent(bcOutReadyFlag ? FopEvent.EventNumber.E25 : FopEvent.EventNumber.E26, tag, directive, qualifier, this.suspendState);
                break;
            case INIT_AD_WITH_SET_V_R:
                event = new FopEvent(bcOutReadyFlag ? FopEvent.EventNumber.E27 : FopEvent.EventNumber.E28, tag, directive, qualifier, this.suspendState);
                break;
            case TERMINATE:
                event = new FopEvent(FopEvent.EventNumber.E29, tag, directive, qualifier, this.suspendState);
                break;
            case RESUME:
                switch (this.suspendState) {
                    case 0:
                        event = new FopEvent(FopEvent.EventNumber.E30, tag, directive, qualifier, this.suspendState);
                        break;
                    case 1:
                        event = new FopEvent(FopEvent.EventNumber.E31, tag, directive, qualifier, this.suspendState);
                        break;
                    case 2:
                        event = new FopEvent(FopEvent.EventNumber.E32, tag, directive, qualifier, this.suspendState);
                        break;
                    case 3:
                        event = new FopEvent(FopEvent.EventNumber.E33, tag, directive, qualifier, this.suspendState);
                        break;
                    case 4:
                        event = new FopEvent(FopEvent.EventNumber.E34, tag, directive, qualifier, this.suspendState);
                        break;
                    default:
                        throw new IllegalStateException("Suspend state for TC VC " + virtualChannelId + " not supported: " + this.suspendState);
                }
            break;
            case SET_V_S:
                event = new FopEvent(FopEvent.EventNumber.E35, tag, directive, qualifier, this.suspendState);
                break;
            case SET_FOP_SLIDING_WINDOW:
                event = new FopEvent(FopEvent.EventNumber.E36, tag, directive, qualifier, this.suspendState);
                break;
            case SET_T1_INITIAL:
                event = new FopEvent(FopEvent.EventNumber.E37, tag, directive, qualifier, this.suspendState);
                break;
            case SET_TRANSMISSION_LIMIT:
                event = new FopEvent(FopEvent.EventNumber.E38, tag, directive, qualifier, this.suspendState);
                break;
            case SET_TIMEOUT_TYPE:
                event = new FopEvent(FopEvent.EventNumber.E39, tag, directive, qualifier, this.suspendState);
                break;
            default:
                event = new FopEvent(FopEvent.EventNumber.E40, tag, directive, qualifier, this.suspendState);
                break;
        }
        applyStateTransition(event);
    }

    private void processLowerLayer(TcTransferFrame frame, boolean accepted) {
        FopEvent event;
        switch(frame.getFrameType()) {
            case AD:
                event = new FopEvent(accepted ? FopEvent.EventNumber.E41 : FopEvent.EventNumber.E42, frame, this.suspendState);
                break;
            case BC:
                event = new FopEvent(accepted ? FopEvent.EventNumber.E43 : FopEvent.EventNumber.E44, frame, this.suspendState);
                break;
            case BD:
                event = new FopEvent(accepted ? FopEvent.EventNumber.E45 : FopEvent.EventNumber.E46, frame, this.suspendState);
                break;
            default:
                throw new IllegalArgumentException("Frame type " + frame.getFrameType() + " not supported");
        }
        applyStateTransition(event);
    }

    private void reportStatus(FopState previousState, FopState currentState, FopEvent.EventNumber number) {
        FopStatus status = new FopStatus(expectedAckFrameSequenceNumber, sentQueue.size(), waitQueue.get() != null, adOutReadyFlag, bcOutReadyFlag, bdOutReadyFlag, previousState, currentState, number);
        observers.forEach(o -> o.statusReport(status));
    }

    private void applyStateTransition(FopEvent event) {
        FopState previousState = this.state.getState();
        this.state = this.state.event(event);
        FopState currentState = this.state.getState();
        reportStatus(previousState, currentState, event.getNumber());
    }

    // ---------------------------------------------------------------------------------------------------------
    // FOP members to interact with low level output executed by the lowLevelExecutor (thread confinement)
    // ---------------------------------------------------------------------------------------------------------

    private void forwardToOutput(TcTransferFrame frame) {
        boolean result = false;
        if(output != null) {
            result = output.apply(frame);
        }
        lowerLayer(frame, result);
    }

    private void lowerLayer(TcTransferFrame frame, boolean accepted) {
        fopExecutor.execute(() -> processLowerLayer(frame, accepted));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Other members
    // ---------------------------------------------------------------------------------------------------------

    private void checkThreadAccess() {
        if(Thread.currentThread() != this.confinementThread) {
            throw new IllegalAccessError("Violation on thread confinement for class FopEngine: method can only be accessed by thread " + this.confinementThread.getName());
        }
    }

    private void timerExpired(TimerTask expiredTask) {
        synchronized (fopTimer) {
            if(currentTimer == expiredTask) {
                fopExecutor.execute(this::processTimerExpired);
            }
        }
    }

    private static class TransferFrameStatus {
        private final TcTransferFrame frame;
        private boolean toBeRetransmitted;

        public TransferFrameStatus(TcTransferFrame frame) {
            this.frame = frame;
            this.toBeRetransmitted = false;
        }

        public void setToBeRetransmitted(boolean toBeRetransmitted) {
            this.toBeRetransmitted = toBeRetransmitted;
        }

        public boolean isToBeRetransmitted() {
            return toBeRetransmitted;
        }

        public TcTransferFrame getFrame() {
            return frame;
        }
    }
}
