/*
 *   Copyright (c) 2023 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.tmtc.datalink.channel.receiver;

/**
 * This class is used to inform implementors of the {@link IVirtualChannelReceiverOutput} interface that the notified
 * packet has a gap due to missing frame, starting from the provided index and of the given length.
 */
public class PacketGap {
    private final int index;
    private final int length;

    /**
     * The constructor of the packet gap.
     *
     * @param index the index of the packet, from which the gap starts
     * @param length the length of the gap
     */
    public PacketGap(int index, int length) {
        this.index = index;
        this.length = length;
    }

    /**
     * This method returns the index of the packet, from which the gap starts
     * @return the index of the packet, from which the gap starts
     */
    public int getIndex() {
        return index;
    }

    /**
     * This method returns the length of the gap
     * @return the length of the gap
     */
    public int getLength() {
        return length;
    }
}
