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

package eu.dariolucia.ccsds.tmtc.datalink.pdu;

import eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm;
import eu.dariolucia.ccsds.tmtc.util.AnnotatedObject;

import java.nio.ByteBuffer;
import java.util.Arrays;

public abstract class AbstractTransferFrame extends AnnotatedObject {

    protected byte[] frame;
    protected boolean fecfPresent; // if present, 2 octets
    protected boolean ocfPresent; // if present, 4 octets

    // Primary header fields
    protected short transferFrameVersionNumber;
    protected short spacecraftId;
    protected short virtualChannelId;
    protected int virtualChannelFrameCount;

    // Data field start offset from the beginning of the frame
    protected short dataFieldStart;

    // Operational control field: valid values only if ocdPresent == true
    protected short ocfStart;

    // FECF result: valid value only if fecfPresent == true
    protected boolean valid;

    public AbstractTransferFrame(byte[] frame, boolean fecfPresent) {
        this.frame = frame;
        this.fecfPresent = fecfPresent;
    }

    protected abstract void decode();

    /**
     * This method returns the direct reference to the frame byte array. Any change to the byte array is not reflected in the
     * state of the {@link AbstractTransferFrame} object. This method is provided to allow zero-copy operations on the underlying
     * storage array.
     *
     * @return the frame byte array (direct reference)
     */
    public byte[] getFrame() {
        return this.frame;
    }

    /**
     * Utility method that provides a copy of the frame byte array.
     *
     * @return the frame byte array (copy)
     */
    public byte[] getFrameCopy() {
        return Arrays.copyOfRange(this.frame, 0, this.frame.length);
    }

    @Override
    public int getLength() {
        return this.frame.length;
    }

    public boolean isFecfPresent() {
        return this.fecfPresent;
    }

    public short getFecf() {
        if (fecfPresent) {
            return ByteBuffer.wrap(frame, frame.length - 2, 2).getShort();
        } else {
            throw new IllegalStateException("FECF not present");
        }
    }

    public short getTransferFrameVersionNumber() {
        return transferFrameVersionNumber;
    }

    public short getSpacecraftId() {
        return spacecraftId;
    }

    public short getVirtualChannelId() {
        return virtualChannelId;
    }

    public int getVirtualChannelFrameCount() {
        return virtualChannelFrameCount;
    }

    public short getDataFieldStart() {
        return dataFieldStart;
    }

    public short getOcfStart() {
        return ocfStart;
    }

    public boolean isOcfPresent() {
        return ocfPresent;
    }

    public boolean isValid() {
        return valid;
    }

    public abstract boolean isIdleFrame();

    /**
     * This method returns a copy of the OCF, if present. If not present, it throws an {@link IllegalStateException} exception.
     *
     * @return the operational control field (copy)
     * @throws IllegalStateException if there is no OCF
     */
    public byte[] getOcfCopy() {
        if (ocfPresent) {
            return Arrays.copyOfRange(frame, ocfStart, ocfStart + 4);
        } else {
            throw new IllegalStateException("Cannot return copy of OCF, OCF not present");
        }
    }

    /**
     * This method returns a copy of the frame data field.
     *
     * @return the frame data field (copy)
     */
    public byte[] getDataFieldCopy() {
        // Start of the data field already computed and stored in dataFieldStart.
        // End of the data field depends on frame.length, OCF presence, FECF presence.
        return Arrays.copyOfRange(frame, dataFieldStart, dataFieldStart + getDataFieldLength());
    }

    /**
     * This method returns the length of the data field.
     *
     * @return the length of the data field (without CLCW and FECF, if present)
     */
    public int getDataFieldLength() {
        return frame.length - dataFieldStart - (ocfPresent ? 4 : 0) - (fecfPresent ? 2 : 0);
    }
}
