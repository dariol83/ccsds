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

package eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.function;

import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.AbstractReceiverVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.transport.pdu.BitstreamData;

/**
 * Mapper used to convert frames to {@link BitstreamData}.
 *
 * @param <T> the source frame type
 */
public class VirtualChannelReceiverBitstreamDataMapper<T extends AbstractTransferFrame> extends AbstractVirtualChannelReceiverMapper<T, BitstreamData> {

    public VirtualChannelReceiverBitstreamDataMapper(AbstractReceiverVirtualChannel<T> virtualChannel) {
        super(virtualChannel);
    }

    @Override
    protected BitstreamData createEmptyData() {
        return BitstreamData.invalid();
    }

    @Override
    public void bitstreamExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data, int numBits) {
        this.data = new BitstreamData(data, numBits);
    }

}
