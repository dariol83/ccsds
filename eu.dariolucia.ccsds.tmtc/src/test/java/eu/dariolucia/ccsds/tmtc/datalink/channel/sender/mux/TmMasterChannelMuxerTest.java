/*
 * Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package eu.dariolucia.ccsds.tmtc.datalink.channel.sender.mux;

import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.TmSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.ocf.builder.ClcwBuilder;
import eu.dariolucia.ccsds.tmtc.ocf.pdu.AbstractOcf;
import eu.dariolucia.ccsds.tmtc.transport.builder.SpacePacketBuilder;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TmMasterChannelMuxerTest {

    @Test
    public void testVcMuxing() throws InterruptedException {
        // Create a sink consumer
        List<TmTransferFrame> list = new LinkedList<>();
        Consumer<TmTransferFrame> sink = list::add;
        // Setup the muxer
        TmMasterChannelMuxer mux = new TmMasterChannelMuxer(sink);
        // Setup 3 VCs
        TmSenderVirtualChannel vc0 = new TmSenderVirtualChannel(123, 0, VirtualChannelAccessMode.Packet, false, 1115, mux::getNextCounter, this::ocfSupplier);
        TmSenderVirtualChannel vc1 = new TmSenderVirtualChannel(123, 1, VirtualChannelAccessMode.Packet, false, 1115, mux::getNextCounter, this::ocfSupplier);
        TmSenderVirtualChannel vc4 = new TmSenderVirtualChannel(123, 4, VirtualChannelAccessMode.Packet, false, 1115, mux::getNextCounter, this::ocfSupplier);
        // Link the muxer with the 3 VCs
        vc0.register(mux);
        vc1.register(mux);
        vc4.register(mux);
        // Create a generation function for each VC
        TmSenderVirtualChannel[] vcs = new TmSenderVirtualChannel[] { vc0, vc1, vc4 };
        Thread[] generators = new Thread[vcs.length];
        for(int i = 0; i < generators.length; ++i) {
            final TmSenderVirtualChannel vc = vcs[i];
            generators[i] = new Thread(() -> generate(vc));
            generators[i].start();
        }
        // Join the threads and wait until they are over
        for(Thread t : generators) {
            t.join();
        }
        // Check that the muxer forwarded all the expected number of TM frames
        assertEquals(438, list.size());
        // Check that all the TM frames have a constantly increasing master channel frame counter, starting from 0, no gaps
        int nextExpected = 0;
        for(TmTransferFrame tmf : list) {
            assertEquals(nextExpected, tmf.getMasterChannelFrameCount());
            nextExpected += 1;
            nextExpected %= 256;
        }

        // Reset: for coverage
        mux.setExpectedCounter(0);
        mux.setMasterChannelCounter(0);
    }

    private void generate(TmSenderVirtualChannel vc) {
        SpacePacketBuilder spp = SpacePacketBuilder.create()
                .setApid(200 + vc.getVirtualChannelId())
                .setQualityIndicator(true)
                .setSecondaryHeaderFlag(false)
                .setTelemetryPacket();
        spp.addData(new byte[400]);
        for(int i = 0; i < 400; ++i) {
            spp.setPacketSequenceCount(i % 16384);
            vc.dispatch(spp.build());
        }
    }

    private AbstractOcf ocfSupplier(int vcId) {
        return ClcwBuilder.create()
                .setCopInEffect(false)
                .setFarmBCounter(2)
                .setLockoutFlag(false)
                .setNoBitlockFlag(true)
                .setNoRfAvailableFlag(false)
                .setReportValue(121)
                .setRetransmitFlag(false)
                .setVirtualChannelId(1)
                .build();
    }
}