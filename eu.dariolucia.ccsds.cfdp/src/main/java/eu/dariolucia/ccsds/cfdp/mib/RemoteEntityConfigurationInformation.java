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
    private long protocolVersion;

    @XmlElement(name = "ut_layer")
    private String utLayer;

    @XmlElement(name = "address")
    private String utAddress;

    @XmlAttribute(name = "positive_ack_timer_interval")
    private long positiveAckTimerInterval; // -1: N/A

    @XmlAttribute(name = "nack_timer_interval")
    private long nackTimerInterval; // -1: N/A

    @XmlAttribute(name = "keep_alive_interval")
    private long keepAliveInterval; // -1: N/A

    @XmlAttribute(name = "immediate_nak_mode")
    private boolean immediateNakModeEnabled;

    @XmlAttribute(name = "default_tx_mode_acknowledged")
    private boolean defaultTransmissionModeAcknowledged;

    @XmlAttribute(name = "transaction_closure_requested")
    private boolean transactionClosureRequested;

    @XmlAttribute(name = "check_limit")
    private long checkLimit;

    @XmlAttribute(name = "default_checksum")
    private int defaultChecksumType;

    @XmlAttribute(name = "retain_incomplete_files_on_cancel")
    private boolean retainIncompleteReceivedFilesOnCancellation;

    @XmlAttribute(name = "crc_required")
    private boolean crcRequiredOnTransmission;

    @XmlAttribute(name = "max_file_segment_length")
    private int maximumFileSegmentLength;

    @XmlAttribute(name = "keep_alive_limit")
    private int keepAliveDiscrepancyLimit; // -1: N/A

    @XmlAttribute(name = "positive_ack_expiration_limit")
    private int positiveAckTimerExpirationLimit;

    @XmlAttribute(name = "nak_expiration_limit")
    private int nakTimerExpirationLimit;

    @XmlAttribute(name = "transaction_inactivity_limit")
    private int transactionInactivityLimit;

    @XmlAttribute(name = "ack_mode_supported")
    private boolean acknowledgedModeSupported = true;

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

    public long getNackTimerInterval() {
        return nackTimerInterval;
    }

    public RemoteEntityConfigurationInformation setNackTimerInterval(long nackTimerInterval) {
        this.nackTimerInterval = nackTimerInterval;
        return this;
    }

    public long getKeepAliveInterval() {
        return keepAliveInterval;
    }

    public RemoteEntityConfigurationInformation setKeepAliveInterval(long keepAliveInterval) {
        this.keepAliveInterval = keepAliveInterval;
        return this;
    }

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

    public long getCheckLimit() {
        return checkLimit;
    }

    public RemoteEntityConfigurationInformation setCheckLimit(long checkLimit) {
        this.checkLimit = checkLimit;
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

    public boolean isAcknowledgedModeSupported() {
        return acknowledgedModeSupported;
    }

    public RemoteEntityConfigurationInformation setAcknowledgedModeSupported(boolean acknowledgedModeSupported) {
        this.acknowledgedModeSupported = acknowledgedModeSupported;
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
                ", nackTimerInterval=" + nackTimerInterval +
                ", keepAliveInterval=" + keepAliveInterval +
                ", immediateNakModeEnabled=" + immediateNakModeEnabled +
                ", defaultTransmissionModeAcknowledged=" + defaultTransmissionModeAcknowledged +
                ", transactionClosureRequested=" + transactionClosureRequested +
                ", checkLimit=" + checkLimit +
                ", defaultChecksumType=" + defaultChecksumType +
                ", retainIncompleteReceivedFilesOnCancellation=" + retainIncompleteReceivedFilesOnCancellation +
                ", crcRequiredOnTransmission=" + crcRequiredOnTransmission +
                ", maximumFileSegmentLength=" + maximumFileSegmentLength +
                ", keepAliveDiscrepancyLimit=" + keepAliveDiscrepancyLimit +
                ", positiveAckTimerExpirationLimit=" + positiveAckTimerExpirationLimit +
                ", nakTimerExpirationLimit=" + nakTimerExpirationLimit +
                ", transactionInactivityLimit=" + transactionInactivityLimit +
                ", acknowledgedModeSupported=" + acknowledgedModeSupported +
                '}';
    }
}
