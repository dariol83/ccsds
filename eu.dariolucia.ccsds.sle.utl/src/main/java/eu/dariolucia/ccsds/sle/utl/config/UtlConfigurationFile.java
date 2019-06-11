/*
 *  Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package eu.dariolucia.ccsds.sle.utl.config;

import eu.dariolucia.ccsds.sle.utl.config.cltu.CltuServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.raf.RafServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.rcf.RcfServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.rocf.RocfServiceInstanceConfiguration;

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
 * This class represents the entry point for the configuration of the SLE User Test Library. The configuration shall
 * specify:
 * <ul>
 * <li>the configuration of the peers, i.e. the remote entities that will be connected to the library;</li>
 * <li>the configuration of the service instances, according to the service management parameters as per Blue Book.</li>
 * </ul>
 * Some of the service management parameters are not used during initialisation (e.g. the SLE version number to be
 * used, or the start-end times), but are added to the configuration of the service instance to have a self-contained
 * configure of the SLE service that one wants to use.
 */
@XmlRootElement(name = "utl-configuration-file")
@XmlAccessorType(XmlAccessType.FIELD)
public class UtlConfigurationFile {

    /**
     * Load the SLE User Test Library configuration in memory from an input stream.
     *
     * @param is the input stream to parse
     * @return the SLE User Test Library configuration
     * @throws IOException if the stream cannot be parsed
     */
    public static UtlConfigurationFile load(InputStream is) throws IOException {
        try {
            JAXBContext jc = JAXBContext.newInstance(UtlConfigurationFile.class);
            Unmarshaller u = jc.createUnmarshaller();
            UtlConfigurationFile o = (UtlConfigurationFile) u.unmarshal(is);
            return o;
        } catch (JAXBException e) {
            throw new IOException(e);
        }
    }

    /**
     * Save the SLE User Test Library configuration to an output stream.
     *
     * @param configuration the configuration to serialize
     * @param out           the output stream of the serialized configuration
     * @throws IOException if there are problems encountered when writing to the output stream
     */
    public static void save(UtlConfigurationFile configuration, OutputStream out) throws IOException {
        try {
            JAXBContext context = JAXBContext.newInstance(UtlConfigurationFile.class);
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(configuration, out);
        } catch (JAXBException e) {
            throw new IOException(e);
        }
    }

    /**
     * The configuration of the remote peers.
     */
    @XmlElement(name = "peer-configuration", required = true)
    private PeerConfiguration peerConfiguration;

    /**
     * The configuration of the service instances.
     */
    @XmlElementWrapper(name = "service-instances")
    @XmlElements({
            @XmlElement(name = "raf", type = RafServiceInstanceConfiguration.class),
            @XmlElement(name = "rcf", type = RcfServiceInstanceConfiguration.class),
            @XmlElement(name = "rocf", type = RocfServiceInstanceConfiguration.class),
            @XmlElement(name = "cltu", type = CltuServiceInstanceConfiguration.class)
    })
    private List<ServiceInstanceConfiguration> serviceInstances = new LinkedList<>();

    /**
     * Retrieve the list of defined service instances.
     *
     * @return the service instances defined by the configuration
     */
    public List<ServiceInstanceConfiguration> getServiceInstances() {
        return serviceInstances;
    }

    /**
     * Set the list of defined service instances. When called, this method simply sets
     * the internal instance variable, hence replacing the old list.
     *
     * @param serviceInstances the list of service instances to set
     */
    public void setServiceInstances(List<ServiceInstanceConfiguration> serviceInstances) {
        this.serviceInstances = serviceInstances;
    }

    /**
     * Retrieve the configuration of the remote peers.
     *
     * @return the peer configuration
     */
    public PeerConfiguration getPeerConfiguration() {
        return peerConfiguration;
    }

    /**
     * Set the configuration of the remote peers.
     *
     * @param peerConfiguration the peer  configuration to set
     */
    public void setPeerConfiguration(PeerConfiguration peerConfiguration) {
        this.peerConfiguration = peerConfiguration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UtlConfigurationFile that = (UtlConfigurationFile) o;
        return Objects.equals(peerConfiguration, that.peerConfiguration) &&
                Objects.equals(serviceInstances, that.serviceInstances);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peerConfiguration, serviceInstances);
    }
}
