package eu.dariolucia.ccsds.cfdp.entity.segmenters;

import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;

public interface ICfdpFileSegmenter {

    /**
     * This method returns a segment delivering data or EOF indication. In case of problem, it throws an exception.
     *
     * @return a {@link FileSegment} with EOF or data. It shall never return null.
     * @throws FilestoreException in case of filestore-related issues.
     */
    FileSegment nextSegment() throws FilestoreException;

    /**
     * Close the segmented and releases all the underlying resources.
     */
    void close();

}
