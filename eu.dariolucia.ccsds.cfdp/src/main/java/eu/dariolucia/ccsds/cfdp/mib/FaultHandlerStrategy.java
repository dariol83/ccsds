package eu.dariolucia.ccsds.cfdp.mib;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class FaultHandlerStrategy {

    public enum Action {
        NOTICE_OF_CANCELLATION,
        NOTICE_OF_SUSPENSION,
        NO_ACTION,
        ABANDON
    }

    @XmlAttribute(name = "condition")
    private int condition;

    @XmlAttribute(name = "strategy")
    private Action strategy;

    public FaultHandlerStrategy() {
    }

    public FaultHandlerStrategy(int condition, Action strategy) {
        this.condition = condition;
        this.strategy = strategy;
    }

    public int getCondition() {
        return condition;
    }

    public FaultHandlerStrategy setCondition(int condition) {
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
}
