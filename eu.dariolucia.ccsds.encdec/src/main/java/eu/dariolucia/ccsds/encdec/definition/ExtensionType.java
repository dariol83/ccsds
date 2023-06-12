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

import eu.dariolucia.ccsds.encdec.extension.ExtensionId;
import eu.dariolucia.ccsds.encdec.extension.IDecoderExtension;
import eu.dariolucia.ccsds.encdec.extension.IEncoderExtension;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import java.util.Objects;

/**
 * The semantic of objects of this class is the following: the type is not one of those handled by the library, but
 * the encoding/decoding is provided by an external functions, implemented by ({@link IEncoderExtension}) and {@link IDecoderExtension}
 * registered extensions.
 *
 * If such functions do not exist, an exception is usually thrown during the encoding/decoding process.
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

    /**
     * The ID identifying the external function to invoke. The ID shall be added as {@link ExtensionId} annotation to the
     * {@link IEncoderExtension} and {@link IDecoderExtension} implementations.
     *
     * This is a mandatory field.
     *
     * @return the ID of the external encoding/decoding function to use
     */
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
        return Objects.equals(getExternal(), that.getExternal());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getExternal());
    }
}
