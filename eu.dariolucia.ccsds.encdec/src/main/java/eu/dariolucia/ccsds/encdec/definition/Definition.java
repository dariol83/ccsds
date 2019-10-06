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

/**
 * This class is the root element of the definition database. It is a container for the identification fields,
 * the packet definitions and the parameter definitions:
 * <ul>
 *     <li>
 * The identification fields are the entities that allow to define identification criteria for
 * packet recognition.
 * </li>
 * <li>
 * The packet definitions specify the structure of the packets in the form of identification criteria and encoded items
 * (i.e. parameters/array/structures and related positions, lengths and
 * types). For each encoded parameter, a way to specify how to compute its generation time is provided.
 * </li>
 * <li>
 * The parameter definitions specify the parameters as source of information to be encoded in packet definitions.
 * Their use is necessary when the type and length of the encoded parameter are derived from other fields, which
 * contain the IDs of these parameters. In ECSS-E-70-41A, these are called on-board parameters and are identified on-board
 * by means of an unsigned integer.
 * </li>
 * </ul>
 */
@XmlRootElement(name = "packet_definitions", namespace = "http://dariolucia.eu/ccsds/encdec")
@XmlAccessorType(XmlAccessType.FIELD)
public class Definition {

    /**
     * This method loads a {@link Definition} object from an {@link InputStream}.
     *
     * @param in the input stream, to read from
     * @return the loaded definition
     * @throws IOException in case of problems while processing the input stream
     */
    public static Definition load(InputStream in) throws IOException {
        try {
            JAXBContext context = JAXBContext.newInstance(Definition.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            return (Definition) unmarshaller.unmarshal(in);
        } catch (JAXBException e) {
            throw new IOException(e);
        }
    }

    /**
     * This method serialises the provided {@link Definition} object to the provided
     * {@link OutputStream}.
     *
     * @param d   the definition to serialise
     * @param out the output stream
     * @throws IOException in case of problems while serialising or writing to the stream
     */
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

    /**
     * This method returns the defined identification fields that can be used for packet recognition.
     *
     * @return the identification fields present in the definition
     */
    public List<IdentField> getIdentificationFields() {
        return identificationFields;
    }

    /**
     * This method returns the packet definitions.
     *
     * @return the packet definitions
     */
    public List<PacketDefinition> getPacketDefinitions() {
        return packetDefinitions;
    }

    /**
     * This method returns the parameters defined in the definition.
     *
     * @return the parameters present in the definition
     */
    public List<ParameterDefinition> getParameters() {
        return parameters;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Definition that = (Definition) o;
        return Objects.equals(getIdentificationFields(), that.getIdentificationFields()) &&
                Objects.equals(getPacketDefinitions(), that.getPacketDefinitions()) &&
                Objects.equals(getParameters(), that.getParameters());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getIdentificationFields(), getPacketDefinitions(), getParameters());
    }
}
