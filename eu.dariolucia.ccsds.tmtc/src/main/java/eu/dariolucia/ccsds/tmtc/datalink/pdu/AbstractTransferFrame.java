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

import eu.dariolucia.ccsds.tmtc.util.AnnotatedObject;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * This class represents an abstraction of a transfer frame. As such, it contains the information and properties
 * which are common to the type of transfer frames supported in this library: {@link AosTransferFrame}, {@link TmTransferFrame}
 * and {@link TcTransferFrame}.
 *
 * This class inherits from the {@link AnnotatedObject} class, therefore it is possible to attach custom properties
 * and values without having to extend the {@link AbstractTransferFrame} subclasses.
 *
 * Decoding is implemented in the constructor of the subclasses: if decoding cannot be performed, it is expected that
 * subclasses raise an IllegalArgumentException in the constructor.
 */
public abstract class AbstractTransferFrame extends AnnotatedObject {

    /**
     * The decoded transfer frame.
     */
    protected final byte[] frame;
    /**
     * Frame error control field presence flag.
     */
    protected final boolean fecfPresent; // if present, 2 octets
    /**
     * Operational control field presence flag.
     */
    protected boolean ocfPresent; // if present, 4 octets

    // Primary header fields
    /**
     * Transfer frame version number.
     */
    protected short transferFrameVersionNumber;
    /**
     * Spacecraft id.
     */
    protected short spacecraftId;
    /**
     * Virtual channel id.
     */
    protected short virtualChannelId;
    /**
     * Frame count on the virtual channel, this frame belongs to.
     */
    protected int virtualChannelFrameCount;

    /**
     * Data field start offset from the beginning of the frame.
     */
    protected short dataFieldStart;

    /**
     * Operational control field start offset from the beginning of the frame: valid values only if ocdPresent is true.
     */
    protected short ocfStart;

    /**
     * FECF result: valid value only if fecfPresent is true. If there is no FECF, then the frame is considered valid.
     */
    protected boolean valid;

    /**
     * Constructor of the transfer frame.
     *
     * @param frame the frame data
     * @param fecfPresent true if the FECF is present, false otherwise
     */
    protected AbstractTransferFrame(byte[] frame, boolean fecfPresent) {
        this.frame = frame;
        this.fecfPresent = fecfPresent;
    }

    /**
     * This method returns the direct reference to the frame byte array. Any change to the byte array is not reflected in the
     * state of the {@link AbstractTransferFrame} object. This method is provided to allow zero-copy operations on the underlying
     * storage array. If changes are expected to the returned byte array, the method getFrameCopy() should be used instead.
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

    /**
     * This method returns the length of the frame. Implementation required by the {@link AnnotatedObject} class.
     *
     * @return the length of the transfer frame in bytes
     */
    @Override
    public int getLength() {
        return this.frame.length;
    }

    /**
     * This method returns whether the FECF is present or not.
     *
     * @return true if the FECF is present, otherwise false
     */
    public boolean isFecfPresent() {
        return this.fecfPresent;
    }

    /**
     * This method returns the value of the FECF as short.
     *
     * @return the FECF value as short, or an exception is thrown if not present
     * @throws IllegalStateException if the FECF is not present
     */
    public short getFecf() {
        if (fecfPresent) {
            return ByteBuffer.wrap(frame, frame.length - 2, 2).getShort();
        } else {
            throw new IllegalStateException("FECF not present");
        }
    }

    /**
     * This method returns the transfer frame version number.
     *
     * @return the transfer frame version number
     */
    public short getTransferFrameVersionNumber() {
        return transferFrameVersionNumber;
    }

    /**
     * This method returns the spacecraft id.
     *
     * @return the spacecraft id
     */
    public short getSpacecraftId() {
        return spacecraftId;
    }

    /**
     * This method returns the virtual channel id.
     *
     * @return the virtual channel id
     */
    public short getVirtualChannelId() {
        return virtualChannelId;
    }

    /**
     * This method returns the virtual channel frame count.
     *
     * @return the virtual channel frame count
     */
    public int getVirtualChannelFrameCount() {
        return virtualChannelFrameCount;
    }

    /**
     * This method returns the index of the byte from which the transfer frame data field starts. The offset
     * is computed from the beginning of the frame.
     *
     * @return the start index of the frame data field
     */
    public short getDataFieldStart() {
        return dataFieldStart;
    }

    /**
     * This method returns the index of the byte from which the OCF starts. The offset
     * is computed from the beginning of the frame.
     *
     * @return the start index of the OCF
     */
    public short getOcfStart() {
        return ocfStart;
    }

    /**
     * This method returns the presence of the OCF.
     *
     * @return true if the OCF is present, otherwise false
     */
    public boolean isOcfPresent() {
        return ocfPresent;
    }

    /**
     * This method returns the validity of the frame: if the FECF is present, then a frame is valid if can be decoded
     * and the FECF is OK. If the FECF is not present, then this field is always set to true if the frame can be correctly
     * decoded.
     *
     * @return true if the frame is correct, otherwise false
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * This method returns true if the frame is an idle frame, false otherwise.
     *
     * @return true if the frame is an idle frame, false otherwise
     */
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
