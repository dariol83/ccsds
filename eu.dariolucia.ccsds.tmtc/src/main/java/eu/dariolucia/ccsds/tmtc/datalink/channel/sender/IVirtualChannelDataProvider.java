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

import eu.dariolucia.ccsds.tmtc.transport.pdu.BitstreamData;
import eu.dariolucia.ccsds.tmtc.transport.pdu.IPacket;

import java.util.List;

/**
 * This interface is implemented by data provider elements, i.e. objects that can provide space packets, bitstream data
 * and/or user data on demand, following a virtual channel request.
 */
public interface IVirtualChannelDataProvider {
    /**
     * This method returns a list of packets, to be inserted into a transfer frame. It is mandatory that the
     * overall size of the returned data does not exceed maxNumBytesBeforeOverflow, otherwise a {@link IllegalStateException} will
     * be thrown by the requesting virtual channel.
     *
     * @param virtualChannelId the virtual channel requesting the data
     * @param availableSpaceInCurrentFrame the amount of data required to close and emit the transfer frame under construction
     * @param maxNumBytesBeforeOverflow the maximum amount of bytes that the virtual channel can handle without overflow
     * @return the list of space packets to encode in the transfer frame (can be null, can be empty)
     */
    List<IPacket> generateSpacePackets(int virtualChannelId, int availableSpaceInCurrentFrame, int maxNumBytesBeforeOverflow);

    /**
     * This method returns the bitstream data to be inserted into a transfer frame. It is mandatory that the
     * overall size of the returned data (num of bits/8 plus possibly one) does not exceed availableSpaceInCurrentFrame,
     * otherwise a {@link IllegalStateException} will be thrown by the requesting virtual channel.
     *
     * @param virtualChannelId the virtual channel requesting the data
     * @param availableSpaceInCurrentFrame the amount of data required to close and emit the transfer frame under construction
     * @return the bitstream data (can be null, length can be 0 bits)
     */
    BitstreamData generateBitstreamData(int virtualChannelId, int availableSpaceInCurrentFrame);

    byte[] generateData(int virtualChannelId, int availableSpaceInCurrentFrame);
}
