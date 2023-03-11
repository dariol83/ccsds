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
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;

/**
 * Virtual channel for the reception and processing of TM frames.
 */
public class TmReceiverVirtualChannel extends AbstractReceiverVirtualChannel<TmTransferFrame> {

    public TmReceiverVirtualChannel(int virtualChannelId, VirtualChannelAccessMode mode, boolean throwExceptionOnVcViolation) {
        super(virtualChannelId, mode, throwExceptionOnVcViolation);
    }

    @Override
    protected int getVcFrameCounterModulo() {
        return 256;
    }

    @Override
    protected boolean frameContainsNoStartOfPacket(TmTransferFrame frame) {
        return frame.isNoStartPacket();
    }

    @Override
    protected int retrieveFirstHeaderPointer(TmTransferFrame frame) {
        return frame.getFirstHeaderPointer();
    }

    @Override
    protected boolean isGapDetectionApplicable(TmTransferFrame frame) {
        return true;
    }

    @Override
    protected int retrievePacketDataFieldLength(TmTransferFrame frame) {
        return frame.getDataFieldLength();
    }

    @Override
    protected int retrievePacketDataFieldStart(TmTransferFrame frame) {
        return frame.getDataFieldStart();
    }

    @Override
    protected void extractPacket(TmTransferFrame frame, boolean gapDetected, int missingBytes) {
        // If the frame sync flag is set (1) then the pdu cannot be extracted and an exception is thrown
        if(frame.isSynchronisationFlag()) {
            throw new IllegalArgumentException("The provided frame has the synchronization flag set, space packets cannot be extracted");
        }
        super.extractPacket(frame, gapDetected, missingBytes);
    }

    @Override
    protected void extractEncapsulationPacket(TmTransferFrame frame, boolean gapDetected, int missingBytes) {
        // If the frame sync flag is set (1) then the pdu cannot be extracted and an exception is thrown
        if(frame.isSynchronisationFlag()) {
            throw new IllegalArgumentException("The provided frame has the synchronization flag set, encapsulation packets cannot be extracted");
        }
        super.extractEncapsulationPacket(frame, gapDetected, missingBytes);
    }
}
