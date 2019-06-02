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

package eu.dariolucia.ccsds.tmtc.coding;

import eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm;
import eu.dariolucia.ccsds.tmtc.coding.encoder.ReedSolomonEncoder;
import eu.dariolucia.ccsds.tmtc.coding.encoder.TmAsmEncoder;
import eu.dariolucia.ccsds.tmtc.datalink.builder.TmTransferFrameBuilder;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.util.StreamUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TmChannelEncoderTest {

	@Test
	public void testTmEncodingWithStream() {
		// TM frame supplier (publisher)
		AtomicInteger counter = new AtomicInteger(100);

		// Use stream approach: no need for structure
		List<byte[]> frames = StreamUtil.from(() -> counter.getAndDecrement() == 0 ? null : generateFrame()) // Publish the frames
				.map(TmTransferFrame::getFrame)
				.map(new ReedSolomonEncoder<TmTransferFrame>(ReedSolomonAlgorithm.TM_255_223).asFunction())
				.map(new TmAsmEncoder<TmTransferFrame>().asFunction())
				.collect(Collectors.toList());

		assertEquals(100, frames.size());
		for (byte[] frame : frames) {
			assertEquals(1279, frame.length);
			assertArrayEquals(TmAsmEncoder.DEFAULT_ATTACHED_SYNC_MARKER, Arrays.copyOfRange(frame, 0, 4));
			byte[] realFrame = Arrays.copyOfRange(frame, 4, frame.length - 160);
			TmTransferFrame ttf = new TmTransferFrame(realFrame, false);
			assertEquals(789, ttf.getSpacecraftId());
			assertEquals(2, ttf.getVirtualChannelId());
			assertEquals(123, ttf.getVirtualChannelFrameCount());
			assertEquals(34, ttf.getMasterChannelFrameCount());
		}
	}

	@Test
	public void testTmEncodingWithStreamAndEncoder() {
		// TM frame supplier (publisher)
		AtomicInteger counter = new AtomicInteger(100);

		// Build the structure: TM Frame encoding function, no FECF, ASM, R-S
		ChannelEncoder<TmTransferFrame> encoder = ChannelEncoder.<TmTransferFrame>create()
				.addEncodingFunction(new ReedSolomonEncoder<TmTransferFrame>(ReedSolomonAlgorithm.TM_255_223)) // Add R-S symbol block 255/223
				.addEncodingFunction(new TmAsmEncoder<TmTransferFrame>()) // Add ASM encoding with default ASM
				.configure();
		// Use stream approach with decoder
		List<byte[]> frames = StreamUtil.from(() -> counter.getAndDecrement() == 0 ? null : generateFrame()) // Publish the frames
				.map(encoder) // Use the preconfigured structure
				.collect(Collectors.toList());

		assertEquals(100, frames.size());
		for (byte[] frame : frames) {
			assertEquals(1279, frame.length);
			assertArrayEquals(TmAsmEncoder.DEFAULT_ATTACHED_SYNC_MARKER, Arrays.copyOfRange(frame, 0, 4));
			byte[] realFrame = Arrays.copyOfRange(frame, 4, frame.length - 160);
			TmTransferFrame ttf = new TmTransferFrame(realFrame, false);
			assertEquals(789, ttf.getSpacecraftId());
			assertEquals(2, ttf.getVirtualChannelId());
			assertEquals(123, ttf.getVirtualChannelFrameCount());
			assertEquals(34, ttf.getMasterChannelFrameCount());
		}
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