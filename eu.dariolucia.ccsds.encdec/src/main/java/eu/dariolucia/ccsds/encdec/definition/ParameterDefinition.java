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

import jakarta.xml.bind.annotation.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * This class allows to define a parameter entity, i.e. an element with an ID, data type and description independently of
 * an encoded parameter. This approach allows to link different encoded parameters, defined in different packets, to the
 * same parameter entity. In other words, the sampled value of the same physical entity (the parameter) can be delivered to the
 * processing facility by means of encoded parameters delivered by different packets.
 *
 * For {@link ParameterType} and {@link ParameterLength} correct usage, the external ID must be correctly specified.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class ParameterDefinition implements Serializable {

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

    /**
     * The ID of this parameter.
     *
     * This is a mandatory field.
     *
     * @return the parameter ID
     */
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * The description of this parameter.
     *
     * This is an optional field.
     *
     * @return the parameter description
     */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * The (fixed) type of this parameter. It cannot be of type 'deduced'.
     *
     * This is an optional field.
     *
     * @return the parameter type
     */
    public FixedType getType() {
        return type;
    }

    public void setType(FixedType type) {
        this.type = type;
    }

    /**
     * The external ID of this parameter, i.e. an ID that identifies this parameter in an external system.
     *
     * This is an optional field.
     *
     * @return the parameter external ID
     */
    public long getExternalId() {
        return externalId;
    }

    public void setExternalId(long externalId) {
        this.externalId = externalId;
    }

    /**
     * The extension string associated to this parameter.
     *
     * This is an optional field.
     *
     * @return the parameter extension
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
        ParameterDefinition that = (ParameterDefinition) o;
        return getExternalId() == that.getExternalId() &&
                Objects.equals(getId(), that.getId()) &&
                Objects.equals(getDescription(), that.getDescription()) &&
                Objects.equals(getType(), that.getType()) &&
                Objects.equals(getExtension(), that.getExtension());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getExternalId(), getDescription(), getType(), getExtension());
    }
}
