package eu.dariolucia.ccsds.cfdp.ut;

import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;

public interface IUtLayer {

    String getName();

    void request(CfdpPdu pdu) throws UtLayerException;

    void register(IUtLayerSubscriber s);

    void deregister(IUtLayerSubscriber s);

    void dispose();
}
