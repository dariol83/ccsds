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

package eu.dariolucia.ccsds.cfdp.entity;

import eu.dariolucia.ccsds.cfdp.protocol.pdu.ConditionCode;

import java.time.Instant;

/**
 * Class to report the status of a CFDP transaction. This class is implementation specific and not defined by the CCSDS
 * CFDP standard.
 */
public class CfdpTransactionStatus {

    private final Instant time;
    private final ICfdpEntity managingEntity;
    private final long transactionId;
    private final long sourceEntityId;
    private final long destinationEntityId;
    private final boolean isDestination;
    private final ConditionCode lastConditionCode;
    private final Long lastFaultEntity;
    private final CfdpTransactionState cfdpTransactionState;
    private final long progress;
    private final long totalFileSize;
    private final CfdpTransmissionMode transmissionMode;

    public CfdpTransactionStatus(Instant time, ICfdpEntity managingEntity, long transactionId, long sourceEntityId, long destinationEntityId,  // NOSONAR: long constructor
                                 boolean isDestination, ConditionCode lastConditionCode, Long lastFaultEntity, CfdpTransactionState cfdpTransactionState,
                                 long progress, long totalFileSize, CfdpTransmissionMode transmissionMode) {
        this.time = time;
        this.managingEntity = managingEntity;
        this.transactionId = transactionId;
        this.sourceEntityId = sourceEntityId;
        this.destinationEntityId = destinationEntityId;
        this.isDestination = isDestination;
        this.lastConditionCode = lastConditionCode;
        this.cfdpTransactionState = cfdpTransactionState;
        this.progress = progress;
        this.totalFileSize = totalFileSize;
        this.lastFaultEntity = lastFaultEntity;
        this.transmissionMode = transmissionMode;
    }

    public Instant getTime() {
        return time;
    }

    public ICfdpEntity getManagingEntity() {
        return managingEntity;
    }

    public long getTransactionId() {
        return transactionId;
    }

    public long getSourceEntityId() {
        return sourceEntityId;
    }

    public long getDestinationEntityId() {
        return destinationEntityId;
    }

    public boolean isDestination() {
        return isDestination;
    }

    public ConditionCode getLastConditionCode() {
        return lastConditionCode;
    }

    public CfdpTransactionState getCfdpTransactionState() {
        return cfdpTransactionState;
    }

    public long getProgress() {
        return progress;
    }

    public long getTotalFileSize() {
        return totalFileSize;
    }

    public Long getLastFaultEntity() {
        return lastFaultEntity;
    }

    public CfdpTransmissionMode getTransmissionMode() {
        return transmissionMode;
    }

    @Override
    public String toString() {
        return "CfdpTransactionStatus{" +
                "time=" + getTime() +
                ", managingEntity=" + getManagingEntity() +
                ", transactionId=" + getTransactionId() +
                ", sourceEntityId=" + getSourceEntityId() +
                ", destinationEntityId=" + getDestinationEntityId() +
                ", isDestination=" + isDestination() +
                ", lastConditionCode=" + getLastConditionCode() +
                ", lastFaultEntity=" + getLastFaultEntity() +
                ", cfdpTransactionState=" + getCfdpTransactionState() +
                ", progress=" + getProgress() +
                ", totalFileSize=" + getTotalFileSize() +
                ", transmissionMode=" + getTransmissionMode() +
                '}';
    }
}
