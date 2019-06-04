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

@XmlAccessorType(XmlAccessType.FIELD)
public class FixedType extends AbstractEncodedType {

    @XmlAttribute(name="type", required = true)
    private DataTypeEnum type;

    /**
     * For enumeration, signed and unsigned integers, the length here always reflects the length of the field in bits.
     * Check the ECSS-E-70-41 standard for the discrepancy with the ECSS PFC values.
     */
    @XmlAttribute(name="length", required = true)
    private int length;

    public FixedType() {
    }

    public FixedType(DataTypeEnum type, int length) {
        this.type = type;
        this.length = length;
    }

    public DataTypeEnum getType() {
        return type;
    }

    public void setType(DataTypeEnum type) {
        this.type = type;
    }

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
        FixedType fixedType = (FixedType) o;
        return length == fixedType.length &&
                type == fixedType.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, length);
    }
}
