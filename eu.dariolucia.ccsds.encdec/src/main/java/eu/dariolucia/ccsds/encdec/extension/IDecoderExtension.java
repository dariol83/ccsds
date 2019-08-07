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

package eu.dariolucia.ccsds.encdec.extension;

import eu.dariolucia.ccsds.encdec.bit.BitEncoderDecoder;
import eu.dariolucia.ccsds.encdec.structure.PathLocation;
import eu.dariolucia.ccsds.encdec.definition.EncodedParameter;
import eu.dariolucia.ccsds.encdec.definition.PacketDefinition;

/**
 * This extension interface allows to decode a parameter value within a packet, when the parameter type does not map to
 * any of the supported value types of the library.
 *
 * Classes implementing this interface must be annotated with the {@link ExtensionId} annotation in order to be looked up
 * by the library.
 */
public interface IDecoderExtension {

    /**
     * This method is invoked when a parameter definition with type equal to {@link eu.dariolucia.ccsds.encdec.definition.ExtensionType}
     * is encountered when decoding a packet. All the required arguments to extract the value from the packet are provided
     * to the extension.
     *
     * As a condition, the extension implementation must use the decoder object to read the underlying bits or bytes. The decoder
     * object remembers the movements (forward and backward) when reading. When the method returns, the decoder must point
     * to the first bit following the end bit of the decoded value.
     *
     * @param definition the packet definition containing the parameter to decode
     * @param parameter the definition of the encoded parameter to be decoded
     * @param location the location of the parameter inside the packet definition
     * @param decoder the {@link BitEncoderDecoder} that must be used to decode the parameter
     * @return the decoded value
     */
    Object decode(PacketDefinition definition, EncodedParameter parameter, PathLocation location, BitEncoderDecoder decoder);

}
