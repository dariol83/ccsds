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

import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
class BchCltuAlgorithmTest {

	@Test
	public void testDecodeEncodeCltu() {
		String cltu = "EB902062042900C10036010203040506077008090A0BAABBCCC4DDEEFFAABBCCDDE6EEFFAABBCCDDEE10FFAABBCCDDEEFF64C5C5C5C5C5C5C579";
		byte[] cltuBytes = StringUtil.toByteArray(cltu.toUpperCase());

		byte[] tcFrame = BchCltuAlgorithm.decode(cltuBytes);
		assertNotNull(tcFrame);
		TcTransferFrame tctf = new TcTransferFrame(tcFrame, true, false);

		assertTrue(tctf.isValid());

		byte[] encoded = BchCltuAlgorithm.encode(tctf.getFrameCopy());

		assertArrayEquals(cltuBytes, encoded);
	}
}