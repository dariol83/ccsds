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

package eu.dariolucia.ccsds.cfdp.protocol.builder;

import eu.dariolucia.ccsds.cfdp.protocol.pdu.*;

import java.io.ByteArrayOutputStream;

/**
 * Builder class for {@link AckPdu} objects.
 */
public class AckPduBuilder extends CfdpPduBuilder<AckPdu, AckPduBuilder> {

    private DirectiveCode directiveCode;

    private byte directiveSubtypeCode;

    private ConditionCode conditionCode = ConditionCode.CC_NOERROR;

    private AckPdu.TransactionStatus transactionStatus;

    /**
     * Construct an empty builder for this file directive PDU.
     */
    public AckPduBuilder() {
        setType(CfdpPdu.PduType.FILE_DIRECTIVE);
    }

    /**
     * Directive code of the PDU that this ACK PDU acknowledges.
     * Only EOF and Finished PDUs are acknowledged.
     *
     * @param directiveCode the directive code
     * @return this
     */
    public AckPduBuilder setDirectiveCode(DirectiveCode directiveCode) {
        this.directiveCode = directiveCode;
        return this;
    }

    /**
     * Values depend on the directive code of the PDU that this ACK PDU acknowledges.
     * For ACK of Finished PDU: binary '0001'.
     * Binary '0000' for ACKs of all other file directives.
     *
     * @param directiveSubtypeCode the directive subtype code
     * @return this
     */
    public AckPduBuilder setDirectiveSubtypeCode(byte directiveSubtypeCode) {
        this.directiveSubtypeCode = directiveSubtypeCode;
        return this;
    }

    /**
     * Condition code of the acknowledged PDU.
     *
     * @param conditionCode the condition code
     * @return this
     */
    public AckPduBuilder setConditionCode(ConditionCode conditionCode) {
        this.conditionCode = conditionCode;
        return this;
    }

    /**
     * Status of the transaction in the context of the entity that is issuing the acknowledgment.
     *
     * @param transactionStatus the transaction status
     * @return this
     */
    public AckPduBuilder setTransactionStatus(AckPdu.TransactionStatus transactionStatus) {
        this.transactionStatus = transactionStatus;
        return this;
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

    public AckPdu.TransactionStatus getTransactionStatus() {
        return transactionStatus;
    }

    @Override
    protected int encodeDataField(ByteArrayOutputStream bos) {
        int totalLength = 0;
        // Directive code
        bos.write(DirectiveCode.DC_ACK_PDU.getCode());
        totalLength += 1;
        // Directive code and subtype
        byte first = (byte) ((this.directiveCode.getCode() << 4) & 0xF0);
        first |= (byte) (this.directiveSubtypeCode & 0x0F);
        bos.write(first);
        totalLength += 1;
        // Condition code (4 bits), spare bit and transaction status (2 bits)
        byte second = (byte) ((this.conditionCode.getCode() << 4) & 0xF0);
        second |= (byte) ((this.transactionStatus.ordinal()) & 0x03);
        bos.write(second);
        totalLength += 1;
        return totalLength;
    }

    @Override
    protected AckPdu buildObject(byte[] pdu) {
        return new AckPdu(pdu);
    }
}
