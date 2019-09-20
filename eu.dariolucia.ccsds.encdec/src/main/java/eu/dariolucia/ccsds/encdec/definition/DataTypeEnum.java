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

import eu.dariolucia.ccsds.encdec.structure.PathLocation;

import java.time.Instant;

/**
 * Data types having, as code, the PTC code as defined by ECSS-E-70-41A, available at
 * http://everyspec.com/ESA/download.php?spec=ECSS-E-70-41A.047794.pdf
 *
 * The actual encoding characteristics are derived from the type and the associated 'length', as documented for
 * each enumeration literal.
 *
 * Even if the Deduced enumeration literal is defined, its use in this library usually generates an
 * error: for the derivation of the specific type, classes like {@link ParameterType} or {@link ReferenceType}
 * must be used.
 */
public enum DataTypeEnum {
    /**
     * A Boolean type (always 1 bit encoding)
     */
    Boolean(1),
    /**
     * Enumerated value (integer encoding).
     * Depending on the associated 'length', the value is encoded to/decoded from
     * 'length' number of bits.
     */
    Enumerated(2),
    /**
     * Unsigned integer.
     * Depending on the associated 'length', the value is encoded to/decoded from
     * 'length' number of bits.
     */
    UnsignedInteger(3),
    /**
     * Signed integer (two complement).
     * Depending on the associated 'length', the value is encoded to/decoded from
     * 'length' number of bits.
     */
    SignedInteger(4),
    /**
     * Real number
     * Depending on the associated 'length', the value is encoded to/decoded from:
     * <ul>
     *     <li>A IEEE single precision float, if length is 1</li>
     *     <li>A IEEE double precision float, if length is 2</li>
     *     <li>A MIL-STD-1750A 32-bits float, if length is 3</li>
     *     <li>A MIL-STD-1750A 48-bits float, if length is 4</li>
     * </ul>
     */
    Real(5),
    /**
     * Sequence of bits.
     * Depending on the associated 'length':
     * <ul>
     *     <li>If length is 0, the length (number of bits) of the actual value is used</li>
     *     <li>If length is not 0, then the length (number of bits) is a mandatory length and it is enforced</li>
     * </ul>
     */
    BitString(6),
    /**
     * Sequence of bytes (8 bits).
     * Depending on the associated 'length':
     * <ul>
     *     <li>If length is 0, the length (number of bytes) of the actual value is used</li>
     *     <li>If length is not 0, then the length (number of bytes) is a mandatory length and it is enforced</li>
     * </ul>
     */
    OctetString(7),
    /**
     * Sequence of ASCII characters.
     * Depending on the associated 'length':
     * <ul>
     *     <li>If length is 0, the length (number of characters) of the actual value is used</li>
     *     <li>If length is not 0, then the length (number of characters) is a mandatory length and it is enforced</li>
     * </ul>
     */
    CharacterString(8),
    /**
     * CDS/CUC absolute time (CCSDS 301.0-B-4).
     * Depending on the associated 'length':
     * <ul>
     *     <li>If length is 0, an explicit CUC/CDS (selection driven by {@link eu.dariolucia.ccsds.encdec.structure.IEncodeResolver#getAbsoluteTimeDescriptor(EncodedParameter, PathLocation, Instant)} with P-Field is used</li>
     *     <li>If length is 1, then implicit CDS, with optional agency-epoch, 16 bits day segment and no sub-milli segment</li>
     *     <li>If length is 2, then implicit CDS, with optional agency-epoch, 16 bits day segment and sub-milli segment with 16 bits resolution (microseconds)</li>
     *     <li>If length is from 3 to 18, then implicit CUC is used, with optional agency-epoch, ((length+1)/4, rounded down) octets of coarse time and ((length+1) modulo 4) octets of fine time</li>
     * </ul>
     */
    AbsoluteTime(9),
    /**
     * CUC Relative time.
     * Depending on the associated 'length':
     * <ul>
     *     <li>If length is 0, an explicit CUC/CDS (selection driven by {@link eu.dariolucia.ccsds.encdec.structure.IEncodeResolver#getAbsoluteTimeDescriptor(EncodedParameter, PathLocation, Instant)} with P-Field is used</li>
     *     <li>If length is 1, then implicit CDS, with optional agency-epoch, 16 bits day segment and no sub-milli segment</li>
     *     <li>If length is 2, then implicit CDS, with optional agency-epoch, 16 bits day segment and sub-milli segment with 16 bits resolution (microseconds)</li>
     *     <li>If length is from 3 to 18, then implicit CUC is used, with optional agency-epoch, ((length+1)/4, rounded down) octets of coarse time and ((length+1) modulo 4) octets of fine time</li>
     * </ul>
     */
    RelativeTime(10),
    /**
     * Deduced type, not supported by the library, here only for compatibility with ECSS-E-70-41A.
     * To define effectively an encoded parameter with deduced type, check {@link ParameterType} and {@link ReferenceType}.
     */
    Deduced(11);

    private int code;

    DataTypeEnum(int code) {
        this.code = code;
    }

    /**
     * This method returns the PFC code linked to the enumeration literal.
     *
     * @return the PFC code
     */
    public int getCode() {
        return code;
    }

    /**
     * This function maps the provided code to the corresponding enumeration literal.
     *
     * @param code the code to map
     * @return the corresponding literal
     * @throws IllegalArgumentException if no literal corresponds to the provided code
     */
    public static DataTypeEnum fromCode(int code) {
        if(code <= 0 || code > 12) {
            throw new IllegalArgumentException("Data type code " + code + " not supported");
        }
        return DataTypeEnum.values()[code - 1];
    }
}
