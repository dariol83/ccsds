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

package eu.dariolucia.ccsds.encdec.extension;

import eu.dariolucia.ccsds.encdec.structure.PathLocation;
import eu.dariolucia.ccsds.encdec.definition.EncodedParameter;
import eu.dariolucia.ccsds.encdec.definition.DataTypeEnum;

/**
 * An instance of this class is used to compute the length in bits of a parameter, when its defined length is of type
 * {@link eu.dariolucia.ccsds.encdec.definition.ParameterLength} and:
 * <ul>
 *     <li>either the value pointed by the parameter reference is not a {@link Number};</li>
 *     <li>or the value is a {@link Number} but a corresponding {@link eu.dariolucia.ccsds.encdec.definition.ParameterDefinition} with
 *     external ID equal to the number cannot be found in the definition database.</li>
 * </ul>
 */
public interface ILengthMapper {

    /**
     * This method returns the length, in bits, of the parameter.
     * @param parameter the definition of the parameter
     * @param location the location of the parameter in the packet definition
     * @param type the type of the parameter
     * @param mapValue the value as extracted by the parameter reference
     * @return the length in bits of the parameter
     */
    int mapLength(EncodedParameter parameter, PathLocation location, DataTypeEnum type, Object mapValue);

}
