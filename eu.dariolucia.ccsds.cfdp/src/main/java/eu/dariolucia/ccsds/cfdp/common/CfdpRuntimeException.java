package eu.dariolucia.ccsds.cfdp.common;

public class CfdpRuntimeException extends RuntimeException {

    public CfdpRuntimeException() {
    }

    public CfdpRuntimeException(String message) {
        super(message);
    }

    public CfdpRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public CfdpRuntimeException(Throwable cause) {
        super(cause);
    }

    public CfdpRuntimeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
