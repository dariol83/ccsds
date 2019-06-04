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

import eu.dariolucia.ccsds.encdec.time.IGenerationTimeProcessor;

public interface IPacketDecoder {

    DecodingResult decode(String packetDefinitionId, byte[] data, int offset, int length, IGenerationTimeProcessor timeProcessor);

    default DecodingResult decode(String packetDefinitionId, byte[] data, int offset, int length) {
        return decode(packetDefinitionId, data, offset, length, null);
    }

    default DecodingResult decode(String packetDefinitionId, byte[] data) {
        return decode(packetDefinitionId, data, 0, data.length);
    }

    default DecodingResult decode(String packetDefinitionId, byte[] data, IGenerationTimeProcessor timeProcessor) {
        return decode(packetDefinitionId, data, 0, data.length, timeProcessor);
    }
}
