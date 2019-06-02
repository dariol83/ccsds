/*
 * Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package eu.dariolucia.ccsds.tmtc.ocf.builder;

import eu.dariolucia.ccsds.tmtc.ocf.pdu.Clcw;

import java.nio.ByteBuffer;

/**
 * This class is a builder for Clcw objects. After calling build() the builder can be reused, i.e. its internal state
 * is not invalidated or marked as finalised. When the builder is initialised, all integer values of the CLCW structure
 * are set to 0 and all flags are set to false.
 *
 * This class is not thread-safe.
 *
 */
public class ClcwBuilder {

    /**
     * This method creates a new builder instance.
     * @return
     */
    public static ClcwBuilder create() {
        return new ClcwBuilder();
    }

    private int statusField;
    private boolean copInEffect;
    private int virtualChannelId;
    private int reservedSpare;

    private boolean noRfAvailableFlag;
    private boolean noBitlockFlag;
    private boolean lockoutFlag;
    private boolean waitFlag;
    private boolean retransmitFlag;

    private int farmBCounter;

    private int reportValue;

    private ClcwBuilder() {
        // Nothing to do here
    }

    /**
     * This method sets the status field of the CLCW. The status field  must be between 0 and 7 (3 bits resolution).
     * If the status field is invalid, an IllegalArgumentException is thrown.
     *
     * @param statusField the status field value to set
     * @return the builder
     */
    public ClcwBuilder setStatusField(int statusField) {
        if(statusField < 0 || statusField > 7) {
            throw new IllegalArgumentException("Status Field must be between 0 and 7 (inclusive), got " + statusField);
        }

        this.statusField = statusField;
        return this;
    }

    /**
     * This method sets the virtual channel ID of the CLCW. The virtual channel must be between 0 and 63 (6 bits resolution).
     * If the VCID is invalid, an IllegalArgumentException is thrown.
     *
     * @param virtualChannelId the virtual channel ID to set
     * @return the builder
     */
    public ClcwBuilder setVirtualChannelId(int virtualChannelId) {
        if(virtualChannelId < 0 || virtualChannelId > 63) {
            throw new IllegalArgumentException("Virtual Channel ID must be between 0 and 63 (inclusive), got " + virtualChannelId);
        }

        this.virtualChannelId = virtualChannelId;
        return this;
    }

    /**
     * This method sets the reserved spare value of the CLCW. The value must be between 0 and 3 (2 bits resolution).
     * If the value is invalid, an IllegalArgumentException is thrown.
     *
     * @param reservedSpare the reserved spare value to set
     * @return the builder
     */
    public ClcwBuilder setReservedSpare(int reservedSpare) {
        if(reservedSpare < 0 || reservedSpare > 3) {
            throw new IllegalArgumentException("Reserved Spare must be between 0 and 3 (inclusive), got " + reservedSpare);
        }

        this.reservedSpare = reservedSpare;
        return this;
    }

    /**
     * This method sets the bit of the No RF Available flag.
     *
     * @param noRfAvailableFlag true if the flag shall be set to 1, false otherwise
     * @return the builder
     */
    public ClcwBuilder setNoRfAvailableFlag(boolean noRfAvailableFlag) {
        this.noRfAvailableFlag = noRfAvailableFlag;
        return this;
    }

    /**
     * This method sets the bit of the No Bitlock Required flag.
     *
     * @param noBitlockFlag true if the flag shall be set to 1, false otherwise
     * @return the builder
     */
    public ClcwBuilder setNoBitlockFlag(boolean noBitlockFlag) {
        this.noBitlockFlag = noBitlockFlag;
        return this;
    }

    /**
     * This method sets the bit of the COP-1 in Effect flag.
     *
     * @param copInEffect true if the flag shall be set to 1, false otherwise
     * @return the builder
     */
    public ClcwBuilder setCopInEffect(boolean copInEffect) {
        this.copInEffect = copInEffect;
        return this;
    }

    /**
     * This method sets the bit of the Lockout flag.
     *
     * @param lockoutFlag true if the flag shall be set to 1, false otherwise
     * @return the builder
     */
    public ClcwBuilder setLockoutFlag(boolean lockoutFlag) {
        this.lockoutFlag = lockoutFlag;
        return this;
    }

    /**
     * This method sets the bit of the Wait flag.
     *
     * @param waitFlag true if the flag shall be set to 1, false otherwise
     * @return the builder
     */
    public ClcwBuilder setWaitFlag(boolean waitFlag) {
        this.waitFlag = waitFlag;
        return this;
    }

    /**
     * This method sets the bit of the Retransmit flag.
     *
     * @param retransmitFlag true if the flag shall be set to 1, false otherwise
     * @return the builder
     */
    public ClcwBuilder setRetransmitFlag(boolean retransmitFlag) {
        this.retransmitFlag = retransmitFlag;
        return this;
    }

    /**
     * This method sets the FARM-B value of the CLCW. The value must be between 0 and 3 (2 bits resolution).
     * If the value is invalid, an IllegalArgumentException is thrown.
     *
     * @param farmBCounter the reserved spare value to set
     * @return the builder
     */
    public ClcwBuilder setFarmBCounter(int farmBCounter) {
        this.farmBCounter = farmBCounter % 4;
        return this;
    }

    /**
     * This method sets the report value of the CLCW. The value must be between 0 and 255 (8 bits resolution).
     * If the value is invalid, an IllegalArgumentException is thrown.
     *
     * @param reportValue the reserved spare value to set
     * @return the builder
     */
    public ClcwBuilder setReportValue(int reportValue) {
        if(reportValue < 0 || reportValue > 255) {
            throw new IllegalArgumentException("Report Value must be between 0 and 255 (inclusive), got " + reportValue);
        }

        this.reportValue = reportValue;
        return this;
    }

    /**
     * This method finalizes the builder and produces the CLCW object.
     *
     * @return the CLCW object
     */
    public Clcw build() {
        int clcw = 0;

        clcw |= (statusField << 26);

        // COP in Effect: 1
        if(copInEffect) {
            clcw |= 0x01000000;
        }

        clcw |= (virtualChannelId << 18);

        clcw |= (reservedSpare << 16);

        if(noRfAvailableFlag) {
            clcw |= 0x00008000;
        }
        if(noBitlockFlag) {
            clcw |= 0x00004000;
        }
        if(lockoutFlag) {
            clcw |= 0x00002000;
        }
        if(waitFlag) {
            clcw |= 0x00001000;
        }
        if(retransmitFlag) {
            clcw |= 0x00000800;
        }

        clcw |= (farmBCounter << 9);

        clcw |= reportValue;

        return new Clcw(ByteBuffer.allocate(4).putInt(clcw).array());
    }
}
