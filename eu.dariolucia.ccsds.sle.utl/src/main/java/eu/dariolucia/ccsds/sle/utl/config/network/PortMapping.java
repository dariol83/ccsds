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

package eu.dariolucia.ccsds.sle.utl.config.network;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import java.util.Objects;

/**
 * This class is used to specify the connection characteristics of the Transmission Mapping Layer (ref.
 * CCSDS 913.1-B-2 3.3):
 * <ul>
 * <li>TCP/IP host and port (ref. CCSDS 913.1-B-2, 2.6.6.3.2 and 2.6.6.3.3)</li>
 * <li>dead factor (ref. CCSDS 913.1-B-2, 2.5.2.3)</li>
 * <li>heartbeat interval</li>
 * <li>TCP buffers.</li>
 * </ul>
 * Depending on the initiator role specified in the service instance configuration, the provided IP host and TCP port
 * can be used as destination address (the user initiates the connection and sends the bind operation, ref.
 * 913.1-B-2 2.6.6.3.3), or as port to use, to wait for incoming connections (the provider initiates the connection
 * and sends the bind operation, ref. CCSDS 913.1-B-2 2.6.6.3.2). In the latter case, the host must be specified
 * but it is ignored (0.0.0.0 or any other string can be used).
 * <p>
 * Differently from the standard, the SLE User Test Library does not allow to specify more than a single mapping
 * (host and port) for a given responder port id.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class PortMapping {

    /**
     * The responder port id, as specified in the service instance configuration.
     */
    @XmlAttribute(name = "port-name")
    private String portName = null;

    /**
     * The dead factor (integer).
     */
    @XmlAttribute(name = "dead-factor")
    private int deadFactor = 3;

    /**
     * The heartbeat interval in seconds.
     */
    @XmlAttribute(name = "heartbeat-interval")
    private int heartbeatInterval = 60;

    /**
     * The TCP/IP address, specified using the following format:
     * xxx.xxx.xxx.xxx:yyyyy
     */
    @XmlAttribute(name = "address")
    private String address = null;

    /**
     * The size of the TCP transmission buffer in bytes. By default is 0, i.e. not set.
     */
    @XmlAttribute(name = "tcp-tx-buffer")
    private int tcpTxBufferSize = 0;

    /**
     * The size of the TCP reception buffer in bytes. By default is 0, i.e. not set.
     */
    @XmlAttribute(name = "tcp-rx-buffer")
    private int tcpRxBufferSize = 0;

    public PortMapping() {
    }

    public PortMapping(String portName, int deadFactor, int heartbeatInterval, String address, int tcpTxBufferSize, int tcpRxBufferSize) {
        this.portName = portName;
        this.deadFactor = deadFactor;
        this.heartbeatInterval = heartbeatInterval;
        this.address = address;
        this.tcpTxBufferSize = tcpTxBufferSize;
        this.tcpRxBufferSize = tcpRxBufferSize;
    }

    public String getPortName() {
        return portName;
    }

    public void setPortName(String portName) {
        this.portName = portName;
    }

    public int getDeadFactor() {
        return deadFactor;
    }

    public void setDeadFactor(int deadFactor) {
        this.deadFactor = deadFactor;
    }

    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(int heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getTcpTxBufferSize() {
        return tcpTxBufferSize;
    }

    public void setTcpTxBufferSize(int tcpTxBufferSize) {
        this.tcpTxBufferSize = tcpTxBufferSize;
    }

    public int getTcpRxBufferSize() {
        return tcpRxBufferSize;
    }

    public void setTcpRxBufferSize(int tcpRxBufferSize) {
        this.tcpRxBufferSize = tcpRxBufferSize;
    }

    public String getRemoteHost() {
        return getAddress().substring(0, getAddress().lastIndexOf(':'));
    }

    public int getRemotePort() {
        return Integer.parseInt(getAddress().substring(getAddress().lastIndexOf(':') + 1));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PortMapping that = (PortMapping) o;
        return deadFactor == that.deadFactor &&
                heartbeatInterval == that.heartbeatInterval &&
                tcpTxBufferSize == that.tcpTxBufferSize &&
                tcpRxBufferSize == that.tcpRxBufferSize &&
                Objects.equals(portName, that.portName) &&
                Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(portName, deadFactor, heartbeatInterval, address, tcpTxBufferSize, tcpRxBufferSize);
    }
}
