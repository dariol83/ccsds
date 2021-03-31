package eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs;

public interface TLV {

    int getType();

    int getLength();

    byte[] encode(boolean withTypeLength);
}
