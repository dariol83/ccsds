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

package eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs;

import eu.dariolucia.ccsds.cfdp.mib.FaultHandlerStrategy;

import java.nio.ByteBuffer;

public class FaultHandlerOverrideTLV implements TLV {

    public static final int TLV_TYPE = 0x04;

    public enum HandlerCode {
        RESERVED,
        ISSUE_NOTICE_OF_CANCELLATION,
        ISSUE_NOTICE_OF_SUSPENSION,
        IGNORE_ERROR,
        ABANDON_TRANSACTION;

        public static HandlerCode map(FaultHandlerStrategy.Action action) {
            switch (action) {
                case ABANDON: return ABANDON_TRANSACTION;
                case NOTICE_OF_CANCELLATION: return ISSUE_NOTICE_OF_CANCELLATION;
                case NOTICE_OF_SUSPENSION: return ISSUE_NOTICE_OF_SUSPENSION;
                case NO_ACTION: return IGNORE_ERROR;
                default: throw new Error("Fault strategy action " + action + " not supported. Software problem."); // NOSONAR this is my way of dealing with potentially catastrophic errors
            }
        }

        public FaultHandlerStrategy.Action toAction() {
            switch(this) {
                case ABANDON_TRANSACTION: return FaultHandlerStrategy.Action.ABANDON;
                case ISSUE_NOTICE_OF_CANCELLATION: return FaultHandlerStrategy.Action.NOTICE_OF_CANCELLATION;
                case ISSUE_NOTICE_OF_SUSPENSION: return FaultHandlerStrategy.Action.NOTICE_OF_SUSPENSION;
                case IGNORE_ERROR: return FaultHandlerStrategy.Action.NO_ACTION;
                default: throw new Error("Fault strategy action " + this + " not recognized. Software problem."); // NOSONAR this is my way of dealing with potentially catastrophic errors
            }
        }
    }

    private final byte conditionCode;

    private final HandlerCode handlerCode;

    private final int encodedLength;

    public FaultHandlerOverrideTLV(byte conditionCode, HandlerCode handlerCode) {
        this.conditionCode = conditionCode;
        this.handlerCode = handlerCode;
        this.encodedLength = 1;
    }

    public FaultHandlerOverrideTLV(byte[] data, int offset) {
        // Starting from offset, assume that there is an encoded Fault Handler Override TLV Contents: Table 5-19
        this.conditionCode = (byte) ((data[offset] & 0xF0) >>> 4);
        this.handlerCode = HandlerCode.values()[data[offset] & 0x0F];
        // Encoded length
        this.encodedLength = 1;
    }

    public byte getConditionCode() {
        return conditionCode;
    }

    public HandlerCode getHandlerCode() {
        return handlerCode;
    }

    @Override
    public int getType() {
        return TLV_TYPE;
    }

    @Override
    public int getLength() {
        return encodedLength;
    }

    @Override
    public byte[] encode() {
        ByteBuffer bb;
        bb = ByteBuffer.allocate(2 + this.encodedLength);
        bb.put((byte) TLV_TYPE);
        bb.put((byte) (this.encodedLength & 0xFF));
        byte tmp = getConditionCode();
        tmp <<= 4;
        tmp |= (byte) getHandlerCode().ordinal();
        bb.put(tmp);
        return bb.array();
    }

    @Override
    public String toString() {
        return "FaultHandlerOverrideTLV{" +
                "conditionCode=" + conditionCode +
                ", handlerCode=" + handlerCode +
                ", encodedLength=" + encodedLength +
                '}';
    }
}
