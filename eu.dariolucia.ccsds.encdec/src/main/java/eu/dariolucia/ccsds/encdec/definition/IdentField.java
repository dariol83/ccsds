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
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.io.Serializable;
import java.util.Objects;

/**
 * The approach to packet identification implemented by this library is based on the concept of global identification fields:
 * an identification field is a definition of an integer field, of length 1, 2, 3 or 4 bytes, starting from a defined byte
 * offset. When this field definition is applied to a packet, an integer value is extracted and it is processed as follows:
 * <ul>
 *     <li>if specified, the extracted value is ANDed with the 'andMask' field (default: -1, i.e. all 1s)</li>
 *     <li>if specified, the resulting value is ORed with the 'orMask' field (default: 0, i.e. all 0s)</li>
 *     <li>if specified, the resulting value is left shifted by a number of bits defined by 'lshift' (default: 0)</li>
 *     <li>if specified, the resulting value is right shifted (no sign bit) by a number of bits defined by 'rshift' (default: 0)</li>
 * </ul>
 * The resulting value is the one provided for equality check with that specified by the {@link IdentFieldMatcher} defined at packet
 * level.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class IdentField implements Serializable {

	@XmlID
	@XmlAttribute(required = true)
	private String id;

	@XmlAttribute(name = "offset", required = true)
	private int byteOffset;

	@XmlAttribute(name="len", required = true)
	private int byteLength; // Up to 4

	@XmlAttribute(name = "and")
	@XmlJavaTypeAdapter(IntAdapter.class)
	private Integer andMask = 0xFFFFFFFF; // -1

	@XmlAttribute(name = "or")
	@XmlJavaTypeAdapter(IntAdapter.class)
	private Integer orMask = 0x0; // 0

	@XmlAttribute(name = "lshift")
	private int lShift = 0;

	@XmlAttribute(name = "rshift")
	private int rShift = 0;

	public IdentField() {
	}

	public IdentField(String id, int byteOffset, int byteLength, int andMask, int orMask, int lShift, int rShift) {
		this.id = id;
		this.byteOffset = byteOffset;
		this.byteLength = byteLength;
		this.andMask = andMask;
		this.orMask = orMask;
		this.lShift = lShift;
		this.rShift = rShift;
	}

	public IdentField(String id, int byteOffset, int byteLength) {
		this.id = id;
		this.byteOffset = byteOffset;
		this.byteLength = byteLength;
	}

	/**
	 * The ID of the identification field definition. This ID shall be referenced by the related {@link IdentFieldMatcher}
	 * defined at packet level.
	 *
	 * This is a mandatory field
	 *
	 * @return the ID of the identification field
	 */
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	/**
	 * The byte offset of the identification field, from the beginning of the defined packet, which marks the start of the
	 * field.
	 *
	 * This is a mandatory field
	 *
	 * @return the offset in bytes of the field
	 */
	public int getByteOffset() {
		return byteOffset;
	}

	public void setByteOffset(int byteOffset) {
		this.byteOffset = byteOffset;
	}

	/**
	 * The length in bytes of the identification field. Allowed values are 1, 2, 3, 4.
	 *
	 * This is a mandatory field
	 *
	 * @return the length in bytes of the field
	 */
	public int getByteLength() {
		return byteLength;
	}

	public void setByteLength(int byteLength) {
		this.byteLength = byteLength;
	}

	/**
	 * The AND mask to be applied to the integer value extracted by the identification field.
	 *
	 * This is an optional field
	 *
	 * @return the AND mask
	 */
	public int getAndMask() {
		return andMask;
	}

	public void setAndMask(int andMask) {
		this.andMask = andMask;
	}

	/**
	 * The OR mask to be applied to the integer value extracted by the identification field, after the AND mask.
	 *
	 * This is an optional field
	 *
	 * @return the OR mask
	 */
	public int getOrMask() {
		return orMask;
	}

	public void setOrMask(int orMask) {
		this.orMask = orMask;
	}

	/**
	 * The left shift to be applied to the integer value extracted by the identification field, after the AND and OR masks.
	 *
	 * This is an optional field
	 *
	 * @return the number of bits to shift to the left
	 */
	public int getLShift() {
		return lShift;
	}

	public void setLShift(int lShift) {
		this.lShift = lShift;
	}

	/**
	 * The right shift to be applied to the integer value extracted by the identification field, after the AND and OR masks,
	 * and the left shift.
	 *
	 * This is an optional field
	 *
	 * @return the number of bits to shift to the right
	 */
	public int getRShift() {
		return rShift;
	}

	public void setRShift(int rShift) {
		this.rShift = rShift;
	}

	/**
	 * This method computes the value extracted by this identification field, after the application of all the defined
	 * processing steps (integer decoding, AND, OR, left shift, right shift).
	 *
	 * @param toExtract the bytes to interpret and process according to the defined extraction process (usually the whole packet)
	 * @return the value corresponding to this identification field definition
	 */
	public int extract(byte[] toExtract) {
		if(byteLength > 4) {
			throw new IllegalStateException("Byte length to extract greater than 4: software limitation");
		}
		int value = 0;
		// MSB reading
		for(int i = 0; i < byteLength; ++i) {
			value <<= 8;
			value |= Byte.toUnsignedInt(toExtract[byteOffset + i]);
		}
		// Masks
		if(andMask != -1) {
			value &= andMask;
		}
		if(orMask != 0) {
			value |= orMask;
		}
		// Shifts
		value <<= lShift;
		value >>>= rShift;

		return value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		IdentField that = (IdentField) o;
		return getByteOffset() == that.getByteOffset() &&
				getByteLength() == that.getByteLength() &&
				lShift == that.lShift &&
				rShift == that.rShift &&
				Objects.equals(getId(), that.getId()) &&
				Objects.equals(getAndMask(), that.getAndMask()) &&
				Objects.equals(getOrMask(), that.getOrMask());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getId(), getByteOffset(), getByteLength(), getAndMask(), getOrMask(), lShift, rShift);
	}
}
