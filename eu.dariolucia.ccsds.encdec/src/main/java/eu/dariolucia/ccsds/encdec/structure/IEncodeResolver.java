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
import eu.dariolucia.ccsds.encdec.time.AbsoluteTimeDescriptor;
import eu.dariolucia.ccsds.encdec.time.RelativeTimeDescriptor;
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
     * @throws EncodingException if there was a problem when resolving the specified parameter value
     */
    boolean getBooleanValue(EncodedParameter parameter, PathLocation location) throws EncodingException;

    /**
     * Provide the enumeration value for the specified {@link EncodedParameter} at the specified {@link PathLocation}.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @return the enumeration value to be encoded
     * @throws EncodingException if there was a problem when resolving the specified parameter value
     */
    int getEnumerationValue(EncodedParameter parameter, PathLocation location) throws EncodingException;

    /**
     * Provide the signed integer value for the specified {@link EncodedParameter} at the specified {@link PathLocation}.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @return the signed integer value to be encoded
     * @throws EncodingException if there was a problem when resolving the specified parameter value
     */
    long getSignedIntegerValue(EncodedParameter parameter, PathLocation location) throws EncodingException;

    /**
     * Provide the unsigned integer value for the specified {@link EncodedParameter} at the specified {@link PathLocation}.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @return the unsigned integer value to be encoded
     * @throws EncodingException if there was a problem when resolving the specified parameter value
     */
    long getUnsignedIntegerValue(EncodedParameter parameter, PathLocation location) throws EncodingException;

    /**
     * Provide the real value for the specified {@link EncodedParameter} at the specified {@link PathLocation}.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @return the real value to be encoded
     * @throws EncodingException if there was a problem when resolving the specified parameter value
     */
    double getRealValue(EncodedParameter parameter, PathLocation location) throws EncodingException;

    /**
     * Provide the {@link Instant} value for the specified {@link EncodedParameter} at the specified {@link PathLocation}.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @return the {@link Instant} value to be encoded as absolute time
     * @throws EncodingException if there was a problem when resolving the specified parameter value
     */
    Instant getAbsoluteTimeValue(EncodedParameter parameter, PathLocation location) throws EncodingException;

    /**
     * Provide the {@link Duration} value for the specified {@link EncodedParameter} at the specified {@link PathLocation}.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @return the {@link Duration} value to be encoded as relative time
     * @throws EncodingException if there was a problem when resolving the specified parameter value
     */
    Duration getRelativeTimeValue(EncodedParameter parameter, PathLocation location) throws EncodingException;

    /**
     * Provide the {@link BitString} for the specified {@link EncodedParameter} at the specified {@link PathLocation}.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @param maxBitlength the maximum length in bits (it might be the exact required length, depending on the parameter specification)
     * @return the {@link BitString} value to be encoded
     * @throws EncodingException if there was a problem when resolving the specified parameter value
     */
    BitString getBitStringValue(EncodedParameter parameter, PathLocation location, int maxBitlength) throws EncodingException;

    /**
     * Provide the byte[] for the specified {@link EncodedParameter} at the specified {@link PathLocation}.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @param maxByteLength the maximum length in bytes (it might be the exact required length, depending on the parameter specification)
     * @return the byte[] value to be encoded
     * @throws EncodingException if there was a problem when resolving the specified parameter value
     */
    byte[] getOctetStringValue(EncodedParameter parameter, PathLocation location, int maxByteLength) throws EncodingException;

    /**
     * Provide the String for the specified {@link EncodedParameter} at the specified {@link PathLocation}.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @param maxStringLength the maximum length in character number (it might be the exact required length, depending on the parameter specification)
     * @return the String value to be encoded
     * @throws EncodingException if there was a problem when resolving the specified parameter value
     */
    String getCharacterStringValue(EncodedParameter parameter, PathLocation location, int maxStringLength) throws EncodingException;

    /**
     * Provide the extension value for the specified {@link EncodedParameter} at the specified {@link PathLocation}. Such
     * value (returned as Object) is then encoded using the associated {@link eu.dariolucia.ccsds.encdec.extension.IEncoderExtension}
     * interface.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @return the extension value to be encoded
     * @throws EncodingException if there was a problem when resolving the specified parameter value
     */
    Object getExtensionValue(EncodedParameter parameter, PathLocation location) throws EncodingException;

    /**
     * Provide the absolute time specification allowing to encoded an absolute time with the P-field.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @param value the value to encode
     * @return the CCSDS specification
     * @throws EncodingException if there was a problem when resolving the specified descriptor
     */
    AbsoluteTimeDescriptor getAbsoluteTimeDescriptor(EncodedParameter parameter, PathLocation location, Instant value) throws EncodingException;

    /**
     * Provide the relative time specification allowing to encoded a relative time with the P-field.
     *
     * @param parameter the parameter definition
     * @param location the encoding path location
     * @param value the value to encode
     * @return the CCSDS specification
     * @throws EncodingException if there was a problem when resolving the specified descriptor
     */
    RelativeTimeDescriptor getRelativeTimeDescriptor(EncodedParameter parameter, PathLocation location, Duration value) throws EncodingException;

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

}
