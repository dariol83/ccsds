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

package eu.dariolucia.ccsds.encdec.identifier;

/**
 * Exception reporting that no packet identification was found for the provided packet.
 */
public class PacketNotIdentifiedException extends Exception {

    private final byte[] packet;

    public PacketNotIdentifiedException(String message, byte[] packet) {
        super(message);
        this.packet = packet;
    }

    public PacketNotIdentifiedException(byte[] packet) {
        this("Unknown packet", packet);
    }

    /**
     * This method returns the not-identified packet.
     *
     * @return the unidentified packet
     */
    public byte[] getPacket() {
        return packet;
    }
}
