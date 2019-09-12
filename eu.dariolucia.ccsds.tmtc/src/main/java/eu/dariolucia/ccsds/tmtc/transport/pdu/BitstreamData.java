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

package eu.dariolucia.ccsds.tmtc.transport.pdu;

/**
 * This class provides an implementation of a bitstream, as defined by the AOS data link protocol.
 */
public class BitstreamData {

	private static final BitstreamData INVALID_BITSTREAM_DATA = new BitstreamData();

	public static BitstreamData invalid() {
		return INVALID_BITSTREAM_DATA;
	}

	private final byte[] data;
	private final int numBits;
	private final boolean invalid;

	public BitstreamData(byte[] data, int numBits) {
		int requiredData = numBits / 8 + (numBits % 8 == 0 ? 0 : 1);
		if (data.length < requiredData) {
			throw new IllegalArgumentException("Bitstream length does not match required data: got " + data.length + ", expected " + requiredData);
		}
		this.data = data;
		this.numBits = numBits;
		this.invalid = false;
	}

	private BitstreamData() {
		this.data = null;
		this.numBits = 0;
		this.invalid = true;
	}

	public boolean isInvalid() {
		return invalid;
	}

	public byte[] getData() {
		return data;
	}

	public int getNumBits() {
		return numBits;
	}
}
