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

package eu.dariolucia.ccsds.tmtc.coding.reader;

import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TcFrameChannelReaderTest {

	private static final String FILE_TC = "dumpFile_tc_plain_2.hex";

	@Test
	void testReadNext() throws IOException {
		// Prepare the input
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TC)));
		String read;
		while ((read = br.readLine()) != null) {
			if (read.trim().isEmpty()) {
				continue;
			}
			bos.writeBytes(StringUtil.toByteArray(read.toUpperCase()));
		}

		ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());

		TcFrameChannelReader reader = new TcFrameChannelReader(bis);
		int counter = 0;
		byte[] frameRead = null;
		while((frameRead = reader.get()) != null) {
			++counter;
		}
		assertEquals(10, counter);
	}

	@Test
	void testReadSmallBuffer() throws IOException {
		{
			// Prepare the input
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TC)));
			String read;
			while ((read = br.readLine()) != null) {
				if (read.trim().isEmpty()) {
					continue;
				}
				bos.writeBytes(StringUtil.toByteArray(read.toUpperCase()));
			}

			ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());

			TcFrameChannelReader reader = new TcFrameChannelReader(bis);
			try {
				reader.readNext(new byte[3], 0, 3);
				fail("IOException expected");
			} catch (IOException e) {
				// Good
			}
		}
		{
			// Prepare the input
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TC)));
			String read;
			while ((read = br.readLine()) != null) {
				if (read.trim().isEmpty()) {
					continue;
				}
				bos.writeBytes(StringUtil.toByteArray(read.toUpperCase()));
			}

			ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());

			TcFrameChannelReader reader = new TcFrameChannelReader(bis);
			try {
				reader.readNext(new byte[30], 0, 30);
				fail("IOException expected");
			} catch (IOException e) {
				// Good
			}
		}
	}

	@Test
	void testReadMalformed() throws IOException {
		{
			// Prepare the input
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TC)));
			String read;
			while ((read = br.readLine()) != null) {
				if (read.trim().isEmpty()) {
					continue;
				}
				bos.writeBytes(StringUtil.toByteArray(read.toUpperCase()));
			}

			byte[] data = bos.toByteArray();
			// Cut the frame
			ByteArrayInputStream bis = new ByteArrayInputStream(Arrays.copyOfRange(data, 0, data.length - 1));

			TcFrameChannelReader reader = new TcFrameChannelReader(bis);
			int counter = 0;
			while (reader.get() != null) {
				++counter;
			}
			assertEquals(9, counter);
		}

		{
			// Prepare the input
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TC)));
			String read;
			while ((read = br.readLine()) != null) {
				if (read.trim().isEmpty()) {
					continue;
				}
				bos.writeBytes(StringUtil.toByteArray(read.toUpperCase()));
			}

			byte[] data = bos.toByteArray();
			// Cut the primary header
			ByteArrayInputStream bis = new ByteArrayInputStream(Arrays.copyOfRange(data, 0, data.length - 720));

			TcFrameChannelReader reader = new TcFrameChannelReader(bis);
			int counter = 0;
			while (reader.get() != null) {
				++counter;
			}
			assertEquals(9, counter);
		}
	}
}