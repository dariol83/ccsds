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
 * The semantic of objects of this class is the following: the length of the encoded parameter is equal to the length of the
 * parameter (PFC, according to its type) identified by the value contained in the referenced encoded field. If the value
 * is encoded as enum (PTC=2), signed (PTC=3) or unsigned (PTC=4) integer, the library will look for a parameter having external ID
 * equals to the value and it will use the length specified for such parameter. This approach can be used to encode parameters
 * whose type and length is deduced by an ID encoded in a previous field (e.g. as ECSS PUS does for deduced values).
 *
 * If the value is not an integer or the external ID is not found, a registered external length mapper will be invoked
 * ({@link eu.dariolucia.ccsds.encdec.extension.ILengthMapper}). If such length mapper does not exist, an exception
 * will be raised.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class ParameterLength extends AbstractEncodedLength {

    @XmlAttribute(name = "ref", required = true)
    private String reference;

    public ParameterLength() {
    }

    public ParameterLength(String reference) {
        this.reference = reference;
    }

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
        ParameterLength that = (ParameterLength) o;
        return reference.equals(that.reference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reference);
    }
}
