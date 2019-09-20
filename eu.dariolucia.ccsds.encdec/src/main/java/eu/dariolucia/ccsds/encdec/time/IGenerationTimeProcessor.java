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

package eu.dariolucia.ccsds.encdec.time;

import eu.dariolucia.ccsds.encdec.definition.EncodedParameter;

import java.time.Duration;
import java.time.Instant;

/**
 * Implementations of this interface can be provided to the decoding process, to compute the generation time of each extracted parameter sample.
 */
public interface IGenerationTimeProcessor {

    /**
     * Compute the generation time of the specified encoded parameter.
     *
     * @param ei the {@link EncodedParameter} definition
     * @param value the value of the encoded parameter
     * @param derivedGenerationTime the generation time as derived by the encoded parameter definition, can be null
     * @param derivedOffset the offset as derived by the encoded parameter definition, can be null
     * @param fixedOffsetMs the fixed offset in milliseconds
     * @return the computed generation time
     */
    Instant computeGenerationTime(EncodedParameter ei, Object value, Instant derivedGenerationTime, Duration derivedOffset, Integer fixedOffsetMs);

}
