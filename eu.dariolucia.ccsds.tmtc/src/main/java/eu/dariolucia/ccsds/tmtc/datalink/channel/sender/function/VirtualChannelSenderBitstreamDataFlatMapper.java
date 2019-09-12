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

package eu.dariolucia.ccsds.tmtc.datalink.channel.sender.function;

import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.AbstractSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.transport.pdu.BitstreamData;

/**
 * Flat mapper used to convert {@link BitstreamData}.
 *
 * @param <T> the destination frame type
 */
public class VirtualChannelSenderBitstreamDataFlatMapper<T extends AbstractTransferFrame> extends AbstractVirtualChannelSenderFlatMapper<T, BitstreamData> {

    public VirtualChannelSenderBitstreamDataFlatMapper(AbstractSenderVirtualChannel<T> sender) {
        super(sender);
    }

    @Override
    protected void doDispatch(BitstreamData data) {
        super.sender.dispatch(data);
    }
}
