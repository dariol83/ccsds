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

package eu.dariolucia.ccsds.tmtc.datalink.channel.sender.mux;

import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.AbstractSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.IVirtualChannelSenderOutput;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * This class implements a simple muxer for the various virtual channels. It makes sure that the generated frames go
 * out from the muxer in the order imposed by the virtual channel frame counter.
 *
 * This class implements the Consumer interface, so that it can be used to receive frames from any stream of
 * {@link eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame} objects. In addition, this class implements the
 * IVirtualChannelSenderOutput, so objects of this class can be registered to a set of virtual channels and provide mux capabilities.
 *
 * This class does not implement any load balancing across the registered virtual channels (i.e. priorities), however it
 * makes sure that no virtual channel suffers from starvation.
 *
 * This class is thread safe.
 */
public class SimpleMuxer<T extends AbstractTransferFrame> implements Consumer<T>, IVirtualChannelSenderOutput<T> {

	protected final Consumer<T> output;

	/**
	 * Create a new muxer, which forwards received frames to the provided sink.
	 *
	 * @param output the sink for received TM frames.
	 */
	public SimpleMuxer(Consumer<T> output) {
		this.output = output;
	}

	/**
	 * This method forwards the frame to the sink. This implementation assumes that frames can be immediately forwarded.
	 * If this approach is not suitable for the application, then this class can be subclassed and this method rewritten
	 * to implement the desired approach.
	 *
	 * @param frame the frame to forward to the sink
	 */
	@Override
	public synchronized void accept(T frame) {
		// Forward it to the output directly, no real check
		this.output.accept(frame);
	}

	/**
	 * This method forwards the generated frame to the apply method. The virtual channel object and the bufferedBytes
	 * value are ignored.
	 *
	 * @param vc The virtual channel that generated the frame
	 * @param generatedFrame The received frame
	 * @param bufferedBytes the number of bytes still in the virtual channel buffer
	 */
	@Override
	public void transferFrameGenerated(AbstractSenderVirtualChannel<T> vc, T generatedFrame, int bufferedBytes) {
		accept(generatedFrame);
	}
}
