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

package eu.dariolucia.ccsds.encdec.structure.impl;

import eu.dariolucia.ccsds.encdec.definition.Definition;
import eu.dariolucia.ccsds.encdec.definition.PacketDefinition;
import eu.dariolucia.ccsds.encdec.structure.DecodingResult;
import eu.dariolucia.ccsds.encdec.structure.IPacketDecoder;
import eu.dariolucia.ccsds.encdec.structure.PacketDefinitionIndexer;
import eu.dariolucia.ccsds.encdec.time.IGenerationTimeProcessor;

import java.time.Instant;

public class DefaultPacketDecoder implements IPacketDecoder {

    /**
     * Default maximum packet size
     */
    public static final int DEFAULT_MAX_PACKET_SIZE = 65536;

    private final PacketDefinitionIndexer definitions;
    private final Instant agencyEpoch;

    public DefaultPacketDecoder(PacketDefinitionIndexer definitions, Instant agencyEpoch) {
        this.definitions = definitions;
        this.agencyEpoch = agencyEpoch;
    }

    public DefaultPacketDecoder(Definition definitions, Instant agencyEpoch) {
        this(new PacketDefinitionIndexer(definitions), agencyEpoch);
    }

    public DefaultPacketDecoder(PacketDefinitionIndexer definitions) {
        this(definitions, null);
    }

    public DefaultPacketDecoder(Definition definitions) {
        this(new PacketDefinitionIndexer(definitions), null);
    }

    @Override
    public DecodingResult decode(String packetDefinitionId, byte[] data, int offset, int length, IGenerationTimeProcessor timeProcessor) {
        // Get the definition
        PacketDefinition definition = definitions.retrieveDefinition(packetDefinitionId);
        // Create a definition walker
        DecodeWalker w = new DecodeWalker(definitions.getDefinitions(), definition, data, offset, length, this.agencyEpoch, timeProcessor);
        // Decode the packet
        return w.walk();
    }
}
