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

package eu.dariolucia.ccsds.tmtc.datalink.channel.sender.processor;

import eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm;
import eu.dariolucia.ccsds.tmtc.coding.ChannelEncoder;
import eu.dariolucia.ccsds.tmtc.coding.encoder.ReedSolomonEncoder;
import eu.dariolucia.ccsds.tmtc.coding.encoder.TmAsmEncoder;
import eu.dariolucia.ccsds.tmtc.coding.processor.ChannelEncoderProcessor;
import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.AosSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.function.VirtualChannelSenderBitstreamDataFlatMapper;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AosTransferFrame;
import eu.dariolucia.ccsds.tmtc.ocf.builder.ClcwBuilder;
import eu.dariolucia.ccsds.tmtc.ocf.pdu.AbstractOcf;
import eu.dariolucia.ccsds.tmtc.transport.pdu.BitstreamData;
import eu.dariolucia.ccsds.tmtc.util.processor.ConsumerWrapper;
import eu.dariolucia.ccsds.tmtc.util.processor.SupplierWrapper;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VirtualChannelSenderBitstreamProcessorTest {

	@Test
	public void testAosBitstreamDataSendingChainProcessor() throws InterruptedException {
		// First of all, create the VC sender: capable to process user data blocks and build AOS frames.
		// I build it here, so that I can get the max user data field size, this is needed to build the bitstream blocks so that the frame is filled
		// (even if useful data might be less).
		AosSenderVirtualChannel vc0 = new AosSenderVirtualChannel(151, 0, VirtualChannelAccessMode.Bitstream, false, 1115, this::ocfSupplier);

		// Prebuild 5508 bitstream blocks of 306 bytes: it should generate 5508 TM frames
		List<BitstreamData> dataBlocks = new LinkedList<>();
		for (int i = 0; i < 5508; ++i) {
			dataBlocks.add(new BitstreamData(new byte[vc0.getMaxUserDataLength()], 306 * Byte.SIZE));
		}
		// Create a supplier processor
		Iterator<BitstreamData> it = dataBlocks.iterator();
		SupplierWrapper<BitstreamData> spSupplier = new SupplierWrapper<>(() -> it.hasNext() ? it.next() : null);

		// Create the mapping function
		VirtualChannelSenderBitstreamDataFlatMapper<AosTransferFrame> vc0mapper = new VirtualChannelSenderBitstreamDataFlatMapper<>(vc0);
		// Create the VC processor
		VirtualChannelSenderBitstreamDataProcessor<AosTransferFrame> vc0processor = new VirtualChannelSenderBitstreamDataProcessor<>(vc0mapper);
		// Link the processor to the space packet supplier
		spSupplier.subscribe(vc0processor);

		// Create the encoding process: R-S encoding, ASM
		ChannelEncoder<AosTransferFrame> encoder = ChannelEncoder.create();
		encoder.addEncodingFunction(new ReedSolomonEncoder<>(ReedSolomonAlgorithm.TM_255_223, 5));
		encoder.addEncodingFunction(new TmAsmEncoder<>());
		encoder.configure();
		// Create the encoding processor
		ChannelEncoderProcessor<AosTransferFrame> encoderProcessor = new ChannelEncoderProcessor<>(encoder);
		// Link the encoding processor to the VC processor
		vc0processor.subscribe(encoderProcessor);

		// Create a frame collector of raw bytes
		List<byte[]> generatedFrames = new CopyOnWriteArrayList<>();
		// Wrap the collector in a specific consumer
		ConsumerWrapper<byte[]> collector = new ConsumerWrapper<>(generatedFrames::add);
		// Link the consumer to the encoding processor
		encoderProcessor.subscribe(collector);

		spSupplier.activate(false);
		// When the activate returns, all packets should be in the processing queue. The SupplierWrapper is in fact
		// asynchronous. So we need to wait a bit. Let's use active wait. Max wait is 10 seconds.
		for (int i = 0; i < 100; ++i) {
			Thread.sleep(100);
			if (generatedFrames.size() == 5508) {
				break;
			}
		}
		assertEquals(5508, generatedFrames.size());
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