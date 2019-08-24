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

/**
 * An object capable to encode an {@link AbstractTransferFrame} into a byte[] suitable to be transmitted. An instance of
 * this class allows the addition of a list of {@link IEncodingFunction}, which are applied in order to each provided
 * subclass instance of {@link AbstractTransferFrame}.
 *
 * Depending on the type of encoding functions, it is possible to instruct the channel encoder to perform a copy of the
 * frame before submitting it to the transformation process, or to run the process directly working on the inner frame
 * buffer. Bear in mind that some transformations, such as the (de)randomization functions, are implemented to run in-place.
 *
 * In order to instantiate and configure a channel encoder, the following build pattern must be used:
 * <ul>
 * <li>A new {@link ChannelEncoder} instance is created using the create() method. By default, create() instructs the channel encoder
 * to not perform a frame copy;</li>
 * <li>Zero or more encoding functions can be added by means of the addEncodingFunction method;</li>
 * <li>To complete the configuration of the object, the method configure must be invoked.</li>
 * </ul>
 *
 * If configure is invoked and a new encoder is added, an exception is thrown.
 * If the channel encoder is attempted to be used without invoking the configure method, an exception is thrown.
 *
 * @param <T> subclass of the {@link AbstractTransferFrame} class
 */
public class ChannelEncoder<T extends AbstractTransferFrame> implements Function<T, byte[]> {

	/**
	 * This static method creates a channel encoder, which does not copy the content of the {@link AbstractTransferFrame}.
	 * It means that the {@link AbstractTransferFrame} could suffer from side-effects if a registered {@link IEncodingFunction}
	 * performs in-place modifications.
	 *
	 * @param <T> the {@link AbstractTransferFrame} specific type
	 * @return a new channel encoder to be configured
	 */
	public static <T extends AbstractTransferFrame> ChannelEncoder<T> create() {
		return create(false);
	}

	/**
	 * This static method creates a channel encoder. Copy of the content of each {@link AbstractTransferFrame} is performed
	 * according to the value of the frameCopy argument.
	 *
	 * @param frameCopy true if the frame content must be copied before encoding it (the copy is encoded), false otherwise
	 * @param <T> the {@link AbstractTransferFrame} specific type
	 * @return a new channel encoder to be configured
	 */
	public static <T extends AbstractTransferFrame> ChannelEncoder<T> create(boolean frameCopy) {
		return new ChannelEncoder<>(frameCopy);
	}

	private final List<IEncodingFunction<T>> sequentialEncoders = new LinkedList<>();

	private final boolean frameCopy;

	private boolean configured = false;

	private ChannelEncoder(boolean frameCopy) {
		this.frameCopy = frameCopy;
	}

	/**
	 * This method adds an encoding function T, byte[] -> byte[] to the encoding chain. Functions are applied in the
	 * order used to add them to the channel encoder.
	 *
	 * @param function the {@link IEncodingFunction} to add
	 * @return this object instance
	 */
	public ChannelEncoder<T> addEncodingFunction(IEncodingFunction<T> function) {
		if (this.configured) {
			throw new IllegalStateException("Channel structure already configured");
		}
		this.sequentialEncoders.add(function);
		return this;
	}

	/**
	 * This method marks the encoder as configured, allows its usage and blocks any further addition of new {@link IEncodingFunction}
	 * objects.
	 *
	 * @return this object instance
	 */
	public ChannelEncoder<T> configure() {
		this.configured = true;
		return this;
	}

	/**
	 * This method applies the full encoding pipeline.
	 *
	 * @param abstractTransferFrame the transfer frame to encode
	 * @return the encoded frame
	 * @throws IllegalStateException if the encoder is not configured via ({@link ChannelEncoder#configure()}
	 */
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
		for (IEncodingFunction<T> f : sequentialEncoders) {
			toEncode = f.apply(abstractTransferFrame, toEncode);
		}
		return toEncode;
	}
}
