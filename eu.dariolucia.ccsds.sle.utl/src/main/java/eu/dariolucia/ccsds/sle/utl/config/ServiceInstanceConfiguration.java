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
import eu.dariolucia.ccsds.sle.utl.si.DeliveryModeEnum;
import eu.dariolucia.ccsds.sle.utl.si.InitiatorRoleEnum;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

/**
 * This abstract class encapsulates the service management parameters that are common to all service instances.
 * There are two ways to initialise this class programmatically:
 * - using the setters;
 * - using the constructor that takes as argument a map of properties.
 *
 * If the map is used, the key is the name of the service management property as defined by the CCSDS Blue Books, while
 * the key is the assigned value, as string. For the initiator role, the string value must have one of the possible
 * value of the enumeration {@link InitiatorRoleEnum}.
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

	@XmlElement(name = SERVICE_INSTANCE_ID_KEY, required = true)
	private String serviceInstanceIdentifier;
    @XmlElement(name = SERVICE_VERSION_NUMBER_KEY)
    private int serviceVersionNumber;
	@XmlElement(name = INITIATOR_KEY, required = true)
	private InitiatorRoleEnum initiator;
    @XmlElement(name = INITIATOR_ID_KEY, required = true)
	private String initiatorIdentifier;
    @XmlElement(name = RESPONDER_ID_KEY, required = true)
	private String responderIdentifier;
    @XmlElement(name = RESPONDER_PORT_ID_KEY, required = true)
	private String responderPortIdentifier;
    @XmlElement(name = RETURN_TIMEOUT_PERIOD_KEY, required = true)
    private int returnTimeoutPeriod;
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
	
	protected final DeliveryModeEnum parseDeliveryMode(String s) {
		return DeliveryModeEnum.valueOf(s);
	}
	
	@Override
	public String toString() {
		return "(" + getType().name() + ") " +  getServiceInstanceIdentifier();
	}
}
