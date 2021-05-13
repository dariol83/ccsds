package eu.dariolucia.ccsds.cfdp.entity.segmenters;

import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import eu.dariolucia.ccsds.cfdp.filestore.IVirtualFilestore;
import eu.dariolucia.ccsds.cfdp.mib.Mib;

public interface ICfdpSegmentationStrategy {

    /**
     * This method returns whether this strategy can support the segmentation of the indicated file in the provider filestore.
     *
     * @param mib the MIB of the CFDP entity
     * @param filestore the filestore that has the file
     * @param fullPath the full path of the file
     * @return true if segmentation is supported, false otherwise
     */
    boolean support(Mib mib, IVirtualFilestore filestore, String fullPath);

    /**
     * This method returns a new object that can provide segments using an iterator-like fashion.
     *
     * @param mib the MIB of the CFDP entity
     * @param filestore the filestore that has the file
     * @param fullPath the full path of the file
     * @param destinationEntityId the ID of the destination entity
     * @return the {@link ICfdpFileSegmenter} related to the provided file
     * @throws FilestoreException case of problems when retrieving the data from the filestore
     */
    ICfdpFileSegmenter newSegmenter(Mib mib, IVirtualFilestore filestore, String fullPath, long destinationEntityId) throws FilestoreException;
}
