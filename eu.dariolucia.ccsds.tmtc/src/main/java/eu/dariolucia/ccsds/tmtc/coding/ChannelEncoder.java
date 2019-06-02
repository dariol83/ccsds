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

import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

public class ChannelEncoder<T extends AbstractTransferFrame> implements Function<T, byte[]> {


	public static <T extends AbstractTransferFrame> ChannelEncoder<T> create() {
		return create(false);
	}

	public static <T extends AbstractTransferFrame> ChannelEncoder<T> create(boolean frameCopy) {
		return new ChannelEncoder<>(frameCopy);
	}

	private final List<IEncodingFunction> sequentialEncoders = new LinkedList<>();

	private final boolean frameCopy;

	private boolean configured = false;

	private ChannelEncoder(boolean frameCopy) {
		this.frameCopy = frameCopy;
	}

	public ChannelEncoder<T> addEncodingFunction(IEncodingFunction... functions) {
		if (this.configured) {
			throw new IllegalStateException("Channel structure already configured");
		}
		this.sequentialEncoders.addAll(Arrays.asList(functions));
		return this;
	}

	public ChannelEncoder<T> configure() {
		this.configured = true;
		return this;
	}

	@Override
	public byte[] apply(T abstractTransferFrame) {
		if (!this.configured) {
			throw new IllegalStateException("Channel structure not configured yet");
		}
		byte[] toEncode;
		if (this.frameCopy) {
			toEncode = abstractTransferFrame.getFrameCopy();
		} else {
			toEncode = abstractTransferFrame.getFrame();
		}
		for (IEncodingFunction f : sequentialEncoders) {
			toEncode = f.encode(abstractTransferFrame, toEncode);
		}
		return toEncode;
	}
}
