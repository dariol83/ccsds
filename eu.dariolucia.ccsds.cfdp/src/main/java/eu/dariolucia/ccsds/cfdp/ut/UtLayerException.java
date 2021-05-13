package eu.dariolucia.ccsds.cfdp.ut;

import eu.dariolucia.ccsds.cfdp.common.CfdpException;

public class UtLayerException extends CfdpException {

    public UtLayerException() {
    }

    public UtLayerException(String message) {
        super(message);
    }

    public UtLayerException(String message, Throwable cause) {
        super(message, cause);
    }

    public UtLayerException(Throwable cause) {
        super(cause);
    }
}
