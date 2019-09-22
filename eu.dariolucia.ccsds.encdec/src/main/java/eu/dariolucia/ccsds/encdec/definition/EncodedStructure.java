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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * A container for encoded parameters, arrays and nested structures, to group related data together.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class EncodedStructure extends AbstractEncodedItem {

    @XmlElements({
            @XmlElement(name="parameter",type=EncodedParameter.class),
            @XmlElement(name="array",type=EncodedArray.class),
            @XmlElement(name="structure",type=EncodedStructure.class),
    })
    private List<AbstractEncodedItem> encodedItems = new LinkedList<>();

    public EncodedStructure() {
    }

    public EncodedStructure(AbstractEncodedItem... encodedItems) {
        this.encodedItems.addAll(Arrays.asList(encodedItems));
    }

    /**
     * The list of encoded items composing this encoded structure.
     *
     * @return the list of encoded items
     */
    public List<AbstractEncodedItem> getEncodedItems() {
        return encodedItems;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        EncodedStructure that = (EncodedStructure) o;
        return Objects.equals(getEncodedItems(), that.getEncodedItems());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getEncodedItems());
    }
}
