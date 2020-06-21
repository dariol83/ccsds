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

package eu.dariolucia.ccsds.tmtc.cop1.farm;

import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.TcReceiverVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.ocf.builder.ClcwBuilder;
import eu.dariolucia.ccsds.tmtc.ocf.pdu.Clcw;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This class implements the FARM side of the COP-1 protocol, as defined by CCSDS 232.1-B-2 Cor. 1.
 */
public class FarmEngine implements Supplier<Clcw> {

    private final int virtualChannelId;

    private final Consumer<TcTransferFrame> output;

    private final ExecutorService farmExecutor;

    private final ExecutorService highLevelExecutor;

    private volatile Thread confinementThread; // NOSONAR only used for thread equality

    private final BlockingQueue<TcTransferFrame> framesToDeliver;

    private final List<IFarmObserver> observers = new CopyOnWriteArrayList<>();

    /**
     * This flag indicates whether the FARM shall behave as per 6.1.8.3.1 or as per 6.1.8.3.2.
     */
    private final boolean retransmissionAllowed;

    private final ClcwBuilder clcwBuilder;

    // ---------------------------------------------------------------------------------------------------------
    // FARM variables as per CCSDS 232.1-B-2 Cor. 1, section 6.1
    // ---------------------------------------------------------------------------------------------------------

    /**
     * This variable represents the state of FARM-1 for the specific Virtual Channel.
     */
    private AbstractFarmState state;

    /**
     * The Retransmit_Flag is set to '1' whenever the state machine knows that a Type-AD
     * Transfer Frame has been lost in transmission or has been discarded because there was no
     * buffer space available; otherwise, it is '0'.
     */
    private boolean retransmitFlag;

    /**
     * This variable is incremented whenever a valid Type-BD or Type-BC Transfer Frame arrives.
     */
    private long farmBCounter;

    /**
     * This variable records the value of N(S) expected to be seen in the next Type-AD Transfer
     * Frame on this Virtual Channel.
     */
    private int receiverFrameSequenceNumber; // V(R)

    private final int farmSlidingWindowWidth; // W

    private final LinkedHashSet<Integer> positiveWindow = new LinkedHashSet<>();
    private final LinkedHashSet<Integer> negativeWindow = new LinkedHashSet<>();

    // These variable must be immediately reflected in the next CLCW
    private volatile int statusField;
    private volatile boolean noBitLockFlag = true; // No bit lock at construction
    private volatile boolean noRfAvailableFlag = true; // No RF at construction
    private volatile int reservedSpare;

    public FarmEngine(TcReceiverVirtualChannel linkedTcVc, boolean retransmissionAllowed, int bufferSize, int farmSlidingWindowWidth) {
        this(linkedTcVc.getVirtualChannelId(), linkedTcVc, retransmissionAllowed, bufferSize, farmSlidingWindowWidth, FarmState.S3, 0);
    }

    public FarmEngine(int virtualChannelId, Consumer<TcTransferFrame> output, boolean retransmissionAllowed, int bufferSize, int farmSlidingWindowWidth, FarmState initialState, int initialReceiverFrameSequenceNumber) {
        if(retransmissionAllowed && (farmSlidingWindowWidth < 2 || farmSlidingWindowWidth > 254)) { // 6.1.8.2
            throw new IllegalArgumentException("If retransmission is allowed, farmSlidingWindowWidth must be within 2 and 254 (included)");
        }
        if(retransmissionAllowed && farmSlidingWindowWidth % 2 != 0) { // 6.1.8.2
            throw new IllegalArgumentException("If retransmission is allowed, farmSlidingWindowWidth must be an EVEN number");
        }
        if(!retransmissionAllowed && farmSlidingWindowWidth > 255) { // 6.1.8.3.2
            throw new IllegalArgumentException("If retransmission is not allowed, farmSlidingWindowWidth must not exceed 255");
        }
        this.output = output;
        this.virtualChannelId = virtualChannelId;
        this.retransmissionAllowed = retransmissionAllowed;
        this.farmSlidingWindowWidth = farmSlidingWindowWidth;
        this.farmExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("FARM Entity Processor for TC VC " + virtualChannelId);
            FarmEngine.this.confinementThread = t;
            return t;
        });
        this.framesToDeliver = new ArrayBlockingQueue<>(bufferSize);
        this.highLevelExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("FARM Entity High Level for TC VC " + virtualChannelId);
            return t;
        });
        this.highLevelExecutor.execute(this::deliverFrames);
        //
        switch (initialState) {
            case S1:
                this.state = new S1FarmState(this);
                break;
            case S2:
                this.state = new S2FarmState(this);
                break;
            default:
                this.state = new S3FarmState(this); // The FARM starts in Lockout state
                break;
        }
        this.receiverFrameSequenceNumber = initialReceiverFrameSequenceNumber % 256;
        if(this.receiverFrameSequenceNumber < 0) {
            this.receiverFrameSequenceNumber += 256;
        }
        // Prepare the builder
        this.clcwBuilder = ClcwBuilder.create().setVirtualChannelId(virtualChannelId);
        try {
            // Set the initial VR to 0
            this.farmExecutor.submit(() -> setVr(this.receiverFrameSequenceNumber)).get();
            // Generate the first CLCW state based on the initialised state
            this.farmExecutor.submit(this::processReport).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Exception while creating first CLCW during initialisation", e);
        }
    }

    public void register(IFarmObserver observer) {
        this.observers.add(observer);
    }

    public void deregister(IFarmObserver observer) {
        this.observers.remove(observer);
    }

    private void deliverFrames() {
        while(!highLevelExecutor.isShutdown()) {
            try {
                TcTransferFrame frameToDeliver = this.framesToDeliver.take();
                farmExecutor.execute(this::processBufferRelease);
                if(!highLevelExecutor.isShutdown()) {
                    this.output.accept(frameToDeliver);
                }
            } catch(InterruptedException e) {
                // Nothing, about to be disposed
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // FARM-1 public operations as per CCSDS definition for event injection
    // ---------------------------------------------------------------------------------------------------------

    public void frameArrived(TcTransferFrame frame) {
        if(frame.getVirtualChannelId() != virtualChannelId) {
            throw new IllegalArgumentException("Injected TC frame does not match expected VC ID, expected " + virtualChannelId + ", arrived " + frame.getVirtualChannelId());
        }
        farmExecutor.execute(() -> processFrame(frame));
    }

    public void setStatusField(int statusField) {
        if(statusField < 0 || statusField > 7) {
            throw new IllegalArgumentException("Status field must be between 0 and 7 (inclusive), got " + statusField);
        }
        this.statusField = statusField;
    }

    public void setNoBitLockFlag(boolean noBitLockFlag) {
        this.noBitLockFlag = noBitLockFlag;
    }

    public void setNoRfAvailableFlag(boolean noRfAvailableFlag) {
        this.noRfAvailableFlag = noRfAvailableFlag;
    }

    public void setReservedSpare(int reservedSpare) {
        if(reservedSpare < 0 || reservedSpare > 3) {
            throw new IllegalArgumentException("Reserved Spare must be between 0 and 3 (inclusive), got " + reservedSpare);
        }
        this.reservedSpare = reservedSpare;
    }

    // ---------------------------------------------------------------------------------------------------------
    // FARM-1 actions defined as per CCSDS definition. All these actions are performed by the state transitions
    // and executed by the farmExecutor service. Thread access is enforced to avoid misuse.
    // ---------------------------------------------------------------------------------------------------------

    void accept(TcTransferFrame frame) {
        checkThreadAccess();
        boolean inBuffer = this.framesToDeliver.offer(frame);
        if(!inBuffer) {
            // State machine problem
            throw new IllegalStateException("FARM buffer full but frame nevertheless accepted");
        }
    }

    void discard(TcTransferFrame frame) { // NOSONAR part of the standard
        checkThreadAccess();
        // Discard
    }

    void increaseVr() {
        checkThreadAccess();
        setVr((this.receiverFrameSequenceNumber + 1) % 256);
    }

    void resetRetransmitFlag() {
        checkThreadAccess();
        this.retransmitFlag = false;
    }

    void setRetransmitFlag() {
        checkThreadAccess();
        this.retransmitFlag = true;
    }

    void increaseFarmB() {
        checkThreadAccess();
        this.farmBCounter++;
    }

    void setVr(int setVrValue) {
        checkThreadAccess();
        this.receiverFrameSequenceNumber = setVrValue;
        this.positiveWindow.clear();
        this.negativeWindow.clear();
        if(retransmissionAllowed) {
            // Use approach as per 6.1.8.3.1
            for (int i = 0; i < this.farmSlidingWindowWidth / 2 - 1; ++i) {
                this.positiveWindow.add((this.receiverFrameSequenceNumber + 1 + i) % 256);
            }
            for (int i = 0; i < this.farmSlidingWindowWidth / 2; ++i) {
                int pos = this.receiverFrameSequenceNumber - 1 - i;
                if (pos < 0) {
                    pos += 256;
                }
                this.negativeWindow.add(pos);
            }
        } else {
            // Use approach as per 6.1.8.3.2, with PW = W
            for (int i = 0; i < this.farmSlidingWindowWidth; ++i) {
                this.positiveWindow.add((this.receiverFrameSequenceNumber + 1 + i) % 256);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // FARM-1 methods performed by the farmExecutor (thread confinement)
    // ---------------------------------------------------------------------------------------------------------

    private void processFrame(TcTransferFrame frame) {
        FarmEvent event;
        if(frame.getFrameType() == TcTransferFrame.FrameType.AD) {
            if(frame.getVirtualChannelFrameCount() == this.receiverFrameSequenceNumber) {
                if(this.framesToDeliver.remainingCapacity() > 0) {
                    event = new FarmEvent(FarmEvent.EventNumber.E1, frame);
                } else {
                    event = new FarmEvent(FarmEvent.EventNumber.E2, frame);
                }
            } else if(insidePositivePart(frame)) {
                event = new FarmEvent(FarmEvent.EventNumber.E3, frame);
            } else if(insideNegativePart(frame)) {
                event = new FarmEvent(FarmEvent.EventNumber.E4, frame);
            } else {
                event = new FarmEvent(FarmEvent.EventNumber.E5, frame);
            }
        } else if(frame.getFrameType() == TcTransferFrame.FrameType.BD) {
            event = new FarmEvent(FarmEvent.EventNumber.E6, frame);
        } else if(frame.getFrameType() == TcTransferFrame.FrameType.BC) {
            if(frame.getControlCommandType() == TcTransferFrame.ControlCommandType.UNLOCK) {
                event = new FarmEvent(FarmEvent.EventNumber.E7, frame);
            } else if(frame.getControlCommandType() == TcTransferFrame.ControlCommandType.SET_VR) {
                event = new FarmEvent(FarmEvent.EventNumber.E8, frame);
            } else {
                event = new FarmEvent(FarmEvent.EventNumber.E9, frame);
            }
        } else {
            event = new FarmEvent(FarmEvent.EventNumber.E9, frame);
        }
        applyStateTransition(event);
    }

    private boolean insideNegativePart(TcTransferFrame frame) {
        return negativeWindow.contains(frame.getVirtualChannelFrameCount());
    }

    private boolean insidePositivePart(TcTransferFrame frame) {
        return positiveWindow.contains(frame.getVirtualChannelFrameCount());
    }

    private void processBufferRelease() {
        FarmEvent event = new FarmEvent(FarmEvent.EventNumber.E10);
        applyStateTransition(event);
    }

    private Clcw processReport() {
        this.clcwBuilder.setFarmBCounter((int) (farmBCounter % 4));
        this.clcwBuilder.setRetransmitFlag(retransmitFlag);
        this.clcwBuilder.setCopInEffect(true);
        this.clcwBuilder.setLockoutFlag(state instanceof S3FarmState);
        this.clcwBuilder.setWaitFlag(state instanceof S2FarmState);
        this.clcwBuilder.setReportValue(receiverFrameSequenceNumber);
        this.clcwBuilder.setStatusField(statusField);
        this.clcwBuilder.setNoBitlockFlag(noBitLockFlag);
        this.clcwBuilder.setNoRfAvailableFlag(noRfAvailableFlag);
        this.clcwBuilder.setReservedSpare(reservedSpare);
        return this.clcwBuilder.build();
    }

    private void reportStatus(FarmState previousState, FarmState currentState, FarmEvent.EventNumber number) {
        FarmStatus status = new FarmStatus(this.framesToDeliver.size(), processReport(), previousState, currentState, number);
        observers.forEach(o -> o.statusReport(status));
    }

    private void applyStateTransition(FarmEvent event) {
        FarmState previousState = this.state.getState();
        this.state = this.state.event(event);
        FarmState currentState = this.state.getState();
        reportStatus(previousState, currentState, event.getNumber());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Other members
    // ---------------------------------------------------------------------------------------------------------

    private void checkThreadAccess() {
        if(Thread.currentThread() != this.confinementThread) {
            throw new IllegalAccessError("Violation on thread confinement for class FarmEngine: method can only be accessed by thread " + this.confinementThread.getName());
        }
    }

    public void dispose() {
        this.farmExecutor.shutdownNow();
        this.highLevelExecutor.shutdownNow();
    }

    /**
     * Generate and return the information to be placed in the CLCW based on the current status of FARM-1.
     *
     * @return the CLCW
     * @throws IllegalStateException in case of interruptions or error in executing the construction of the CLCW
     */
    @Override
    public Clcw get() {
        try {
            return farmExecutor.submit(this::processReport).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Exception while retrieving CLCW from FARM for TC VC ID " + virtualChannelId, e);
        }
    }
}
