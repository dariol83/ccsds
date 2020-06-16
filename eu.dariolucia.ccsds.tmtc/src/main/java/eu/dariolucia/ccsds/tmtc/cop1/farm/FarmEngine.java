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

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * This class implements the FARM side of the COP-1 protocol, as defined by CCSDS 232.1-B-2 Cor. 1.
 */
public class FarmEngine implements Supplier<Clcw> {

    private final TcReceiverVirtualChannel tcVc;

    private final ExecutorService farmExecutor;

    private final ExecutorService highLevelExecutor;

    private volatile Thread confinementThread;

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

    private int farmSlidingWindowWidth; // W

    private int farmPositiveWindowWidth; // PW

    private int farmNegativeWindowWidth; // NW

    public FarmEngine(TcReceiverVirtualChannel linkedTcVc, boolean retransmissionAllowed, int bufferSize) {
        this.retransmissionAllowed = retransmissionAllowed;
        this.tcVc = linkedTcVc;
        this.farmExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("FARM Entity Processor for TC VC " + this.tcVc.getVirtualChannelId());
            FarmEngine.this.confinementThread = t;
            return t;
        });
        this.framesToDeliver = new ArrayBlockingQueue<>(bufferSize);
        this.highLevelExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("FARM Entity High Level for TC VC " + this.tcVc.getVirtualChannelId());
            return t;
        });
        this.highLevelExecutor.submit(this::deliverFrames);
        //
        this.state = new S3FarmState(this); // The FARM starts in Lockout state
        // Generate the first CLCW state based on the initialised state
        // Prepare the builder
        this.clcwBuilder = ClcwBuilder.create().setVirtualChannelId(tcVc.getVirtualChannelId());
        try {
            this.farmExecutor.submit(this::report).wait();
        } catch (InterruptedException e) {
            // TODO
            e.printStackTrace();
        }
    }

    private void deliverFrames() {
        while(!farmExecutor.isShutdown()) {
            try {
                TcTransferFrame frameToDeliver = this.framesToDeliver.take();
                this.tcVc.accept(frameToDeliver);
            } catch(InterruptedException e) {
                // TODO
                e.printStackTrace();
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // FARM-1 public operations as per CCSDS definition for event injection
    // ---------------------------------------------------------------------------------------------------------

    public void frameArrived(TcTransferFrame frame) {
        if(frame.getVirtualChannelId() != tcVc.getVirtualChannelId()) {
            throw new IllegalArgumentException("Injected TC frame does not match expected VC ID, expected " + tcVc.getVirtualChannelId() + ", arrived " + frame.getVirtualChannelId());
        }
        farmExecutor.execute(() -> processFrame(frame));
    }

    // ---------------------------------------------------------------------------------------------------------
    // FARM-1 actions defined as per CCSDS definition. All these actions are performed by the state transitions
    // and executed by the farmExecutor service. Thread access is enforced to avoid misuse.
    // ---------------------------------------------------------------------------------------------------------

    void accept(TcTransferFrame frame) {
        // TODO
    }

    void discard(TcTransferFrame frame) {
        // Discard
    }

    // ---------------------------------------------------------------------------------------------------------
    // FARM-1 methods performed by the farmExecutor (thread confinement)
    // ---------------------------------------------------------------------------------------------------------

    private void processFrame(TcTransferFrame frame) {
        FarmEvent event;
        if(frame.getFrameType() == TcTransferFrame.FrameType.AD) {
            if(frame.getVirtualChannelFrameCount() == this.receiverFrameSequenceNumber) {

            }
            // TODO resume from here
        } else if(frame.getFrameType() == TcTransferFrame.FrameType.BD) {

        } else if(frame.getFrameType() == TcTransferFrame.FrameType.BC) {

        } else {
            event = new FarmEvent(FarmEvent.EventNumber.E9, null);
        }
        applyStateTransition(event);
    }

    private void processBufferRelease() {
        FarmEvent event = new FarmEvent(FarmEvent.EventNumber.E10);
        applyStateTransition(event);
    }

    private Clcw report() {
        // TODO
        return this.clcwBuilder.build();
    }

    private void applyStateTransition(FarmEvent event) {
        this.state = this.state.event(event);
    }

    /**
     * Generate and return the information to be placed in the CLCW based on the current status of FARM-1.
     */
    @Override
    public Clcw get() {
        try {
            return farmExecutor.submit(this::report).get();
        } catch (InterruptedException e) {
            // TODO
            e.printStackTrace();
        } catch (ExecutionException e) {
            // TODO
            e.printStackTrace();
        }
        return null;
    }
}
