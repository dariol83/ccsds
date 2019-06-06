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

package eu.dariolucia.ccsds.sle.utl.config.cltu;

import java.util.Date;
import java.util.Map;
import java.util.Objects;

import eu.dariolucia.ccsds.sle.utl.config.ServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.si.ApplicationIdentifierEnum;

import javax.xml.bind.DatatypeConverter;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

/**
 * CLTU Service Instance configuration specification.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class CltuServiceInstanceConfiguration extends ServiceInstanceConfiguration {

	public static final String MAXIMUM_CLTU_LENGTH_KEY = "maximum-cltu-length";
	public static final String MINIMUM_CLTU_DELAY_KEY = "minimum-cltu-delay";
	public static final String BIT_LOCK_REQUIRED_KEY = "bit-lock-required";
	public static final String RF_AVAILABLE_REQUIRED_KEY = "rf-available-required";
	public static final String PROTOCOL_ABORT_CLEAR_ENABLED_KEY = "protocol-abort-clear-enabled";
	public static final String EXPECTED_CLTU_IDENTIFICATION_KEY = "expected-cltu-identification";


	@XmlElement(name = MAXIMUM_CLTU_LENGTH_KEY)
	private Integer maxCltuLength;
	@XmlElement(name = MINIMUM_CLTU_DELAY_KEY)
	private Integer minCltuDelay;
	@XmlElement(name = BIT_LOCK_REQUIRED_KEY)
	private boolean bitlockRequired;
	@XmlElement(name = RF_AVAILABLE_REQUIRED_KEY)
	private boolean rfAvailableRequired;
	@XmlElement(name = PROTOCOL_ABORT_CLEAR_ENABLED_KEY)
	private boolean protocolAbortClearEnabled;
	@XmlElement(name = MIN_REPORTING_CYCLE_KEY)
	private Integer minReportingCycle;

	@XmlElement(name = EXPECTED_CLTU_IDENTIFICATION_KEY)
	private int expectedCltuIdentification;
	@XmlElement(name = START_TIME_KEY)
	private Date startTime;
	@XmlElement(name = END_TIME_KEY)
	private Date endTime;

	public CltuServiceInstanceConfiguration() {
	}

	public Integer getMinReportingCycle() {
		return minReportingCycle;
	}

	public void setMinReportingCycle(Integer minReportingCycle) {
		this.minReportingCycle = minReportingCycle;
	}

	public Integer getMaxCltuLength() {
		return maxCltuLength;
	}

	public void setMaxCltuLength(Integer maxCltuLength) {
		this.maxCltuLength = maxCltuLength;
	}

	public Integer getMinCltuDelay() {
		return minCltuDelay;
	}

	public void setMinCltuDelay(Integer minCltuDelay) {
		this.minCltuDelay = minCltuDelay;
	}

	public boolean isBitlockRequired() {
		return bitlockRequired;
	}

	public void setBitlockRequired(boolean bitlockRequired) {
		this.bitlockRequired = bitlockRequired;
	}

	public boolean isRfAvailableRequired() {
		return rfAvailableRequired;
	}

	public void setRfAvailableRequired(boolean rfAvailableRequired) {
		this.rfAvailableRequired = rfAvailableRequired;
	}

	public boolean isProtocolAbortClearEnabled() {
		return protocolAbortClearEnabled;
	}

	public void setProtocolAbortClearEnabled(boolean protocolAbortClearEnabled) {
		this.protocolAbortClearEnabled = protocolAbortClearEnabled;
	}

	public int getExpectedCltuIdentification() {
		return expectedCltuIdentification;
	}

	public void setExpectedCltuIdentification(int expectedCltuIdentification) {
		this.expectedCltuIdentification = expectedCltuIdentification;
	}

	public Date getStartTime() {
		return startTime;
	}

	public void setStartTime(Date startTime) {
		this.startTime = startTime;
	}

	public Date getEndTime() {
		return endTime;
	}

	public void setEndTime(Date endTime) {
		this.endTime = endTime;
	}

	@Override
	public ApplicationIdentifierEnum getType() {
		return ApplicationIdentifierEnum.CLTU;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		if (!super.equals(o)) return false;
		CltuServiceInstanceConfiguration that = (CltuServiceInstanceConfiguration) o;
		return bitlockRequired == that.bitlockRequired &&
				rfAvailableRequired == that.rfAvailableRequired &&
				protocolAbortClearEnabled == that.protocolAbortClearEnabled &&
				expectedCltuIdentification == that.expectedCltuIdentification &&
				Objects.equals(maxCltuLength, that.maxCltuLength) &&
				Objects.equals(minCltuDelay, that.minCltuDelay) &&
				Objects.equals(minReportingCycle, that.minReportingCycle) &&
				Objects.equals(startTime, that.startTime) &&
				Objects.equals(endTime, that.endTime);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), maxCltuLength, minCltuDelay, bitlockRequired, rfAvailableRequired, protocolAbortClearEnabled, minReportingCycle, expectedCltuIdentification, startTime, endTime);
	}
}
