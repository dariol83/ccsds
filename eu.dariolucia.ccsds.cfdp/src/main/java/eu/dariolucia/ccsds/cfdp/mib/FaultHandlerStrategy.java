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

import eu.dariolucia.ccsds.cfdp.protocol.pdu.ConditionCode;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class FaultHandlerStrategy {

    public enum Action {
        NOTICE_OF_CANCELLATION,
        NOTICE_OF_SUSPENSION,
        NO_ACTION,
        ABANDON
    }

    @XmlAttribute(name = "condition")
    private ConditionCode condition;

    @XmlAttribute(name = "strategy")
    private Action strategy;

    public FaultHandlerStrategy() {
    }

    public FaultHandlerStrategy(ConditionCode condition, Action strategy) {
        this.condition = condition;
        this.strategy = strategy;
    }

    public ConditionCode getCondition() {
        return condition;
    }

    public FaultHandlerStrategy setCondition(ConditionCode condition) {
        this.condition = condition;
        return this;
    }

    public Action getStrategy() {
        return strategy;
    }

    public FaultHandlerStrategy setStrategy(Action strategy) {
        this.strategy = strategy;
        return this;
    }

    @Override
    public String toString() {
        return "FaultHandlerStrategy{" +
                "condition=" + condition +
                ", strategy=" + strategy +
                '}';
    }
}
