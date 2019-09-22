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
 * When the location is set with this class, it indicates that the related encoded item start at the position (in bits)
 * from the beginning of the data block to decode. In case of encoding, the structure moves the encoder to point to
 * the indicated bit position, before encoding the associated item.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class FixedAbsoluteLocation extends AbstractEncodedLocation {

    @XmlAttribute(name = "bit_absolute", required = true)
    private int absoluteLocation;

    public FixedAbsoluteLocation() {
    }

    public FixedAbsoluteLocation(int absoluteLocation) {
        this.absoluteLocation = absoluteLocation;
    }

    /**
     * The absolute location to move the encoding/decoding bit pointer to, computed from the beginning of the decoding
     * block (as specified by the offset).
     *
     * This is a mandatory field.
     *
     * @return the fixed location as number of bits
     */
    public int getAbsoluteLocation() {
        return absoluteLocation;
    }

    public void setAbsoluteLocation(int absoluteLocation) {
        this.absoluteLocation = absoluteLocation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FixedAbsoluteLocation that = (FixedAbsoluteLocation) o;
        return getAbsoluteLocation() == that.getAbsoluteLocation();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getAbsoluteLocation());
    }
}
