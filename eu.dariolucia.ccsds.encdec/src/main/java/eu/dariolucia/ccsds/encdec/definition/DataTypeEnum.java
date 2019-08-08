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

/**
 * Data types having, as code, the PTC code as defined by ECSS-E-70-41A, available at
 * http://everyspec.com/ESA/download.php?spec=ECSS-E-70-41A.047794.pdf
 *
 * Even if the Deduced enumeration literal is defined, its use in this library usually generates an
 * error: for the derivation of the specific type, classes like {@link ParameterType} or {@link ReferenceType}
 * must be used.
 */
public enum DataTypeEnum {
    Boolean(1),
    Enumerated(2),
    UnsignedInteger(3),
    SignedInteger(4),
    Real(5),
    BitString(6),
    OctetString(7),
    CharacterString(8),
    AbsoluteTime(9),
    RelativeTime(10),
    Deduced(11); // Not supported, here only for compatibility with ECSS-E-70-41A

    private int code;

    DataTypeEnum(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static DataTypeEnum fromCode(int code) {
        if(code <= 0 || code > 12) {
            throw new IllegalArgumentException("Data type code " + code + " not supported");
        }
        return DataTypeEnum.values()[code - 1];
    }
}
