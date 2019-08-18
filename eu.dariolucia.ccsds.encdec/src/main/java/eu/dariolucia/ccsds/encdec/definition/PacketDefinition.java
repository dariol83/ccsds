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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<IdentFieldMatcher> getMatchers() {
        return matchers;
    }

    public PacketStructure getStructure() {
        return structure;
    }

    public void setStructure(PacketStructure structure) {
        this.structure = structure;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

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
        return Objects.equals(id, that.id) &&
                Objects.equals(description, that.description) &&
                Objects.equals(type, that.type) &&
                Objects.equals(matchers, that.matchers) &&
                Objects.equals(structure, that.structure) &&
                Objects.equals(extension, that.extension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, description, type, matchers, structure, extension);
    }
}
