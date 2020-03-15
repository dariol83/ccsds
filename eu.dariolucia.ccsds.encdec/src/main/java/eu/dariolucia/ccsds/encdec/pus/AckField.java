/*
 *   Copyright (c) 2019 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.encdec.pus;

import java.util.Objects;

/**
 * Representation of a TC PUS Header ack field.
 */
public class AckField {

    private static final AckField ALL_ACKS = new AckField(true, true, true, true);

    /**
     * Return an ack field where all acks are enabled (i.e. 0b1111).
     *
     * @return the ack field with all 1s
     */
    public static AckField allAcks() {
        return ALL_ACKS;
    }

    private final byte bitSet;

    /**
     * Class constructor, the least 4 bits are used to store the information.
     *
     * @param bitSet the ack field to set
     */
    public AckField(byte bitSet) {
        this.bitSet = bitSet;
    }

    /**
     * Class constructor with explicit value for each ack flag.
     *
     * @param acceptanceAckSet true if the flag is set, otherwise false
     * @param startAckSet true if the flag is set, otherwise false
     * @param progressAckSet true if the flag is set, otherwise false
     * @param completionAckSet true if the flag is set, otherwise false
     */
    public AckField(boolean acceptanceAckSet, boolean startAckSet, boolean progressAckSet, boolean completionAckSet) {
        byte toSet = 0;
        if(acceptanceAckSet) {
            toSet |= 0x01;
        }
        if(startAckSet) {
            toSet |= 0x02;
        }
        if(progressAckSet) {
            toSet |= 0x04;
        }
        if(completionAckSet) {
            toSet |= 0x08;
        }
        this.bitSet = toSet;
    }

    /**
     * Return true if the acceptance flag is set.
     *
     * @return the set state of the acceptance flag
     */
    public boolean isAcceptanceAckSet() {
        return (bitSet & 0x01) != 0;
    }

    /**
     * Return true if the start flag is set.
     *
     * @return the set state of the start flag
     */
    public boolean isStartAckSet() {
        return (bitSet & 0x02) != 0;
    }

    /**
     * Return true if the progress flag is set.
     *
     * @return the set state of the progress flag
     */
    public boolean isProgressAckSet() {
        return (bitSet & 0x04) != 0;
    }

    /**
     * Return true if the completion flag is set.
     *
     * @return the set state of the completion flag
     */
    public boolean isCompletionAckSet() {
        return (bitSet & 0x08) != 0;
    }

    /**
     * Return the ack field bit representation (4 least significant bits)
     *
     * @return the ack field
     */
    public byte getBitRepresentation() {
        return bitSet;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AckField ackField = (AckField) o;
        return bitSet == ackField.bitSet;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bitSet);
    }

    @Override
    public String toString() {
        return "[" +
                (isCompletionAckSet() ? "C" : "x") +
                (isProgressAckSet() ? "P" : "x") +
                (isStartAckSet() ? "S" : "x") +
                (isAcceptanceAckSet() ? "A" : "x") +
                ']';
    }
}
