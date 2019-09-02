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

/**
 * Implementation of this interface is required to collect information from a specific {@link AbstractReceiverVirtualChannel} object.
 */
public interface IVirtualChannelReceiverOutput {

    /**
     * This method signals the reception of the provided transfer frame from the provided virtual channel.
     *
     * @param vc The virtual channel that received the frame
     * @param receivedFrame The received frame
     */
    void transferFrameReceived(AbstractReceiverVirtualChannel vc, AbstractTransferFrame receivedFrame);

    /**
     * This method signals the extraction of the provided packet, including the last frame, from the provided virtual channel.
     *
     * @param vc the virtual channel that extracted the packet
     * @param firstFrame the frame containing the last part of the packet
     * @param packet the extracted space packet
     * @param qualityIndicator true if the packet has been fully extracted, false if there were problems (frame gap, packet overlap)
     */
    void spacePacketExtracted(AbstractReceiverVirtualChannel vc,AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator);

    /**
     * This method signals the reception of the provided user data from the provided virtual channel.
     *
     * @param vc the virtual channel that extracted the user data
     * @param frame the frame containing the user data
     * @param data the user data
     */
    void dataExtracted(AbstractReceiverVirtualChannel vc,AbstractTransferFrame frame, byte[] data);

    /**
     * This method signals the reception of the provided bit data from the provided virtual channel.
     *
     * @param vc the virtual channel that extracted the bit data
     * @param frame the frame containing the bit data
     * @param data the bit data
     * @param numBits the number of valid bits in the bit data
     */
    void bitstreamExtracted(AbstractReceiverVirtualChannel vc,AbstractTransferFrame frame, byte[] data, int numBits);

    /**
     * This method signals the identification of a frame gap.
     *
     * @param vc the virtual channel that identified the gap
     * @param expectedVc the expected VC counter
     * @param receivedVc the received VC counter
     * @param missingFrames the computed number of missing frames
     */
    void gapDetected(AbstractReceiverVirtualChannel vc,int expectedVc, int receivedVc, int missingFrames);
}
