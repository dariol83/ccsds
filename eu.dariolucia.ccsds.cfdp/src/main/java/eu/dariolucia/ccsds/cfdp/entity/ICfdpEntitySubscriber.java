package eu.dariolucia.ccsds.cfdp.entity;

import eu.dariolucia.ccsds.cfdp.entity.indication.ICfdpIndication;
import eu.dariolucia.ccsds.cfdp.entity.internal.CfdpEntity;

public interface ICfdpEntitySubscriber {

    void indication(CfdpEntity emitter, ICfdpIndication indication);
}
