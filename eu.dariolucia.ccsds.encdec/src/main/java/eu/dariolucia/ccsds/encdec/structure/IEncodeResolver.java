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

package eu.dariolucia.ccsds.encdec.structure;

import eu.dariolucia.ccsds.encdec.definition.EncodedParameter;
import eu.dariolucia.ccsds.encdec.definition.PacketDefinition;
import eu.dariolucia.ccsds.encdec.value.BitString;

import java.time.Duration;
import java.time.Instant;

/**
 * An interface to be provided to the {@link IPacketEncoder}, which allows to resolve the value associated to an encoded parameter.
 *
 * The implementation shall also provide the preferred encoding for absolute and relative times, when the related format
 * (length) is set to 0 (P-field present).
 *
 * The implementation is informed about the start and end of the encoding of a specific packet.
 */
public interface IEncodeResolver {

    /**
     * Provide the boolean value for the specified {@link EncodedParameter} at the specified {@link PathLocation}.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @return the boolean value to be encoded
     */
    boolean getBooleanValue(EncodedParameter parameter, PathLocation location);

    /**
     * Provide the enumeration value for the specified {@link EncodedParameter} at the specified {@link PathLocation}.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @return the enumeration value to be encoded
     */
    int getEnumerationValue(EncodedParameter parameter, PathLocation location);

    /**
     * Provide the signed integer value for the specified {@link EncodedParameter} at the specified {@link PathLocation}.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @return the signed integer value to be encoded
     */
    long getSignedIntegerValue(EncodedParameter parameter, PathLocation location);

    /**
     * Provide the unsigned integer value for the specified {@link EncodedParameter} at the specified {@link PathLocation}.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @return the unsigned integer value to be encoded
     */
    long getUnsignedIntegerValue(EncodedParameter parameter, PathLocation location);

    /**
     * Provide the real value for the specified {@link EncodedParameter} at the specified {@link PathLocation}.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @return the real value to be encoded
     */
    double getRealValue(EncodedParameter parameter, PathLocation location);

    /**
     * Provide the {@link Instant} value for the specified {@link EncodedParameter} at the specified {@link PathLocation}.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @return the {@link Instant} value to be encoded as absolute time
     */
    Instant getAbsoluteTimeValue(EncodedParameter parameter, PathLocation location);

    /**
     * Provide the {@link Duration} value for the specified {@link EncodedParameter} at the specified {@link PathLocation}.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @return the {@link Duration} value to be encoded as relative time
     */
    Duration getRelativeTimeValue(EncodedParameter parameter, PathLocation location);

    /**
     * Provide the {@link BitString} for the specified {@link EncodedParameter} at the specified {@link PathLocation}.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @param maxBitlength the maximum length in bits (it might be the exact required length, depending on the parameter specification)
     * @return the {@link BitString} value to be encoded
     */
    BitString getBitStringValue(EncodedParameter parameter, PathLocation location, int maxBitlength);

    /**
     * Provide the byte[] for the specified {@link EncodedParameter} at the specified {@link PathLocation}.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @param maxByteLength the maximum length in bytes (it might be the exact required length, depending on the parameter specification)
     * @return the byte[] value to be encoded
     */
    byte[] getOctetStringValue(EncodedParameter parameter, PathLocation location, int maxByteLength);

    /**
     * Provide the String for the specified {@link EncodedParameter} at the specified {@link PathLocation}.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @param maxStringLength the maximum length in character number (it might be the exact required length, depending on the parameter specification)
     * @return the String value to be encoded
     */
    String getCharacterStringValue(EncodedParameter parameter, PathLocation location, int maxStringLength);

    /**
     * Provide the extension value for the specified {@link EncodedParameter} at the specified {@link PathLocation}. Such
     * value (returned as Object) is then encoded using the associated {@link eu.dariolucia.ccsds.encdec.extension.IEncoderExtension}
     * interface.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @return the extension value to be encoded
     */
    Object getExtensionValue(EncodedParameter parameter, PathLocation location);

    /**
     * Provide the absolute time specification allowing to encoded an absolute time with the P-field.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @param value the value to encode
     * @return the CCSDS specification
     */
    AbsoluteTimeDescriptor getAbsoluteTimeDescriptor(EncodedParameter parameter, PathLocation location, Instant value);

    /**
     * Provide the relative time specification allowing to encoded a relative time with the P-field.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @param value the value to encode
     * @return the CCSDS specification
     */
    RelativeTimeDescriptor getRelativeTimeDescriptor(EncodedParameter parameter, PathLocation location, Duration value);

    /**
     * Notify the implementation of the start of a packet encoding.
     *
     * @param pd the {@link PacketDefinition} that is about to be used for the encoding of a packet
     */
    default void startPacketEncoding(PacketDefinition pd) {
        // Stub, redefine if packet definition is required for the encoding
    }

    /**
     * Notify the implementation of the end of a packet encoding.
     */
    default void endPacketEncoding() {
        // Stub, can be redefined
    }

    /**
     * This class allows to specify a CUC or CDS time descriptor, with the related characteristics, for absolute time encoding.
     */
    final class AbsoluteTimeDescriptor {

        /**
         * Create a CUC descriptor with the provided characteristics.
         *
         * @param coarseTime the size of coarse time in bytes
         * @param fineTime the size of fine time in bytes
         * @return the CUC descriptor
         */
        public static AbsoluteTimeDescriptor newCucDescriptor(int coarseTime, int fineTime) {
            return new AbsoluteTimeDescriptor(true, coarseTime, fineTime, false, 0);
        }

        /**
         * Create a CDS descriptor with the provided characteristics.
         *
         * @param use16bits true if two bytes are used for the day field, false if three bytes are used
         * @param subMsPart the size of sub-millisecond field (0: not used, 1: microseconds, 2: picoseconds).
         * @return the CDS descriptor
         */
        public static AbsoluteTimeDescriptor newCdsDescriptor(boolean use16bits, int subMsPart) {
            return new AbsoluteTimeDescriptor(false, 0, 0, use16bits, subMsPart);
        }

        public final boolean cuc;
        public final int coarseTime;
        public final int fineTime;
        public final boolean use16bits;
        public final int subMsPart;

        private AbsoluteTimeDescriptor(boolean cuc, int coarseTime, int fineTime, boolean use16bits, int subMsPart) {
            this.cuc = cuc;
            this.coarseTime = coarseTime;
            this.fineTime = fineTime;
            this.use16bits = use16bits;
            this.subMsPart = subMsPart;
        }
    }

    /**
     * This class allows to specify a CUC time descriptor, with the related characteristics, for relative time encoding.
     */
    final class RelativeTimeDescriptor {

        /**
         * Create a CUC descriptor with the provided characteristics.
         *
         * @param coarseTime the size of coarse time in bytes
         * @param fineTime the size of fine time in bytes
         * @return the CUC descriptor
         */
        public static RelativeTimeDescriptor newCucDescriptor(int coarseTime, int fineTime) {
            return new RelativeTimeDescriptor(coarseTime, fineTime);
        }

        public final int coarseTime;
        public final int fineTime;

        private RelativeTimeDescriptor(int coarseTime, int fineTime) {
            this.coarseTime = coarseTime;
            this.fineTime = fineTime;
        }
    }
}
