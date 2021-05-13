package eu.dariolucia.ccsds.cfdp.common;

public class CfdpException extends Exception {

    public CfdpException() {
    }

    public CfdpException(String message) {
        super(message);
    }

    public CfdpException(String message, Throwable cause) {
        super(message, cause);
    }

    public CfdpException(Throwable cause) {
        super(cause);
    }

    public CfdpException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
