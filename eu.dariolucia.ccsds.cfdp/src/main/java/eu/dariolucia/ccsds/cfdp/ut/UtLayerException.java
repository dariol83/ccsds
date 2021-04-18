package eu.dariolucia.ccsds.cfdp.ut;

public class UtLayerException extends Exception {

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
