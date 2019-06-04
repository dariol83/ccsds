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
import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
public class ParameterDefinition {

    public static final int EXTERNAL_ID_NOT_SET = -1;

    @XmlID
    @XmlAttribute(name = "id", required = true)
    private String id;

    @XmlAttribute(name = "external_id")
    private long externalId = EXTERNAL_ID_NOT_SET;

    @XmlAttribute(name = "description")
    private String description = "";

    @XmlElement(name = "type", required = true)
    private FixedType type;

    @XmlElement(name = "extension")
    private String extension = null;

    public ParameterDefinition() {
    }

    public ParameterDefinition(String id, long externalId, String description, FixedType type) {
        this.id = id;
        this.externalId = externalId;
        this.description = description;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public FixedType getType() {
        return type;
    }

    public void setType(FixedType type) {
        this.type = type;
    }

    public long getExternalId() {
        return externalId;
    }

    public void setExternalId(long externalId) {
        this.externalId = externalId;
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
        ParameterDefinition that = (ParameterDefinition) o;
        return externalId == that.externalId &&
                Objects.equals(id, that.id) &&
                Objects.equals(description, that.description) &&
                Objects.equals(type, that.type) &&
                Objects.equals(extension, that.extension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, externalId, description, type, extension);
    }
}
