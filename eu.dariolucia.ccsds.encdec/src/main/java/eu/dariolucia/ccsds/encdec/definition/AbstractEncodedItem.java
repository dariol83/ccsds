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

/**
 * This class represents a generalisation of an encoded item (simple parameter, arrays, data structures). The item is
 * uniquely identified by an ID, which shall be unique within the context of a packet structure. The item can specify
 * an optional location, which is taken into account by the encoding and decoding process accordingly.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractEncodedItem {

    @XmlAttribute(name = "id", required = true)
    private String id;

    @XmlElements({
            @XmlElement(name="location_absolute",type= FixedAbsoluteLocation.class, required = false),
            @XmlElement(name="location_last",type= LastRelativeLocation.class, required = false),
            @XmlElement(name="location_param",type= EncodedItemRelativeLocation.class, required = false),
    })
    private AbstractEncodedLocation location = null;

    public AbstractEncodedItem() {
    }

    public AbstractEncodedItem(String id) {
        this.id = id;
    }

    /**
     * The ID of the encoded item. This ID must be unique within the scope of the packet.
     * It cannot be null.
     *
     * @return the ID of the encoded item
     */
    public String getId() {
        return id;
    }

    /**
     * The location of the encoded item.
     * When the packet encoding/decoding takes place, this field is used to move the position of the encoding/decoding
     * index accordingly.
     * It can be null.
     *
     * @return the encoding/decoding location of the item
     * @see AbstractEncodedLocation
     */
    public AbstractEncodedLocation getLocation() {
        return location;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setLocation(AbstractEncodedLocation location) {
        this.location = location;
    }
}
