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

package eu.dariolucia.ccsds.cfdp.protocol.pdu;

/**
 * Ack PDU - CCSDS 727.0-B-5, 5.2.4
 */
public class AckPdu extends FileDirectivePdu {

    public enum TransactionStatus {
        /**
         * The transaction to which the acknowledged PDU belongs is not
         * currently active at this entity, and the CFDP implementation does not retain
         * transaction history. The transaction might be one that was formerly active
         * and has been terminated, or it might be one that has never been active at this
         * entity.
         */
        UNDEFINED,
        /**
         * The transaction to which the acknowledged PDU belongs is currently
         * active at this entity.
         */
        ACTIVE,
        /**
         * The transaction to which the acknowledged PDU belongs is not
         * currently active at this entity, the CFDP implementation does retain
         * transaction history, and the transaction is thereby known to be one that was
         * formerly active and has been terminated.
         */
        TERMINATED,
        /**
         * The transaction to which the acknowledged PDU belongs is
         * not currently active at this entity, the CFDP implementation does retain
         * transaction history, and the transaction is thereby known to be one that has
         * never been active at this entity
         */
        UNRECOGNIZED
    }

    /**
     * Directive code of the PDU that this ACK PDU acknowledges.
     * Only EOF and Finished PDUs are acknowledged.
     */
    private final DirectiveCode directiveCode;

    /**
     * Values depend on the directive code of the PDU that this ACK PDU acknowledges.
     * For ACK of Finished PDU: binary '0001'.
     * Binary '0000' for ACKs of all other file directives.
     */
    private final byte directiveSubtypeCode;

    /**
     * Condition code of the acknowledged PDU.
     */
    private final ConditionCode conditionCode;

    /**
     * Status of the transaction in the context of the entity that is issuing the acknowledgment.
     */
    private final TransactionStatus transactionStatus;

    public AckPdu(byte[] pdu) {
        super(pdu);
        // Directive code check
        if(pdu[getHeaderLength()] != DirectiveCode.DC_ACK_PDU.getCode()) {
            throw new IllegalArgumentException("Directive code mismatch: " + String.format("0x%02X",pdu[getHeaderLength()]));
        }
        // PDU-specific parsing
        this.directiveCode = DirectiveCode.fromCode((byte) ((pdu[getDirectiveParameterIndex()] & 0xF0) >>> 4));
        this.directiveSubtypeCode = (byte) (pdu[getDirectiveParameterIndex()] & 0x0F);
        this.conditionCode = ConditionCode.fromCode((byte) ((pdu[getDirectiveParameterIndex() + 1] & 0xF0) >>> 4));
        this.transactionStatus = TransactionStatus.values()[(pdu[getDirectiveParameterIndex() + 1] & 0x03)];
    }

    public DirectiveCode getDirectiveCode() {
        return directiveCode;
    }

    public byte getDirectiveSubtypeCode() {
        return directiveSubtypeCode;
    }

    public ConditionCode getConditionCode() {
        return conditionCode;
    }

    public TransactionStatus getTransactionStatus() {
        return transactionStatus;
    }

    @Override
    public String toString() {
        return super.toString() + " AckPdu{" +
                "directiveCode=" + directiveCode +
                ", directiveSubtypeCode=" + directiveSubtypeCode +
                ", conditionCode=" + conditionCode +
                ", transactionStatus=" + transactionStatus +
                '}';
    }
}
