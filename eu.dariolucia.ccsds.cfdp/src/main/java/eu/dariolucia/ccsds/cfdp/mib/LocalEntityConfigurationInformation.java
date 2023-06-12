/*
 *   Copyright (c) 2022 Dario Lucia (https://www.dariolucia.eu)
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

import eu.dariolucia.ccsds.cfdp.protocol.pdu.ConditionCode;

import jakarta.xml.bind.annotation.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@XmlAccessorType(XmlAccessType.FIELD)
public class LocalEntityConfigurationInformation {

    @XmlAttribute(name = "id")
    private long localEntityId;

    @XmlAttribute(name = "EOF-sent-indication")
    private boolean eofSentIndicationRequired;

    @XmlAttribute(name = "EOF-recv-indication")
    private boolean eofRecvIndicationRequired;

    @XmlAttribute(name = "file-segment-recv-indication")
    private boolean fileSegmentRecvIndicationRequired;

    @XmlAttribute(name = "transaction-finished-indication")
    private boolean transactionFinishedIndicationRequired; // When acting as receiving entity

    @XmlAttribute(name = "suspended-indication")
    private boolean suspendedIndicationRequired; // When acting as receiving entity

    @XmlAttribute(name = "resumed-indication")
    private boolean resumedIndicationRequired; // When acting as receiving entity

    @XmlAttribute(name = "completed-transactions-cleanup-period")
    private int completedTransactionsCleanupPeriod = -1; // >0: disabled; 0: cleanup as soon as the transaction is disposed; >0: regular cleanup job everything X seconds

    @XmlAttribute(name = "file-temp-folder")
    private String tempFolder = null;

    @XmlAttribute(name = "file-based-temp-storage")
    private boolean fileBasedTempStorage = true;

    @XmlElementWrapper(name = "fault-handlers")
    @XmlElement(name = "fault-handler")
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

    public Map<ConditionCode, FaultHandlerStrategy.Action> getFaultHandlerMap() {
        return this.faultHandlerStrategyList.stream().collect(Collectors.toMap(FaultHandlerStrategy::getCondition, FaultHandlerStrategy::getStrategy));
    }

    public int getCompletedTransactionsCleanupPeriod() {
        return completedTransactionsCleanupPeriod;
    }

    public void setCompletedTransactionsCleanupPeriod(int completedTransactionsCleanupPeriod) {
        this.completedTransactionsCleanupPeriod = completedTransactionsCleanupPeriod;
    }

    public String getTempFolder() {
        return tempFolder;
    }

    public void setTempFolder(String tempFolder) {
        this.tempFolder = tempFolder;
    }

    public boolean isFileBasedTempStorage() {
        return fileBasedTempStorage;
    }

    public void setFileBasedTempStorage(boolean fileBasedTempStorage) {
        this.fileBasedTempStorage = fileBasedTempStorage;
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
                ", completedTransactionsCleanupPeriod=" + completedTransactionsCleanupPeriod +
                ", fileBasedTempStorage=" + fileBasedTempStorage +
                ", tempFolder=" + tempFolder +
                ", faultHandlerStrategyList=" + faultHandlerStrategyList +
                '}';
    }
}
