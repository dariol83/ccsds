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
import eu.dariolucia.ccsds.encdec.structure.*;

import java.time.Instant;

/**
 * The default packet encoder provided by the library.
 */
public class DefaultPacketEncoder implements IPacketEncoder {

    /**
     * Default maximum packet size
     */
    public static final int DEFAULT_MAX_PACKET_SIZE = 65536;

    private final int maxPacketSize;

    private final PacketDefinitionIndexer definitions;

    private final Instant agencyEpoch;

    /**
     * Construct a default packet encoder from the provided packet definition indexer, using maxPacketSize as packet construction
     * buffer, and with the provided agency epoch.
     *
     * @param definitions the definitions to use
     * @param maxPacketSize the maximum size of an encoded packet
     * @param agencyEpoch the agency epoch, can be null
     */
    public DefaultPacketEncoder(PacketDefinitionIndexer definitions, int maxPacketSize, Instant agencyEpoch) {
        this.definitions = definitions;
        this.maxPacketSize = maxPacketSize;
        this.agencyEpoch = agencyEpoch;
    }

    /**
     * Construct a default packet encoder from the provided definition, using maxPacketSize as packet construction
     * buffer, and with a null agency epoch. A {@link PacketDefinitionIndexer} is constructed internally.
     *
     * @param definitions the definitions to use
     * @param maxPacketSize the maximum size of an encoded packet
     */
    public DefaultPacketEncoder(Definition definitions, int maxPacketSize) {
        this(new PacketDefinitionIndexer(definitions), maxPacketSize, null);
    }

    /**
     * Construct a default packet encoder from the provided definition, using DEFAULT_MAX_PACKET_SIZE as packet construction
     * buffer, and with a null agency epoch. A {@link PacketDefinitionIndexer} is constructed internally.
     *
     * @param definitions the definitions to use
     */
    public DefaultPacketEncoder(Definition definitions) {
        this(definitions, DEFAULT_MAX_PACKET_SIZE);
    }

    @Override
    public byte[] encode(String packetDefinitionId, IEncodeResolver resolver) throws EncodingException {
        // Get the definition
        PacketDefinition definition = definitions.retrieveDefinition(packetDefinitionId);
        if(definition == null) {
            throw new EncodingException("Packet definition " + packetDefinitionId + " unknown");
        }
        // Create a definition walker
        EncodeWalker w = new EncodeWalker(definitions.getDefinitions(), definition, maxPacketSize, agencyEpoch, resolver);
        // Notify encoding start
        resolver.startPacketEncoding(definition);
        // Encode the definition
        byte[] data = w.walk();
        // Notify encoding end
        resolver.endPacketEncoding();
        // Return the encoded block
        return data;
    }
}
