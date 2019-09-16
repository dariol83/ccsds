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

/**
 * An encoded item that represents a single value. In addition to the field properties defined for an {@link AbstractEncodedItem},
 * a parameter is defined by a mandatory type, an optional length, an optional generation time, an optional default value,
 * an optional linked parameter and an optional padding.
 */
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

    /**
     * Bit padding allows to express a minimum length in bits for the encoded parameter.
     * After encoding, if the encoded value is smaller than the specified padded width, the current encoding position
     * is moved forward to align to the padded width. For instance, if an encoded parameter takes 9 bits, but its padded
     * width is 12 bits, after encoding the position of the encoding pointer is moved forward by 3 bits, so that the overall
     * length of the encoded parameter is equal to the padded width.
     *
     * This is an optional field.
     *
     * @return the padded width in bits, or null if no padding is defined
     */
    public Integer getPaddedWidth() {
        return paddedWidth;
    }

    public void setPaddedWidth(Integer paddedWidth) {
        this.paddedWidth = paddedWidth;
    }

    /**
     * The type of the encoded parameter.
     *
     * This is a mandatory field.
     *
     * @return the type of the encoded parameter
     * @see AbstractEncodedType
     */
    public AbstractEncodedType getType() {
        return type;
    }

    public void setType(AbstractEncodedType type) {
        this.type = type;
    }

    /**
     * The length of the encoded parameter.
     *
     * This is an optional field.
     *
     * @return the length of the encoded parameter, or null if the length shall be derived from the type
     * @see AbstractEncodedLength
     */
    public AbstractEncodedLength getLength() {
        return length;
    }

    public void setLength(AbstractEncodedLength length) {
        this.length = length;
    }

    /**
     * The associated top level parameter (or on-board parameter). When this field is specified, the decoding process
     * will create an additional object, with the ID of the top level parameter and the value of the encoded parameter.
     *
     * This is an optional field.
     *
     * @return the associated top level parameter, or null if not set
     */
    public ParameterDefinition getLinkedParameter() {
        return linkedParameter;
    }

    public void setLinkedParameter(ParameterDefinition linkedParameter) {
        this.linkedParameter = linkedParameter;
    }

    /**
     * The processing specification at decoding level to derive the generation time (or the input to compute the generation time).
     *
     * This is an optional field.
     *
     * @return the generation time processing specification, or null if not set.
     */
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
     *     <li>{@link java.time.Instant#parse(CharSequence)} for values of type {@link DataTypeEnum#AbsoluteTime}
     *     with limitation on the number of digits for the fractional part of the second</li>
     *     <li>{@link java.time.Duration#parse(CharSequence)} for values of type {@link DataTypeEnum#RelativeTime}
     *     with limitation on the number of digits for the fractional part of the second</li>
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
