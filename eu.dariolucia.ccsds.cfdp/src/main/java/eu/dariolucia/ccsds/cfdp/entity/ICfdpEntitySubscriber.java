package eu.dariolucia.ccsds.cfdp.entity;

import eu.dariolucia.ccsds.cfdp.entity.indication.ICfdpIndication;

public interface ICfdpEntitySubscriber {

    void indication(CfdpEntity emitter, ICfdpIndication indication);
}
