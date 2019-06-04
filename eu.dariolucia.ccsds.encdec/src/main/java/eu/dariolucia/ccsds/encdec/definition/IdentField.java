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
import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
public class IdentField {

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

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public int getByteOffset() {
		return byteOffset;
	}

	public void setByteOffset(int byteOffset) {
		this.byteOffset = byteOffset;
	}

	public int getByteLength() {
		return byteLength;
	}

	public void setByteLength(int byteLength) {
		this.byteLength = byteLength;
	}

	public int getAndMask() {
		return andMask;
	}

	public void setAndMask(int andMask) {
		this.andMask = andMask;
	}

	public int getOrMask() {
		return orMask;
	}

	public void setOrMask(int orMask) {
		this.orMask = orMask;
	}

	public int getLShift() {
		return lShift;
	}

	public void setLShift(int lShift) {
		this.lShift = lShift;
	}

	public int getRShift() {
		return rShift;
	}

	public void setRShift(int rShift) {
		this.rShift = rShift;
	}

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
		value >>= rShift;

		return value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		IdentField that = (IdentField) o;
		return byteOffset == that.byteOffset &&
				byteLength == that.byteLength &&
				lShift == that.lShift &&
				rShift == that.rShift &&
				id.equals(that.id) &&
				Objects.equals(andMask, that.andMask) &&
				Objects.equals(orMask, that.orMask);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, byteOffset, byteLength, andMask, orMask, lShift, rShift);
	}
}
