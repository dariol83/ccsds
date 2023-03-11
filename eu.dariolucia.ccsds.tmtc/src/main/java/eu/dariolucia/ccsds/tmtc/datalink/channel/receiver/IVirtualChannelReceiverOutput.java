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

import java.util.List;

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
    default void transferFrameReceived(AbstractReceiverVirtualChannel vc, AbstractTransferFrame receivedFrame) {
        // None
    }

    /**
     * This method signals the extraction of the provided space packet, including the first frame, from the provided virtual channel.
     *
     * @param vc the virtual channel that extracted the packet
     * @param firstFrame the frame containing the first part of the packet
     * @param packet the extracted space packet
     * @param qualityIndicator true if the packet has been fully extracted, false if there were problems (frame gap, packet overlap)
     */
    default void spacePacketExtracted(AbstractReceiverVirtualChannel vc,AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
        // None
    }

    /**
     * This method signals the extraction of the provided space packet, including the first frame, from the provided virtual channel.
     *
     * @param vc the virtual channel that extracted the packet
     * @param firstFrame the frame containing the first part of the packet
     * @param packet the extracted space packet
     * @param qualityIndicator true if the packet has been fully extracted, false if there were problems (frame gap, packet overlap)
     * @param gaps the gaps present in the packet, due to frame gaps
     */
    default void spacePacketExtracted(AbstractReceiverVirtualChannel vc,AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator, List<PacketGap> gaps) {
        // for backward compatibility
        spacePacketExtracted(vc, firstFrame, packet, qualityIndicator);
    }

    /**
     * This method signals the extraction of the provided encapsulation packet, including the first frame, from the provided virtual channel.
     *
     * @param vc the virtual channel that extracted the packet
     * @param firstFrame the frame containing the first part of the packet
     * @param packet the extracted space packet
     * @param qualityIndicator true if the packet has been fully extracted, false if there were problems (frame gap, packet overlap)
     */
    default void encapsulationPacketExtracted(AbstractReceiverVirtualChannel vc,AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
        // None
    }

    /**
     * This method signals the extraction of the provided encapsulation packet, including the first frame, from the provided virtual channel.
     *
     * @param vc the virtual channel that extracted the packet
     * @param firstFrame the frame containing the first part of the packet
     * @param packet the extracted space packet
     * @param qualityIndicator true if the packet has been fully extracted, false if there were problems (frame gap, packet overlap)
     * @param gaps the gaps present in the packet, due to frame gaps
     */
    default void encapsulationPacketExtracted(AbstractReceiverVirtualChannel vc,AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator, List<PacketGap> gaps) {
        // for backward compatibility
        encapsulationPacketExtracted(vc, firstFrame, packet, qualityIndicator);
    }

    /**
     * This method signals the reception of the provided user data from the provided virtual channel.
     *
     * @param vc the virtual channel that extracted the user data
     * @param frame the frame containing the user data
     * @param data the user data
     */
    default void dataExtracted(AbstractReceiverVirtualChannel vc,AbstractTransferFrame frame, byte[] data) {
        // None
    }

    /**
     * This method signals the reception of the provided user data from the provided virtual channel, with the information
     * related to the number of missing bytes, if a gap is detected.
     *
     * @param vc the virtual channel that extracted the user data
     * @param frame the frame containing the user data
     * @param data the user data
     * @param missingBytes the number of bytes missed from the same virtual channel, due to frame gaps
     */
    default void dataExtracted(AbstractReceiverVirtualChannel vc,AbstractTransferFrame frame, byte[] data, int missingBytes) {
        // for backward compatibility
        dataExtracted(vc, frame, data);
    }

    /**
     * This method signals the reception of the provided bit data from the provided virtual channel.
     *
     * @param vc the virtual channel that extracted the bit data
     * @param frame the frame containing the bit data
     * @param data the bit data
     * @param numBits the number of valid bits in the bit data
     */
    default void bitstreamExtracted(AbstractReceiverVirtualChannel vc,AbstractTransferFrame frame, byte[] data, int numBits) {
        // None
    }

    /**
     * This method signals the reception of the provided bit data from the provided virtual channel.
     *
     * @param vc the virtual channel that extracted the bit data
     * @param frame the frame containing the bit data
     * @param data the bit data
     * @param numBits the number of valid bits in the bit data
     * @param missingBytes the number of bytes missed from the same virtual channel, due to frame gaps
     */
    default void bitstreamExtracted(AbstractReceiverVirtualChannel vc,AbstractTransferFrame frame, byte[] data, int numBits, int missingBytes) {
        // for backward compatibility
        bitstreamExtracted(vc, frame, data, numBits);
    }

    /**
     * This method signals the identification of a frame gap.
     *
     * @param vc the virtual channel that identified the gap
     * @param expectedVc the expected VC counter
     * @param receivedVc the received VC counter
     * @param missingFrames the computed number of missing frames
     */
    default void gapDetected(AbstractReceiverVirtualChannel vc,int expectedVc, int receivedVc, int missingFrames) {
        // None
    }
}
