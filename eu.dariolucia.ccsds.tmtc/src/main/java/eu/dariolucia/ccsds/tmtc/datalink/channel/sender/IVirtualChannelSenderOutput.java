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

package eu.dariolucia.ccsds.tmtc.datalink.channel.sender;

import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.AbstractReceiverVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;

@FunctionalInterface
public interface IVirtualChannelSenderOutput<T extends AbstractTransferFrame> {

    /**
     * This method signals the generation of a transfer frame by the given virtual channel.
     *
     * @param vc The virtual channel that generated the frame
     * @param generatedFrame The received frame
     * @param bufferedBytes the number of bytes still in the virtual channel buffer
     */
    void transferFrameGenerated(AbstractSenderVirtualChannel<T> vc, T generatedFrame, int bufferedBytes);

}
