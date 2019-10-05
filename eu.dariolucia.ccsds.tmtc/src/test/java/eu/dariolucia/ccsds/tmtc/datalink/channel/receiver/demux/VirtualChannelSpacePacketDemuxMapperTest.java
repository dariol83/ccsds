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

package eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.demux;

import eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm;
import eu.dariolucia.ccsds.tmtc.coding.decoder.ReedSolomonDecoder;
import eu.dariolucia.ccsds.tmtc.coding.decoder.TmAsmDecoder;
import eu.dariolucia.ccsds.tmtc.coding.reader.LineHexDumpChannelReader;
import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.TmReceiverVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;
import eu.dariolucia.ccsds.tmtc.util.StreamUtil;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VirtualChannelSpacePacketDemuxMapperTest {

    @Test
    public void testProcessFrame() {
        // Create a virtual channel for VC0
        TmReceiverVirtualChannel vc0 = new TmReceiverVirtualChannel(0, VirtualChannelAccessMode.PACKET, true);
        // Create a virtual channel for VC7
        TmReceiverVirtualChannel vc7 = new TmReceiverVirtualChannel(7, VirtualChannelAccessMode.DATA, true);
        // Create a VC demux for VC0 and VC7
        VirtualChannelSpacePacketDemuxMapper<TmTransferFrame> demux = new VirtualChannelSpacePacketDemuxMapper<>();
        // Register the virtual channels
        demux.register(vc0);
        demux.register(vc7);

        // Subscribe a packet collector for VC0
        List<byte[]> goodPackets = new LinkedList<>();

        // Build the reader
        String FILE_TM1 = "dumpFile_tm_1.hex";
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1));
        // Use stream approach: no need for decoder
        StreamUtil.from(reader) // Reads the frames, correctly segmented
                .map(new TmAsmDecoder()) // Remove ASM
                .map(new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223)) // Remove R-S codeblock
                .map(TmTransferFrame.decodingFunction(false)) // Convert to TM frame
                .map(demux) // Map the frame to the list of space packets
                .flatMap(Collection::stream) // Now flatten the list and stream the extracted packets
                .map(SpacePacket::getPacket) // Get the packet body
                .forEach(goodPackets::add); // Add to the array
        // Check the list of packets
        assertEquals(613, goodPackets.size());
    }
}