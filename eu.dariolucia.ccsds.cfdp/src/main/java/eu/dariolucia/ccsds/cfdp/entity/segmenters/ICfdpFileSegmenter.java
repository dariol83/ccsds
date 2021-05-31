package eu.dariolucia.ccsds.cfdp.entity.segmenters;

import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;

public interface ICfdpFileSegmenter {

    FileSegment nextSegment() throws FilestoreException;

    void close();

}
