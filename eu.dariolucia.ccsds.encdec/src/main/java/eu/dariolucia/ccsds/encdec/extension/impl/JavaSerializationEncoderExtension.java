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
import eu.dariolucia.ccsds.encdec.extension.IEncoderExtension;
import eu.dariolucia.ccsds.encdec.structure.EncodingException;
import eu.dariolucia.ccsds.encdec.structure.PathLocation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * This class implements a very basic object serialisation based on Java serialization. The serialisation format is the
 * following:
 * <ul>
 *     <li>4 bytes are used to write the length of the serialised object, as unsigned integer</li>
 *     <li>N bytes are used to store the serialised object</li>
 * </ul>
 */
@ExtensionId(id = "__java_serialization")
public class JavaSerializationEncoderExtension implements IEncoderExtension {

    private static final int INITIAL_BUFFER_LENGTH = 65536;

    @Override
    public void encode(PacketDefinition definition, EncodedParameter parameter, PathLocation location, BitEncoderDecoder encoder, Object value)
            throws EncodingException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(INITIAL_BUFFER_LENGTH);
        try {
            ObjectOutputStream dos = new ObjectOutputStream(bos);
            dos.writeObject(value);
            dos.flush();
            dos.close();
            byte[] serialized = bos.toByteArray();
            encoder.setNextIntegerUnsigned(serialized.length, Integer.SIZE);
            encoder.setNextByte(serialized, serialized.length * Byte.SIZE);
        } catch(IOException e) {
            throw new EncodingException("Error while encoding encoded parameter " + location + " as Java object at bit position " + encoder.getCurrentBitIndex(), e);
        }
    }
}
