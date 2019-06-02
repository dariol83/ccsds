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

package eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.processor;

import eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm;
import eu.dariolucia.ccsds.tmtc.coding.ChannelDecoder;
import eu.dariolucia.ccsds.tmtc.coding.decoder.ReedSolomonDecoder;
import eu.dariolucia.ccsds.tmtc.coding.decoder.TmAsmDecoder;
import eu.dariolucia.ccsds.tmtc.coding.processor.ChannelDecoderProcessor;
import eu.dariolucia.ccsds.tmtc.coding.reader.LineHexDumpChannelReader;
import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.TmReceiverVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.function.VirtualChannelReceiverSpacePacketMapper;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;
import eu.dariolucia.ccsds.tmtc.util.processor.ConsumerWrapper;
import eu.dariolucia.ccsds.tmtc.util.processor.PredicateWrapper;
import eu.dariolucia.ccsds.tmtc.util.processor.SupplierWrapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VirtualChannelSenderSpacePacketMapperTest {

    private static String FILE_TM1 = "dumpFile_tm_1.hex";

    @Test
    public void testSpacePacketDecodingCompleteAsync() throws InterruptedException {
        // Build the reader (as supplier)
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1));
        // Wrap the reader in a Flow.Publisher
        SupplierWrapper<byte[]> rawFramePublisher = new SupplierWrapper<>(reader);

        // Build the frame decoder: TM Frame decoding function, no FECF
        ChannelDecoder<TmTransferFrame> cd = ChannelDecoder.create(TmTransferFrame.decodingFunction(false))
                .addDecodingFunction(new TmAsmDecoder()) // Add ASM removal with default ASM
                .addDecodingFunction(new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223)) // Add R-S symbol block removal 255/223
                .configure();
        // Create a ChannelDecoderProcessor, asynchronous processing, complete
        ChannelDecoderProcessor<TmTransferFrame> frameDecoderProcessor = new ChannelDecoderProcessor<>(cd, Executors.newFixedThreadPool(1), false);
        // Chain the frame decoder processor with the raw frame publisher
        rawFramePublisher.subscribe(frameDecoderProcessor);

        // Create a virtual channel for VC0, ignore other VCs (no exception on VC violation)
        TmReceiverVirtualChannel vc0 = new TmReceiverVirtualChannel(0, VirtualChannelAccessMode.Packet, false);
        // Create a space packet extractor function on the virtual channel (frame -> list of space packets)
        VirtualChannelReceiverSpacePacketMapper<TmTransferFrame> packetMapper = new VirtualChannelReceiverSpacePacketMapper<>(vc0);
        // Create the space packet extraction processor
        VirtualChannelReceiverSpacePacketProcessor<TmTransferFrame> packetProcessor = new VirtualChannelReceiverSpacePacketProcessor<>(packetMapper, Executors.newFixedThreadPool(1), false);
        // Subscribe to the frame decoder processor
        frameDecoderProcessor.subscribe(packetProcessor);

        // Build a data collector
        List<SpacePacket> retrievedPackets = new CopyOnWriteArrayList<>();
        // Wrap the collector in a specific consumer
        ConsumerWrapper<SpacePacket> collector = new ConsumerWrapper<>(retrievedPackets::add);
        // Link the consumer to the space packet processor
        packetProcessor.subscribe(collector);

        // Start the processing by activating the publisher, the processing chain is in place
        rawFramePublisher.activate(false);
        // When the activate returns, all packets should be in the processing queue. The SupplierWrapper is in fact
        // asynchronous. So we need to wait a bit. Let's use active wait. Max wait is 10 seconds.
        for (int i = 0; i < 100; ++i) {
            Thread.sleep(100);
            if (retrievedPackets.size() == 613) {
                break;
            }
        }
        assertEquals(613, retrievedPackets.size());
    }

    @Test
    public void testSpacePacketDecodingFilterCompleteAsync() throws InterruptedException {
        // Build the reader (as supplier)
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1));
        // Wrap the reader in a Flow.Publisher
        SupplierWrapper<byte[]> rawFramePublisher = new SupplierWrapper<>(reader);

        // Build the frame decoder: TM Frame decoding function, no FECF
        ChannelDecoder<TmTransferFrame> cd = ChannelDecoder.create(TmTransferFrame.decodingFunction(false))
                .addDecodingFunction(new TmAsmDecoder()) // Add ASM removal with default ASM
                .addDecodingFunction(new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223)) // Add R-S symbol block removal 255/223
                .configure();
        // Create a ChannelDecoderProcessor, asynchronous processing, complete
        ChannelDecoderProcessor<TmTransferFrame> frameDecoderProcessor = new ChannelDecoderProcessor<>(cd, Executors.newFixedThreadPool(1), false);
        // Chain the frame decoder processor with the raw frame publisher
        rawFramePublisher.subscribe(frameDecoderProcessor);

        // Make a filter for VC0
        PredicateWrapper<TmTransferFrame> vc0filter = new PredicateWrapper<>((o) -> o.getVirtualChannelId() == 0);
        // Attach filter to frame decoder
        frameDecoderProcessor.subscribe(vc0filter);

        // Create a virtual channel for VC0, do not ignore other VCs (exception on VC violation)
        TmReceiverVirtualChannel vc0 = new TmReceiverVirtualChannel(0, VirtualChannelAccessMode.Packet, true);
        // Create a space packet extractor function on the virtual channel (frame -> list of space packets)
        VirtualChannelReceiverSpacePacketMapper<TmTransferFrame> packetMapper = new VirtualChannelReceiverSpacePacketMapper<>(vc0);
        // Create the space packet extraction processor
        VirtualChannelReceiverSpacePacketProcessor<TmTransferFrame> packetProcessor = new VirtualChannelReceiverSpacePacketProcessor<>(packetMapper, Executors.newFixedThreadPool(1), false);
        // Subscribe to the vc0 filter
        vc0filter.subscribe(packetProcessor);

        // Build a data collector
        List<SpacePacket> retrievedPackets = new CopyOnWriteArrayList<>();
        // Wrap the collector in a specific consumer
        ConsumerWrapper<SpacePacket> collector = new ConsumerWrapper<>(retrievedPackets::add);
        // Link the consumer to the space packet processor
        packetProcessor.subscribe(collector);

        // Start the processing by activating the publisher, the processing chain is in place
        rawFramePublisher.activate(false);
        // When the activate returns, all packets should be in the processing queue. The SupplierWrapper is in fact
        // asynchronous. So we need to wait a bit. Let's use active wait. Max wait is 10 seconds.
        for (int i = 0; i < 100; ++i) {
            Thread.sleep(100);
            if (retrievedPackets.size() == 613) {
                break;
            }
        }
        assertEquals(613, retrievedPackets.size());
    }
}