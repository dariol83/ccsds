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

import eu.dariolucia.ccsds.sle.utl.config.ServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.si.ApplicationIdentifierEnum;
import eu.dariolucia.ccsds.sle.utl.si.DeliveryModeEnum;
import eu.dariolucia.ccsds.sle.utl.si.GVCID;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import java.util.Date;
import java.util.List;
import java.util.Objects;

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

    /**
     * The delivery mode for this instance of RCF service, which is set by service management: its value
     * shall be "timely online delivery mode", "complete online delivery mode", or "offline delivery mode".
     * By default is not set.
     */
    @XmlElement(name = DELIVERY_MODE_KEY)
    private DeliveryModeEnum deliveryMode;
    /**
     * The maximum allowable delivery latency time (in seconds) for the online delivery mode, i.e. the maximum delay
     * from when the frame is acquired by the provider until the RCF TRANSFER-DATA extracted from it is delivered to
     * the user: the value of this parameter shall be null if the delivery mode is offline. By default is not set.
     */
    @XmlElement(name = LATENCY_LIMIT_KEY)
    private Integer latencyLimit; // NULL if offline, otherwise a value
    /**
     * The size of the transfer buffer: the value of this parameter shall indicate the number of RCF-TRANSFER-DATA and
     * RCF-SYNC-NOTIFY invocations that can be stored in the transfer buffer. By default is set to 0.
     */
    @XmlElement(name = TRANSFER_BUFFER_SIZE_KEY)
    private int transferBufferSize;
    /**
     * The minimum setting (in seconds) of the reporting cycle for status reports that the RCF service user may request
     * in an RCF-SCHEDULE-STATUS-REPORT invocation. By default is not set.
     */
    @XmlElement(name = MIN_REPORTING_CYCLE_KEY)
    private Integer minReportingCycle;
    /**
     * The MCID and/or the set of global VCIDs permitted for this RCF service instance. By default is not set.
     */
    @XmlElementWrapper(name = PERMITTED_VCIDS_KEY)
    @XmlElement(name = "gvcid")
    private List<GVCID> permittedGvcid;
    /**
     * If the provider is in state 3 (‘active’), the GVCID set by the RCF-START operation, used to determine which
     * frames are selected for delivery. By default is not set.
     * <p>
     * This parameter is a hint.
     */
    @XmlElement(name = REQUESTED_VCID_KEY)
    private GVCID requestedGvcid;
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

    public RcfServiceInstanceConfiguration() {
        super();
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        RcfServiceInstanceConfiguration that = (RcfServiceInstanceConfiguration) o;
        return transferBufferSize == that.transferBufferSize &&
                deliveryMode == that.deliveryMode &&
                Objects.equals(latencyLimit, that.latencyLimit) &&
                Objects.equals(minReportingCycle, that.minReportingCycle) &&
                Objects.equals(permittedGvcid, that.permittedGvcid) &&
                Objects.equals(requestedGvcid, that.requestedGvcid) &&
                Objects.equals(startTime, that.startTime) &&
                Objects.equals(endTime, that.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), deliveryMode, latencyLimit, transferBufferSize, minReportingCycle, permittedGvcid, requestedGvcid, startTime, endTime);
    }
}
