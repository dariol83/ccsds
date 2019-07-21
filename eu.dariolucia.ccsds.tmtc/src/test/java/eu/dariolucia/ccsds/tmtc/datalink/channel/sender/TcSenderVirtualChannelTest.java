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

package eu.dariolucia.ccsds.tmtc.datalink.channel.sender;

import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.mux.TmMasterChannelMuxer;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.ocf.builder.ClcwBuilder;
import eu.dariolucia.ccsds.tmtc.ocf.pdu.AbstractOcf;
import eu.dariolucia.ccsds.tmtc.transport.builder.SpacePacketBuilder;
import eu.dariolucia.ccsds.tmtc.transport.pdu.BitstreamData;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TcSenderVirtualChannelTest {

    private SpacePacket generateSpacePacket(int apid, int counter) {
        SpacePacketBuilder spp = SpacePacketBuilder.create()
                .setApid(apid)
                .setQualityIndicator(true)
                .setSecondaryHeaderFlag(true)
                .setTelemetryPacket();
        spp.addData(new byte[400]);
        spp.setPacketSequenceCount(counter % 16384);
        return spp.build();
    }


    @Test
    public void testPushModePacketsUnsegmented() {
        // Create a sink consumer
        List<TcTransferFrame> list = new LinkedList<>();
        IVirtualChannelSenderOutput<TcTransferFrame> sink = (vc, generatedFrame, bufferedBytes) -> list.add(generatedFrame);
        // Setup the VC
        TcSenderVirtualChannel vc0 = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.Packet, false, false);
        // Register the sink
        vc0.register(sink);
        // Set channel properties (BD mode)
        vc0.setAdMode(false);
        // Generate 10 TC packets
        for(int i = 0; i < 10; ++i) {
            vc0.dispatch(generateSpacePacket(300, i));
        }
        //
        // TODO: check frame contents
        assertEquals(10, list.size());
    }

    // TODO: test segmentation + map ID
    // TODO: test unlock
    // TODO: test set VR
    // TODO: test multi packet with segmentation
    // TODO: test multi packet without segmentation
    // TODO: test large packet with segmentation
    // TODO: test large packet without segmentation
    // TODO: test user data (single frame)
    // TODO: test user data (multi frame)



}