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

import eu.dariolucia.ccsds.encdec.structure.PathLocation;
import eu.dariolucia.ccsds.encdec.definition.EncodedParameter;
import eu.dariolucia.ccsds.encdec.structure.IEncodeResolver;
import eu.dariolucia.ccsds.encdec.value.BitString;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A resolver that looks up the values from a map, provided at construction time.
 *
 * This class is not thread-safe and it does not impose any control to prevent race conditions upon accessing the map. If
 * the map can be written externally from this class by a different thread, it is suggested to use a {@link java.util.concurrent.ConcurrentHashMap}
 * or {@link java.util.concurrent.ConcurrentSkipListMap}.
 */
public class PathLocationBasedResolver implements IEncodeResolver {

    private final Map<String, Object> location2value;

    private final AbsoluteTimeDescriptor absoluteTimeDescriptor;

    private final RelativeTimeDescriptor relativeTimeDescriptor;

    public PathLocationBasedResolver(Map<String, Object> location2value) {
        this(location2value, null, null);
    }

    public PathLocationBasedResolver(Map<String, Object> location2value, AbsoluteTimeDescriptor absoluteTimeDescriptor, RelativeTimeDescriptor relativeTimeDescriptor) {
        this.location2value = location2value;
        this.absoluteTimeDescriptor = Objects.requireNonNullElseGet(absoluteTimeDescriptor, () -> AbsoluteTimeDescriptor.cuc(4, 3));
        this.relativeTimeDescriptor = Objects.requireNonNullElseGet(relativeTimeDescriptor, () -> RelativeTimeDescriptor.cuc(4, 3));
    }

    private Object safeGet(PathLocation location) {
        if(location2value.containsKey(location.toString())) {
            return location2value.get(location.toString());
        } else {
            throw new IllegalArgumentException("Value for path " + location + " not found");
        }
    }

    @Override
    public boolean getBooleanValue(EncodedParameter parameter, PathLocation location) {
            return (boolean) safeGet(location);
    }

    @Override
    public int getEnumerationValue(EncodedParameter ei, PathLocation location) {
        return ((Number) safeGet(location)).intValue();
    }

    @Override
    public long getSignedIntegerValue(EncodedParameter parameter, PathLocation location) {
        return ((Number) safeGet(location)).longValue();
    }

    @Override
    public long getUnsignedIntegerValue(EncodedParameter parameter, PathLocation location) {
        return ((Number) safeGet(location)).longValue();
    }

    @Override
    public double getRealValue(EncodedParameter parameter, PathLocation location) {
        return ((Number) safeGet(location)).doubleValue();
    }

    @Override
    public Instant getAbsoluteTimeValue(EncodedParameter parameter, PathLocation location) {
        return (Instant) safeGet(location);
    }

    @Override
    public Duration getRelativeTimeValue(EncodedParameter parameter, PathLocation location) {
        return (Duration) safeGet(location);
    }

    @Override
    public BitString getBitStringValue(EncodedParameter parameter, PathLocation location, int maxBitlength) {
        return (BitString) safeGet(location);
    }

    @Override
    public byte[] getOctetStringValue(EncodedParameter parameter, PathLocation location, int maxByteLength) {
        return (byte[]) safeGet(location);
    }

    @Override
    public String getCharacterStringValue(EncodedParameter parameter, PathLocation location, int maxStringLength) {
        return (String) safeGet(location);
    }

    @Override
    public Object getExtensionValue(EncodedParameter parameter, PathLocation location) {
        return safeGet(location);
    }

    @Override
    public AbsoluteTimeDescriptor getAbsoluteTimeDescriptor(EncodedParameter parameter, PathLocation location, Instant value) {
        return absoluteTimeDescriptor;
    }

    @Override
    public RelativeTimeDescriptor getRelativeTimeDescriptor(EncodedParameter parameter, PathLocation location, Duration value) {
        return relativeTimeDescriptor;
    }
}
