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
 * Specification of the interface for packet identification service. Implementations of this interface can be used to
 * identify packets.
 */
public interface IPacketIdentifier {
    /**
     * Identify the provided byte array as packet.
     *
     * @param packet the packet to identify (as byte array)
     * @return the ID of the identified packet
     * @throws PacketNotIdentifiedException when the packet could not be identified
     * @throws PacketAmbiguityException if more than one packet identification definition matches the provided packet (this depends on the identification strategy)
     */
    String identify(byte[] packet) throws PacketNotIdentifiedException, PacketAmbiguityException;
}
