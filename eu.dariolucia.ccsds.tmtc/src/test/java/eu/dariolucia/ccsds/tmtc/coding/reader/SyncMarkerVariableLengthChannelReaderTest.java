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

package eu.dariolucia.ccsds.tmtc.coding.reader;

import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class SyncMarkerVariableLengthChannelReaderTest {

	private static final String FILE_TC1 = "dumpFile_tc_1.hex";

	@Test
	public void testReadNext() throws IOException {
		// Prepare the input
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TC1)));
		String read = null;
		while ((read = br.readLine()) != null) {
			if (read.trim().isEmpty()) {
				continue;
			}
			bos.writeBytes(StringUtil.toByteArray(read.toUpperCase()));
		}

		ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
		// Use CLTU markers
		SyncMarkerVariableLengthChannelReader smReader = new SyncMarkerVariableLengthChannelReader(bis,
				new byte[]{(byte) 0xEB, (byte) 0x90},
				new byte[]{(byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, 0x79},
				true,
				true,
				4096);

		byte[] cltu = null;
		int counter = 0;
		while ((cltu = smReader.readNext()) != null) {
			++counter;
		}
		smReader.close();

		assertEquals(7, counter);
	}

	@Test
	public void testReadNextOutSync() throws IOException {
		// Prepare the input
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TC1)));
		String read = null;
		while ((read = br.readLine()) != null) {
			if (read.trim().isEmpty()) {
				continue;
			}
			bos.writeBytes(StringUtil.toByteArray(read.toUpperCase()));
		}
		byte[] stream = bos.toByteArray();
		stream[59] = (byte) 0x91;

		ByteArrayInputStream bis = new ByteArrayInputStream(stream);
		// Use CLTU markers
		SyncMarkerVariableLengthChannelReader smReader = new SyncMarkerVariableLengthChannelReader(bis,
				new byte[]{(byte) 0xEB, (byte) 0x90},
				new byte[]{(byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, 0x79});

		byte[] cltu = null;
		int counter = 0;
		while ((cltu = smReader.readNext()) != null) {
			++counter;
		}
		smReader.close();

		assertEquals(6, counter);
	}

	@Test
	public void testReadNextOutSyncException() throws IOException {
		// Prepare the input
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TC1)));
		String read = null;
		while ((read = br.readLine()) != null) {
			if (read.trim().isEmpty()) {
				continue;
			}
			bos.writeBytes(StringUtil.toByteArray(read.toUpperCase()));
		}
		byte[] stream = bos.toByteArray();
		stream[59] = (byte) 0x91;

		ByteArrayInputStream bis = new ByteArrayInputStream(stream);
		// Use CLTU markers
		SyncMarkerVariableLengthChannelReader smReader = new SyncMarkerVariableLengthChannelReader(bis,
				new byte[]{(byte) 0xEB, (byte) 0x90},
				new byte[]{(byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, 0x79},
				true,
				true,
				4096);

		try {
			byte[] cltu = null;
			int counter = 0;
			while ((cltu = smReader.readNext()) != null) {
				++counter;
			}
			fail("SynchronizationLostException expected");
		} catch (SynchronizationLostException e) {
			// Good
			smReader.close();
		}
	}

	@Test
	public void testReadNextIoException() throws IOException {
		// Prepare the input
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TC1)));
		String read = null;
		while ((read = br.readLine()) != null) {
			if (read.trim().isEmpty()) {
				continue;
			}
			bos.writeBytes(StringUtil.toByteArray(read.toUpperCase()));
		}
		byte[] stream = bos.toByteArray();
		stream = Arrays.copyOfRange(stream, 0, stream.length - 1);
		ByteArrayInputStream bis = new ByteArrayInputStream(stream);
		// Use CLTU markers
		SyncMarkerVariableLengthChannelReader smReader = new SyncMarkerVariableLengthChannelReader(bis,
				new byte[]{(byte) 0xEB, (byte) 0x90},
				new byte[]{(byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, 0x79},
				true,
				true,
				4096);

		try {
			byte[] cltu = null;
			int counter = 0;
			while ((cltu = smReader.readNext()) != null) {
				++counter;
			}
			fail("IOException expected");
		} catch (IOException e) {
			// Good
			smReader.close();
		}
	}
}