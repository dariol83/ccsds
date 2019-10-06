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

package eu.dariolucia.ccsds.tmtc.datalink.builder;

import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.algorithm.Crc16Algorithm;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * This class allows to build a CCSDS TC frame using a typical Builder pattern. Once a frame is built, the builder should
 * not be re-used, as the internals (such as the payload data) are not cleaned up upon build.
 *
 * This class is not thread-safe.
 */
public class TcTransferFrameBuilder implements ITransferFrameBuilder<TcTransferFrame> {

    /**
     * This static method allows to compute the maximum length of the user data field of a TC frame, based on the frame characteristics.
     * It has to be noted that the security data is part of length returned by this method.
     *
     * @param fecfPresent true if the FECF is present, false otherwise
     * @return size of the user data field in bytes (including the security fields, if present)
     */
    public static int computeMaxUserDataLength(boolean fecfPresent) {
        return TcTransferFrame.MAX_TC_FRAME_LENGTH - TcTransferFrame.TC_PRIMARY_HEADER_LENGTH - (fecfPresent ? 2 : 0);
    }

    /**
     * This method creates an instance of this class.
     *
     * @param fecfPresent true if the FECF is present, false otherwise
     * @return the builder object
     */
    public static TcTransferFrameBuilder create(boolean fecfPresent) {
        return new TcTransferFrameBuilder(fecfPresent);
    }

    private final boolean fecfPresent;

    private boolean bypassFlag;
    private boolean controlCommandFlag;

    private int spacecraftId;
    private int virtualChannelId;
    private int frameSequenceNumber;

    private int freeUserDataLength;

    private int mapId;
    private boolean segmented;
    private TcTransferFrame.SequenceFlagType sequenceFlag;

    private byte[] securityHeader;
    private byte[] securityTrailer;

    private List<byte[]> payloadUnits = new LinkedList<>();

    private TcTransferFrameBuilder(boolean fecfPresent) {
        this.fecfPresent = fecfPresent;
        this.freeUserDataLength = TcTransferFrame.MAX_TC_FRAME_LENGTH - TcTransferFrame.TC_PRIMARY_HEADER_LENGTH - (fecfPresent ? 2 : 0);
    }

    public TcTransferFrameBuilder setBypassFlag(boolean bypassFlag) {
        this.bypassFlag = bypassFlag;
        return this;
    }

    public TcTransferFrameBuilder setControlCommandFlag(boolean controlCommandFlag) {
        this.controlCommandFlag = controlCommandFlag;
        return this;
    }

    public TcTransferFrameBuilder setSpacecraftId(int spacecraftId) {
        if(spacecraftId < 0 || spacecraftId > 1023) {
            throw new IllegalArgumentException("Spacecraft ID must be 0 <= SC ID <= 1023, actual " + spacecraftId);
        }

        this.spacecraftId = spacecraftId;
        return this;
    }

    public TcTransferFrameBuilder setVirtualChannelId(int virtualChannelId) {
        if(virtualChannelId < 0 || virtualChannelId > 63) {
            throw new IllegalArgumentException("Virtual Channel ID must be 0 <= VC ID <= 63, actual " + virtualChannelId);
        }

        this.virtualChannelId = virtualChannelId;
        return this;
    }

    public TcTransferFrameBuilder setFrameSequenceNumber(int frameSequenceNumber) {
        if(frameSequenceNumber < 0 || frameSequenceNumber > 255) {
            throw new IllegalArgumentException("Frame Sequence Number must be 0 <= seq. num. <= 255, actual " + frameSequenceNumber);
        }

        this.frameSequenceNumber = frameSequenceNumber;
        return this;
    }

    public TcTransferFrameBuilder setSegment(TcTransferFrame.SequenceFlagType sequenceFlag, int mapId) {
        if(mapId < 0 || mapId > 63) {
            throw new IllegalArgumentException("Map ID must be 0 <= Map ID <= 64, actual " + mapId);
        }
        if(isFull()) {
            throw new IllegalArgumentException("TC Frame already full");
        }
        if(!this.segmented) {
            this.segmented = true;
            this.freeUserDataLength -= 1;
        }
        this.sequenceFlag = sequenceFlag;
        this.mapId = (byte) mapId;
        return this;
    }

    public TcTransferFrameBuilder setUnlockControlCommand() {
        // CCSDS 232.0-B-3, 4.1.3.3.2
        this.addData(new byte[] { 0x00 });
        return this;
    }

    public TcTransferFrameBuilder setSetVrControlCommand(int frameSequenceNumber) {
        if(frameSequenceNumber < 0 || frameSequenceNumber > 255) {
            throw new IllegalArgumentException("Set V(R) Sequence Number must be 0 <= V(R) <= 255, actual " + frameSequenceNumber);
        }

        // CCSDS 232.0-B-3, 4.1.3.3.3
        this.addData(new byte[] { (byte) 0x82, 0x00, (byte) frameSequenceNumber });
        return this;
    }

    public TcTransferFrameBuilder setSecurity(byte[] header, byte[] trailer) {
        if(header == null && trailer == null) {
            // Do not do anything
            return this;
        }
        if(isFull()) {
            throw new IllegalArgumentException("TC Frame already full");
        }
        int securityDataSize = (header == null ? 0 : header.length) + (trailer == null ? 0 : trailer.length);
        if(getFreeUserDataLength() < securityDataSize) {
            throw new IllegalArgumentException("TC Frame cannot accomodate additional "
                    + securityDataSize + " bytes, remaining space is " + getFreeUserDataLength() + " bytes");
        }
        this.securityHeader = header;
        this.securityTrailer = trailer;
        this.freeUserDataLength -= securityDataSize;

        return this;
    }

    public int addData(byte[] b, int offset, int length) {
        // Compute if you can add the requested amount
        int dataToBeWritten = Math.min(freeUserDataLength, length);
        int notWrittenData = freeUserDataLength < length ? length - freeUserDataLength : 0;
        if(dataToBeWritten > 0) {
            this.payloadUnits.add(Arrays.copyOfRange(b, offset, offset + dataToBeWritten));
            freeUserDataLength -= dataToBeWritten;
        }
        return notWrittenData;
    }

    public int addData(byte[] b) {
        return addData(b, 0, b.length);
    }

    @Override
    public int getFreeUserDataLength() {
        return this.freeUserDataLength;
    }

    public boolean isFull() {
        return this.freeUserDataLength == 0;
    }

    @Override
    public TcTransferFrame build() {
        int payloadDataLength = this.payloadUnits.stream().map(o -> o.length).reduce(0, (a,b) -> a + b);
        int frameLength = TcTransferFrame.TC_PRIMARY_HEADER_LENGTH + payloadDataLength + (fecfPresent ? 2 : 0) + (this.segmented ? 1 : 0);
        if(securityHeader != null) {
            frameLength += securityHeader.length;
        }
        if(securityTrailer != null) {
            frameLength += securityTrailer.length;
        }

        ByteBuffer bb = ByteBuffer.allocate(frameLength);

        short first2octets = 0;

        if(bypassFlag) {
            first2octets |= 0x2000;
        }

        if(controlCommandFlag) {
            first2octets |= 0x1000;
        }

        first2octets |= (short) spacecraftId;

        bb.putShort(first2octets);

        short next2octets = 0;

        next2octets |= (short) (virtualChannelId << 10);

        next2octets |= (short) (frameLength - 1);

        bb.putShort(next2octets);

        bb.put((byte) frameSequenceNumber);

        if(this.segmented) {
            int toWrite = 0;
            toWrite |= this.sequenceFlag.ordinal();
            toWrite <<= 6;
            toWrite |= this.mapId;
            bb.put((byte) toWrite);
        }

        // If security header, write it
        if(securityHeader != null) {
            bb.put(securityHeader);
        }

        // Write the user data
        for(byte[] pu : this.payloadUnits) {
            bb.put(pu);
        }

        // If security trailer, write it
        if(securityTrailer != null) {
            bb.put(securityTrailer);
        }

        byte[] encodedFrame;

        // Compute and write the FECF (if present, 2 bytes)
        if(this.fecfPresent) {
            bb.put((byte) 0x00);
            bb.put((byte) 0x00);
            encodedFrame = bb.array();
            short crc = Crc16Algorithm.getCrc16(encodedFrame, 0, encodedFrame.length - 2);
            encodedFrame[encodedFrame.length - 2] = (byte) (crc >> 8);
            encodedFrame[encodedFrame.length - 1] = (byte) (crc);
        } else {
            encodedFrame = bb.array();
        }

        // Return the frame
        return new TcTransferFrame(encodedFrame, segmented, fecfPresent,
                (securityHeader != null ? securityHeader.length : 0), (securityTrailer != null ? securityTrailer.length : 0));
    }
}
