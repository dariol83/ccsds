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

package eu.dariolucia.ccsds.tmtc.datalink.channel.sender.processor;

import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.transport.pdu.BitstreamData;
import eu.dariolucia.ccsds.tmtc.util.internal.TransformationStreamProcessor;

import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Stream;

public class VirtualChannelSenderBitstreamDataProcessor<T extends AbstractTransferFrame> extends TransformationStreamProcessor<BitstreamData, T> {

	public VirtualChannelSenderBitstreamDataProcessor(Function<BitstreamData, Stream<T>> mapper, ExecutorService executor, boolean timely) {
		super(mapper, executor, timely);
	}

	public VirtualChannelSenderBitstreamDataProcessor(Function<BitstreamData, Stream<T>> mapper, boolean timely) {
		this(mapper, null, timely);
	}

	public VirtualChannelSenderBitstreamDataProcessor(Function<BitstreamData, Stream<T>> mapper) {
		this(mapper, false);
	}
}
