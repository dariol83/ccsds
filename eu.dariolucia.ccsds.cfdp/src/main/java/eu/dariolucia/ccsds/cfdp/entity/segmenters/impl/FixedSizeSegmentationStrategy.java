package eu.dariolucia.ccsds.cfdp.entity.segmenters.impl;

import eu.dariolucia.ccsds.cfdp.entity.segmenters.ICfdpFileSegmenter;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.ICfdpSegmentationStrategy;
import eu.dariolucia.ccsds.cfdp.filestore.IVirtualFilestore;
import eu.dariolucia.ccsds.cfdp.mib.Mib;

public class FixedSizeSegmentationStrategy implements ICfdpSegmentationStrategy {

    @Override
    public boolean support(Mib mib, IVirtualFilestore filestore, String fullPath) {
        // All files can be segmented
        return true;
    }

    @Override
    public ICfdpFileSegmenter newSegmenter(Mib mib, IVirtualFilestore filestore, String fullPath, long destinationEntityId) {
        return new FixedSizeSegmenter(filestore, fullPath, mib.getRemoteEntityById(destinationEntityId).getMaximumFileSegmentLength());
    }
}
