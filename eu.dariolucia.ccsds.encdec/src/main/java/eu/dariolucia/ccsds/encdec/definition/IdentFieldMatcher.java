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
import javax.xml.bind.annotation.XmlIDREF;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Objects;

/**
 * One or more identification field matchers are defined at packet definition level. When all the values extracted by
 * the corresponding identification fields match the values by the identification field matchers, the packet is identified.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class IdentFieldMatcher {

    @XmlIDREF
    @XmlAttribute(name = "field", required = true)
    private IdentField field;

    @XmlAttribute(name = "value", required = true)
    @XmlJavaTypeAdapter(IntAdapter.class)
    private Integer value;

    public IdentFieldMatcher() {
    }

    public IdentFieldMatcher(IdentField field, Integer value) {
        this.field = field;
        this.value = value;
    }

    /**
     * The identification field linked to this matcher.
     *
     * This is a mandatory field.
     *
     * @return the linked identification field
     */
    public IdentField getField() {
        return field;
    }

    public void setField(IdentField field) {
        this.field = field;
    }

    /**
     * The value associated to this matcher.
     *
     * This is a mandatory field.
     *
     * @return the value of the matcher
     */
    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdentFieldMatcher that = (IdentFieldMatcher) o;
        return field.equals(that.field) &&
                value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, value);
    }
}
