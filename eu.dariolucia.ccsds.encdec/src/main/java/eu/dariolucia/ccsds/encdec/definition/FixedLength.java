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
 * The field length of this class indicates the PFC-like of the associated encoded item. If present, it overwrites
 * the PFC-like code linked to the type.
 *
 * For an understanding on how the 'length' value is used, refer to the {@link DataTypeEnum} class.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class FixedLength extends AbstractEncodedLength {

    @XmlAttribute(name="len", required = true)
    private int length;

    public FixedLength() {
    }

    public FixedLength(int length) {
        this.length = length;
    }

    /**
     * The 'length' to be used for the associated encoded parameter.
     *
     * This is a mandatory field.
     *
     * @return the 'length' to be used for encoding/decoding
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
        FixedLength that = (FixedLength) o;
        return length == that.length;
    }

    @Override
    public int hashCode() {
        return Objects.hash(length);
    }
}
