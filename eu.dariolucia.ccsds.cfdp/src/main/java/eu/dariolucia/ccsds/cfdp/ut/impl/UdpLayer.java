package eu.dariolucia.ccsds.cfdp.ut.impl;

import eu.dariolucia.ccsds.cfdp.mib.Mib;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayer;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayerSubscriber;
import eu.dariolucia.ccsds.cfdp.ut.UtLayerException;

// TODO implement
public class UdpLayer implements IUtLayer {

    private final Mib mib;

    public UdpLayer(Mib mib, int localUdpPort) {
        this.mib = mib;
    }

    @Override
    public String getName() {
        return "UDP";
    }

    @Override
    public void request(CfdpPdu pdu, long destinationEntityId) throws UtLayerException {
        // If the destination is not available for TX, exception

        // For each destination ID, we have a specific socket
        // If the socket is not there, create it

        // Build the datagram and send it to the socket
    }

    @Override
    public void register(IUtLayerSubscriber s) {
        // Refactor by introducing a parent abstract class
    }

    @Override
    public void deregister(IUtLayerSubscriber s) {
        // Refactor by introducing a parent abstract class
    }

    @Override
    public void dispose() {
        // Close all the sockets
    }

    public void setTxAvailability(boolean available, long... entityIds) {
        // Mark the entities as unavailable for TX
        // Propagate
    }

    public void setRxAvailability(boolean available, long... entityIds) {
        // Do nothing
    }

    public void activate() {
        // setRxAvailability for all entities to true ??
        // setTxAvailability for all entities to false ??
        // open server socket
        // start processing thread
    }

    public void deactivate() {
        // setRxAvailability for all entities to false ??
        // setTxAvailability for all entities to false ??
        // close server socket
        // stop processing thread
        // close and cleanup all client sockets
    }

}
