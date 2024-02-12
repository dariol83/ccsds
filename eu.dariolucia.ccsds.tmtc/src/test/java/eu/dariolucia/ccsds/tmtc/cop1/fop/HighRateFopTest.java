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

class HighRateFopTest {

    @Test
    public void testBdFrame() throws InterruptedException {
        // Test sink
        List<TcTransferFrame> sinkList = new LinkedList<>();
        Function<TcTransferFrame, Boolean> sink = o -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // Ignore
            }
            sinkList.add(o);
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

        // Prepare and send 1000 frames in a burst
        for(int i = 0; i < 1000; ++i) {
            tcVc.dispatch(false, 0, new byte[200]);
            TcTransferFrame frame = collector.retrieveFirst(true);
            try {
                fop.transmit(frame, 2000); // 0
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        Thread.sleep(1000);
        assertEquals(1000, sinkList.size());

        fop.deregister(stub);
        fop.dispose();
    }


    @Test
    public void testAdFrame() throws InterruptedException {
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

        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 10);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 10);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 3);
        fop.directive(5, FopDirective.INIT_AD_WITH_CLCW, 0);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S4, 5000));

        fop.clcw(clcw);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSITIVE_CONFIRM && Objects.equals(o[1], 5), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S1, 5000));

        // Send some frame and (later) check that it is sent with VCC 10
        // Prepare and send 100 frames in a burst
        new Thread(() -> {
            for(int i = 0; i < 50; ++i) {
                tcVc.dispatch(true, 0, new byte[200]);
                TcTransferFrame frame = collector.retrieveFirst(true);
                try {
                    fop.transmit(frame, 2000); // 0
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        Thread.sleep(1000);
        // Inform arrival of 8
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(9)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        Thread.sleep(1000);
        // Inform arrival of 15
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(16)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        Thread.sleep(1000);
        // Inform arrival of 24
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(25)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        Thread.sleep(1000);
        // Inform arrival of 33
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(34)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        Thread.sleep(1000);
        // Inform arrival of 42
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(43)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        Thread.sleep(1000);
        // Inform arrival of 50
        clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(50)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        fop.clcw(clcw);

        Thread.sleep(2000);
        fop.directive(6, FopDirective.TERMINATE, 0);

        assertTrue(stub.waitForDirective(o -> o[0] == FopOperationStatus.POSITIVE_CONFIRM && Objects.equals(o[1], 6), 5000));
        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FopState.S6, 5000));
        Thread.sleep(2000);
        assertEquals(50, sink.size());

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
            // System.out.println("Transfer frame " + frame.hashCode() + " - type " + frame.getFrameType() + ": " + status);
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
    }
}