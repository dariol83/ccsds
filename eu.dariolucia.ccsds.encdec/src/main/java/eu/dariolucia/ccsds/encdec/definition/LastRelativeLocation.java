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
 * The location of the encoded field is computed from the last bit of the last encoded (or decoded) field and bit offset
 * (negative or positive) according to the specified value. If the bitAlignment field is set, then the result is increased
 * by the minimal amount, such that the resulting location % bitAlignment == 0.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class LastRelativeLocation extends AbstractEncodedLocation {

    @XmlAttribute(name = "bit_offset")
    private int bitOffset = 0;

    @XmlAttribute(name = "bit_align")
    private int bitAlignment = 0;

    public LastRelativeLocation() {
    }

    public LastRelativeLocation(int bitOffset, int bitAlignment) {
        this.bitOffset = bitOffset;
        this.bitAlignment = bitAlignment;
    }

    /**
     * The offset in bits that shall be added to the last bit of the last encoded/decoded item to compute the location.
     * This offset can be positive or negative.
     *
     * @return the bit offset
     */
    public int getBitOffset() {
        return bitOffset;
    }

    public void setBitOffset(int bitOffset) {
        this.bitOffset = bitOffset;
    }

    /**
     * The bit alignment that shall be used (after applying the offset) to compute the final location.
     *
     * @return the bit alignment
     */
    public int getBitAlignment() {
        return bitAlignment;
    }

    public void setBitAlignment(int bitAlignment) {
        this.bitAlignment = bitAlignment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LastRelativeLocation that = (LastRelativeLocation) o;
        return getBitOffset() == that.getBitOffset() &&
                getBitAlignment() == that.getBitAlignment();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getBitOffset(), getBitAlignment());
    }
}
