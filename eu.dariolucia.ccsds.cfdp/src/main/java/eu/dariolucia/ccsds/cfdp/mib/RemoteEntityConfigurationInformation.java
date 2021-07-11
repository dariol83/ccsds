/*
 *   Copyright (c) 2021 Dario Lucia (https://www.dariolucia.eu)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and 
 *   limitations under the License.
 */

package eu.dariolucia.ccsds.cfdp.mib;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class RemoteEntityConfigurationInformation {

    @XmlAttribute(name = "id")
    private long remoteEntityId;

    @XmlAttribute(name = "version")
    private long protocolVersion = 1;

    @XmlElement(name = "ut-layer")
    private String utLayer;

    @XmlElement(name = "address")
    private String utAddress;

    @XmlAttribute(name = "positive-ack-timer-interval")
    private long positiveAckTimerInterval = -1; // -1: N/A

    @XmlAttribute(name = "nak-timer-interval")
    private long nakTimerInterval = -1; // -1: N/A

    @XmlAttribute(name = "keep-alive-interval")
    private long keepAliveInterval = -1; // -1: N/A

    @XmlAttribute(name = "immediate-nak-mode")
    private boolean immediateNakModeEnabled;

    @XmlAttribute(name = "default-tx-mode-acknowledged")
    private boolean defaultTransmissionModeAcknowledged;

    @XmlAttribute(name = "transaction-closure-requested")
    private boolean transactionClosureRequested;

    @XmlAttribute(name = "check-interval")
    private long checkInterval; // In the standard, this is referred as Check Limit

    @XmlAttribute(name = "check-interval-expiration-limit")
    private int checkIntervalExpirationLimit = 1;

    @XmlAttribute(name = "default-checksum")
    private int defaultChecksumType;

    @XmlAttribute(name = "retain-incomplete-files-on-cancel")
    private boolean retainIncompleteReceivedFilesOnCancellation;

    @XmlAttribute(name = "crc-required")
    private boolean crcRequiredOnTransmission;

    @XmlAttribute(name = "max-file-segment-length")
    private int maximumFileSegmentLength;

    @XmlAttribute(name = "keep-alive-limit")
    private int keepAliveDiscrepancyLimit = -1; // -1: N/A, max number of bytes that the receiver can stay behind

    @XmlAttribute(name = "positive-ack-expiration-limit")
    private int positiveAckTimerExpirationLimit;

    @XmlAttribute(name = "nak-expiration-limit")
    private int nakTimerExpirationLimit;

    @XmlAttribute(name = "transaction-inactivity-limit")
    private int transactionInactivityLimit;

    @XmlAttribute(name = "nak-recomputation-interval")
    private long nakRecomputationInterval;

    public long getRemoteEntityId() {
        return remoteEntityId;
    }

    public RemoteEntityConfigurationInformation setRemoteEntityId(long remoteEntityId) {
        this.remoteEntityId = remoteEntityId;
        return this;
    }

    public long getProtocolVersion() {
        return protocolVersion;
    }

    public RemoteEntityConfigurationInformation setProtocolVersion(long protocolVersion) {
        this.protocolVersion = protocolVersion;
        return this;
    }

    public String getUtLayer() {
        return utLayer;
    }

    public RemoteEntityConfigurationInformation setUtLayer(String utLayer) {
        this.utLayer = utLayer;
        return this;
    }

    public String getUtAddress() {
        return utAddress;
    }

    public RemoteEntityConfigurationInformation setUtAddress(String utAddress) {
        this.utAddress = utAddress;
        return this;
    }

    public long getPositiveAckTimerInterval() {
        return positiveAckTimerInterval;
    }

    public RemoteEntityConfigurationInformation setPositiveAckTimerInterval(long positiveAckTimerInterval) {
        this.positiveAckTimerInterval = positiveAckTimerInterval;
        return this;
    }

    public long getNakTimerInterval() {
        return nakTimerInterval;
    }

    public RemoteEntityConfigurationInformation setNakTimerInterval(long nakTimerInterval) {
        this.nakTimerInterval = nakTimerInterval;
        return this;
    }

    /**
     * 4.6.5.2.1 In all acknowledged modes, the receiving CFDP entity may periodically send a
     * Keep Alive PDU to the sending CFDP entity reporting on the transaction’s reception
     * progress so far at this entity.
     *
     * @return the keep alive interval in milliseconds
     */
    public long getKeepAliveInterval() {
        return keepAliveInterval;
    }

    public RemoteEntityConfigurationInformation setKeepAliveInterval(long keepAliveInterval) {
        this.keepAliveInterval = keepAliveInterval;
        return this;
    }

    /**
     * If enabled, then the metadata PDU is requested to be retransmitted (in case missing) at the reception of the
     * first File Data PDU.
     *
     * @return true to enable the immediate metadata PDU NAK
     */
    public boolean isImmediateNakModeEnabled() {
        return immediateNakModeEnabled;
    }

    public RemoteEntityConfigurationInformation setImmediateNakModeEnabled(boolean immediateNakModeEnabled) {
        this.immediateNakModeEnabled = immediateNakModeEnabled;
        return this;
    }

    public boolean isDefaultTransmissionModeAcknowledged() {
        return defaultTransmissionModeAcknowledged;
    }

    public RemoteEntityConfigurationInformation setDefaultTransmissionModeAcknowledged(boolean defaultTransmissionModeAcknowledged) {
        this.defaultTransmissionModeAcknowledged = defaultTransmissionModeAcknowledged;
        return this;
    }

    public boolean isTransactionClosureRequested() {
        return transactionClosureRequested;
    }

    public RemoteEntityConfigurationInformation setTransactionClosureRequested(boolean transactionClosureRequested) {
        this.transactionClosureRequested = transactionClosureRequested;
        return this;
    }

    public long getCheckInterval() {
        return checkInterval;
    }

    public RemoteEntityConfigurationInformation setCheckInterval(long checkInterval) {
        this.checkInterval = checkInterval;
        return this;
    }

    public int getDefaultChecksumType() {
        return defaultChecksumType;
    }

    public RemoteEntityConfigurationInformation setDefaultChecksumType(int defaultChecksumType) {
        this.defaultChecksumType = defaultChecksumType;
        return this;
    }

    public boolean isRetainIncompleteReceivedFilesOnCancellation() {
        return retainIncompleteReceivedFilesOnCancellation;
    }

    public RemoteEntityConfigurationInformation setRetainIncompleteReceivedFilesOnCancellation(boolean retainIncompleteReceivedFilesOnCancellation) {
        this.retainIncompleteReceivedFilesOnCancellation = retainIncompleteReceivedFilesOnCancellation;
        return this;
    }

    public boolean isCrcRequiredOnTransmission() {
        return crcRequiredOnTransmission;
    }

    public RemoteEntityConfigurationInformation setCrcRequiredOnTransmission(boolean crcRequiredOnTransmission) {
        this.crcRequiredOnTransmission = crcRequiredOnTransmission;
        return this;
    }

    public int getMaximumFileSegmentLength() {
        return maximumFileSegmentLength;
    }

    public RemoteEntityConfigurationInformation setMaximumFileSegmentLength(int maximumFileSegmentLength) {
        this.maximumFileSegmentLength = maximumFileSegmentLength;
        return this;
    }

    public int getKeepAliveDiscrepancyLimit() {
        return keepAliveDiscrepancyLimit;
    }

    public RemoteEntityConfigurationInformation setKeepAliveDiscrepancyLimit(int keepAliveDiscrepancyLimit) {
        this.keepAliveDiscrepancyLimit = keepAliveDiscrepancyLimit;
        return this;
    }

    public int getPositiveAckTimerExpirationLimit() {
        return positiveAckTimerExpirationLimit;
    }

    public RemoteEntityConfigurationInformation setPositiveAckTimerExpirationLimit(int positiveAckTimerExpirationLimit) {
        this.positiveAckTimerExpirationLimit = positiveAckTimerExpirationLimit;
        return this;
    }

    public int getNakTimerExpirationLimit() {
        return nakTimerExpirationLimit;
    }

    public RemoteEntityConfigurationInformation setNakTimerExpirationLimit(int nakTimerExpirationLimit) {
        this.nakTimerExpirationLimit = nakTimerExpirationLimit;
        return this;
    }

    public int getTransactionInactivityLimit() {
        return transactionInactivityLimit;
    }

    public RemoteEntityConfigurationInformation setTransactionInactivityLimit(int transactionInactivityLimit) {
        this.transactionInactivityLimit = transactionInactivityLimit;
        return this;
    }

    public int getCheckIntervalExpirationLimit() {
        return checkIntervalExpirationLimit;
    }

    public RemoteEntityConfigurationInformation setCheckIntervalExpirationLimit(int checkIntervalExpirationLimit) {
        this.checkIntervalExpirationLimit = checkIntervalExpirationLimit;
        return this;
    }

    public long getNakRecomputationInterval() {
        return nakRecomputationInterval;
    }

    public RemoteEntityConfigurationInformation setNakRecomputationInterval(long nakRecomputationInterval) {
        this.nakRecomputationInterval = nakRecomputationInterval;
        return this;
    }

    @Override
    public String toString() {
        return "RemoteEntityConfigurationInformation{" +
                "remoteEntityId=" + remoteEntityId +
                ", protocolVersion=" + protocolVersion +
                ", utLayer='" + utLayer + '\'' +
                ", utAddress='" + utAddress + '\'' +
                ", positiveAckTimerInterval=" + positiveAckTimerInterval +
                ", nackTimerInterval=" + nakTimerInterval +
                ", keepAliveInterval=" + keepAliveInterval +
                ", immediateNakModeEnabled=" + immediateNakModeEnabled +
                ", defaultTransmissionModeAcknowledged=" + defaultTransmissionModeAcknowledged +
                ", transactionClosureRequested=" + transactionClosureRequested +
                ", checkInterval=" + checkInterval +
                ", defaultChecksumType=" + defaultChecksumType +
                ", retainIncompleteReceivedFilesOnCancellation=" + retainIncompleteReceivedFilesOnCancellation +
                ", crcRequiredOnTransmission=" + crcRequiredOnTransmission +
                ", maximumFileSegmentLength=" + maximumFileSegmentLength +
                ", keepAliveDiscrepancyLimit=" + keepAliveDiscrepancyLimit +
                ", positiveAckTimerExpirationLimit=" + positiveAckTimerExpirationLimit +
                ", nakTimerExpirationLimit=" + nakTimerExpirationLimit +
                ", transactionInactivityLimit=" + transactionInactivityLimit +
                ", checkIntervalExpirationLimit=" + checkIntervalExpirationLimit +
                ", nakRecomputationInterval=" + nakRecomputationInterval +
                '}';
    }
}
