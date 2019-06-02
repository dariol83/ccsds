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

package eu.dariolucia.ccsds.sle.utl.config.rcf;

import java.util.Date;
import java.util.List;
import java.util.Map;

import eu.dariolucia.ccsds.sle.utl.config.ServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.si.ApplicationIdentifierEnum;
import eu.dariolucia.ccsds.sle.utl.si.DeliveryModeEnum;
import eu.dariolucia.ccsds.sle.utl.si.GVCID;

import javax.xml.bind.DatatypeConverter;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

/**
 * RCF Service Instance configuration specification.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class RcfServiceInstanceConfiguration extends ServiceInstanceConfiguration {

	public static final String DELIVERY_MODE_KEY = "delivery-mode";
	public static final String TRANSFER_BUFFER_SIZE_KEY = "transfer-buffer-size";
	public static final String LATENCY_LIMIT_KEY = "latency-limit";
	public static final String REQUESTED_VCID_KEY = "requested-global-VCID";
	public static final String PERMITTED_VCIDS_KEY = "permitted-global-VCID-set";

	@XmlElement(name = DELIVERY_MODE_KEY)
	private DeliveryModeEnum deliveryMode;
	@XmlElement(name = LATENCY_LIMIT_KEY)
	private Integer latencyLimit; // NULL if offline, otherwise a value
	@XmlElement(name = TRANSFER_BUFFER_SIZE_KEY)
	private int transferBufferSize;
	@XmlElement(name = MIN_REPORTING_CYCLE_KEY)
	private Integer minReportingCycle;

	@XmlElementWrapper(name = PERMITTED_VCIDS_KEY)
	@XmlElement(name = "gvcid")
	private List<GVCID> permittedGvcid;

	@XmlElement(name = REQUESTED_VCID_KEY)
	private GVCID requestedGvcid;
	@XmlElement(name = START_TIME_KEY)
	private Date startTime;
	@XmlElement(name = END_TIME_KEY)
	private Date endTime;

	public RcfServiceInstanceConfiguration() {
	}

	// TODO move away
	public RcfServiceInstanceConfiguration(Map<String, String> configuration) {
		super(configuration);

		this.minReportingCycle = configuration.containsKey(MIN_REPORTING_CYCLE_KEY)
				? Integer.parseInt(configuration.get(MIN_REPORTING_CYCLE_KEY))
				: null;
		this.deliveryMode = parseDeliveryMode(configuration.get(DELIVERY_MODE_KEY));
		this.transferBufferSize = Integer.parseInt(configuration.get(TRANSFER_BUFFER_SIZE_KEY));
		this.latencyLimit = configuration.containsKey(LATENCY_LIMIT_KEY)
				? Integer.parseInt(configuration.get(LATENCY_LIMIT_KEY))
				: null;
		this.permittedGvcid = GVCID.parsePermittedGvcid(configuration.get(PERMITTED_VCIDS_KEY));
		String startTimeString = configuration.get(START_TIME_KEY);
		this.startTime = startTimeString == null ? null : DatatypeConverter.parseDateTime(startTimeString).getTime();
		String endTimeString = configuration.get(END_TIME_KEY);
		this.endTime = endTimeString == null ? null : DatatypeConverter.parseDateTime(endTimeString).getTime();
		this.requestedGvcid = GVCID.parseGvcid(configuration.get(REQUESTED_VCID_KEY));
	}

	public GVCID getRequestedGvcid() {
		return requestedGvcid;
	}

	public Date getStartTime() {
		return startTime;
	}

	public Date getEndTime() {
		return endTime;
	}

	public Integer getLatencyLimit() {
		return latencyLimit;
	}

	public List<GVCID> getPermittedGvcid() {
		return permittedGvcid;
	}

	public Integer getMinReportingCycle() {
		return minReportingCycle;
	}

	public int getTransferBufferSize() {
		return transferBufferSize;
	}

	public DeliveryModeEnum getDeliveryMode() {
		return deliveryMode;
	}

	public void setLatencyLimit(Integer latencyLimit) {
		this.latencyLimit = latencyLimit;
	}

	public void setPermittedGvcid(List<GVCID> permittedGvcid) {
		this.permittedGvcid = permittedGvcid;
	}

	public void setMinReportingCycle(Integer minReportingCycle) {
		this.minReportingCycle = minReportingCycle;
	}

	public void setTransferBufferSize(int transferBufferSize) {
		this.transferBufferSize = transferBufferSize;
	}

	public void setDeliveryMode(DeliveryModeEnum deliveryMode) {
		this.deliveryMode = deliveryMode;
	}

	public void setRequestedGvcid(GVCID requestedGvcid) {
		this.requestedGvcid = requestedGvcid;
	}

	public void setStartTime(Date startTime) {
		this.startTime = startTime;
	}

	public void setEndTime(Date endTime) {
		this.endTime = endTime;
	}

	@Override
	public ApplicationIdentifierEnum getType() {
		return ApplicationIdentifierEnum.RCF;
	}

}
