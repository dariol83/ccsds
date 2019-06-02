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

package eu.dariolucia.ccsds.tmtc.algorithm;

import eu.dariolucia.ccsds.tmtc.datalink.builder.TcTransferFrameBuilder;
import eu.dariolucia.ccsds.tmtc.datalink.builder.TmTransferFrameBuilder;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class RandomizerAlgorithmTest {

	@Test
	public void testTmRandomization() {
		// Build a frame
		int userDataLength = TmTransferFrameBuilder.computeUserDataLength(1115, 0, true, false);
		TmTransferFrameBuilder builder = TmTransferFrameBuilder.create(1115, 0, true, false)
				.setSpacecraftId(789)
				.setVirtualChannelId(2)
				.setMasterChannelFrameCount(34)
				.setVirtualChannelFrameCount(123)
				.setPacketOrderFlag(false)
				.setSynchronisationFlag(false)
				.setSegmentLengthIdentifier(3)
				.setOcf(new byte[] { 0x00, 0x00, 0x00, 0x00 })
				.setIdle();

		int residual = builder.addData(new byte[userDataLength]);
		assertEquals(0, residual);

		TmTransferFrame ttf = builder.build();

		// Get a copy of the frame for randomization
		byte[] theFrame = ttf.getFrameCopy();
		// Randomize
		RandomizerAlgorithm.randomizeFrameTm(theFrame);
		// De-randomize
		RandomizerAlgorithm.randomizeFrameTm(theFrame);
		// Compare
		assertTrue(Arrays.equals(theFrame, ttf.getFrame()));
	}

	@Test
	public void testCltuRandomization() {
		// Build a frame
		int userDataLength = 112;
		TcTransferFrameBuilder builder = TcTransferFrameBuilder.create(false)
				.setSpacecraftId(789)
				.setVirtualChannelId(2)
				.setFrameSequenceNumber(123)
				.setBypassFlag(true)
				.setControlCommandFlag(false);

		int residual = builder.addData(new byte[userDataLength]);
		assertEquals(0, residual);

		TcTransferFrame ttf = builder.build();

		// Get a copy of the frame for randomization
		byte[] theFrame = ttf.getFrameCopy();
		// Randomize
		RandomizerAlgorithm.randomizeFrameCltu(theFrame);
		// De-randomize
		RandomizerAlgorithm.randomizeFrameCltu(theFrame);
		// Compare
		assertArrayEquals(theFrame, ttf.getFrame());
	}
}