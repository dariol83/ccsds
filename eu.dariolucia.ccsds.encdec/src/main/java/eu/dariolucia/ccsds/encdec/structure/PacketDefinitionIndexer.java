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

package eu.dariolucia.ccsds.encdec.structure;

import eu.dariolucia.ccsds.encdec.definition.Definition;
import eu.dariolucia.ccsds.encdec.definition.PacketDefinition;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * This class is a simple indexer (map) for all {@link PacketDefinition} defined inside a {@link Definition} object.
 */
public class PacketDefinitionIndexer {

    private static final int TREE_MAP_THRESHOLD = 1000;

    private final Definition definitions;

    private final Map<String, PacketDefinition> index;

    /**
     * Construct an index based on the provided {@link Definition} object.
     *
     * @param definitions the definitions to be indexed
     */
    public PacketDefinitionIndexer(Definition definitions) {
        this.definitions = definitions;
        if(definitions.getPacketDefinitions().size() > TREE_MAP_THRESHOLD) {
            index = new HashMap<>();
        } else {
            index = new TreeMap<>();
        }
        for(PacketDefinition pd : this.definitions.getPacketDefinitions()) {
            index.put(pd.getId(), pd);
        }
    }

    /**
     * This method returns the definition as-is.
     *
     * @return the {@link Definition} object
     */
    public Definition getDefinitions() {
        return definitions;
    }

    /**
     * This method returns the {@link PacketDefinition} of the provided packetDefinitionId, or raises an exception if
     * the specified definition is not found.
     *
     * @param packetDefinitionId the packet definition ID
     * @return the {@link PacketDefinition} by ID
     * @throws IllegalArgumentException if the provided packetDefinitionId is unknown
     */
    public PacketDefinition retrieveDefinition(String packetDefinitionId) {
        PacketDefinition pd = this.index.get(packetDefinitionId);
        if(pd == null) {
            throw new IllegalArgumentException("Packet definition " + packetDefinitionId + " unknown");
        } else {
            return pd;
        }
    }
}
