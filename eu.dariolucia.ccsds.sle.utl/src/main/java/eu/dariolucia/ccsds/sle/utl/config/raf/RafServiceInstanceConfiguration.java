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

package eu.dariolucia.ccsds.sle.utl.config.raf;

import java.util.*;

import eu.dariolucia.ccsds.sle.utl.config.ServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.si.ApplicationIdentifierEnum;
import eu.dariolucia.ccsds.sle.utl.si.DeliveryModeEnum;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafRequestedFrameQualityEnum;

import javax.xml.bind.DatatypeConverter;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

/**
 * RAF Service Instance configuration specification.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class RafServiceInstanceConfiguration extends ServiceInstanceConfiguration {

    public static final String DELIVERY_MODE_KEY = "delivery-mode";
    public static final String TRANSFER_BUFFER_SIZE_KEY = "transfer-buffer-size";
    public static final String LATENCY_LIMIT_KEY = "latency-limit";
    public static final String PERMITTED_FRAME_QUALITY_KEY = "permitted-frame-quality-set";
    public static final String REQUESTED_FRAME_QUALITY_KEY = "requested-frame-quality";

    @XmlElement(name = DELIVERY_MODE_KEY)
    private DeliveryModeEnum deliveryMode;
    @XmlElement(name = LATENCY_LIMIT_KEY)
    private Integer latencyLimit; // NULL if offline, otherwise a value
    @XmlElement(name = TRANSFER_BUFFER_SIZE_KEY)
    private int transferBufferSize;
    @XmlElement(name = MIN_REPORTING_CYCLE_KEY)
    private Integer minReportingCycle;

    @XmlElementWrapper(name = PERMITTED_FRAME_QUALITY_KEY)
    @XmlElement(name = "quality")
    private List<RafRequestedFrameQualityEnum> permittedFrameQuality;

    @XmlElement(name = REQUESTED_FRAME_QUALITY_KEY)
    private RafRequestedFrameQualityEnum requestedFrameQuality;
    @XmlElement(name = START_TIME_KEY)
    private Date startTime;
    @XmlElement(name = END_TIME_KEY)
    private Date endTime;

    public RafServiceInstanceConfiguration() {
    }

    private List<RafRequestedFrameQualityEnum> parsePermittedFrameQuality(String string) {
        List<RafRequestedFrameQualityEnum> theList = new LinkedList<>();
        fillFrameQuality(theList, string.trim());
        return theList;
    }

    private void fillFrameQuality(List<RafRequestedFrameQualityEnum> theList, String trim) {
        if (trim.isEmpty()) {
            return;
        }
        String[] spl = trim.split("\\.", -1);
        for (String ss : spl) {
            RafRequestedFrameQualityEnum q = RafRequestedFrameQualityEnum.fromConfigurationString(ss);
            if (q == null) {
                throw new IllegalArgumentException(trim + " is not a valid frame quality string: block '" + trim + "' is invalid");
            }
            theList.add(q);
        }
    }

    public Integer getLatencyLimit() {
        return latencyLimit;
    }

    public List<RafRequestedFrameQualityEnum> getPermittedFrameQuality() {
        return permittedFrameQuality;
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

    public RafRequestedFrameQualityEnum getRequestedFrameQuality() {
        return requestedFrameQuality;
    }

    public Date getStartTime() {
        return startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setLatencyLimit(Integer latencyLimit) {
        this.latencyLimit = latencyLimit;
    }

    public void setPermittedFrameQuality(List<RafRequestedFrameQualityEnum> permittedFrameQuality) {
        this.permittedFrameQuality = permittedFrameQuality;
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

    public void setRequestedFrameQuality(RafRequestedFrameQualityEnum requestedFrameQuality) {
        this.requestedFrameQuality = requestedFrameQuality;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    @Override
    public ApplicationIdentifierEnum getType() {
        return ApplicationIdentifierEnum.RAF;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        RafServiceInstanceConfiguration that = (RafServiceInstanceConfiguration) o;
        return transferBufferSize == that.transferBufferSize &&
                deliveryMode == that.deliveryMode &&
                Objects.equals(latencyLimit, that.latencyLimit) &&
                Objects.equals(minReportingCycle, that.minReportingCycle) &&
                Objects.equals(permittedFrameQuality, that.permittedFrameQuality) &&
                requestedFrameQuality == that.requestedFrameQuality &&
                Objects.equals(startTime, that.startTime) &&
                Objects.equals(endTime, that.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), deliveryMode, latencyLimit, transferBufferSize, minReportingCycle, permittedFrameQuality, requestedFrameQuality, startTime, endTime);
    }
}
