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

package eu.dariolucia.ccsds.encdec.structure.resolvers;

import eu.dariolucia.ccsds.encdec.definition.EncodedParameter;
import eu.dariolucia.ccsds.encdec.structure.IEncodeResolver;
import eu.dariolucia.ccsds.encdec.structure.PathLocation;
import eu.dariolucia.ccsds.encdec.value.BitString;

import java.time.Duration;
import java.time.Instant;

/**
 * A resolver that returns always 0, false, strings of "0" characters, absolute Instant with time 0, relative Duration
 * with length 0, null extension value, byte arrays with elements equal to 0x00, bitstrings with bits set to 0.
 */
public class DefaultNullBasedResolver implements IEncodeResolver {

    private final AbsoluteTimeDescriptor absoluteTimeDescriptor;

    private final RelativeTimeDescriptor relativeTimeDescriptor;

    public DefaultNullBasedResolver() {
        this.absoluteTimeDescriptor = AbsoluteTimeDescriptor.cuc(4, 3);
        this.relativeTimeDescriptor = RelativeTimeDescriptor.cuc(4, 3);
    }

    @Override
    public boolean getBooleanValue(EncodedParameter parameter, PathLocation location) {
        return false;
    }

    @Override
    public int getEnumerationValue(EncodedParameter parameter, PathLocation location) {
        return 0;
    }

    @Override
    public long getSignedIntegerValue(EncodedParameter parameter, PathLocation location) {
        return 0;
    }

    @Override
    public long getUnsignedIntegerValue(EncodedParameter parameter, PathLocation location) {
        return 0;
    }

    @Override
    public double getRealValue(EncodedParameter parameter, PathLocation location) {
        return 0;
    }

    @Override
    public Instant getAbsoluteTimeValue(EncodedParameter parameter, PathLocation location) {
        return Instant.ofEpochMilli(0);
    }

    @Override
    public Duration getRelativeTimeValue(EncodedParameter parameter, PathLocation location) {
        return Duration.ofSeconds(0);
    }

    @Override
    public BitString getBitStringValue(EncodedParameter parameter, PathLocation location, int maxBitlength) {
        return new BitString(new byte[(int) Math.ceil(maxBitlength/8.0)], maxBitlength);
    }

    @Override
    public byte[] getOctetStringValue(EncodedParameter parameter, PathLocation location, int maxByteLength) {
        return new byte[maxByteLength];
    }

    @Override
    public String getCharacterStringValue(EncodedParameter parameter, PathLocation location, int maxStringLength) {
        return "0".repeat(Math.max(0, maxStringLength));
    }

    @Override
    public Object getExtensionValue(EncodedParameter parameter, PathLocation location) {
        // Not supported
        return null;
    }

    @Override
    public IEncodeResolver.AbsoluteTimeDescriptor getAbsoluteTimeDescriptor(EncodedParameter parameter, PathLocation location, Instant value) {
        return absoluteTimeDescriptor;
    }

    @Override
    public IEncodeResolver.RelativeTimeDescriptor getRelativeTimeDescriptor(EncodedParameter parameter, PathLocation location, Duration value) {
        return relativeTimeDescriptor;
    }
}
