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

import java.time.Instant;

/**
 * An instance of this class is provided in the {@link DecodingResult} object for each {@link eu.dariolucia.ccsds.encdec.definition.EncodedParameter}
 * that is linked to a {@link eu.dariolucia.ccsds.encdec.definition.ParameterDefinition}. In such case, the instance contains
 * the ID of the {@link eu.dariolucia.ccsds.encdec.definition.ParameterDefinition}, the value and the generation time, if computed.
 */
public class ParameterValue {

    private final String id;
    private final long externalId;
    private final Object value;
    private final Instant generationTime;

    /**
     * Construct a {@link ParameterValue} instance.
     *
     * @param id the ID of the {@link ParameterValue}
     * @param externalId the external ID of the parameter
     * @param value the value as extracted from the packet
     * @param time the generation time, can be null
     */
    public ParameterValue(String id, long externalId, Object value, Instant time) {
        this.id = id;
        this.externalId = externalId;
        this.value = value;
        this.generationTime = time;
    }

    /**
     * Construct a {@link ParameterValue} instance with null generation time.
     *
     * @param id the ID of the {@link ParameterValue}
     * @param externalId the external ID of the parameter
     * @param value the value as extracted from the packet
     */
    public ParameterValue(String id, long externalId, Object value) {
        this(id, externalId, value, null);
    }

    /**
     * Return the generation time, can be null.
     *
     * @return the generation time or null if not computed
     */
    public Instant getGenerationTime() {
        return generationTime;
    }

    /**
     * Return the {@link eu.dariolucia.ccsds.encdec.definition.ParameterDefinition} ID linked to the decoded parameter.
     *
     * @return the {@link eu.dariolucia.ccsds.encdec.definition.ParameterDefinition} ID
     */
    public String getId() {
        return id;
    }

    /**
     * Return the value extracted from the decoded parameter.
     *
     * @return the decoded value
     */
    public Object getValue() {
        return value;
    }

    /**
     * Return the parameter external ID, as defined in the related {@link eu.dariolucia.ccsds.encdec.definition.ParameterDefinition}.
     *
     * @return the external ID
     */
    public long getExternalId() {
        return externalId;
    }

    @Override
    public String toString() {
        return "ParameterValue {" +
                "id='" + id + '\'' +
                ", externalId=" + externalId +
                ", value=" + value +
                ", generationTime=" + generationTime +
                '}';
    }
}
