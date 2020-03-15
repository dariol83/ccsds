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
import eu.dariolucia.ccsds.encdec.definition.IdentFieldMatcher;
import eu.dariolucia.ccsds.encdec.definition.PacketDefinition;
import eu.dariolucia.ccsds.encdec.structure.EncodingException;
import eu.dariolucia.ccsds.encdec.structure.IEncodeResolver;
import eu.dariolucia.ccsds.encdec.structure.PathLocation;
import eu.dariolucia.ccsds.encdec.time.AbsoluteTimeDescriptor;
import eu.dariolucia.ccsds.encdec.time.RelativeTimeDescriptor;
import eu.dariolucia.ccsds.encdec.value.BitString;

import java.time.Duration;
import java.time.Instant;

/**
 * A resolver that returns the value of the corresponding identification field of the packet under encoding, if the parameter
 * is of type integer (signed or unsigned) and the id of the encoded parameter is equal to the id of the identification field.
 * If the id is different, then the call is routed to the delegate resolver.
 */
public class IdentificationFieldBasedResolver implements IEncodeResolver {

    private final IEncodeResolver delegate;

    private PacketDefinition currentDefinition;

    public IdentificationFieldBasedResolver(IEncodeResolver delegate) {
        if(delegate == null) {
            throw new NullPointerException("Delegate resolver must be provided");
        }
        this.delegate = delegate;
    }

    @Override
    public boolean getBooleanValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
        return delegate.getBooleanValue(parameter, location);
    }

    @Override
    public int getEnumerationValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
        return delegate.getEnumerationValue(parameter, location);
    }

    @Override
    public long getSignedIntegerValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
        if(this.currentDefinition != null) {
            for (IdentFieldMatcher ifm : this.currentDefinition.getMatchers()) {
                if (ifm.getField().getId().equals(parameter.getId())) {
                    return ifm.getValue();
                }
            }
        }
        return delegate.getSignedIntegerValue(parameter, location);
    }

    @Override
    public long getUnsignedIntegerValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
        if(this.currentDefinition != null) {
            for (IdentFieldMatcher ifm : this.currentDefinition.getMatchers()) {
                if (ifm.getField().getId().equals(parameter.getId())) {
                    return ifm.getValue();
                }
            }
        }
        return delegate.getUnsignedIntegerValue(parameter, location);
    }

    @Override
    public double getRealValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
        return delegate.getRealValue(parameter, location);
    }

    @Override
    public Instant getAbsoluteTimeValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
        return delegate.getAbsoluteTimeValue(parameter, location);
    }

    @Override
    public Duration getRelativeTimeValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
        return delegate.getRelativeTimeValue(parameter, location);
    }

    @Override
    public BitString getBitStringValue(EncodedParameter parameter, PathLocation location, int maxBitlength) throws EncodingException {
        return delegate.getBitStringValue(parameter, location, maxBitlength);
    }

    @Override
    public byte[] getOctetStringValue(EncodedParameter parameter, PathLocation location, int maxByteLength) throws EncodingException {
        return delegate.getOctetStringValue(parameter, location, maxByteLength);
    }

    @Override
    public String getCharacterStringValue(EncodedParameter parameter, PathLocation location, int maxStringLength) throws EncodingException {
        return delegate.getCharacterStringValue(parameter, location, maxStringLength);
    }

    @Override
    public Object getExtensionValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
        return delegate.getExtensionValue(parameter, location);
    }

    @Override
    public AbsoluteTimeDescriptor getAbsoluteTimeDescriptor(EncodedParameter parameter, PathLocation location, Instant value) throws EncodingException {
        return delegate.getAbsoluteTimeDescriptor(parameter, location, value);
    }

    @Override
    public RelativeTimeDescriptor getRelativeTimeDescriptor(EncodedParameter parameter, PathLocation location, Duration value) throws EncodingException {
        return delegate.getRelativeTimeDescriptor(parameter, location, value);
    }

    @Override
    public void startPacketEncoding(PacketDefinition pd) {
        this.currentDefinition = pd;
        delegate.startPacketEncoding(pd);
    }

    @Override
    public void endPacketEncoding() {
        this.currentDefinition = null;
        delegate.endPacketEncoding();
    }
}
