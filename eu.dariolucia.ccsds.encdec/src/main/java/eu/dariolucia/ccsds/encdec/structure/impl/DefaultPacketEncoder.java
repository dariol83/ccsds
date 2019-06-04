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
import eu.dariolucia.ccsds.encdec.structure.IEncodeResolver;
import eu.dariolucia.ccsds.encdec.structure.IPacketEncoder;
import eu.dariolucia.ccsds.encdec.structure.PacketDefinitionIndexer;

import java.time.Instant;

public class DefaultPacketEncoder implements IPacketEncoder {

    /**
     * Default maximum packet size
     */
    public static final int DEFAULT_MAX_PACKET_SIZE = 65536;

    private final int maxPacketSize;

    private final PacketDefinitionIndexer definitions;

    private final Instant agencyEpoch;

    public DefaultPacketEncoder(PacketDefinitionIndexer definitions, int maxPacketSize, Instant agencyEpoch) {
        this.definitions = definitions;
        this.maxPacketSize = maxPacketSize;
        this.agencyEpoch = agencyEpoch;
    }

    public DefaultPacketEncoder(Definition definitions, int maxPacketSize, Instant agencyEpoch) {
        this(new PacketDefinitionIndexer(definitions), maxPacketSize, agencyEpoch);
    }

    public DefaultPacketEncoder(PacketDefinitionIndexer definitions, int maxPacketSize) {
       this(definitions, maxPacketSize, null);
    }

    public DefaultPacketEncoder(Definition definitions, int maxPacketSize) {
        this(new PacketDefinitionIndexer(definitions), maxPacketSize, null);
    }

    public DefaultPacketEncoder(PacketDefinitionIndexer definitions) {
        this(definitions, DEFAULT_MAX_PACKET_SIZE);
    }

    public DefaultPacketEncoder(Definition definitions) {
        this(definitions, DEFAULT_MAX_PACKET_SIZE);
    }

    @Override
    public byte[] encode(String packetDefinitionId, IEncodeResolver resolver) {
        // Get the definition
        PacketDefinition definition = definitions.retrieveDefinition(packetDefinitionId);
        // Create a definition walker
        EncodeWalker w = new EncodeWalker(definitions.getDefinitions(), definition, maxPacketSize, agencyEpoch, resolver);
        // Encode the definition
        return w.walk();
    }
}
