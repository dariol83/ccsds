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
import eu.dariolucia.ccsds.sle.utl.si.GVCID;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuNotificationModeEnum;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuPlopInEffectEnum;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuProtocolAbortModeEnum;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
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

    public static final String ACQUISITION_SEQUENCE_LENGTH_KEY = "acquisition-sequence-length";
    public static final String CLCW_PHYSICAL_CHANNEL_KEY = "clcw-physical-channel";
    public static final String CLCW_GLOBAL_VCID_KEY = "clcw-global-vcid";
    public static final String MODULATION_FREQUENCY_KEY = "modulation-frequency";
    public static final String MODULATION_INDEX_KEY = "modulation-index";
    public static final String NOTIFICATION_MODE_KEY = "notification-mode";
    public static final String PLOP_1_IDLE_SEQUENCE_LENGTH_KEY = "plop-1-idle-sequence-length";
    public static final String PLOP_IN_EFFECT_KEY = "plop-in-effect";
    public static final String SUBCARRIER_TO_BIT_RATE_RATIO_KEY = "subcarrier-to-bit-rate-ratio";

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
     * The size, in octets, of the bit pattern to be radiated to enable the spacecraft telecommand system to achieve
     * bit lock.
     */
    @XmlElement(name = ACQUISITION_SEQUENCE_LENGTH_KEY)
    private Integer acquisitionSequenceLength;
    /**
     * The RF return channel that carries the CLCW to be used by the F-CLTU provider to determine the forward link RF
     * and bit lock status. If the configuration of the given service instance is such that the CLCW shall not be
     * evaluated then the parameter value reported is 'not configured'.
     */
    @XmlElement(name = CLCW_PHYSICAL_CHANNEL_KEY)
    private String clcwPhysicalChannel;
    /**
     * The Master or Virtual Channel that carries the CLCW to be used by the F-CLTU provider to determine the forward
     * link RF and/or bit lock status. If the configuration of the given service instance is such that the CLCW shall not be
     * evaluated then the parameter value reported is 'not configured'.
     */
    @XmlElement(name = CLCW_GLOBAL_VCID_KEY)
    private GVCID clcwGlobalVcid;
    /**
     * The subcarrier frequency (when applicable) or the frequency of direct on-carrier data modulation, expressed in
     * tenths of Hertz.
     */
    @XmlElement(name = MODULATION_FREQUENCY_KEY)
    private Long modulationFrequency;
    /**
     * The angle by which the RF carrier is phase shifted with respect to the un-modulated RF carrier, expressed in
     * milliradians (10 pow -3 rad).
     */
    @XmlElement(name = MODULATION_INDEX_KEY)
    private Integer modulationIndex;
    /**
     * The notification-mode may be 'immediate' or 'deferred'. If 'immediate', the user is notified of a production-status
     * change to 'interrupted' by means of a CLTU-ASYNC-NOTIFY invocation as soon as this transition is detected. If 'deferred',
     * the user is notified about the production-status change by means of a CLTU-ASYNC-NOTIFY invocation only if and
     * when the radiation of a CLTU is affected.
     */
    @XmlElement(name = NOTIFICATION_MODE_KEY)
    private CltuNotificationModeEnum notificationMode;
    /**
     * The size, in octets, of the optional idle sequence that shall be used in conjunction with PLOP-1. If 0, no idle
     * sequence is applied.
     */
    @XmlElement(name = PLOP_1_IDLE_SEQUENCE_LENGTH_KEY)
    private Integer plop1idleSequenceLength;
    /**
     * The physical layer operation procedure (PLOP) being used: 'PLOP-1' or 'PLOP-2'.
     */
    @XmlElement(name = PLOP_IN_EFFECT_KEY)
    private CltuPlopInEffectEnum plopInEffect;
    /**
     * When subcarrier modulation is used, the value represents the ratio of the subcarrier frequency to the uplink data
     * rate (i.e., the bit rate). A value of one indicates that data will be directly modulated onto the carrier.
     */
    @XmlElement(name = SUBCARRIER_TO_BIT_RATE_RATIO_KEY)
    private Integer subcarrierToBitrateRatio;
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

    public Integer getAcquisitionSequenceLength() {
        return acquisitionSequenceLength;
    }

    public void setAcquisitionSequenceLength(Integer acquisitionSequenceLength) {
        this.acquisitionSequenceLength = acquisitionSequenceLength;
    }

    public String getClcwPhysicalChannel() {
        return clcwPhysicalChannel;
    }

    public void setClcwPhysicalChannel(String clcwPhysicalChannel) {
        this.clcwPhysicalChannel = clcwPhysicalChannel;
    }

    public GVCID getClcwGlobalVcid() {
        return clcwGlobalVcid;
    }

    public void setClcwGlobalVcid(GVCID clcwGlobalVcid) {
        this.clcwGlobalVcid = clcwGlobalVcid;
    }

    public Long getModulationFrequency() {
        return modulationFrequency;
    }

    public void setModulationFrequency(Long modulationFrequency) {
        this.modulationFrequency = modulationFrequency;
    }

    public Integer getModulationIndex() {
        return modulationIndex;
    }

    public void setModulationIndex(Integer modulationIndex) {
        this.modulationIndex = modulationIndex;
    }

    public CltuNotificationModeEnum getNotificationMode() {
        return notificationMode;
    }

    public void setNotificationMode(CltuNotificationModeEnum notificationMode) {
        this.notificationMode = notificationMode;
    }

    public Integer getPlop1idleSequenceLength() {
        return plop1idleSequenceLength;
    }

    public void setPlop1idleSequenceLength(Integer plop1idleSequenceLength) {
        this.plop1idleSequenceLength = plop1idleSequenceLength;
    }

    public CltuPlopInEffectEnum getPlopInEffect() {
        return plopInEffect;
    }

    public void setPlopInEffect(CltuPlopInEffectEnum plopInEffect) {
        this.plopInEffect = plopInEffect;
    }

    public Integer getSubcarrierToBitrateRatio() {
        return subcarrierToBitrateRatio;
    }

    public void setSubcarrierToBitrateRatio(Integer subcarrierToBitrateRatio) {
        this.subcarrierToBitrateRatio = subcarrierToBitrateRatio;
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
        return isBitlockRequired() == that.isBitlockRequired() &&
                isRfAvailableRequired() == that.isRfAvailableRequired() &&
                Objects.equals(getMaxCltuLength(), that.getMaxCltuLength()) &&
                Objects.equals(getMinCltuDelay(), that.getMinCltuDelay()) &&
                getProtocolAbortMode() == that.getProtocolAbortMode() &&
                Objects.equals(getMinReportingCycle(), that.getMinReportingCycle()) &&
                Objects.equals(getAcquisitionSequenceLength(), that.getAcquisitionSequenceLength()) &&
                Objects.equals(getClcwPhysicalChannel(), that.getClcwPhysicalChannel()) &&
                Objects.equals(getClcwGlobalVcid(), that.getClcwGlobalVcid()) &&
                Objects.equals(getModulationFrequency(), that.getModulationFrequency()) &&
                Objects.equals(getModulationIndex(), that.getModulationIndex()) &&
                getNotificationMode() == that.getNotificationMode() &&
                Objects.equals(getPlop1idleSequenceLength(), that.getPlop1idleSequenceLength()) &&
                getPlopInEffect() == that.getPlopInEffect() &&
                Objects.equals(getSubcarrierToBitrateRatio(), that.getSubcarrierToBitrateRatio()) &&
                Objects.equals(getStartTime(), that.getStartTime()) &&
                Objects.equals(getEndTime(), that.getEndTime());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getMaxCltuLength(), getMinCltuDelay(), isBitlockRequired(), isRfAvailableRequired(), getProtocolAbortMode(), getMinReportingCycle(), getAcquisitionSequenceLength(), getClcwPhysicalChannel(), getClcwGlobalVcid(), getModulationFrequency(), getModulationIndex(), getNotificationMode(), getPlop1idleSequenceLength(), getPlopInEffect(), getSubcarrierToBitrateRatio(), getStartTime(), getEndTime());
    }
}
