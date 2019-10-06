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

package eu.dariolucia.ccsds.encdec.extension.impl;

import eu.dariolucia.ccsds.encdec.bit.BitEncoderDecoder;
import eu.dariolucia.ccsds.encdec.definition.EncodedParameter;
import eu.dariolucia.ccsds.encdec.definition.PacketDefinition;
import eu.dariolucia.ccsds.encdec.extension.ExtensionId;
import eu.dariolucia.ccsds.encdec.extension.IDecoderExtension;
import eu.dariolucia.ccsds.encdec.structure.DecodingException;
import eu.dariolucia.ccsds.encdec.structure.PathLocation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * This class can deserialise object values encoded using the corresponding {@link JavaSerializationEncoderExtension} encoder.
 */
@ExtensionId(id = "__java_serialization")
public class JavaSerializationDecoderExtension implements IDecoderExtension {

    @Override
    public Object decode(PacketDefinition definition, EncodedParameter parameter, PathLocation location, BitEncoderDecoder decoder)
            throws DecodingException {
        int len = decoder.getNextIntegerUnsigned(Integer.SIZE);
        byte[] data = decoder.getNextByte(len * Byte.SIZE);
        try {
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
            return ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new DecodingException("Error while decoding encoded parameter " + location + " as Java object at bit position " + decoder.getCurrentBitIndex(), e);
        }
    }
}
