/*
 *   Copyright (c) 2021 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.encdec.structure.resolvers;

import eu.dariolucia.ccsds.encdec.definition.*;
import eu.dariolucia.ccsds.encdec.structure.EncodingException;
import eu.dariolucia.ccsds.encdec.structure.IEncodeResolver;
import eu.dariolucia.ccsds.encdec.structure.PathLocation;
import eu.dariolucia.ccsds.encdec.time.AbsoluteTimeDescriptor;
import eu.dariolucia.ccsds.encdec.time.RelativeTimeDescriptor;
import eu.dariolucia.ccsds.encdec.value.BitString;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DefinitionValueBasedResolverTest {

    @Test
    public void testDefinitionValueBasedResolverCases() throws EncodingException {
        // Null delegate
        assertThrows(NullPointerException.class, () -> {
            new DefinitionValueBasedResolver(null, true);
        });

        DefinitionValueBasedResolver resolver = new DefinitionValueBasedResolver(new DummyResolver(), true);

        EncodedParameter paramDef = new EncodedParameter("param1", new FixedType(DataTypeEnum.ENUMERATED, 6), null);
        EncodedParameter paramDef2 = new EncodedParameter("param2", new FixedType(DataTypeEnum.BIT_STRING, 3), null);
        paramDef.setValue("24.5");
        paramDef2.setValue("101");
        PacketStructure struct = new PacketStructure(paramDef, paramDef2);
        PacketDefinition def = new PacketDefinition("test", struct);

        resolver.startPacketEncoding(def);
        assertEquals(24.5, resolver.getRealValue(paramDef, PathLocation.of("test", "param1")));
        assertEquals(3, resolver.getEnumerationValue(paramDef, PathLocation.of("test", "param1")));
        paramDef.setValue(null);
        assertEquals(12.4, resolver.getRealValue(paramDef, PathLocation.of("test", "param1")));

        DefinitionValueBasedResolver resolver2 = new DefinitionValueBasedResolver(new DummyResolver(), false);
        paramDef.setValue("24.5");
        resolver2.startPacketEncoding(def);
        assertThrows(EncodingException.class, () -> {
           resolver2.getEnumerationValue(paramDef, PathLocation.of("test", "param1"));
        });

        assertNull(resolver.getAbsoluteTimeDescriptor(paramDef, PathLocation.of("test"), Instant.now()));
        assertNull(resolver.getRelativeTimeDescriptor(paramDef, PathLocation.of("test"), Duration.ZERO));

        assertEquals(new BitString(new byte[] {(byte) 0xA0} , 3), resolver.getBitStringValue(paramDef2, PathLocation.of("test", "param2"), 3));
        paramDef2.setValue(null);
        assertEquals(new BitString(new byte[] { 0 }, 3), resolver.getBitStringValue(paramDef2, PathLocation.of("test", "param2"), 3));
        paramDef2.setValue("wrong");
        assertEquals(new BitString(new byte[] { 0 }, 3), resolver.getBitStringValue(paramDef2, PathLocation.of("test", "param2"), 3));
        assertThrows(EncodingException.class, () -> {
            resolver2.getBitStringValue(paramDef, PathLocation.of("test", "param1"), 3);
        });
    }

    public static class DummyResolver implements IEncodeResolver {

        @Override
        public boolean getBooleanValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
            return false;
        }

        @Override
        public int getEnumerationValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
            return 3;
        }

        @Override
        public long getSignedIntegerValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
            return 0;
        }

        @Override
        public long getUnsignedIntegerValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
            return 0;
        }

        @Override
        public double getRealValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
            return 12.4;
        }

        @Override
        public Instant getAbsoluteTimeValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
            return Instant.now();
        }

        @Override
        public Duration getRelativeTimeValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
            return Duration.ZERO;
        }

        @Override
        public BitString getBitStringValue(EncodedParameter parameter, PathLocation location, int maxBitlength) throws EncodingException {
            return new BitString(new byte[] { 0 }, 3);
        }

        @Override
        public byte[] getOctetStringValue(EncodedParameter parameter, PathLocation location, int maxByteLength) throws EncodingException {
            return new byte[0];
        }

        @Override
        public String getCharacterStringValue(EncodedParameter parameter, PathLocation location, int maxStringLength) throws EncodingException {
            return "w00t";
        }

        @Override
        public Object getExtensionValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
            return null;
        }

        @Override
        public AbsoluteTimeDescriptor getAbsoluteTimeDescriptor(EncodedParameter parameter, PathLocation location, Instant value) throws EncodingException {
            return null;
        }

        @Override
        public RelativeTimeDescriptor getRelativeTimeDescriptor(EncodedParameter parameter, PathLocation location, Duration value) throws EncodingException {
            return null;
        }
    }
}