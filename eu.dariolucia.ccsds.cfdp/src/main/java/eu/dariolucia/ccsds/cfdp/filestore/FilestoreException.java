package eu.dariolucia.ccsds.cfdp.filestore;

public class FilestoreException extends Exception {

    public FilestoreException() {
    }

    public FilestoreException(String message) {
        super(message);
    }

    public FilestoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public FilestoreException(Throwable cause) {
        super(cause);
    }
}
