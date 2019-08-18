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

    @XmlElement(name="value")
    private String value = null;

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

    /**
     * The value assigned to this encoded parameter, or null if not set. The type of the value is a {@link String} object.
     * Depending on the type, it can be parsed using the standard Java methods for objects:
     * <ul>
     *     <li>{@link Boolean#parseBoolean(String)} for values of type {@link DataTypeEnum#Boolean}</li>
     *     <li>{@link Integer#parseInt(String)} for values of type {@link DataTypeEnum#Enumerated}</li>
     *     <li>{@link Long#parseLong(String)} for values of type {@link DataTypeEnum#UnsignedInteger}</li>
     *     <li>{@link Long#parseLong(String)} for values of type {@link DataTypeEnum#SignedInteger}</li>
     *     <li>{@link Double#parseDouble(String)} for values of type {@link DataTypeEnum#Real}</li>
     *     <li>{@link eu.dariolucia.ccsds.encdec.value.BitString#parseBitString(String)} for values of type {@link DataTypeEnum#BitString}</li>
     *     <li>{@link eu.dariolucia.ccsds.encdec.value.StringUtil#toByteArray(String)} for values of type {@link DataTypeEnum#OctetString}</li>
     *     <li>Direct value for values of type {@link DataTypeEnum#CharacterString}</li>
     *     <li>{@link java.time.Instant#parse(CharSequence)} for values of type {@link DataTypeEnum#AbsoluteTime}</li>
     *     <li>{@link java.time.Duration#parse(CharSequence)} for values of type {@link DataTypeEnum#RelativeTime}</li>
     * </ul>
     *
     * Since the exact type of a parameter might be discovered only at encoding or decoding time, the {@link EncodedParameter} class
     * does not provide a method to convert the string into the equivalent object.
     *
     * It must be noted that an {@link eu.dariolucia.ccsds.encdec.structure.IPacketEncoder} is not obliged to use this value,
     * it depends on the encoder or associated {@link eu.dariolucia.ccsds.encdec.structure.IEncodeResolver} implementation.
     *
     * @return the value to be assigned to the parameter according to the definition (if set), or null (if not set)
     */
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EncodedParameter that = (EncodedParameter) o;
        return Objects.equals(type, that.type) &&
                Objects.equals(length, that.length) &&
                Objects.equals(time, that.time) &&
                Objects.equals(value, that.value) &&
                Objects.equals(linkedParameter, that.linkedParameter) &&
                Objects.equals(paddedWidth, that.paddedWidth);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, length, time, value, linkedParameter, paddedWidth);
    }
}
