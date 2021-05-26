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
