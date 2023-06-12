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
 * This type of {@link AbstractEncodedLocation} derives from {@link LastRelativeLocation}, but it allows to specify a different
 * encoded item, in contrary to the last encoded/decoded item used by {@link LastRelativeLocation}.
 * The reference field contains the ID of the encoded item that shall be used as positional reference. Once this item is
 * identified, the bitOffset and bitAlignment fields are used to compute the final location.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class EncodedItemRelativeLocation extends LastRelativeLocation {

    @XmlAttribute(name = "ref", required = true)
    private String reference;

    public EncodedItemRelativeLocation() {
    }

    public EncodedItemRelativeLocation(int bitOffset, int bitAlignment, String reference) {
        super(bitOffset, bitAlignment);
        this.reference = reference;
    }

    /**
     * The ID of the encoded item that shall be used as positional reference.
     *
     * This is a mandatory field.
     *
     * @return the ID of the reference encoded item to use to derive the location
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
        if (!super.equals(o)) return false;
        EncodedItemRelativeLocation that = (EncodedItemRelativeLocation) o;
        return Objects.equals(getReference(), that.getReference());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getReference());
    }
}
