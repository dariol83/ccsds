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

package eu.dariolucia.ccsds.sle.utl.config.rocf;

import java.util.*;
import java.util.stream.Collectors;

import eu.dariolucia.ccsds.sle.utl.config.ServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.si.ApplicationIdentifierEnum;
import eu.dariolucia.ccsds.sle.utl.si.DeliveryModeEnum;
import eu.dariolucia.ccsds.sle.utl.si.GVCID;
import eu.dariolucia.ccsds.sle.utl.si.rocf.RocfControlWordTypeEnum;
import eu.dariolucia.ccsds.sle.utl.si.rocf.RocfUpdateModeEnum;

import javax.xml.bind.DatatypeConverter;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

/**
 * ROCF Service Instance configuration specification.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class RocfServiceInstanceConfiguration extends ServiceInstanceConfiguration {

	public static final String DELIVERY_MODE_KEY = "delivery-mode";
	public static final String TRANSFER_BUFFER_SIZE_KEY = "transfer-buffer-size";
	public static final String LATENCY_LIMIT_KEY = "latency-limit";

	public static final String PERMITTED_VCIDS_KEY = "permitted-global-VCID-set";
	public static final String PERMITTED_TCVCIDS_KEY = "permitted-tc-vcid-set";
	public static final String PERMITTED_CONTROL_WORD_TYPES_KEY = "permitted-control-word-type-set";
	public static final String PERMITTED_UPDATE_MODES_KEY = "permitted-update-mode-set";

	public static final String REQUESTED_VCID_KEY = "requested-global-VCID";
	public static final String REQUESTED_TC_VCID_KEY = "requested-tc-vcid";
	public static final String REQUESTED_CONTROL_WORD_TYPE_KEY = "requested-control-word-type";
	public static final String REQUESTED_UPDATE_MODE_KEY = "requested-update-mode";

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
	@XmlElementWrapper(name = PERMITTED_TCVCIDS_KEY)
	@XmlElement(name = "tcvcid")
	private List<Integer> permittedTcVcids;
	@XmlElementWrapper(name = PERMITTED_CONTROL_WORD_TYPES_KEY)
	@XmlElement(name = "control-word-type")
	private List<RocfControlWordTypeEnum> permittedControlWordTypes;
	@XmlElementWrapper(name = PERMITTED_UPDATE_MODES_KEY)
	@XmlElement(name = "update-mode")
	private List<RocfUpdateModeEnum> permittedUpdateModes;

	@XmlElement(name = REQUESTED_VCID_KEY)
	private GVCID requestedGvcid;
	@XmlElement(name = REQUESTED_TC_VCID_KEY)
	private Integer requestedTcVcid;
	@XmlElement(name = REQUESTED_CONTROL_WORD_TYPE_KEY)
	private RocfControlWordTypeEnum requestedControlWordType;
	@XmlElement(name = REQUESTED_UPDATE_MODE_KEY)
	private RocfUpdateModeEnum requestedUpdateMode;
	@XmlElement(name = START_TIME_KEY)
	private Date startTime;
	@XmlElement(name = END_TIME_KEY)
	private Date endTime;

	public RocfServiceInstanceConfiguration() {
	}

	private List<RocfControlWordTypeEnum> parsePermittedControlWordTypes(String string) {
		if(string != null && !string.isEmpty()) {
			return Arrays.stream(string.split("\\.", -1)).map(RocfControlWordTypeEnum::fromConfigurationString).collect(Collectors.toList());
		} else {
			return Collections.emptyList();
		}
	}
	
	private List<RocfUpdateModeEnum> parsePermittedUpdateModes(String string) {
		if(string != null && !string.isEmpty()) {
			return Arrays.stream(string.split("\\.", -1)).map(o -> RocfUpdateModeEnum.fromConfigurationString(o)).collect(Collectors.toList());
		} else {
			return Collections.emptyList();
		}
	}

	private List<Integer> parsePermittedTcVcid(String string) {
		if(string != null && !string.isEmpty()) {
			return Arrays.stream(string.split("\\.", -1)).map(this::toInteger).filter(o -> o != null).collect(Collectors.toList());
		} else {
			return Collections.emptyList();
		}
	}

	private Integer toInteger(String s) {
		try {
			return Integer.parseInt(s);
		} catch(NumberFormatException e) {
			return null;
		}
	}

	public DeliveryModeEnum getDeliveryMode() {
		return deliveryMode;
	}

	public void setDeliveryMode(DeliveryModeEnum deliveryMode) {
		this.deliveryMode = deliveryMode;
	}

	public Integer getLatencyLimit() {
		return latencyLimit;
	}

	public void setLatencyLimit(Integer latencyLimit) {
		this.latencyLimit = latencyLimit;
	}

	public Integer getMinReportingCycle() {
		return minReportingCycle;
	}

	public void setMinReportingCycle(Integer minReportingCycle) {
		this.minReportingCycle = minReportingCycle;
	}

	public int getTransferBufferSize() {
		return transferBufferSize;
	}

	public void setTransferBufferSize(int transferBufferSize) {
		this.transferBufferSize = transferBufferSize;
	}

	public List<GVCID> getPermittedGvcid() {
		return permittedGvcid;
	}

	public void setPermittedGvcid(List<GVCID> permittedGvcid) {
		this.permittedGvcid = permittedGvcid;
	}

	public List<Integer> getPermittedTcVcids() {
		return permittedTcVcids;
	}

	public void setPermittedTcVcids(List<Integer> permittedTcVcids) {
		this.permittedTcVcids = permittedTcVcids;
	}

	public List<RocfControlWordTypeEnum> getPermittedControlWordTypes() {
		return permittedControlWordTypes;
	}

	public void setPermittedControlWordTypes(List<RocfControlWordTypeEnum> permittedControlWordTypes) {
		this.permittedControlWordTypes = permittedControlWordTypes;
	}

	public List<RocfUpdateModeEnum> getPermittedUpdateModes() {
		return permittedUpdateModes;
	}

	public void setPermittedUpdateModes(List<RocfUpdateModeEnum> permittedUpdateModes) {
		this.permittedUpdateModes = permittedUpdateModes;
	}

	public GVCID getRequestedGvcid() {
		return requestedGvcid;
	}

	public void setRequestedGvcid(GVCID requestedGvcid) {
		this.requestedGvcid = requestedGvcid;
	}

	public Integer getRequestedTcVcid() {
		return requestedTcVcid;
	}

	public void setRequestedTcVcid(Integer requestedTcVcid) {
		this.requestedTcVcid = requestedTcVcid;
	}

	public RocfControlWordTypeEnum getRequestedControlWordType() {
		return requestedControlWordType;
	}

	public void setRequestedControlWordType(RocfControlWordTypeEnum requestedControlWordType) {
		this.requestedControlWordType = requestedControlWordType;
	}

	public RocfUpdateModeEnum getRequestedUpdateMode() {
		return requestedUpdateMode;
	}

	public void setRequestedUpdateMode(RocfUpdateModeEnum requestedUpdateMode) {
		this.requestedUpdateMode = requestedUpdateMode;
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
		return ApplicationIdentifierEnum.ROCF;
	}

}
