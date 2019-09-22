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

package eu.dariolucia.ccsds.encdec.definition;

import javax.xml.bind.annotation.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * The definition of a packet in terms of matchers and structure. Such definition is composed by:
 * <ul>
 *     <li>a mandatory ID, which must be unique among the packet definitions</li>
 *     <li>an optional textual description</li>
 *     <li>an optional type, which is a label that can be used to include/exclude categories of packets from the identification process</li>
 *     <li>an optional identification definition, which contains an ordered list of matchers</li>
 *     <li>an optional packet structure, which contains the structural definition of the packet</li>
 *     <li>an optional extension, which is a textual value that can be used to carry any additional information</li>
 * </ul>
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class PacketDefinition {

    @XmlID
    @XmlAttribute(name = "id", required = true)
    private String id;

    @XmlAttribute(name = "description")
    private String description;

    @XmlAttribute(name = "type")
    private String type;

    /**
     * The ordered list of matchers: beware, the order matters. Be consistent among packet definitions!
     */
    @XmlElementWrapper(name = "identification")
    @XmlElement(name = "match")
    private List<IdentFieldMatcher> matchers = new LinkedList<>();

    @XmlElement(name = "structure")
    private PacketStructure structure = null;

    @XmlElement(name = "extension")
    private String extension = null;

    public PacketDefinition() {
    }

    public PacketDefinition(String id) {
        this.id = id;
    }

    public PacketDefinition(String id, IdentFieldMatcher... matchers) {
        this.id = id;
        getMatchers().addAll(Arrays.asList(matchers));
    }

    public PacketDefinition(String id, PacketStructure structure, IdentFieldMatcher... matchers) {
        this.id = id;
        this.structure = structure;
        getMatchers().addAll(Arrays.asList(matchers));
    }

    /**
     * The description of the packet.
     *
     * This is an optional field.
     *
     * @return the packet description
     */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * The ID of the packet. Within a definition object, there cannot be two packets with the same ID.
     *
     * This is a mandatory field.
     *
     * @return the packet ID
     */
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * The list of identification field matchers, which unequivocally identify this packet. The definition order matters
     * and a consistent order among packet definitions is mandatory, in order to achieve correct packet identification
     * results.
     *
     * This is an optional field. The list can be empty.
     *
     * @return the list of identity field matchers
     */
    public List<IdentFieldMatcher> getMatchers() {
        return matchers;
    }

    /**
     * The definition of the structure (in terms of encoded items) of this packet.
     *
     * This is an optional field.
     *
     * @return the packet structure definition
     */
    public PacketStructure getStructure() {
        return structure;
    }

    public void setStructure(PacketStructure structure) {
        this.structure = structure;
    }

    /**
     * The type associated to this packet (e.g. "TM" or "TC", or whatever).
     *
     * This is an optional field.
     *
     * @return the packet type
     */
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * The extension string associated to this packet.
     *
     * This is an optional field.
     *
     * @return the packet extension (not used by the library)
     */
    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PacketDefinition that = (PacketDefinition) o;
        return Objects.equals(getId(), that.getId()) &&
                Objects.equals(getDescription(), that.getDescription()) &&
                Objects.equals(getType(), that.getType()) &&
                Objects.equals(getMatchers(), that.getMatchers()) &&
                Objects.equals(getStructure(), that.getStructure()) &&
                Objects.equals(getExtension(), that.getExtension());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getDescription(), getType(), getMatchers(), getStructure(), getExtension());
    }
}
