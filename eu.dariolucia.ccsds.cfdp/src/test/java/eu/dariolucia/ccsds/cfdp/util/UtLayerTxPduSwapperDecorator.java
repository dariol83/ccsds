/*
 *   Copyright (c) 2021 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.cfdp.util;

import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayer;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayerSubscriber;
import eu.dariolucia.ccsds.cfdp.ut.UtLayerException;

import java.util.logging.Logger;

public class UtLayerTxPduSwapperDecorator implements IUtLayer {

    private static final Logger LOG = Logger.getLogger(UtLayerTxPduSwapperDecorator.class.getName());

    private final IUtLayer delegate;
    private final TriConsumer consumptionRule;

    public UtLayerTxPduSwapperDecorator(IUtLayer delegate, TriConsumer consumptionRule) {
        this.delegate = delegate;
        this.consumptionRule = consumptionRule;
    }

    public IUtLayer getDelegate() {
        return delegate;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public void request(CfdpPdu pdu, long destinationEntityId) throws UtLayerException {
        if(consumptionRule != null) {
            consumptionRule.accept(pdu, delegate, destinationEntityId);
        } else {
            delegate.request(pdu, destinationEntityId);
        }
    }

    @Override
    public void register(IUtLayerSubscriber s) {
        delegate.register(s);
    }

    @Override
    public void deregister(IUtLayerSubscriber s) {
        delegate.deregister(s);
    }

    @Override
    public boolean getRxAvailability(long destinationId) {
        return delegate.getRxAvailability(destinationId);
    }

    @Override
    public boolean getTxAvailability(long destinationId) {
        return delegate.getTxAvailability(destinationId);
    }

    @Override
    public void dispose() {
        delegate.dispose();
    }

    public interface TriConsumer {
        void accept(CfdpPdu pdu, IUtLayer delegate, long destinationEntityId) throws UtLayerException;
    }
}
