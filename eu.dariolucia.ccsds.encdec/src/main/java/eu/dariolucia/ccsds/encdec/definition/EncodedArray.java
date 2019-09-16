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
 * The definition of a static or dynamic array. An array is a repetition of a sequence of one or more logically grouped encoded items.
 * In its typical definition, the size (i.e. number of repetitions) is fixed by definition, and the definition of encoded items
 * is a sequence of {@link EncodedParameter}. This data structure supports dynamic arrays, i.e. arrays whose number of
 * repetitions are driven by the value of a referenced encoded parameter. Arrays of array and arrays of structures are also supported.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class EncodedArray extends AbstractEncodedItem {

    @XmlElements({
            @XmlElement(name = "size_fixed", type = FixedArraySize.class),
            @XmlElement(name = "size_ref", type = ReferenceArraySize.class)
    })
    private AbstractArraySize size;

    @XmlElements({
            @XmlElement(name="parameter",type=EncodedParameter.class),
            @XmlElement(name="array",type=EncodedArray.class),
            @XmlElement(name="structure",type= EncodedStructure.class),
    })
    private List<AbstractEncodedItem> encodedItems = new LinkedList<>();

    public EncodedArray() {
    }

    public EncodedArray(AbstractEncodedItem... encodedItems) {
        this.encodedItems.addAll(Arrays.asList(encodedItems));
    }

    /**
     * This method returns the encoded items that will be repeated for every repetition of the array
     *
     * @return the elements composing one 'row' of the array
     */
    public List<AbstractEncodedItem> getEncodedItems() {
        return encodedItems;
    }

    /**
     * This method returns the definition of the array size, i.e. {@link FixedArraySize} or {@link ReferenceArraySize}.
     *
     * This is a mandatory field.
     *
     * @return the definition of the array size
     */
    public AbstractArraySize getSize() {
        return size;
    }

    public void setSize(AbstractArraySize size) {
        this.size = size;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EncodedArray that = (EncodedArray) o;
        return getId().equals(that.getId()) && size.equals(that.size) &&
                encodedItems.equals(that.encodedItems);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), size, encodedItems);
    }
}
