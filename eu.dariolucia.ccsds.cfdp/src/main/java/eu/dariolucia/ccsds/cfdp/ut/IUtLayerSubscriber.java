package eu.dariolucia.ccsds.cfdp.ut;

import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;

public interface IUtLayerSubscriber {

    void indication(IUtLayer layer, CfdpPdu pdu);

    void startTxPeriod(IUtLayer layer, long entityId);

    void endTxPeriod(IUtLayer layer, long entityId);

    void startRxPeriod(IUtLayer layer, long entityId);

    void endRxPeriod(IUtLayer layer, long entityId);
}
