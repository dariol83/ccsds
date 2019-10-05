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

import eu.dariolucia.ccsds.sle.utl.config.ServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.si.ApplicationIdentifierEnum;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuProtocolAbortModeEnum;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.util.Date;
import java.util.Objects;

/**
 * CLTU Service Instance configuration specification.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class CltuServiceInstanceConfiguration extends ServiceInstanceConfiguration {

    public static final String MAXIMUM_CLTU_LENGTH_KEY = "maximum-cltu-length";
    public static final String MINIMUM_CLTU_DELAY_KEY = "minimum-cltu-delay";
    public static final String BIT_LOCK_REQUIRED_KEY = "bit-lock-required";
    public static final String RF_AVAILABLE_REQUIRED_KEY = "rf-available-required";
    public static final String PROTOCOL_ABORT_MODE_KEY = "protocol-abort-mode";
    public static final String EXPECTED_CLTU_IDENTIFICATION_KEY = "expected-cltu-identification";

    /**
     * The size, in octets, of the maximum-length CLTU that will be accepted by the provider for this service
     * instance. By default is not set.
     */
    @XmlElement(name = MAXIMUM_CLTU_LENGTH_KEY)
    private Integer maxCltuLength;
    /**
     * The minimum guard time the F-CLTU provider will accept between two consecutive CLTUs. By default set to 0.
     * By default is not set.
     */
    @XmlElement(name = MINIMUM_CLTU_DELAY_KEY)
    private Integer minCltuDelay;
    /**
     * If the value is true, the no-bit-lock flag in the CLCW must be false in order for the provider to set
     * production-status to operational. By default set to false.
     */
    @XmlElement(name = BIT_LOCK_REQUIRED_KEY)
    private boolean bitlockRequired;
    /**
     * If the value is true, the no-RF-available flag in the CLCW must be false in order for the provider to set
     * production status to operational. By default set to false.
     */
    @XmlElement(name = RF_AVAILABLE_REQUIRED_KEY)
    private boolean rfAvailableRequired;
    /**
     * The protocol-abort-mode may be set to clear or continue. If it is abort, service production shall cease in
     * the event of a protocol abort. If it is continue, service production shall disregard this event and continue
     * radiating the CLTUs already buffered at that time. By default set to ABORT.
     */
    @XmlElement(name = PROTOCOL_ABORT_MODE_KEY)
    private CltuProtocolAbortModeEnum protocolAbortMode = CltuProtocolAbortModeEnum.ABORT_MODE;
    /**
     * The minimum setting (in seconds) of the reporting cycle for status reports that the F-CLTU service user may
     * request in an CLTU-SCHEDULE-STATUS-REPORT invocation. By default is not set.
     */
    @XmlElement(name = MIN_REPORTING_CYCLE_KEY)
    private Integer minReportingCycle;
    /**
     * The expected value of the cltu-identification parameter to be received in the next CLTU-TRANSFERDATA
     * invocation. If no CLTU-START has been received, zero shall be returned as the default value of this parameter.
     * By default is set to 0.
     */
    @XmlElement(name = EXPECTED_CLTU_IDENTIFICATION_KEY)
    private int expectedCltuIdentification;
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

    public CltuServiceInstanceConfiguration() {
        super();
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

    public CltuProtocolAbortModeEnum getProtocolAbortMode() {
        return protocolAbortMode;
    }

    public void setProtocolAbortMode(CltuProtocolAbortModeEnum protocolAbortMode) {
        this.protocolAbortMode = protocolAbortMode;
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
                protocolAbortMode == that.protocolAbortMode &&
                expectedCltuIdentification == that.expectedCltuIdentification &&
                Objects.equals(maxCltuLength, that.maxCltuLength) &&
                Objects.equals(minCltuDelay, that.minCltuDelay) &&
                Objects.equals(minReportingCycle, that.minReportingCycle) &&
                Objects.equals(startTime, that.startTime) &&
                Objects.equals(endTime, that.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), maxCltuLength, minCltuDelay, bitlockRequired, rfAvailableRequired, protocolAbortMode, minReportingCycle, expectedCltuIdentification, startTime, endTime);
    }
}
