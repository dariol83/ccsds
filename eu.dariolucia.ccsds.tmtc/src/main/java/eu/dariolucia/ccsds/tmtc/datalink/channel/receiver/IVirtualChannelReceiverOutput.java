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

package eu.dariolucia.ccsds.tmtc.datalink.channel.receiver;

import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;

public interface IVirtualChannelReceiverOutput {

    /**
     * This method signals the reception of the provided transfer frame from the given virtual channel.
     *
     * @param vc The virtual channel that received the frame
     * @param receivedFrame The received frame
     */
    void transferFrameReceived(AbstractReceiverVirtualChannel vc, AbstractTransferFrame receivedFrame);

    void spacePacketExtracted(AbstractReceiverVirtualChannel vc,AbstractTransferFrame lastFrame, byte[] packet, boolean qualityIndicator);

    void dataExtracted(AbstractReceiverVirtualChannel vc,AbstractTransferFrame frame, byte[] data);

    void bitstreamExtracted(AbstractReceiverVirtualChannel vc,AbstractTransferFrame frame, byte[] data, int numBits);

    void gapDetected(AbstractReceiverVirtualChannel vc,int expectedVc, int receivedVc, int missingFrames);
}
