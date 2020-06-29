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

import eu.dariolucia.ccsds.tmtc.cop1.fop.util.BcFrameCollector;
import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.TcSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.util.TransferFrameCollector;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.ocf.builder.ClcwBuilder;
import eu.dariolucia.ccsds.tmtc.ocf.pdu.Clcw;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FopEngineTest {

    @Test
    public void testAdFrameWaitQueueReject() throws InterruptedException {
        // Test sink
        Function<TcTransferFrame, Boolean> sink = o -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // Ignore
            }
            return true;
        };
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 3);
        fop.directive(5, FopDirective.INIT_AD_WITH_CLCW, 0);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S4, 5000));

        fop.clcw(clcw);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        // Send some frame and (later) check that it is sent with VCC 10
        tcVc.dispatch(true, 0, new byte[200]);
        TcTransferFrame frame = collector.retrieveFirst(true);
        fop.transmit(frame); // 0
        tcVc.dispatch(true, 0, new byte[200]);
        TcTransferFrame frame2 = collector.retrieveFirst(true);
        fop.transmit(frame2); // 1

        tcVc.dispatch(true, 0, new byte[200]);
        TcTransferFrame frame3 = collector.retrieveFirst(true);
        fop.transmit(frame3); // 2

        assertTrue(stub.waitForFrame(o -> o[0] == FopOperationStatus.REJECT_RESPONSE && o[1] == frame3, 5000));

        Thread.sleep(5000);

        fop.directive(6, FopDirective.TERMINATE, 0);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 6), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testBdFrameWaitQueueReject() throws InterruptedException {
        // Test sink
        Function<TcTransferFrame, Boolean> sink = o -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // Ignore
            }
            return true;
        };
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 3);
        fop.directive(5, FopDirective.INIT_AD_WITH_CLCW, 0);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S4, 5000));

        fop.clcw(clcw);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        // Send some frame and (later) check that it is sent with VCC 10
        tcVc.dispatch(false, 0, new byte[200]);
        TcTransferFrame frame = collector.retrieveFirst(true);
        fop.transmit(frame); // 0
        tcVc.dispatch(false, 0, new byte[200]);
        TcTransferFrame frame2 = collector.retrieveFirst(true);
        fop.transmit(frame2); // 1
        tcVc.dispatch(false, 0, new byte[200]);
        TcTransferFrame frame3 = collector.retrieveFirst(true);
        fop.transmit(frame3); // 2

        assertTrue(stub.waitForFrame(o -> o[0] == FopOperationStatus.REJECT_RESPONSE && o[1] == frame3, 5000));

        Thread.sleep(5000);

        fop.directive(6, FopDirective.TERMINATE, 0);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 6), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testInitAdWithClcw() throws InterruptedException {
        // Test sink
        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink::add);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 3);
        fop.directive(5, FopDirective.INIT_AD_WITH_CLCW, 0);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S4, 5000));

        fop.clcw(clcw);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        // Send some frame and (later) check that it is sent with VCC 10
        tcVc.dispatch(true, 0, new byte[200]);
        TcTransferFrame frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 0
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 1
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 2
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 3

        // Inform arrival of 0 and 1, activate retransmission
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(true)
                .setReportValue(2)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        // Wait 5 seconds in total to fail also the second transmission
        Thread.sleep(2000);
        fop.clcw(clcw);
        Thread.sleep(3000);

        // Inform arrival of 2 and 3, clear retransmission
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(4)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        fop.directive(6, FopDirective.TERMINATE, 0);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 6), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));
        Thread.sleep(2000);
        assertEquals(8, sink.size());

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testInitAdWithClcwRetransmissionLimit() throws InterruptedException {
        // Test sink
        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink::add);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 3);
        fop.directive(5, FopDirective.INIT_AD_WITH_CLCW, 0);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S4, 5000));

        fop.clcw(clcw);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        // Send some frame and (later) check that it is sent with VCC 10
        tcVc.dispatch(true, 0, new byte[200]);
        TcTransferFrame frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 0
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 1
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 2
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 3

        // Inform arrival of 0 and 1, activate retransmission
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(true)
                .setReportValue(2)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        // Wait 5 seconds in total to fail also the second transmission
        Thread.sleep(2000);
        fop.clcw(clcw);
        Thread.sleep(3000);

        // Fail also the third
        fop.clcw(clcw);
        Thread.sleep(5000);

        assertTrue(stub.waitForAlert(FopAlertCode.T1, 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));
        Thread.sleep(2000);
        assertEquals(8, sink.size());

        fop.deregister(stub);
        fop.dispose();
    }


    @Test
    public void testS1RetransmissionLimitSuspend() throws InterruptedException {
        // Test sink
        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink::add);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 1);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 1);
        fop.directive(5, FopDirective.INIT_AD_WITH_CLCW, 0);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S4, 5000));

        fop.clcw(clcw);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        // Send some frame and (later) check that it is sent with VCC 10
        tcVc.dispatch(true, 0, new byte[200]);
        TcTransferFrame frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 0
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 1
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 2
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 3

        // Wait 5 seconds in total to go to timeout
        Thread.sleep(5000);

        // Move to S6 - Suspend
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));

        // Send RESUME
        fop.directive(8, FopDirective.RESUME, 0);
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        // Send new CLCW
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(4)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        fop.directive(6, FopDirective.TERMINATE, 0);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 6), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));
        Thread.sleep(2000);
        assertEquals(4, sink.size());

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testS2RetransmissionLimitSuspend() throws InterruptedException {
        // Test sink
        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink::add);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 1);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 3);
        fop.directive(5, FopDirective.INIT_AD_WITH_CLCW, 0);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S4, 5000));

        fop.clcw(clcw);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        // Send some frame and (later) check that it is sent with VCC 10
        tcVc.dispatch(true, 0, new byte[200]);
        TcTransferFrame frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 0
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 1
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 2
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 3

        // Inform arrival of 0 and 1, activate retransmission
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(true)
                .setReportValue(2)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        // Wait 5 seconds in total to fail also the second transmission
        Thread.sleep(2000);
        fop.clcw(clcw);
        Thread.sleep(3000);

        // Fail also the third
        fop.clcw(clcw);
        Thread.sleep(5000);

        // Move to S6 - Suspend
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));

        // Send RESUME
        fop.directive(8, FopDirective.RESUME, 0);
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S2, 5000));

        // Send new CLCW
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(4)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        fop.directive(6, FopDirective.TERMINATE, 0);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 6), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));
        Thread.sleep(2000);
        assertEquals(8, sink.size());

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testS4RetransmissionLimitSuspend() throws InterruptedException {
        // Test sink
        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink::add);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 1);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 1);
        fop.directive(5, FopDirective.INIT_AD_WITH_CLCW, 0);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S4, 5000));

        Thread.sleep(5000);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));

        fop.clcw(clcw);

        // Send RESUME
        fop.directive(8, FopDirective.RESUME, 0);
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S4, 5000));

        // Send new CLCW
        fop.clcw(clcw);
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        fop.directive(6, FopDirective.TERMINATE, 0);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 6), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testInitAdWithClcwTransmissionLimit() throws InterruptedException {
        // Test sink
        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink::add);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 1);
        fop.directive(5, FopDirective.INIT_AD_WITH_CLCW, 0);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S4, 5000));

        fop.clcw(clcw);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        // Send some frame and (later) check that it is sent with VCC 10
        tcVc.dispatch(true, 0, new byte[200]);
        TcTransferFrame frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 0
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 1
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 2
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 3

        // Inform arrival of 0 and 1, activate retransmission
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(true)
                .setReportValue(2)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        assertTrue(stub.waitForAlert(FopAlertCode.LIMIT, 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testAdReject() throws InterruptedException {
        // Test sink: takes one second to force race condition
        Function<TcTransferFrame, Boolean> sink = o -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                //
            }
            return false;
        };
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 3);
        fop.directive(5, FopDirective.INIT_AD_WITH_CLCW, 0);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S4, 5000));

        fop.clcw(clcw);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        // Send some frame and (later) check that it is sent with VCC 10
        tcVc.dispatch(true, 0, new byte[200]);
        TcTransferFrame frame = collector.retrieveFirst(true);
        fop.transmit(frame);
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame);

        assertTrue(stub.waitForAlert(FopAlertCode.LLIF, 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testInitAdWithClcwWaitTransition() throws InterruptedException {
        // Test sink
        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink::add);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 3);
        fop.directive(5, FopDirective.INIT_AD_WITH_CLCW, 0);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S4, 5000));

        fop.clcw(clcw);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        // Send some frame and (later) check that it is sent with VCC 10
        tcVc.dispatch(true, 0, new byte[200]);
        TcTransferFrame frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 0
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 1
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 2
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 3

        // Inform arrival of 0 and 1, activate retransmission and signal wait
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(true)
                .setReportValue(2)
                .setWaitFlag(true)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        // Wait 5 seconds to check that the timer expiry does not request retransmission at this stage
        Thread.sleep(4000);
        fop.clcw(clcw);
        Thread.sleep(2000);

        // Clear wait
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(true)
                .setReportValue(2)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        Thread.sleep(5000);
        // Notify 2 and 3
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(4)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        fop.directive(6, FopDirective.TERMINATE, 0);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 6), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));
        Thread.sleep(2000);
        assertEquals(6, sink.size());

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testS3RetransmissionLimitSuspend() throws InterruptedException {
        // Test sink
        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink::add);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 1);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 2);
        fop.directive(5, FopDirective.INIT_AD_WITH_CLCW, 0);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S4, 5000));

        fop.clcw(clcw);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        // Send some frame and (later) check that it is sent with VCC 10
        tcVc.dispatch(true, 0, new byte[200]);
        TcTransferFrame frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 0
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 1
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 2
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 3

        // Inform arrival of 0 and 1, activate retransmission and signal wait
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(true)
                .setReportValue(2)
                .setWaitFlag(true)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        // Wait 5 seconds to expire everything
        Thread.sleep(5000);
        // Another clcw with wait cleared
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(true)
                .setReportValue(2)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);
        Thread.sleep(2000);
        // Another clcw with wait on
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(true)
                .setReportValue(2)
                .setWaitFlag(true)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);
        Thread.sleep(5000);
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));

        // Send RESUME
        fop.directive(8, FopDirective.RESUME, 0);
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S3, 5000));

        // Clear wait
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(true)
                .setReportValue(2)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        Thread.sleep(1000);
        // Notify 2 and 3
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(4)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        fop.directive(6, FopDirective.TERMINATE, 0);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 6), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));
        Thread.sleep(2000);
        assertEquals(4, sink.size());

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testInitAdWithClcwWaitTransitionError() throws InterruptedException {
        // Test sink
        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink::add);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 3);
        fop.directive(5, FopDirective.INIT_AD_WITH_CLCW, 0);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S4, 5000));

        fop.clcw(clcw);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        // Send some frame and (later) check that it is sent with VCC 10
        tcVc.dispatch(true, 0, new byte[200]);
        TcTransferFrame frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 0
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 1
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 2
        tcVc.dispatch(true, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000); // 3

        // Inform arrival of 0 and 1, activate retransmission and signal wait
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(true)
                .setReportValue(2)
                .setWaitFlag(true)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        // Wait 5 seconds to check that the timer expiry does not request retransmission at this stage
        Thread.sleep(4000);
        fop.clcw(clcw);
        Thread.sleep(2000);

        // Notify 2 and 3 but leave wait
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(4)
                .setWaitFlag(true)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        assertTrue(stub.waitForAlert(FopAlertCode.CLCW, 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testS1NnrAlarm() throws InterruptedException {
        // Test sink
        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink::add);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 3);
        fop.directive(5, FopDirective.INIT_AD_WITH_CLCW, 0);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S4, 5000));

        fop.clcw(clcw);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        // Retransmission and Wait at S1
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(true)
                .setReportValue(2)
                .setWaitFlag(true)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        // Timeout in 5 seconds, add some margin
        assertTrue(stub.waitForAlert(FopAlertCode.NN_R, 5000));

        fop.directive(6, FopDirective.TERMINATE, 0);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 6), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testS1NotExpectedWaitTransition() throws InterruptedException {
        // Test sink
        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink::add);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 3);
        fop.directive(5, FopDirective.INIT_AD_WITH_CLCW, 0);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S4, 5000));

        fop.clcw(clcw);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        // Retransmission and Wait at S1 with no outstanding AD
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(true)
                .setReportValue(0)
                .setWaitFlag(true)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        // Timeout in 5 seconds, add some margin
        assertTrue(stub.waitForAlert(FopAlertCode.SYNCH, 5000));

        fop.directive(6, FopDirective.TERMINATE, 0);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 6), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testS1Lockout() throws InterruptedException {
        // Test sink
        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink::add);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 3);
        fop.directive(5, FopDirective.INIT_AD_WITH_CLCW, 0);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S4, 5000));

        fop.clcw(clcw);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        // Retransmission and Wait at S1 with no outstanding AD
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(true)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        // Timeout in 5 seconds, add some margin
        assertTrue(stub.waitForAlert(FopAlertCode.LOCKOUT, 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testInitAdWithClcwTimeout() throws InterruptedException {
        // Test sink
        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.PACKET, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink::add);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(false)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 1);
        fop.directive(5, FopDirective.INIT_AD_WITH_CLCW, 0);

        // Timeout in 5 seconds, add some margin
        assertTrue(stub.waitForAlert(FopAlertCode.T1, 7000));

        fop.clcw(clcw);
        Thread.sleep(1000);
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testInitAdWithoutClcw() throws InterruptedException {
        // Test sink
        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink::add);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(9, FopDirective.SET_V_S, 10);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 1);
        fop.directive(8, FopDirective.RESUME, 0);
        fop.directive(5, FopDirective.INIT_AD_WITHOUT_CLCW, 0);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        // Send a frame and (later) check that it is sent with VCC 10
        tcVc.dispatch(true, 0, new byte[200]);
        final TcTransferFrame frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000);

        // Ack frame 10
        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(11)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);
        assertTrue(stub.waitForFrame(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && o[1] == frame, 5000));

        // Send 3 frames and then ack the first 2 frames
        tcVc.dispatch(true, 0, new byte[200]);
        TcTransferFrame frame2 = collector.retrieveFirst(true);
        fop.transmit(frame2,2000);
        tcVc.dispatch(true, 0, new byte[200]);
        TcTransferFrame frame3 = collector.retrieveFirst(true);
        fop.transmit(frame3,2000);
        tcVc.dispatch(true, 0, new byte[200]);
        TcTransferFrame frame4 = collector.retrieveFirst(true);
        fop.transmit(frame4, 2000);

        // Ack frame 10 again
        fop.clcw(clcw);

        // Ack frame 11 and 12
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(13)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        Thread.sleep(500);

        // Ack frame 13
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(14)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        assertTrue(stub.waitForFrame(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && o[1] == frame4, 5000));

        fop.directive(6, FopDirective.TERMINATE, 0);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 6), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));
        Thread.sleep(2000);
        assertEquals(4, sink.size());
        assertEquals(10, sink.get(0).getVirtualChannelFrameCount());

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testInitAdWithoutClcwWithWait() throws InterruptedException {
        // Test sink
        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink::add);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(9, FopDirective.SET_V_S, 10);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 1);
        fop.directive(8, FopDirective.RESUME, 0);
        fop.directive(5, FopDirective.INIT_AD_WITHOUT_CLCW, 0);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        // Send a frame and (later) check that it is sent with VCC 10
        tcVc.dispatch(true, 0, new byte[200]);
        final TcTransferFrame frame = collector.retrieveFirst(true);
        fop.transmit(frame, 2000);

        // Ack frame 10
        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(11)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);
        assertTrue(stub.waitForFrame(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && o[1] == frame, 5000));

        // Send 3 frames and then ack the first 2 frames
        tcVc.dispatch(true, 0, new byte[200]);
        TcTransferFrame frame2 = collector.retrieveFirst(true);
        fop.transmit(frame2,2000);
        tcVc.dispatch(true, 0, new byte[200]);
        TcTransferFrame frame3 = collector.retrieveFirst(true);
        fop.transmit(frame3,2000);
        tcVc.dispatch(true, 0, new byte[200]);
        TcTransferFrame frame4 = collector.retrieveFirst(true);
        fop.transmit(frame4, 2000);

        // Ack frame 10 again
        fop.clcw(clcw);

        // Ack frame 10 with wait flag
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(13)
                .setWaitFlag(true)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        assertTrue(stub.waitForAlert(FopAlertCode.CLCW, 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testInitAdWithUnlock() throws InterruptedException {
        // Test sink
        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.PACKET, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink::add);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();

        fop.clcw(clcw);

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 1);
        fop.directive(5, FopDirective.INIT_AD_WITH_UNLOCK, 0);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S5, 5000));

        fop.clcw(clcw);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        Thread.sleep(3000);

        fop.directive(6, FopDirective.TERMINATE, 0);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 6), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));
        Thread.sleep(2000);
        assertEquals(1, sink.size()); // 1 frames

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testBdReject() throws InterruptedException {
        // Test sink
        Function<TcTransferFrame, Boolean> sink = o -> false;
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        tcVc.dispatch(false, 0, new byte[200]);
        TcTransferFrame frame = collector.retrieveFirst(true);
        fop.transmit(frame);

        assertTrue(stub.waitForAlert(FopAlertCode.LLIF, 5000));
        assertTrue(stub.waitForStatus(o -> o.getEvent() == FopEvent.EventNumber.E46, 5000));

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testInitAdWithUnlockReject() throws InterruptedException {
        // Test sink
        Function<TcTransferFrame, Boolean> sink = o -> false;
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.PACKET, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();

        fop.clcw(clcw);

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 1);
        fop.directive(5, FopDirective.INIT_AD_WITH_UNLOCK, 0);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S5, 5000));

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.NEGATIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));
        assertTrue(stub.waitForAlert(FopAlertCode.LLIF, 5000));

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testAdFrameBdFrameNoInit() throws InterruptedException {
        // Test sink
        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink::add);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        tcVc.dispatch(true, 0, new byte[300]);
        TcTransferFrame frame = collector.retrieveFirst(true);
        fop.transmit(frame);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));

        tcVc.dispatch(false, 0, new byte[200]);
        frame = collector.retrieveFirst(true);
        fop.transmit(frame);

        assertTrue(stub.waitForStatus(o -> o.getEvent() == FopEvent.EventNumber.E45, 5000));
        Thread.sleep(1000);

        assertEquals(1, sink.size()); // 1 frame

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testInitAdWithUnlockTimeoutAndRetransmission() throws InterruptedException {
        // Test sink
        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.PACKET, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink::add);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(true)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 5);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 2);
        fop.directive(5, FopDirective.INIT_AD_WITH_UNLOCK, 0);

        // Timeout in 5 seconds, add some margin
        Thread.sleep(4000);

        fop.clcw(clcw);

        // Retransmission
        Thread.sleep(3000);

        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(1)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();

        fop.clcw(clcw);

        Thread.sleep(1000);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        fop.directive(6, FopDirective.TERMINATE, 0);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 6), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));
        Thread.sleep(2000);
        assertEquals(2, sink.size()); // 2 frames

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testInitAdWithSetVr() throws InterruptedException {
        // Test sink
        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.PACKET, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);
        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink::add);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(23)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();

        fop.clcw(clcw);

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 1);
        fop.directive(5, FopDirective.INIT_AD_WITH_SET_V_R, 0);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S5, 2000));

        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();

        fop.clcw(clcw);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        fop.directive(6, FopDirective.TERMINATE, 0);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 6), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));

        Thread.sleep(2000);
        assertEquals(1, sink.size()); // 1 frame

        fop.deregister(stub);
        fop.dispose();
    }

    @Test
    public void testInitAdWithSetVrTimeout() throws InterruptedException {
        // Test sink
        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();
        // VC
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.PACKET, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.AD || o.getFrameType() == TcTransferFrame.FrameType.BD);
        tcVc.register(collector);

        // Fop Engine
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, sink::add);
        // Observer stub
        FopListenerStub stub = new FopListenerStub();
        fop.register(stub);

        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(23)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();

        fop.clcw(clcw);

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 2);
        fop.directive(5, FopDirective.INIT_AD_WITH_SET_V_R, 0);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S5, 5000));

        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(20)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();

        fop.clcw(clcw);

        assertTrue(stub.waitForAlert(FopAlertCode.T1, 7000));
        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.NEGATIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));
        Thread.sleep(2000);
        assertEquals(2, sink.size()); // 2 frames

        fop.deregister(stub);
        fop.dispose();
    }

    private static class FopListenerStub implements IFopObserver {

        private final List<FopStatus> lastStatus = new LinkedList<>();
        private final AtomicReference<Object[]> lastDirective = new AtomicReference<>();
        private final AtomicReference<Object[]> lastFrame = new AtomicReference<>();
        private final AtomicReference<FopAlertCode> lastAlert = new AtomicReference<>();

        @Override
        public void transferNotification(FopEngine engine, FopOperationStatus status, TcTransferFrame frame) {
            // System.out.println("Transfer frame - type " + frame.getFrameType() + ": " + status);
            synchronized (lastFrame) {
                lastFrame.set(new Object[] {status, frame});
                lastFrame.notifyAll();
            }
        }

        @Override
        public void directiveNotification(FopEngine engine, FopOperationStatus status, Object tag, FopDirective directive, int qualifier) {
            // System.out.println("Directive " + tag + ", directive " + " " + qualifier + ": " + status);
            synchronized (lastDirective) {
                lastDirective.set(new Object[] {status, tag, directive, qualifier});
                lastDirective.notifyAll();
            }
        }

        @Override
        public void alert(FopEngine engine, FopAlertCode code) {
            // System.out.println("Alert: " + code);
            synchronized (lastAlert) {
                lastAlert.set(code);
                lastAlert.notifyAll();
            }
        }

        @Override
        public void suspend(FopEngine engine) {
            // System.out.println("Suspend");
        }

        @Override
        public void statusReport(FopEngine engine, FopStatus status) {
            // System.out.println(status);
            synchronized (lastStatus) {
                lastStatus.add(status);
                lastStatus.notifyAll();
            }
        }

        public boolean waitForStatus(Function<FopStatus, Boolean> condition, int msTimeout) throws InterruptedException {
            long waitUntil = System.currentTimeMillis() + msTimeout;
            synchronized (lastStatus) {
                FopStatus lastExtracted = null;
                while (System.currentTimeMillis() < waitUntil) {
                    while (System.currentTimeMillis() < waitUntil && lastStatus.isEmpty()) {
                        lastStatus.wait(msTimeout);
                    }
                    // Extract and check
                    lastExtracted = lastStatus.isEmpty() ? null : lastStatus.remove(0);
                    if(lastExtracted != null && condition.apply(lastExtracted)) {
                        return true;
                    }
                }
                return lastExtracted != null && condition.apply(lastExtracted);
            }
        }

        public boolean waitForFrame(Function<Object[], Boolean> condition, int msTimeout) throws InterruptedException {
            long waitUntil = System.currentTimeMillis() + msTimeout;
            synchronized (lastFrame) {
                while(System.currentTimeMillis() < waitUntil && (lastFrame.get() == null || !condition.apply(lastFrame.get()))) {
                    lastFrame.wait(msTimeout);
                    if(System.currentTimeMillis() > waitUntil) {
                        break;
                    }
                }
                return lastFrame.get() != null && condition.apply(lastFrame.get());
            }
        }

        public boolean waitForDirective(Function<Object[], Boolean> condition, int msTimeout) throws InterruptedException {
            long waitUntil = System.currentTimeMillis() + msTimeout;
            synchronized (lastDirective) {
                while(System.currentTimeMillis() < waitUntil && (lastDirective.get() == null || !condition.apply(lastDirective.get()))) {
                    lastDirective.wait(msTimeout);
                    if(System.currentTimeMillis() > waitUntil) {
                        break;
                    }
                }
                return lastDirective.get() != null && condition.apply(lastDirective.get());
            }
        }

        public boolean waitForAlert(FopAlertCode code, int msTimeout) throws InterruptedException {
            long waitUntil = System.currentTimeMillis() + msTimeout;
            synchronized (lastAlert) {
                while(System.currentTimeMillis() < waitUntil && (lastAlert.get() == null || lastAlert.get() != code)) {
                    lastAlert.wait(msTimeout);
                    if(System.currentTimeMillis() > waitUntil) {
                        break;
                    }
                }
                return lastAlert.get() != null && lastAlert.get() == code;
            }
        }
    }
}