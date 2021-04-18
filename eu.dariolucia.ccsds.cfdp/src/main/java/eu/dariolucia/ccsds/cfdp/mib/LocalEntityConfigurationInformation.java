package eu.dariolucia.ccsds.cfdp.mib;

import javax.xml.bind.annotation.*;
import java.util.LinkedList;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class LocalEntityConfigurationInformation {

    @XmlAttribute(name = "id")
    private long localEntityId;

    @XmlAttribute(name = "EOF_sent_indication")
    private boolean eofSentIndicationRequired;

    @XmlAttribute(name = "EOF_recv_indication")
    private boolean eofRecvIndicationRequired;

    @XmlAttribute(name = "file_segment_recv_indication")
    private boolean fileSegmentRecvIndicationRequired;

    @XmlAttribute(name = "transaction_finished_indication")
    private boolean transactionFinishedIndicationRequired; // When acting as receiving entity

    @XmlAttribute(name = "suspended_indication")
    private boolean suspendedIndicationRequired; // When acting as receiving entity

    @XmlAttribute(name = "resumed_indication")
    private boolean resumedIndicationRequired; // When acting as receiving entity

    @XmlElementWrapper(name = "fault_handlers")
    @XmlElement(name = "fault_handler")
    private List<FaultHandlerStrategy> faultHandlerStrategyList = new LinkedList<>(); // For each type of fault condition, a default handler

    public long getLocalEntityId() {
        return localEntityId;
    }

    public LocalEntityConfigurationInformation setLocalEntityId(long localEntityId) {
        this.localEntityId = localEntityId;
        return this;
    }

    public boolean isEofSentIndicationRequired() {
        return eofSentIndicationRequired;
    }

    public LocalEntityConfigurationInformation setEofSentIndicationRequired(boolean eofSentIndicationRequired) {
        this.eofSentIndicationRequired = eofSentIndicationRequired;
        return this;
    }

    public boolean isEofRecvIndicationRequired() {
        return eofRecvIndicationRequired;
    }

    public LocalEntityConfigurationInformation setEofRecvIndicationRequired(boolean eofRecvIndicationRequired) {
        this.eofRecvIndicationRequired = eofRecvIndicationRequired;
        return this;
    }

    public boolean isFileSegmentRecvIndicationRequired() {
        return fileSegmentRecvIndicationRequired;
    }

    public LocalEntityConfigurationInformation setFileSegmentRecvIndicationRequired(boolean fileSegmentRecvIndicationRequired) {
        this.fileSegmentRecvIndicationRequired = fileSegmentRecvIndicationRequired;
        return this;
    }

    public boolean isTransactionFinishedIndicationRequired() {
        return transactionFinishedIndicationRequired;
    }

    public LocalEntityConfigurationInformation setTransactionFinishedIndicationRequired(boolean transactionFinishedIndicationRequired) {
        this.transactionFinishedIndicationRequired = transactionFinishedIndicationRequired;
        return this;
    }

    public boolean isSuspendedIndicationRequired() {
        return suspendedIndicationRequired;
    }

    public LocalEntityConfigurationInformation setSuspendedIndicationRequired(boolean suspendedIndicationRequired) {
        this.suspendedIndicationRequired = suspendedIndicationRequired;
        return this;
    }

    public boolean isResumedIndicationRequired() {
        return resumedIndicationRequired;
    }

    public LocalEntityConfigurationInformation setResumedIndicationRequired(boolean resumedIndicationRequired) {
        this.resumedIndicationRequired = resumedIndicationRequired;
        return this;
    }

    public List<FaultHandlerStrategy> getFaultHandlerStrategyList() {
        return faultHandlerStrategyList;
    }

    public LocalEntityConfigurationInformation setFaultHandlerStrategyList(List<FaultHandlerStrategy> faultHandlerStrategyList) {
        this.faultHandlerStrategyList = faultHandlerStrategyList;
        return this;
    }

    @Override
    public String toString() {
        return "LocalEntityConfigurationInformation{" +
                "localEntityId=" + localEntityId +
                ", eofSentIndicationRequired=" + eofSentIndicationRequired +
                ", eofRecvIndicationRequired=" + eofRecvIndicationRequired +
                ", fileSegmentRecvIndicationRequired=" + fileSegmentRecvIndicationRequired +
                ", transactionFinishedIndicationRequired=" + transactionFinishedIndicationRequired +
                ", suspendedIndicationRequired=" + suspendedIndicationRequired +
                ", resumedIndicationRequired=" + resumedIndicationRequired +
                ", faultHandlerStrategyList=" + faultHandlerStrategyList +
                '}';
    }
}
