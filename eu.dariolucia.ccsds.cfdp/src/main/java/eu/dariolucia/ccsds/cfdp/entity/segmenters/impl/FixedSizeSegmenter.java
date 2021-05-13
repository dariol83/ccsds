package eu.dariolucia.ccsds.cfdp.entity.segmenters.impl;

import eu.dariolucia.ccsds.cfdp.entity.segmenters.FileSegment;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.ICfdpFileSegmenter;
import eu.dariolucia.ccsds.cfdp.filestore.IVirtualFilestore;

public class FixedSizeSegmenter implements ICfdpFileSegmenter {

    private final IVirtualFilestore filestore;
    private final String fullPath;
    private final int maxLength;

    public FixedSizeSegmenter(IVirtualFilestore filestore, String fullPath, int maximumFileSegmentLength) {
        this.filestore = filestore;
        this.fullPath = fullPath;
        this.maxLength = maximumFileSegmentLength;
    }

    @Override
    public FileSegment nextSegment() {
        // TODO
        return null;
    }

    @Override
    public void close() {
        // TODO
    }
}
