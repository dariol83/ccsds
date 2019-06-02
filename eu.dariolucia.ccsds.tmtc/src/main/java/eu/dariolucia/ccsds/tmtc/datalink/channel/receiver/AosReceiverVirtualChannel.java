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

package eu.dariolucia.ccsds.tmtc.datalink.channel.receiver;

import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AosTransferFrame;

import java.util.Arrays;

public class AosReceiverVirtualChannel extends AbstractReceiverVirtualChannel<AosTransferFrame> {

    public AosReceiverVirtualChannel(int virtualChannelId, VirtualChannelAccessMode mode, boolean throwExceptionOnVcViolation) {
        super(virtualChannelId, mode, throwExceptionOnVcViolation);
    }

    @Override
    protected int getVcFrameCounterModulo() {
        return 16777216;
    }

    @Override
    protected boolean frameContainsNoStartOfPacket(AosTransferFrame frame) {
        return frame.isNoStartPacket();
    }

    @Override
    protected int retrieveFirstHeaderPointer(AosTransferFrame frame) {
        return frame.getFirstHeaderPointer();
    }

    @Override
    protected int retrievePacketDataFieldLength(AosTransferFrame frame) {
        return frame.getPacketZoneLength();
    }

    @Override
    protected int retrievePacketDataFieldStart(AosTransferFrame frame) {
        return frame.getPacketZoneStart();
    }

    @Override
    protected boolean isGapDetectionApplicable(AosTransferFrame frame) {
        return frame.isVirtualChannelFrameCountUsageFlag();
    }

    @Override
    protected void extractPacket(AosTransferFrame frame, boolean gapDetected) {
        if(frame.getUserDataType() != AosTransferFrame.UserDataType.M_PDU) {
            throw new IllegalArgumentException("The provided frame is not marked as M-PDU, space packets cannot be extracted");
        }
        super.extractPacket(frame, gapDetected);
    }

    @Override
    protected void extractBitstream(AosTransferFrame frame, boolean gapDetected) {
        if(frame.getUserDataType() != AosTransferFrame.UserDataType.B_PDU) {
            throw new IllegalArgumentException("The provided frame is not marked as B-PDU, bitstream data cannot be extracted");
        }
        int startIdx = frame.getBitstreamDataZoneStart();
        int bytesToRead = frame.getBitstreamDataPointer() / 8;
        if(frame.getBitstreamDataPointer() % 8 != 0) {
            ++bytesToRead;
        }
        byte[] extracted = Arrays.copyOfRange(frame.getFrame(), startIdx, startIdx + bytesToRead);
        notifyBitstreamExtracted(frame, extracted, frame.getBitstreamDataPointer());
    }
}
