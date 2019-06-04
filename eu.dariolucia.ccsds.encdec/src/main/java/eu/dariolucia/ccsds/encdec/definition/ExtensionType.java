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

import eu.dariolucia.ccsds.encdec.extension.IEncoderExtension;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import java.util.Objects;

/**
 * The semantic of objects of this class is the following: the type of the encoded parameter is provided by an external
 * function ({@link IEncoderExtension}). If such function does not exist, an exception is thrown.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class ExtensionType extends AbstractEncodedType {

    @XmlAttribute(name = "external", required = true)
    private String external;

    public ExtensionType() {
    }

    public ExtensionType(String external) {
        this.external = external;
    }

    public String getExternal() {
        return external;
    }

    public void setExternal(String external) {
        this.external = external;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtensionType that = (ExtensionType) o;
        return external.equals(that.external);
    }

    @Override
    public int hashCode() {
        return Objects.hash(external);
    }
}
