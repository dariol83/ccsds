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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import java.util.Objects;

/**
 * The semantic of objects of this class is the following: the length (PFC) of the encoded parameter is equal to the
 * value contained in the referenced encoded field, which must be encoded as a signed (PTC=3) or unsigned (PTC=4)
 * integer.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class ReferenceLength extends AbstractEncodedLength {

    @XmlAttribute(name = "ref")
    private String reference;

    public ReferenceLength() {
    }

    public ReferenceLength(String reference) {
        this.reference = reference;
    }

    /**
     * The ID of the encoded parameter that contains the value to be used for the length.
     *
     * This is a mandatory field.
     *
     * @return the encoded parameter ID in the same packet
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
        ReferenceLength that = (ReferenceLength) o;
        return Objects.equals(getReference(), that.getReference());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getReference());
    }
}
