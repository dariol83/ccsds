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

package eu.dariolucia.ccsds.tmtc.ocf.pdu;

import java.util.Arrays;

/**
 * This class handles the Communications Link Control Word (CLCW) data, as per CCSDS 232.0-B-3.
 */
public class Clcw extends AbstractOcf {

    public enum CopEffectType {
        NONE,
        COP1,
        RESERVED2,
        RESERVED3
    }

    private final byte versionNumber;
    private final byte statusField;
    private final byte reservedSpare;
    private final CopEffectType copInEffect;
    private final short virtualChannelId;

    private final boolean noRfAvailableFlag;
    private final boolean noBitlockFlag;
    private final boolean lockoutFlag;
    private final boolean waitFlag;
    private final boolean retransmitFlag;

    private final byte farmBCounter;

    private final short reportValue;

    public Clcw(byte[] ocf) {
        super(ocf);
        // 4.2.1.2.2
        if(!clcw) {
            throw new IllegalArgumentException("CLCW Type: expected 0, actual 1");
        }

        this.versionNumber = (byte) ((ocf[0] & 0x60) >> 5);
        // 4.2.1.3.2
        if(versionNumber != 0) {
            throw new IllegalArgumentException("CLCW Version Number: expected 0, actual " + this.versionNumber);
        }

        this.statusField = (byte) ((ocf[0] & 0x1C) >> 2);

        int copInEffectFlag = (ocf[0] & 0x03);

        // 4.2.1.5.2: it should be set to 1, check relaxed
        this.copInEffect = CopEffectType.values()[copInEffectFlag];

        this.virtualChannelId = (short) ((ocf[1] & 0xFC) >> 2);

        // 4.2.1.7.2: it should be set to 00, check relaxed
        this.reservedSpare = (byte) ((ocf[1]) & 0x03);

        this.noRfAvailableFlag = (ocf[2] & 0x80) != 0;
        this.noBitlockFlag = (ocf[2] & 0x40) != 0;
        this.lockoutFlag = (ocf[2] & 0x20) != 0;
        this.waitFlag = (ocf[2] & 0x10) != 0;
        this.retransmitFlag = (ocf[2] & 0x08) != 0;

        this.farmBCounter = (byte) ((ocf[2] & 0x06) >> 1);

        this.reportValue = (short) Byte.toUnsignedInt(ocf[3]);
    }

    public byte getVersionNumber() {
        return versionNumber;
    }

    public byte getStatusField() {
        return statusField;
    }

    public CopEffectType getCopInEffect() {
        return copInEffect;
    }

    public short getVirtualChannelId() {
        return virtualChannelId;
    }

    public boolean isNoRfAvailableFlag() {
        return noRfAvailableFlag;
    }

    public boolean isNoBitlockFlag() {
        return noBitlockFlag;
    }

    public boolean isLockoutFlag() {
        return lockoutFlag;
    }

    public boolean isWaitFlag() {
        return waitFlag;
    }

    public boolean isRetransmitFlag() {
        return retransmitFlag;
    }

    public byte getFarmBCounter() {
        return farmBCounter;
    }

    public short getReportValue() {
        return reportValue;
    }

    public byte getReservedSpare() {
        return reservedSpare;
    }

    @Override
    public String toString() {
        return "Clcw{" +
                "versionNumber=" + versionNumber +
                ", statusField=" + statusField +
                ", copInEffect=" + copInEffect +
                ", virtualChannelId=" + virtualChannelId +
                ", reservedSpare=" + reservedSpare +
                ", noRfAvailableFlag=" + noRfAvailableFlag +
                ", noBitlockFlag=" + noBitlockFlag +
                ", lockoutFlag=" + lockoutFlag +
                ", waitFlag=" + waitFlag +
                ", retransmitFlag=" + retransmitFlag +
                ", farmBCounter=" + farmBCounter +
                ", reportValue=" + reportValue +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return ((Clcw) o).ocf[0] == this.ocf[0] &&
                ((Clcw) o).ocf[1] == this.ocf[1] &&
                ((Clcw) o).ocf[2] == this.ocf[2] &&
                ((Clcw) o).ocf[3] == this.ocf[3];
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(super.ocf);
    }
}
