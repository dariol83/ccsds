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
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;

public class TcReceiverVirtualChannel extends AbstractReceiverVirtualChannel<TcTransferFrame> {

    public TcReceiverVirtualChannel(int virtualChannelId, VirtualChannelAccessMode mode, boolean throwExceptionOnVcViolation) {
        super(virtualChannelId, mode, throwExceptionOnVcViolation);
    }

    @Override
    protected int getVcFrameCounterModulo() {
        return 256;
    }

    @Override
    protected boolean frameContainsNoStartOfPacket(TcTransferFrame frame) {
        return false;
    }

    @Override
    protected int retrieveFirstHeaderPointer(TcTransferFrame frame) {
        return (frame.isSegmented() ? 1 : 0);
    }

    @Override
    protected boolean isGapDetectionApplicable(TcTransferFrame frame) {
        return false;
    }

    @Override
    protected int retrievePacketDataFieldLength(TcTransferFrame frame) {
        return frame.getDataFieldLength();
    }

    @Override
    protected int retrievePacketDataFieldStart(TcTransferFrame frame) {
        return frame.getDataFieldStart();
    }

}
