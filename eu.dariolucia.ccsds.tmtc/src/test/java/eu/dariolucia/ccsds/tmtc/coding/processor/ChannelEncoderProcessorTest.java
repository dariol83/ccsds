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

package eu.dariolucia.ccsds.tmtc.coding.processor;

import eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm;
import eu.dariolucia.ccsds.tmtc.coding.ChannelEncoder;
import eu.dariolucia.ccsds.tmtc.coding.encoder.ReedSolomonEncoder;
import eu.dariolucia.ccsds.tmtc.coding.encoder.TmAsmEncoder;
import eu.dariolucia.ccsds.tmtc.datalink.builder.TmTransferFrameBuilder;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.util.processor.ConsumerWrapper;
import eu.dariolucia.ccsds.tmtc.util.processor.SupplierWrapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChannelEncoderProcessorTest {

	@Test
	public void testTmEncodingCompleteSync() throws InterruptedException {
		// TM frame supplier (publisher)
		AtomicInteger counter = new AtomicInteger(100);
		SupplierWrapper<TmTransferFrame> rawFramePublisher = new SupplierWrapper<>(() -> counter.getAndDecrement() == 0 ? null : generateFrame());

		// Build the structure: TM frame to RS, ASM
		ChannelEncoder<TmTransferFrame> cd = ChannelEncoder.<TmTransferFrame>create()
				.addEncodingFunction(new ReedSolomonEncoder(ReedSolomonAlgorithm.TM_255_223, 5)) // Add R-S symbol block 255/223
				.addEncodingFunction(new TmAsmEncoder()) // Add ASM
				.configure();
		// Create a ChannelDecoderProcessor, no asynchronous processing, complete
		ChannelEncoderProcessor<TmTransferFrame> encoderProcessor = new ChannelEncoderProcessor<>(cd);
		// Chain the processor with the published
		rawFramePublisher.subscribe(encoderProcessor);

		// Build a data collector
		List<byte[]> generatedFrames = new CopyOnWriteArrayList<>();
		// Wrap the collector in a specific consumer
		ConsumerWrapper<byte[]> collector = new ConsumerWrapper<>(generatedFrames::add);
		// Link the consumer to the processor
		encoderProcessor.subscribe(collector);

		// Start the processing by activating the publisher, the processing chain is in place
		rawFramePublisher.activate(false);

		// When the activate returns, all frames should be in the processing queue. The SupplierWrapper is in fact
		// asynchronous. So we need to wait a bit. Let's use active wait. Max wait is 10 seconds.
		for (int i = 0; i < 100; ++i) {
			Thread.sleep(100);
			if (generatedFrames.size() == 100) {
				break;
			}
		}
		assertEquals(100, generatedFrames.size());
	}

	public TmTransferFrame generateFrame() {
		int userDataLength = TmTransferFrameBuilder.computeUserDataLength(1115, 0, true, false);
		TmTransferFrameBuilder builder = TmTransferFrameBuilder.create(1115, 0, true, false)
				.setSpacecraftId(789)
				.setVirtualChannelId(2)
				.setMasterChannelFrameCount(34)
				.setVirtualChannelFrameCount(123)
				.setPacketOrderFlag(false)
				.setSynchronisationFlag(false)
				.setSegmentLengthIdentifier(3)
				.setOcf(new byte[]{0x00, 0x00, 0x00, 0x00})
				.setIdle();

		builder.addData(new byte[userDataLength]);
		return builder.build();
	}
}