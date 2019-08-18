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

package eu.dariolucia.ccsds.encdec.identifier.impl;

import eu.dariolucia.ccsds.encdec.definition.Definition;
import eu.dariolucia.ccsds.encdec.definition.IdentField;
import eu.dariolucia.ccsds.encdec.definition.IdentFieldMatcher;
import eu.dariolucia.ccsds.encdec.definition.PacketDefinition;
import eu.dariolucia.ccsds.encdec.identifier.IPacketIdentifier;
import eu.dariolucia.ccsds.encdec.identifier.PacketAmbiguityException;
import eu.dariolucia.ccsds.encdec.identifier.PacketNotIdentifiedException;

import javax.xml.bind.DatatypeConverter;
import java.util.*;
import java.util.stream.Collectors;

public class FieldGroupBasedPacketIdentifier implements IPacketIdentifier {

    private final List<IdSet> identificationList;

    private final boolean checkForAmbiguity;

    public FieldGroupBasedPacketIdentifier(Definition d) {
        this(d, false, null);
    }

    public FieldGroupBasedPacketIdentifier(Definition d, boolean checkForAmbiguity) {
        this(d, checkForAmbiguity, null);
    }

    public FieldGroupBasedPacketIdentifier(Definition d, boolean checkForAmbiguity, List<String> typesToConsider) {
        // Build the list of IdSets
        identificationList = new ArrayList<>();
        for (PacketDefinition pd : d.getPacketDefinitions()) {
            if(typesToConsider == null || typesToConsider.contains(pd.getType())) {
                IdSet set = getOrCreateIdSet(pd);
                set.addDefinition(pd);
            }
        }
        Collections.sort(identificationList);
        this.checkForAmbiguity = checkForAmbiguity;
    }

    private IdSet getOrCreateIdSet(PacketDefinition pd) {
        for (IdSet ids : identificationList) {
            if (ids.supports(pd)) {
                return ids;
            }
        }
        IdSet ids = new IdSet(pd.getMatchers().stream().map(IdentFieldMatcher::getField).collect(Collectors.toList()));
        identificationList.add(ids);
        return ids;
    }

    @Override
    public String identify(byte[] packet) throws PacketNotIdentifiedException, PacketAmbiguityException {
        PacketDefinition pd = null;
        for (IdSet idset : identificationList) {
            IdKey key = idset.computeKey(packet);
            if (key == null) {
                // e.g. packet too short, so not this group
                continue;
            }
            PacketDefinition temp = idset.getDefinition(key);
            if (temp != null) {
                if(checkForAmbiguity) {
                    if(pd != null) {
                        throw new PacketAmbiguityException("Definition ambiguity for packet: " + pd.getId() + " and " + temp.getId() + " both match packet " + DatatypeConverter.printHexBinary(packet));
                    }
                    pd = temp;
                } else {
                    return temp.getId();
                }
            }
        }
        if(pd != null) {
            return pd.getId();
        }
        throw new PacketNotIdentifiedException(packet);
    }

    private class IdSet implements Comparable<IdSet> {

        private List<IdentField> fields;

        private Map<IdKey, PacketDefinition> id2packet;

        public IdSet(List<IdentField> fields) {
            this.fields = fields;
            this.id2packet = new HashMap<>();
        }

        public boolean supports(PacketDefinition pd) {
            if (pd.getMatchers().size() != fields.size()) {
                return false;
            }
            // Assuming that the matchers are always specified in the same order
            for (int i = 0; i < pd.getMatchers().size(); ++i) {
                if (!pd.getMatchers().get(i).getField().equals(fields.get(i))) {
                    return false;
                }
            }
            return true;
        }

        public void addDefinition(PacketDefinition pd) {
            if (pd.getMatchers().size() != fields.size()) {
                throw new RuntimeException("Bug, number of matchers shall be equal to fields");
            }
            for (int i = 0; i < pd.getMatchers().size(); ++i) {
                if (!pd.getMatchers().get(i).getField().equals(fields.get(i))) {
                    throw new RuntimeException("Bug, matcher fields shall be equal to defined IdSet fields, same order");
                }
            }
            this.id2packet.put(IdKey.derive(pd), pd);
        }

        @Override
        public int compareTo(IdSet o) {
            // Criteria to sort IdSets
            if (fields.size() > o.fields.size()) {
                return -1;
            } else if (fields.size() < o.fields.size()) {
                return +1;
            } else {
                if (id2packet.size() > o.id2packet.size()) {
                    return -1;
                } else if (id2packet.size() < o.id2packet.size()) {
                    return +1;
                } else {
                    // Same number of fields, same number of packet discriminants, use hashcode
                    return o.hashCode() - hashCode();
                }
            }
        }

        public IdKey computeKey(byte[] packet) {
            int[] key = new int[fields.size()];
            int i = 0;
            for (IdentField idf : fields) {
                try {
                    key[i] = idf.extract(packet);
                    ++i;
                } catch (IndexOutOfBoundsException e) {
                    // Cannot read fields outside the packet boundary.
                    // Sure, it should be properly controlled (e.g. add "canExtract" in IdentField) but ... ok (for now)
                    return null;
                }
            }
            return new IdKey(key);
        }

        public PacketDefinition getDefinition(IdKey key) {
            return id2packet.get(key);
        }
    }

    private static class IdKey {

        private static IdKey derive(PacketDefinition pd) {
            int[] ks = new int[pd.getMatchers().size()];
            int i = 0;
            for (IdentFieldMatcher m : pd.getMatchers()) {
                ks[i] = m.getValue();
                ++i;
            }
            return new IdKey(ks);
        }

        private final int[] keys;

        private final int hashcode;

        public IdKey(int... keys) {
            this.keys = keys;
            this.hashcode = Arrays.hashCode(keys);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IdKey idKey = (IdKey) o;
            if (hashcode == idKey.hashcode) {
                return Arrays.equals(keys, idKey.keys);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return hashcode;
        }
    }
}
