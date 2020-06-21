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

package eu.dariolucia.ccsds.tmtc.datalink.channel.sender.util;

import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.AbstractSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.IVirtualChannelSenderOutput;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * This class is a collector of frames that can be registered to an {@link AbstractSenderVirtualChannel} object.
 * An optional filter can be provided.
 *
 * When a retrieve operation is invoked, the retrieved object are cleared from the underlying buffer.
 */
public class TransferFrameCollector<T extends AbstractTransferFrame> implements IVirtualChannelSenderOutput<T> {

    private final Predicate<T> filter;
    private final List<T> list = new ArrayList<>();

    public TransferFrameCollector(Predicate<T> filter) {
        this.filter = filter;
    }

    public TransferFrameCollector() {
        this.filter = null;
    }

    public synchronized T retrieveFirst(boolean clearList) {
        T first = !list.isEmpty() ? list.remove(0) : null;
        if(clearList) {
            list.clear();
        }
        return first;
    }

    public synchronized List<T> retrieve() {
        List<T> copy = new ArrayList<>(list);
        list.clear();
        return copy;
    }

    @Override
    public synchronized void transferFrameGenerated(AbstractSenderVirtualChannel<T> vc, T generatedFrame, int bufferedBytes) {
        if(filter == null || filter.test(generatedFrame)) {
            list.add(generatedFrame);
        }
    }
}
