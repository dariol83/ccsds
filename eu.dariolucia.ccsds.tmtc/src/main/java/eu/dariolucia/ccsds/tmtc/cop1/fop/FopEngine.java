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
    private FopState state;
    /**
     * The Transmitter_Frame_Sequence_Number, V(S), contains the value of the Frame Sequence
     * Number, N(S), to be put in the Transfer Frame Primary Header of the next Type-AD
     * Transfer Frame to be transmitted.
     * In this implementation, this information is read from the transmitted frame of Type-AD
     */
    private Integer transmitterFrameSequenceNumber; // V(S)
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

    private boolean adOutFlag = false;
    private boolean bdOutFlag = false;
    private boolean bcOutFlag = false;

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
    private int timeInitialValue;

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
        this.state = FopState.S6; // In principle, the ‘Initial’ State is the first state entered by the state machine for a particular Virtual Channel.
    }

    public void setOutput(Consumer<TcTransferFrame> output) {
        this.output = output;
    }

    public void directive(Object tag, FopDirective directive, int qualifier) {
        // TODO
    }

    public void transmit(TcTransferFrame frame) {
        // TODO
    }

    public void abort() {
        // TODO
    }

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
