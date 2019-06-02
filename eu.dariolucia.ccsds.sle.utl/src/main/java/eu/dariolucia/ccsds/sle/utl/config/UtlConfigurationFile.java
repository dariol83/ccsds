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
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.*;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

/**
 * This class represents the entry point for the configuration of the SLE User Test Library. The configuration shall
 * specify:
 * - the configuration of the peers, i.e. the remote entities that will be connected to the library;
 * - the configuration of the service instances, according to the service management parameters as per Blue Book.
 *
 * Some of the service management parameters are not used during initialisation (e.g. the SLE version number to be
 * used, or the start-end times), but are added to the configuration of the service instance to have a self-contained
 * configure of the SLE service that one wants to use.
 */
@XmlRootElement(name = "utl-configuration-file")
@XmlAccessorType(XmlAccessType.FIELD)
public class UtlConfigurationFile {

    public static UtlConfigurationFile load(InputStream is) throws JAXBException {
        JAXBContext jc = JAXBContext.newInstance(UtlConfigurationFile.class);
        Unmarshaller u = jc.createUnmarshaller();
        UtlConfigurationFile o = (UtlConfigurationFile) u.unmarshal(is);
        return o;
    }

    /**
     * The configuration of the remote peers.
     */
    @XmlElement(name = "peer-configuration", required = true)
    private PeerConfiguration peerConfiguration;

    /**
     * The configuration of the service instances.
     */
    @XmlElementWrapper(name="service-instances")
    @XmlElements({
            @XmlElement(name = "raf", type = RafServiceInstanceConfiguration.class),
            @XmlElement(name = "rcf", type = RcfServiceInstanceConfiguration.class),
            @XmlElement(name = "rocf", type = RocfServiceInstanceConfiguration.class),
            @XmlElement(name = "cltu", type = CltuServiceInstanceConfiguration.class)
    })
    private List<ServiceInstanceConfiguration> serviceInstances = new LinkedList<>();

    public List<ServiceInstanceConfiguration> getServiceInstances() {
        return serviceInstances;
    }

    public void setServiceInstances(List<ServiceInstanceConfiguration> serviceInstances) {
        this.serviceInstances = serviceInstances;
    }

    public PeerConfiguration getPeerConfiguration() {
        return peerConfiguration;
    }

    public void setPeerConfiguration(PeerConfiguration peerConfiguration) {
        this.peerConfiguration = peerConfiguration;
    }
}
