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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import java.util.Objects;

/**
 * The semantic of objects of this class is the following: the type of the encoded parameter is equal to the type of the
 * parameter identified by the value contained in the referenced encoded field. If the value is encoded as enum (PTC=2),
 * signed (PTC=3) or unsigned (PTC=4) integer, the library will look for a parameter having external ID equals to the value.
 *
 * If the value is not an integer or the external ID is not found, a registered external type mapper will be invoked
 * ({@link eu.dariolucia.ccsds.encdec.extension.ITypeMapper}). If such type mapper does not exist, an exception will
 * be raised.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class ParameterType extends AbstractEncodedType {

    @XmlAttribute(name = "ref", required = true)
    private String reference;

    public ParameterType() {
    }

    public ParameterType(String reference) {
        this.reference = reference;
    }

    /**
     * The ID of the encoded parameter that contains the external ID of the parameter definition, to be used to derive the
     * type of the parameter to encode/decode.
     *
     * This is a mandatory field.
     *
     * @return the parameter reference
     */
    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParameterType that = (ParameterType) o;
        return Objects.equals(getReference(), that.getReference());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getReference());
    }
}
