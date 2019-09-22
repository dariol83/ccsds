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
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlIDREF;
import java.util.Objects;

/**
 * The fixed top level parameter, linked to an encoded parameter.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class FixedLinkedParameter extends AbstractLinkedParameter {

    @XmlIDREF
    @XmlAttribute(name="parameter", required = true)
    private ParameterDefinition parameter;

    public FixedLinkedParameter() {
    }

    public FixedLinkedParameter(ParameterDefinition id) {
        this.parameter = id;
    }

    /**
     * The {@link ParameterDefinition} of the linked top level parameter.
     *
     * This is a mandatory field.
     *
     * @return the definition of the linked top level parameter
     */
    public ParameterDefinition getParameter() {
        return parameter;
    }

    public void setParameter(ParameterDefinition parameter) {
        this.parameter = parameter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FixedLinkedParameter that = (FixedLinkedParameter) o;
        return Objects.equals(getParameter(), that.getParameter());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getParameter());
    }
}
