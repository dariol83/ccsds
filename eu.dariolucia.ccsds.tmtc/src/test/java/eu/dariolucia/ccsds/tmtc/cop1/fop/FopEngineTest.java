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
    public void testInitAdWithClcw() throws InterruptedException {
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
        fop.directive(5, FopDirective.INIT_AD_WITH_CLCW, 0);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S4, 5000));

        fop.clcw(clcw);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        fop.directive(6, FopDirective.TERMINATE, 0);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 6), 5000));
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

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 5);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 1);
        fop.directive(5, FopDirective.INIT_AD_WITHOUT_CLCW, 0);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        fop.directive(6, FopDirective.TERMINATE, 0);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 6), 5000));
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

        fop.directive(6, FopDirective.TERMINATE, 0);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSIIVE_CONFIRM && Objects.equals(o[1], 6), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));
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

        assertEquals(2, sink.size()); // 2 frames

        fop.deregister(stub);
        fop.dispose();
    }

    private static class FopListenerStub implements IFopObserver {

        private final List<FopStatus> lastStatus = new LinkedList<>();
        private final AtomicReference<Object[]> lastDirective = new AtomicReference<>();
        private final AtomicReference<FopAlertCode> lastAlert = new AtomicReference<>();

        @Override
        public void transferNotification(FopOperationStatus status, TcTransferFrame frame) {
            System.out.println("Transfer frame - type " + frame.getFrameType() + ": " + status);
        }

        @Override
        public void directiveNotification(FopOperationStatus status, Object tag, FopDirective directive, int qualifier) {
            System.out.println("Directive " + tag + ", directive " + " " + qualifier + ": " + status);
            synchronized (lastDirective) {
                lastDirective.set(new Object[] {status, tag, directive, qualifier});
                lastDirective.notifyAll();
            }
        }

        @Override
        public void alert(FopAlertCode code) {
            System.out.println("Alert: " + code);
            synchronized (lastAlert) {
                lastAlert.set(code);
                lastAlert.notifyAll();
            }
        }

        @Override
        public void suspend() {
            System.out.println("Suspend");
        }

        @Override
        public void statusReport(FopStatus status) {
            System.out.println(status);
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
                    while (lastStatus.isEmpty()) {
                        lastStatus.wait(msTimeout);
                    }
                    // Extract and check
                    lastExtracted = lastStatus.remove(0);
                    if(condition.apply(lastExtracted)) {
                        return true;
                    }
                }
                return lastExtracted != null && condition.apply(lastExtracted);
            }
        }

        public boolean waitForDirective(Function<Object[], Boolean> condition, int msTimeout) throws InterruptedException {
            long waitUntil = System.currentTimeMillis() + msTimeout;
            synchronized (lastDirective) {
                while(lastDirective.get() == null || !condition.apply(lastDirective.get())) {
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
                while(lastAlert.get() == null || lastAlert.get() != code) {
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