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

import eu.dariolucia.ccsds.sle.utl.config.ServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.si.ApplicationIdentifierEnum;
import eu.dariolucia.ccsds.sle.utl.si.DeliveryModeEnum;
import eu.dariolucia.ccsds.sle.utl.si.GVCID;
import eu.dariolucia.ccsds.sle.utl.si.rocf.RocfControlWordTypeEnum;
import eu.dariolucia.ccsds.sle.utl.si.rocf.RocfUpdateModeEnum;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import java.util.Date;
import java.util.List;
import java.util.Objects;

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

    /**
     * The delivery mode for this instance of ROCF service, which is set by service management: its value
     * shall be "timely online delivery mode", "complete online delivery mode", or "offline delivery mode".
     * By default is not set.
     */
    @XmlElement(name = DELIVERY_MODE_KEY)
    private DeliveryModeEnum deliveryMode;
    /**
     * The maximum allowable delivery latency time (in seconds) for the online delivery mode, i.e. the maximum delay
     * from when the frame is acquired by the provider until the ROCF TRANSFER-DATA extracted from it is delivered to
     * the user: the value of this parameter shall be null if the delivery mode is offline. By default is not set.
     */
    @XmlElement(name = LATENCY_LIMIT_KEY)
    private Integer latencyLimit; // NULL if offline, otherwise a value
    /**
     * The size of the transfer buffer: the value of this parameter shall indicate the number of ROCF-TRANSFER-DATA and
     * ROCF-SYNC-NOTIFY invocations that can be stored in the transfer buffer. By default is set to 0.
     */
    @XmlElement(name = TRANSFER_BUFFER_SIZE_KEY)
    private int transferBufferSize;
    /**
     * The minimum setting (in seconds) of the reporting cycle for status reports that the ROCF service user may request
     * in an ROCF-SCHEDULE-STATUS-REPORT invocation. By default is not set.
     */
    @XmlElement(name = MIN_REPORTING_CYCLE_KEY)
    private Integer minReportingCycle;
    /**
     * The MCID and/or the set of global VCIDs permitted for this ROCF service instance. By default is not set.
     */
    @XmlElementWrapper(name = PERMITTED_VCIDS_KEY)
    @XmlElement(name = "gvcid")
    private List<GVCID> permittedGvcid;
    /**
     * The set of tc-vcid values permitted for this ROCF service. By default is not set.
     */
    @XmlElementWrapper(name = PERMITTED_TCVCIDS_KEY)
    @XmlElement(name = "tcvcid")
    private List<Integer> permittedTcVcids;
    /**
     * The set of control-word-type values permitted for this ROCF service instance. By default is not set.
     */
    @XmlElementWrapper(name = PERMITTED_CONTROL_WORD_TYPES_KEY)
    @XmlElement(name = "control-word-type")
    private List<RocfControlWordTypeEnum> permittedControlWordTypes;
    /**
     * The set of update-mode values permitted for this ROCF service instance. By default is not set.
     */
    @XmlElementWrapper(name = PERMITTED_UPDATE_MODES_KEY)
    @XmlElement(name = "update-mode")
    private List<RocfUpdateModeEnum> permittedUpdateModes;
    /**
     * The global VCID requested by the most recent ROCF-START operation. By default is not set.
     * <p>
     * This parameter is a hint.
     */
    @XmlElement(name = REQUESTED_VCID_KEY)
    private GVCID requestedGvcid;
    /**
     * The tc-vcid value requested by the most recent ROCF-START operation. By default is not set.
     * <p>
     * This parameter is a hint.
     */
    @XmlElement(name = REQUESTED_TC_VCID_KEY)
    private Integer requestedTcVcid;
    /**
     * The control word type requested by the most recent ROCF-START operation. By default is set to ALL.
     * <p>
     * This parameter is a hint.
     */
    @XmlElement(name = REQUESTED_CONTROL_WORD_TYPE_KEY)
    private RocfControlWordTypeEnum requestedControlWordType = RocfControlWordTypeEnum.ALL;
    /**
     * The update-mode value requested by the most recent ROCF-START operation.  By default is set to CONTINUOUS.
     * <p>
     * This parameter is a hint.
     */
    @XmlElement(name = REQUESTED_UPDATE_MODE_KEY)
    private RocfUpdateModeEnum requestedUpdateMode = RocfUpdateModeEnum.CONTINUOUS;
    /**
     * Start time. If not set, VOID is used. By default is not set.
     * <p>
     * This parameter is a hint.
     */
    @XmlElement(name = START_TIME_KEY)
    private Date startTime;
    /**
     * End time. If not set, VOID is used. By default is not set.
     * <p>
     * This parameter is a hint.
     */
    @XmlElement(name = END_TIME_KEY)
    private Date endTime;

    public RocfServiceInstanceConfiguration() {
        super();
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        RocfServiceInstanceConfiguration that = (RocfServiceInstanceConfiguration) o;
        return transferBufferSize == that.transferBufferSize &&
                deliveryMode == that.deliveryMode &&
                Objects.equals(latencyLimit, that.latencyLimit) &&
                Objects.equals(minReportingCycle, that.minReportingCycle) &&
                Objects.equals(permittedGvcid, that.permittedGvcid) &&
                Objects.equals(permittedTcVcids, that.permittedTcVcids) &&
                Objects.equals(permittedControlWordTypes, that.permittedControlWordTypes) &&
                Objects.equals(permittedUpdateModes, that.permittedUpdateModes) &&
                Objects.equals(requestedGvcid, that.requestedGvcid) &&
                Objects.equals(requestedTcVcid, that.requestedTcVcid) &&
                requestedControlWordType == that.requestedControlWordType &&
                requestedUpdateMode == that.requestedUpdateMode &&
                Objects.equals(startTime, that.startTime) &&
                Objects.equals(endTime, that.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), deliveryMode, latencyLimit, transferBufferSize, minReportingCycle, permittedGvcid, permittedTcVcids, permittedControlWordTypes, permittedUpdateModes, requestedGvcid, requestedTcVcid, requestedControlWordType, requestedUpdateMode, startTime, endTime);
    }
}
