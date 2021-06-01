package eu.dariolucia.ccsds.cfdp.entity.indication;

/**
 * The EntityDisposed.indication primitive is not part of the standard and it is introduced by this implementation
 * to notify subscribers that a given CFDP entity was disposed. As a consequence of the disposal, all active transactions
 * were cancelled.
 */
public class EntityDisposedIndication implements ICfdpIndication {

    @Override
    public String toString() {
        return "EntityDisposedIndication {}";
    }
}
