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
 * The field length of this class indicates the size of the associated array.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class FixedArraySize extends AbstractArraySize {

    @XmlAttribute(name="len", required = true)
    private int length;

    public FixedArraySize() {
    }

    public FixedArraySize(int length) {
        this.length = length;
    }

    /**
     * The size (i.e. number of rows, or repetitions) of the associated encoded array.
     *
     * This is a mandatory field.
     *
     * @return the number of rows of the array
     */
    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FixedArraySize that = (FixedArraySize) o;
        return getLength() == that.getLength();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLength());
    }
}
