/*
 *   Copyright (c) 2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package eu.dariolucia.ccsds.tmtc.cop1.fop.util;

import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.TcSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.util.TransferFrameCollector;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;

import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * This class is a collector of BC frames that wraps a {@link TcSenderVirtualChannel} object. This object can be used as constructor argument when building a {@link eu.dariolucia.ccsds.tmtc.cop1.fop.FopEngine}
 * instance, if you want to delegate the creation of the BC frames directly to the provided TC-sender virtual channel.
 */
public class BcFrameCollector extends TransferFrameCollector<TcTransferFrame> implements Supplier<TcTransferFrame>, IntFunction<TcTransferFrame> {

    private final TcSenderVirtualChannel virtualChannel;

    public BcFrameCollector(TcSenderVirtualChannel virtualChannel) {
        super(o -> o.getFrameType() == TcTransferFrame.FrameType.BC);
        this.virtualChannel = virtualChannel;
        this.virtualChannel.register(this);
    }

    @Override
    public synchronized TcTransferFrame apply(int setVr) {
        retrieve();
        this.virtualChannel.dispatchSetVr(setVr);
        return retrieveFirst(true);
    }

    @Override
    public synchronized TcTransferFrame get() {
        retrieve();
        this.virtualChannel.dispatchUnlock();
        return retrieveFirst(true);
    }
}
