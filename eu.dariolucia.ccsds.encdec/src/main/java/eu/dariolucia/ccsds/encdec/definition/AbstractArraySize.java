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

import java.io.Serializable;

/**
 * This abstract class defines the size of an array, which can be provided statically in the definition, using the
 * {@link FixedArraySize} class, or can be set to be read from a specific parameter, using the {@link ReferenceArraySize}
 * class.
 */
public abstract class AbstractArraySize implements Serializable {

    public AbstractArraySize() {
        // Nothing to do
    }
}
