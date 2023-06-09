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

import eu.dariolucia.ccsds.sle.utl.si.ApplicationIdentifierEnum;
import eu.dariolucia.ccsds.sle.utl.si.InitiatorRoleEnum;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.Objects;

/**
 * This abstract class encapsulates the service management parameters that are common to all service instances.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class ServiceInstanceConfiguration {

    public static final String SERVICE_INSTANCE_ID_KEY = "service-instance-id";
    public static final String INITIATOR_KEY = "initiator";
    public static final String INITIATOR_ID_KEY = "initiator-id";
    public static final String RESPONDER_ID_KEY = "responder-id";
    public static final String RESPONDER_PORT_ID_KEY = "responder-port-id";
    public static final String RETURN_TIMEOUT_PERIOD_KEY = "return-timeout-period";
    public static final String REPORTING_CYCLE_KEY = "reporting-cycle";
    public static final String SERVICE_VERSION_NUMBER_KEY = "service-version-number";

    public static final String MIN_REPORTING_CYCLE_KEY = "minimum-reporting-cycle";
    public static final String START_TIME_KEY = "start-time";
    public static final String END_TIME_KEY = "end-time";

    /**
     * The service instance identifier, in the ASCII format defined by CCSDS.
     */
    @XmlElement(name = SERVICE_INSTANCE_ID_KEY, required = true)
    private String serviceInstanceIdentifier;
    /**
     * The SLE version number to be used when sending the BIND operation.
     * <p>
     * This parameter is a hint.
     */
    @XmlElement(name = SERVICE_VERSION_NUMBER_KEY)
    private int serviceVersionNumber;
    /**
     * The role of the initiator: can be USER, PPROVIDER or USER_OR_PROVIDER.
     */
    @XmlElement(name = INITIATOR_KEY, required = true)
    private InitiatorRoleEnum initiator;
    /**
     * The ID of the initiator: if the initiator is the user, this ID must be equal to the LOCAL_ID defined in the
     * {@link PeerConfiguration} section. If the initiator is the provider, this ID must be one of those defined as
     * {@link eu.dariolucia.ccsds.sle.utl.config.network.RemotePeer} in the {@link PeerConfiguration} section.
     */
    @XmlElement(name = INITIATOR_ID_KEY, required = true)
    private String initiatorIdentifier;
    /**
     * The ID of the responder: if the responder is the user, this ID must be equal to the LOCAL_ID defined in the
     * {@link PeerConfiguration} section. If the responder is the provider, this ID must be one of those defined as
     * {@link eu.dariolucia.ccsds.sle.utl.config.network.RemotePeer} in the {@link PeerConfiguration} section.
     */
    @XmlElement(name = RESPONDER_ID_KEY, required = true)
    private String responderIdentifier;
    /**
     * Logical port used to access the SLE servicen instance.
     */
    @XmlElement(name = RESPONDER_PORT_ID_KEY, required = true)
    private String responderPortIdentifier;
    /**
     * The maximum time period (in seconds) permitted from when a confirmed SLE operation is invoked until the return is
     * received by the invoker.
     */
    @XmlElement(name = RETURN_TIMEOUT_PERIOD_KEY, required = true)
    private int returnTimeoutPeriod;
    /**
     * Reporting cycle for schedule status report.
     * <p>
     * This parameter is a hint.
     */
    @XmlElement(name = REPORTING_CYCLE_KEY)
    private int reportingCycle;

    public ServiceInstanceConfiguration() {
    }

    public int getReportingCycle() {
        return reportingCycle;
    }

    public InitiatorRoleEnum getInitiator() {
        return initiator;
    }

    public String getServiceInstanceIdentifier() {
        return serviceInstanceIdentifier;
    }

    public String getInitiatorIdentifier() {
        return initiatorIdentifier;
    }

    public String getResponderIdentifier() {
        return responderIdentifier;
    }

    public String getResponderPortIdentifier() {
        return responderPortIdentifier;
    }

    public int getReturnTimeoutPeriod() {
        return returnTimeoutPeriod;
    }

    public int getServiceVersionNumber() {
        return serviceVersionNumber;
    }

    public void setInitiator(InitiatorRoleEnum initiator) {
        this.initiator = initiator;
    }

    public void setServiceInstanceIdentifier(String serviceInstanceIdentifier) {
        this.serviceInstanceIdentifier = serviceInstanceIdentifier;
    }

    public void setInitiatorIdentifier(String initiatorIdentifier) {
        this.initiatorIdentifier = initiatorIdentifier;
    }

    public void setResponderIdentifier(String responderIdentifier) {
        this.responderIdentifier = responderIdentifier;
    }

    public void setResponderPortIdentifier(String responderPortIdentifier) {
        this.responderPortIdentifier = responderPortIdentifier;
    }

    public void setReportingCycle(int reportingCycle) {
        this.reportingCycle = reportingCycle;
    }

    public void setReturnTimeoutPeriod(int returnTimeoutPeriod) {
        this.returnTimeoutPeriod = returnTimeoutPeriod;
    }

    public void setServiceVersionNumber(int serviceVersionNumber) {
        this.serviceVersionNumber = serviceVersionNumber;
    }

    public abstract ApplicationIdentifierEnum getType();

    @Override
    public String toString() {
        return "(" + getType().name() + ") " + getServiceInstanceIdentifier();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceInstanceConfiguration that = (ServiceInstanceConfiguration) o;
        return serviceVersionNumber == that.serviceVersionNumber &&
                returnTimeoutPeriod == that.returnTimeoutPeriod &&
                reportingCycle == that.reportingCycle &&
                Objects.equals(serviceInstanceIdentifier, that.serviceInstanceIdentifier) &&
                initiator == that.initiator &&
                Objects.equals(initiatorIdentifier, that.initiatorIdentifier) &&
                Objects.equals(responderIdentifier, that.responderIdentifier) &&
                Objects.equals(responderPortIdentifier, that.responderPortIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceInstanceIdentifier, serviceVersionNumber, initiator, initiatorIdentifier, responderIdentifier, responderPortIdentifier, returnTimeoutPeriod, reportingCycle);
    }
}
