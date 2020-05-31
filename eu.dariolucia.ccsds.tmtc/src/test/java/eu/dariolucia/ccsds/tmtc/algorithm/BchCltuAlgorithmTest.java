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
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
class BchCltuAlgorithmTest {

	@Test
	public void testDecodeEncodeCltu() {
		String cltu = "EB902062042900C10036010203040506077008090A0BAABBCCC4DDEEFFAABBCCDDE6EEFFAABBCCDDEE10FFAABBCCDDEEFF64C5C5C5C5C5C5C579";
		byte[] cltuBytes = StringUtil.toByteArray(cltu.toUpperCase());

		byte[] tcFrame = BchCltuAlgorithm.decode(cltuBytes);
		assertNotNull(tcFrame);
		TcTransferFrame tctf = new TcTransferFrame(tcFrame, (vc) -> true, false);

		assertTrue(tctf.isValid());

		byte[] encoded = BchCltuAlgorithm.encode(tctf.getFrameCopy());

		assertArrayEquals(cltuBytes, encoded);
	}

	@Test
	public void testEncodingWithCustomPrefixSuffixFill() {
		BchCltuAlgorithm customAlgorithm = new BchCltuAlgorithm(
				new byte[] {(byte) 0xfc, (byte) 0x80},
				(byte) 0x11,
				new byte[]{(byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, 0x79});

		TcTransferFrameBuilder builder = TcTransferFrameBuilder.create(false)
				.setSpacecraftId(123)
				.setVirtualChannelId(1)
				.setFrameSequenceNumber(0)
				.setControlCommandFlag(false)
				.setBypassFlag(false);
		builder.addData(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });

		TcTransferFrame tctf = builder.build();
		assertEquals(15, tctf.getLength());

		byte[] encoded = customAlgorithm.encodeCltu(tctf.getFrameCopy());
		// 2 (prefix) + 15 (TC frame length) + 3 (parity bytes for 7 + 7 + 1) + 6 (effective fill for the last block) + 8 (suffix)
		assertEquals(2 + 15 + 3 + 6 + 8, encoded.length);
		// Prefix
		assertEquals((byte) 0xfc, encoded[0]);
		assertEquals((byte) 0x80, encoded[1]);
		// Fill
		assertEquals((byte) 0x11, encoded[19]);
		assertEquals((byte) 0x11, encoded[20]);
		assertEquals((byte) 0x11, encoded[21]);
		assertEquals((byte) 0x11, encoded[22]);
		assertEquals((byte) 0x11, encoded[23]);
		assertEquals((byte) 0x11, encoded[24]);
		// Suffix
		assertArrayEquals(new byte[]{(byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, 0x79},
				Arrays.copyOfRange(encoded, encoded.length - 8, encoded.length));

		byte[] decodedWithFill = customAlgorithm.decodeCltu(encoded);
		assertEquals(15 + 6, decodedWithFill.length);

		TcTransferFrame decoded = TcTransferFrame.decodingFunction((vc) -> false, false).apply(decodedWithFill);
		assertNotNull(decoded);
		assertEquals(tctf.getSpacecraftId(), decoded.getSpacecraftId());
		assertEquals(tctf.getVirtualChannelId(), decoded.getVirtualChannelId());
		assertEquals(tctf.getVirtualChannelFrameCount(), decoded.getVirtualChannelFrameCount());
		assertEquals(tctf.isControlCommandFlag(), decoded.isControlCommandFlag());
		assertEquals(tctf.isBypassFlag(), decoded.isBypassFlag());
		assertEquals(tctf.getLength(), decoded.getLength());


	}
}