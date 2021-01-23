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
 * This abstract class defines the length of an encoded parameter. There are three ways to specify the encoded
 * length:
 * <ul>
 *     <li>Statically, by using the {@link FixedLength} class</li>
 *     <li>Dynamically, by the encoded value in the packet pointed by the {@link ReferenceLength} class</li>
 *     <li>Dynamically, by using the length of the parameter specified by reading the value pointed by the {@link ParameterLength} class</li>
 * </ul>
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractEncodedLength implements Serializable { // NOSONAR I won't convert this class into an interface, logically it is not an interface

    public AbstractEncodedLength() {
        // Nothing to do
    }

}
