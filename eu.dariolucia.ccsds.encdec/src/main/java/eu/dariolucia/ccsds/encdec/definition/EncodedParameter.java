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

import javax.xml.bind.annotation.*;
import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
public class EncodedParameter extends AbstractEncodedItem {

    @XmlElements({
            @XmlElement(name="type_fixed",type=FixedType.class),
            @XmlElement(name="type_ref",type=ReferenceType.class),
            @XmlElement(name="type_param",type=ParameterType.class),
            @XmlElement(name="type_ext",type=ExtensionType.class)
    })
    private AbstractEncodedType type; // This field should never be null

    @XmlElements({
            @XmlElement(name="length_fixed",type=FixedLength.class),
            @XmlElement(name="length_ref",type=ReferenceLength.class),
            @XmlElement(name="length_param",type=ParameterLength.class)
    })
    private AbstractEncodedLength length = null;

    @XmlElement(name="time")
    private GenerationTime time = null;

    @XmlIDREF
    @XmlAttribute(name = "parameter")
    private ParameterDefinition linkedParameter = null;

    @XmlAttribute(name = "pad_to")
    private Integer paddedWidth = null;

    public EncodedParameter() {
    }

    public EncodedParameter(String id, AbstractEncodedType type, AbstractEncodedLength length) {
        super(id);
        this.type = type;
        this.length = length;
    }

    public Integer getPaddedWidth() {
        return paddedWidth;
    }

    public void setPaddedWidth(Integer paddedWidth) {
        this.paddedWidth = paddedWidth;
    }

    public AbstractEncodedType getType() {
        return type;
    }

    public void setType(AbstractEncodedType type) {
        this.type = type;
    }

    public AbstractEncodedLength getLength() {
        return length;
    }

    public void setLength(AbstractEncodedLength length) {
        this.length = length;
    }

    public ParameterDefinition getLinkedParameter() {
        return linkedParameter;
    }

    public void setLinkedParameter(ParameterDefinition linkedParameter) {
        this.linkedParameter = linkedParameter;
    }

    public GenerationTime getTime() {
        return time;
    }

    public void setTime(GenerationTime time) {
        this.time = time;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EncodedParameter that = (EncodedParameter) o;
        return Objects.equals(type, that.type) &&
                Objects.equals(length, that.length) &&
                Objects.equals(time, that.time) &&
                Objects.equals(linkedParameter, that.linkedParameter) &&
                Objects.equals(paddedWidth, that.paddedWidth);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, length, time, linkedParameter, paddedWidth);
    }
}
