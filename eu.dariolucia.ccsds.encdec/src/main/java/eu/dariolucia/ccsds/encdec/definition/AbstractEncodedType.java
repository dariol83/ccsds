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

package eu.dariolucia.ccsds.encdec.definition;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * This abstract class defines the type of the encoded parameter:
 * <ul>
 *     <li>When the type is known and it is static, then the {@link FixedType} class is used;</li>
 *     <li>When the type is dynamic and it depends on the value of a previous field, then the {@link ReferenceType} class is used;</li>
 *     <li>When the type is dynamic and it depends on the parameter ID provided in a previous field, then the {@link ParameterType} class is used</li>
 *     <li>When the type is not supported by the library, then an extension must be defined and the {@link ExtensionType} class is used.</li>
 * </ul>
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractEncodedType implements Serializable {

    public AbstractEncodedType() {
        // Nothing to do
    }

}
