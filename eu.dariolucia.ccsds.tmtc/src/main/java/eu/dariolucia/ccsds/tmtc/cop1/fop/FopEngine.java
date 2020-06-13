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

import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.AbstractSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.IVirtualChannelSenderOutput;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.TcSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.ocf.pdu.Clcw;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * This class implements the FOP side of the COP-1 protocol, as defined by CCSDS 232.1-B-2 Cor. 1.
 */
public class FopEngine implements IVirtualChannelSenderOutput<TcTransferFrame> {

    private final TcSenderVirtualChannel tcVc;

    private final ExecutorService fopExecutor;

    private Consumer<TcTransferFrame> output;

    // FOP variables as per CCSDS 232.1-B-2 Cor. 1, section 5.1

    /**
     * This variable represents the state of FOP-1 for the specific Virtual Channel.
     */
    private AbstractFopState state;
    /**
     * The Transmitter_Frame_Sequence_Number, V(S), contains the value of the Frame Sequence
     * Number, N(S), to be put in the Transfer Frame Primary Header of the next Type-AD
     * Transfer Frame to be transmitted.
     * In this implementation, this information is read from the transmitted frame of Type-AD
     */
    private int transmitterFrameSequenceNumber; // V(S)
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
     * Whether or not a ‘Transmit Request for Frame’ is outstanding for AD.
     */
    private boolean adOutReadyFlag = false;
    /**
     * Whether or not a ‘Transmit Request for Frame’ is outstanding for BD.
     */
    private boolean bdOutReadyFlag = false;
    /**
     * Whether or not a ‘Transmit Request for Frame’ is outstanding for BC.
     */
    private boolean bcOutReadyFlag = false;
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
    private int timeoutType;
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
    private int suspendState;
    /**
     * The FOP Sliding Window is a mechanism which limits the number of Transfer Frames which
     * can be transmitted ahead of the last acknowledged Transfer Frame, i.e., before a CLCW
     * report is received which updates the status of acknowledged Transfer Frames. This is done
     * to prevent sending a new Transfer Frame with the same sequence number as a rejected
     * Transfer Frame.
     */
    private int fopSlidingWindow;

    public FopEngine(TcSenderVirtualChannel linkedTcVc) {
        this.tcVc = linkedTcVc;
        this.tcVc.register(this);
        this.fopExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("FOP Entity for TC VC " + this.tcVc.getVirtualChannelId());
            return t;
        });
        //
        this.state = new S6FopState(this); // In principle, the ‘Initial’ State is the first state entered by the state machine for a particular Virtual Channel.
    }

    public void setOutput(Consumer<TcTransferFrame> output) {
        this.output = output;
    }

    // ---------------------------------------------------------------------------------------------------------
    // FOP-1 public operations as per CCSDS definition for event injection
    // ---------------------------------------------------------------------------------------------------------

    public void directive(Object tag, FopDirective directive, int qualifier) {
        // TODO
    }

    public void transmit(TcTransferFrame frame) {
        switch(frame.getFrameType()) {
            case AD: {
                fopExecutor.execute(() -> processAdFrame(frame));
            }
            break;
            case BC: {
                // Direct output
                output.accept(frame);
            }
            break;
            case BD: {
                fopExecutor.execute(() -> processBdFrame(frame));
            }
            break;
            default:
                throw new IllegalArgumentException("TC Transfer Frame has unsupported type: " + frame.getFrameType());
        }
    }

    public void channelResponse(TcTransferFrame frame, boolean accepted) {

    }

    public void timerExpired() {
        fopExecutor.execute(() -> processTimerExpired());
    }

    public void clcw(Clcw clcw) {
        if(clcw.getCopInEffect() == Clcw.CopEffectType.COP1 && clcw.getVirtualChannelId() == tcVc.getVirtualChannelId()) {
            fopExecutor.execute(() -> processClcw(clcw));
        }
    }

    public void abort() {
        // TODO
    }

    // ---------------------------------------------------------------------------------------------------------
    // FOP-1 actions defined as per CCSDS definition
    // ---------------------------------------------------------------------------------------------------------

    void purgeSentQueue() {

    }

    void purgeWaitQueue() {

    }

    void transmitTypeAdFrame(TcTransferFrame frame) {

    }

    void transmitTypeBdFrame(TcTransferFrame frame) {

    }

    void initiateRetransmission() {

    }

    void removeAckFramesFromSentQueue() {

    }

    void lookForDirective() {

    }

    void lookForFrame(FopEvent fopEvent) {

    }

    void addToWaitQueue(FopEvent fopEvent) {

    }

    void confirm(FopOperationStatus status, FopEvent event) {

    }

    void initialise() {

    }

    void alert(FopAlertCode code) {

    }

    void suspend(int suspendState) {

    }

    void resume() {

    }

    void restartTimer() {

    }

    void cancelTimer() {

    }

    void reject(FopEvent event) {

    }

    void setFopSlidingWindow(int fopSlidingWindow) {
        this.fopSlidingWindow = fopSlidingWindow;
    }


    public void setT1Initial(int t1initial) {
        this.timerInitialValue = t1initial;
    }


    public void setTransmissionLimit(int limit) {
        this.transmissionLimit = limit;
    }

    public void setTimeoutType(int type) {
        this.timeoutType = type;
    }


    public void accept(FopEvent fopEvent) {

    }

    public void setAdOutReadyFlag(boolean flag) {
        this.adOutReadyFlag = flag;
    }

    public void setBcOutReadyFlag(boolean flag) {
        this.bcOutReadyFlag = flag;
    }

    public void setBdOutReadyFlag(boolean flag) {
        this.bdOutReadyFlag = flag;
    }

    public void transmitTypeBcFrameUnlock() {
        this.tcVc.dispatchUnlock();
    }

    public void transmitTypeBcFrameSetVr(int vr) {
        this.tcVc.dispatchSetVr(vr);
    }

    public void prepareForSetVr(int vr) {

    }

    public void setVs(int vs) {

    }

    // ---------------------------------------------------------------------------------------------------------
    // FOP-1 class methods performed by the fopExecutor (thread confinement)
    // ---------------------------------------------------------------------------------------------------------

    private void processClcw(Clcw clcw) {
        FopEvent event;
        if(!clcw.isLockoutFlag()) {
            // Lockout == 0
            if(clcw.getReportValue() == this.transmitterFrameSequenceNumber) {
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
            } else if(clcw.getReportValue() < this.transmitterFrameSequenceNumber && clcw.getReportValue() >= this.expectedAckFrameSequenceNumber) {
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
                        if(clcw.getReportValue() != this.expectedAckFrameSequenceNumber) {
                            // N(R) != NN(R)
                            event = new FopEvent(FopEvent.EventNumber.E101, clcw, this.suspendState);
                        } else {
                            // N(R) == NN(R)
                            event = new FopEvent(FopEvent.EventNumber.E102, clcw, this.suspendState);
                        }
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
        this.state = this.state.event(event);
    }

    private void processTimerExpired() {
        FopEvent event;
        if(this.transmissionCount < this.transmissionLimit) {
            // Transmission count < Transmission limit
            event = new FopEvent(timeoutType == 0 ? FopEvent.EventNumber.E16 : FopEvent.EventNumber.E104, true, this.suspendState);
        } else {
            // Transmission count >= Transmission limit
            event = new FopEvent(timeoutType == 0 ? FopEvent.EventNumber.E17 : FopEvent.EventNumber.E18, true, this.suspendState);
        }
        this.state = this.state.event(event);
    }

    private void processBdFrame(TcTransferFrame frame) {
        // TODO
    }

    private void processAdFrame(TcTransferFrame frame) {
        // TODO
    }

    // ---------------------------------------------------------------------------------------------------------
    // Other members
    // ---------------------------------------------------------------------------------------------------------

    @Override
    public void transferFrameGenerated(AbstractSenderVirtualChannel<TcTransferFrame> vc, TcTransferFrame generatedFrame, int bufferedBytes) {
        transmit(generatedFrame);
    }

    public void dispose() {
        this.tcVc.deregister(this);
        this.fopExecutor.shutdownNow();
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
    }
}
