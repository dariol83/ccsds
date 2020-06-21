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

import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.TcSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.util.TransferFrameCollector;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.ocf.builder.ClcwBuilder;
import eu.dariolucia.ccsds.tmtc.ocf.pdu.Clcw;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmEngineTest {

    @Test
    public void testFarmUnlock() throws InterruptedException {

        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();

        FarmEngine farm = new FarmEngine(0, sink::add, true, 4, 10, FarmState.S3, 0);
        FarmListenerStub stub = new FarmListenerStub();
        farm.register(stub);

        Clcw expectedClcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(true)
                .setNoBitlockFlag(true)
                .setNoRfAvailableFlag(true)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        assertEquals(expectedClcw, farm.get());

        TcSenderVirtualChannel vc = new TcSenderVirtualChannel(321, 0 , VirtualChannelAccessMode.DATA, false, false);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>();
        vc.register(collector);

        // Send unlock
        vc.dispatchUnlock();
        TcTransferFrame fr = collector.retrieveFirst(true);
        farm.frameArrived(fr);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FarmState.S1, 5000));

        farm.setNoBitLockFlag(false);
        farm.setNoRfAvailableFlag(false);
        expectedClcw = ClcwBuilder.create()
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
        assertEquals(expectedClcw, farm.get());

        assertEquals(0, sink.size());

        // Send BD frame
        vc.dispatch(false, 0, new byte[200]);
        fr = collector.retrieveFirst(true);
        farm.frameArrived(fr);

        farm.setReservedSpare(1);
        farm.setStatusField(1);
        expectedClcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(2)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(1)
                .setStatusField(1)
                .build();
        assertEquals(expectedClcw, farm.get());

        Thread.sleep(2000);

        assertEquals(1, sink.size());

        // Send 2 BD frames
        vc.dispatch(false, 0, new byte[200]);
        fr = collector.retrieveFirst(true);
        farm.frameArrived(fr);
        vc.dispatch(false, 0, new byte[200]);
        fr = collector.retrieveFirst(true);
        farm.frameArrived(fr);

        expectedClcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(1)
                .setStatusField(1)
                .build();
        assertEquals(expectedClcw, farm.get());

        Thread.sleep(2000);

        assertEquals(3, sink.size());

        // Send 2 AD frames
        vc.dispatch(true, 0, new byte[200]);
        fr = collector.retrieveFirst(true);
        farm.frameArrived(fr);
        vc.dispatch(true, 0, new byte[200]);
        fr = collector.retrieveFirst(true);
        farm.frameArrived(fr);

        expectedClcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(2)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(1)
                .setStatusField(1)
                .build();
        assertEquals(expectedClcw, farm.get());

        Thread.sleep(2000);

        assertEquals(5, sink.size());

        // Generate 2 AD frames but send only the second one
        vc.dispatch(true, 0, new byte[200]);
        TcTransferFrame missing = collector.retrieveFirst(true); // 2

        vc.dispatch(true, 0, new byte[200]);
        fr = collector.retrieveFirst(true); // 3
        farm.frameArrived(fr);

        expectedClcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(true)
                .setReportValue(2)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(1)
                .setStatusField(1)
                .build();
        assertEquals(expectedClcw, farm.get());

        Thread.sleep(2000);

        assertEquals(5, sink.size());

        // Resend the 2 frames
        farm.frameArrived(missing);
        farm.frameArrived(fr);

        expectedClcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(4)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(1)
                .setStatusField(1)
                .build();
        assertEquals(expectedClcw, farm.get());

        Thread.sleep(2000);

        assertEquals(7, sink.size());

        // Send again the missing frame
        farm.frameArrived(missing);

        expectedClcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(4)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(1)
                .setStatusField(1)
                .build();
        assertEquals(expectedClcw, farm.get());

        Thread.sleep(2000);

        assertEquals(7, sink.size());

        // Send unlock
        vc.dispatchUnlock();
        fr = collector.retrieveFirst(true);
        farm.frameArrived(fr);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FarmState.S1, 5000));

        expectedClcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(1)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(4)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(1)
                .setStatusField(1)
                .build();
        assertEquals(expectedClcw, farm.get());

        farm.deregister(stub);
        farm.dispose();
    }

    @Test
    public void testFarmSetVr() throws InterruptedException {

        List<TcTransferFrame> sink = new CopyOnWriteArrayList<>();

        FarmEngine farm = new FarmEngine(0, sink::add, true, 4, 10, FarmState.S3, 0);
        FarmListenerStub stub = new FarmListenerStub();
        farm.register(stub);

        Clcw expectedClcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(true)
                .setNoBitlockFlag(true)
                .setNoRfAvailableFlag(true)
                .setRetransmitFlag(false)
                .setReportValue(0)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        assertEquals(expectedClcw, farm.get());

        TcSenderVirtualChannel vc = new TcSenderVirtualChannel(321, 0 , VirtualChannelAccessMode.DATA, false, false);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>();
        vc.register(collector);

        // Send SetVR - No effect except FARM-B
        vc.dispatchSetVr(12);
        TcTransferFrame fr = collector.retrieveFirst(true);
        farm.frameArrived(fr);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FarmState.S3, 5000));

        farm.setNoBitLockFlag(false);
        farm.setNoRfAvailableFlag(false);
        expectedClcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(1)
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
        assertEquals(expectedClcw, farm.get());

        assertEquals(0, sink.size());

        // Send BD frame
        vc.dispatch(false, 0, new byte[200]);
        fr = collector.retrieveFirst(true);
        farm.frameArrived(fr);

        expectedClcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(2)
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
        assertEquals(expectedClcw, farm.get());

        Thread.sleep(2000);

        assertEquals(1, sink.size());

        // Send unlock - FARM open
        vc.dispatchUnlock();
        fr = collector.retrieveFirst(true);
        farm.frameArrived(fr);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FarmState.S1, 5000));

        expectedClcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(3)
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
        assertEquals(expectedClcw, farm.get());

        assertEquals(1, sink.size());

        // Send again SetVr
        vc.dispatchSetVr(12);
        fr = collector.retrieveFirst(true);
        farm.frameArrived(fr);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FarmState.S1, 5000));

        farm.setNoBitLockFlag(false);
        farm.setNoRfAvailableFlag(false);
        expectedClcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(false)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(12)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        assertEquals(expectedClcw, farm.get());

        assertEquals(1, sink.size());

        // Send AD frame very far away in terms of VCC (negative side) -> lockout
        vc.dispatch(true, 0, new byte[200]);
        fr = collector.retrieveFirst(true);
        farm.frameArrived(fr);

        assertTrue(stub.waitForStatus(o -> o.getCurrentState() == FarmState.S3, 5000));

        expectedClcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(0)
                .setLockoutFlag(true)
                .setNoBitlockFlag(false)
                .setNoRfAvailableFlag(false)
                .setRetransmitFlag(false)
                .setReportValue(12)
                .setWaitFlag(false)
                .setVirtualChannelId(0)
                .setReservedSpare(0)
                .setStatusField(0)
                .build();
        assertEquals(expectedClcw, farm.get());

        Thread.sleep(2000);

        assertEquals(1, sink.size());

        farm.deregister(stub);
        farm.dispose();
    }

    private static class FarmListenerStub implements IFarmObserver {

        private final List<FarmStatus> lastStatus = new LinkedList<>();

        @Override
        public void statusReport(FarmStatus status) {
            System.out.println(status);
            synchronized (lastStatus) {
                lastStatus.add(status);
                lastStatus.notifyAll();
            }
        }

        public boolean waitForStatus(Function<FarmStatus, Boolean> condition, int msTimeout) throws InterruptedException {
            long waitUntil = System.currentTimeMillis() + msTimeout;
            synchronized (lastStatus) {
                FarmStatus lastExtracted = null;
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
    }
}