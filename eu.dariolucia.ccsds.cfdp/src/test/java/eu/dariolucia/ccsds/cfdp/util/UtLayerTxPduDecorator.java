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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;

public class UtLayerTxPduDecorator implements IUtLayer {

    private static final Logger LOG = Logger.getLogger(UtLayerTxPduDecorator.class.getName());

    private final IUtLayer delegate;
    private final List<CfdpPdu> txPdus = new LinkedList<>();
    private final List<Function<CfdpPdu, Boolean>> pduDiscardRules = new LinkedList<>();

    public UtLayerTxPduDecorator(IUtLayer delegate, Function<CfdpPdu, Boolean>... discardRules) {
        this.delegate = delegate;
        this.pduDiscardRules.addAll(Arrays.asList(discardRules));
    }

    public List<CfdpPdu> getTxPdus() {
        synchronized (this) {
            return txPdus;
        }
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
        synchronized (this) {
            txPdus.add(pdu);
        }
        for(Function<CfdpPdu, Boolean> r : pduDiscardRules) {
            if(r.apply(pdu)) {
                // Discard
                LOG.info("PDU " + pdu + " discarded according to rule " + r);
                return;
            }
        }
        delegate.request(pdu, destinationEntityId);
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

    public static Function<CfdpPdu, Boolean> rule(String name, Function<CfdpPdu, Boolean> rule) {
        return new Function<>() {
            @Override
            public Boolean apply(CfdpPdu cfdpPdu) {
                return rule.apply(cfdpPdu);
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }
}
