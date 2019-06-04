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

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

@XmlRootElement(name = "packet_definitions", namespace = "http://dariolucia.eu/ccsds/encdec")
@XmlAccessorType(XmlAccessType.FIELD)
public class Definition {

    public static Definition load(InputStream in) throws IOException {
        try {
            JAXBContext context = JAXBContext.newInstance(Definition.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            Definition d = (Definition) unmarshaller.unmarshal(in);
            return d;
        } catch (JAXBException e) {
            throw new IOException(e);
        }
    }

    public static void save(Definition d, OutputStream out) throws IOException {
        try {
            JAXBContext context = JAXBContext.newInstance(Definition.class);
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(d, out);
        } catch (JAXBException e) {
            throw new IOException(e);
        }
    }

    @XmlElementWrapper(name = "id_fields")
    @XmlElement(name = "field")
    private List<IdentField> identificationFields = new LinkedList<>();

    @XmlElementWrapper(name = "packets")
    @XmlElement(name = "packet")
    private List<PacketDefinition> packetDefinitions = new LinkedList<>();

    @XmlElementWrapper(name = "parameters")
    @XmlElement(name = "parameter")
    private List<ParameterDefinition> parameters = new LinkedList<>();

    public List<IdentField> getIdentificationFields() {
        return identificationFields;
    }

    public List<PacketDefinition> getPacketDefinitions() {
        return packetDefinitions;
    }

    public List<ParameterDefinition> getParameters() {
        return parameters;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Definition that = (Definition) o;
        return identificationFields.equals(that.identificationFields) &&
                packetDefinitions.equals(that.packetDefinitions) && parameters.equals(that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identificationFields, packetDefinitions, parameters);
    }
}
