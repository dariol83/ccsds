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

package eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.function;

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

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VirtualChannelReceiverSpacePacketFlatMapperTest {

    @Test
    public void testTmVc0SpacePacket() {
        // Create a virtual channel for VC0
        TmReceiverVirtualChannel vc0 = new TmReceiverVirtualChannel(0, VirtualChannelAccessMode.PACKET, true);
        // Create a space packet extractor function on the virtual channel (frame -> list of space packets)
        VirtualChannelReceiverSpacePacketMapper<TmTransferFrame> packetMapper = new VirtualChannelReceiverSpacePacketMapper<>(vc0);
        // Create a flat mapper, to be used for stream processing (frame -> stream of space packets
        VirtualChannelReceiverSpacePacketFlatMapper<TmTransferFrame> flatMapper = new VirtualChannelReceiverSpacePacketFlatMapper<>(packetMapper);
        // Build the reader
        String FILE_TM1 = "dumpFile_tm_1.hex";
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1));
        // Use stream approach: no need for decoder
        List<SpacePacket> packets = StreamUtil.from(reader) // Reads the frames, correctly segmented
                .map(new TmAsmDecoder()) // Remove ASM
                .map(new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223)) // Remove R-S codeblock
                .map(TmTransferFrame.decodingFunction(false)) // Convert to TM frame
                .filter(o -> o.getVirtualChannelId() == 0) // Filter out VCs not equal to 0
                .flatMap(flatMapper) // Map the frames to a stream of space packets
                .collect(Collectors.toList()); // Collect the space packets
        // Check the list of packets
        assertEquals(613, packets.size());

        packetMapper.dispose();
    }
}