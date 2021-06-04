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

import eu.dariolucia.ccsds.cfdp.common.CfdpException;
import eu.dariolucia.ccsds.cfdp.mib.FaultHandlerStrategy;

public class FaultDeclaredException extends CfdpException {

    private final FaultHandlerStrategy.Action action;
    private final byte conditionCode;
    private final long generatingEntityId;
    private final long transactionId;

    public FaultDeclaredException(long transactionId, FaultHandlerStrategy.Action action, byte conditionCode, long generatingEntityId) {
        super(String.format("Transaction %d: fault with code 0x%02X detected from entity %d, action %s", transactionId, conditionCode, generatingEntityId, action.toString()));
        this.action = action;
        this.conditionCode = conditionCode;
        this.generatingEntityId = generatingEntityId;
        this.transactionId = transactionId;
    }

    public long getTransactionId() {
        return transactionId;
    }

    public FaultHandlerStrategy.Action getAction() {
        return action;
    }

    public byte getConditionCode() {
        return conditionCode;
    }

    public long getGeneratingEntityId() {
        return generatingEntityId;
    }
}
