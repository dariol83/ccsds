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

public interface IEncodeResolver {

    boolean getBooleanValue(EncodedParameter parameter, PathLocation location);

    int getEnumerationValue(EncodedParameter parameter, PathLocation location);

    long getSignedIntegerValue(EncodedParameter parameter, PathLocation location);

    long getUnsignedIntegerValue(EncodedParameter parameter, PathLocation location);

    double getRealValue(EncodedParameter parameter, PathLocation location);

    Instant getAbsoluteTimeValue(EncodedParameter parameter, PathLocation location);

    Duration getRelativeTimeValue(EncodedParameter parameter, PathLocation location);

    BitString getBitStringValue(EncodedParameter parameter, PathLocation location, int maxBitlength);

    byte[] getOctetStringValue(EncodedParameter parameter, PathLocation location, int maxByteLength);

    String getCharacterStringValue(EncodedParameter parameter, PathLocation location, int maxStringLength);

    Object getExtensionValue(EncodedParameter parameter, PathLocation location);

    AbsoluteTimeDescriptor getAbsoluteTimeDescriptor(EncodedParameter parameter, PathLocation location, Instant value);

    RelativeTimeDescriptor getRelativeTimeDescriptor(EncodedParameter parameter, PathLocation location, Duration value);

    default void startPacketEncoding(PacketDefinition pd) {
        // Stub, redefine if packet definition is required for the encoding
    }

    default void endPacketEncoding() {
        // Stub, can be redefined
    }

    final class AbsoluteTimeDescriptor {

        public static AbsoluteTimeDescriptor cuc(int coarseTime, int fineTime) {
            return new AbsoluteTimeDescriptor(true, coarseTime, fineTime, false, 0);
        }

        public static AbsoluteTimeDescriptor cds(boolean use16bits, int subMsPart) {
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

    final class RelativeTimeDescriptor {

        public static RelativeTimeDescriptor cuc(int coarseTime, int fineTime) {
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
