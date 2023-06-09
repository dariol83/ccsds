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

import eu.dariolucia.ccsds.sle.utl.config.network.PortMapping;
import eu.dariolucia.ccsds.sle.utl.config.network.RemotePeer;

import jakarta.xml.bind.DatatypeConverter;
import jakarta.xml.bind.annotation.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * This class contains the mapping configuration and authentication configuration of the local peer, the different
 * remote peers and the remote ports. For each port mapping it is possible to specify some lower layer parameters,
 * such as the TCP reception and transmission buffers.
 * <p>
 * Since binary data is defined to be used for the passwords, password fields must be specified as hex dump.
 * See CCSDS 913.1-B-2 3.1.1, point b).
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class PeerConfiguration {

    /**
     * The authentication delay in seconds (ref. CCSDS 913.1-B-2, 3.1.2.2.1)
     */
    @XmlAttribute(name = "auth-delay")
    private int authenticationDelay = 6;

    /**
     * The local peer ID: this must be equal to the corresponding role specified for the service instance that
     * is intended to be used (ref. CCSDS 913.1-B-2, 3.1.1, point a), comma 2).
     */
    @XmlAttribute(name = "id")
    private String localId = "";

    /**
     * The local password as hex dump.
     */
    @XmlAttribute(name = "password")
    private String localPassword = "";

    /**
     * The list of port mappings that map each responder port identifier to the physical TCP/IP host and port to connect
     * to (initiator role: user) or to listen to (initiator role: provider).
     */
    @XmlElementWrapper(name = "port-mappings")
    @XmlElement(name = "mapping")
    private List<PortMapping> portMappings = new LinkedList<>();

    /**
     * The list of remote peers, with their id, password, authentication mode and authentication algorithm. The
     * authentication algorithm to be used can be specified independently from the SLE version and it is applied per
     * peer.
     */
    @XmlElementWrapper(name = "remote-peers")
    @XmlElement(name = "peer")
    private List<RemotePeer> remotePeers = new LinkedList<>();

    public int getAuthenticationDelay() {
        return authenticationDelay;
    }

    public String getLocalId() {
        return localId;
    }

    public byte[] getLocalPassword() {
        return DatatypeConverter.parseHexBinary(localPassword);
    }

    public List<PortMapping> getPortMappings() {
        return portMappings;
    }

    public List<RemotePeer> getRemotePeers() {
        return remotePeers;
    }

    public void setAuthenticationDelay(int authenticationDelay) {
        this.authenticationDelay = authenticationDelay;
    }

    public void setLocalId(String localId) {
        this.localId = localId;
    }

    public void setLocalPassword(String localPassword) {
        this.localPassword = localPassword;
    }

    public void setPortMappings(List<PortMapping> portMappings) {
        this.portMappings = portMappings;
    }

    public void setRemotePeers(List<RemotePeer> remotePeers) {
        this.remotePeers = remotePeers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerConfiguration that = (PeerConfiguration) o;
        return authenticationDelay == that.authenticationDelay &&
                Objects.equals(localId, that.localId) &&
                Objects.equals(localPassword, that.localPassword) &&
                Objects.equals(portMappings, that.portMappings) &&
                Objects.equals(remotePeers, that.remotePeers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authenticationDelay, localId, localPassword, portMappings, remotePeers);
    }
}
