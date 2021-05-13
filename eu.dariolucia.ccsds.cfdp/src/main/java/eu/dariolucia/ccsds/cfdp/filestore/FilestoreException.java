package eu.dariolucia.ccsds.cfdp.filestore;

import eu.dariolucia.ccsds.cfdp.common.CfdpException;

public class FilestoreException extends CfdpException {

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
