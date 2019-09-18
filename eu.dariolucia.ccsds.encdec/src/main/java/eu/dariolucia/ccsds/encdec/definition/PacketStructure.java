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

import javax.xml.bind.annotation.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * An object of this class specifies the structure of a packet. The defined structure can be used to decode a packet
 * (i.e. to extract all the encoded information in the form of manageable collection of parameter values) and to encode
 * a packet (i.e. to format a provided set of parameter values into a binary format, respecting the encoding rules
 * defined inside the structure definition).
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class PacketStructure {

    @XmlElements({
            @XmlElement(name="parameter",type=EncodedParameter.class),
            @XmlElement(name="array",type=EncodedArray.class),
            @XmlElement(name="structure",type=EncodedStructure.class),
    })
    private List<AbstractEncodedItem> encodedItems = new LinkedList<>();

    public PacketStructure() {
    }

    public PacketStructure(AbstractEncodedItem... encodedItems) {
        this.encodedItems.addAll(Arrays.asList(encodedItems));
    }

    /**
     * The definition list of the encoded items.
     *
     * @return the encoded item definitions
     */
    public List<AbstractEncodedItem> getEncodedItems() {
        return encodedItems;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PacketStructure that = (PacketStructure) o;
        return encodedItems.equals(that.encodedItems);
    }

    @Override
    public int hashCode() {
        return Objects.hash(encodedItems);
    }
}
