package eu.dariolucia.ccsds.cfdp.entity.segmenters.impl;

import eu.dariolucia.ccsds.cfdp.entity.segmenters.FileSegment;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.ICfdpFileSegmenter;
import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import eu.dariolucia.ccsds.cfdp.filestore.IVirtualFilestore;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FixedSizeSegmenter implements ICfdpFileSegmenter {

    private static final Logger LOG = Logger.getLogger(FixedSizeSegmenter.class.getName());

    private final IVirtualFilestore filestore;
    private final String fullPath;
    private final int maxLength;

    private InputStream fileStream;
    private long currentOffset;
    private boolean endOfStreamReached;

    public FixedSizeSegmenter(IVirtualFilestore filestore, String fullPath, int maximumFileSegmentLength) {
        if(LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, String.format("FixedSizeSegmented constructor: file store %s, path %s, maximum segment length %d", filestore, fullPath, maximumFileSegmentLength));
        }
        this.filestore = filestore;
        this.fullPath = fullPath;
        this.maxLength = maximumFileSegmentLength;
    }

    @Override
    public FileSegment nextSegment() throws FilestoreException {
        if(this.fileStream == null) {
            if(LOG.isLoggable(Level.FINEST)) {
                LOG.log(Level.FINEST, String.format("FixedSizeSegmented nextSegment(): file store %s, path %s, maximum segment length %d - Initialisation", filestore, fullPath, maxLength));
            }
            this.fileStream = this.filestore.readFile(this.fullPath);
            this.currentOffset = 0;
        }
        if(this.endOfStreamReached) {
            if(LOG.isLoggable(Level.FINEST)) {
                LOG.log(Level.FINEST, String.format("FixedSizeSegmented nextSegment(): file store %s, path %s, maximum segment length %d - End of stream reached, returning immediately", filestore, fullPath, maxLength));
            }
            return FileSegment.eof();
        }
        byte[] maxBuffer;
        try {
            maxBuffer = fileStream.readNBytes(this.maxLength);
        } catch (IOException e) {
            throw new FilestoreException(e);
        }
        long offset = this.currentOffset;
        this.currentOffset += maxBuffer.length;
        if(maxBuffer.length == 0) {
            if(LOG.isLoggable(Level.FINEST)) {
                LOG.log(Level.FINEST, String.format("FixedSizeSegmented nextSegment(): file store %s, path %s, maximum segment length %d - End of stream reached, final offset %d", filestore, fullPath, maxLength, currentOffset));
            }
            this.endOfStreamReached = true;
            return FileSegment.eof();
        } else {
            if(LOG.isLoggable(Level.FINEST)) {
                LOG.log(Level.FINEST, String.format("FixedSizeSegmented nextSegment(): file store %s, path %s, maximum segment length %d - Returning file segment, offset %d, data length %d", filestore, fullPath, maxLength, offset, maxBuffer.length));
            }
            return FileSegment.segment(offset, maxBuffer);
        }
    }

    @Override
    public void close() {
        if(this.fileStream != null) {
            try {
                this.fileStream.close();
            } catch (IOException e) {
                if(LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, String.format("FixedSizeSegmented nextSegment(): file store %s, path %s, maximum segment length %d - Exception while closing stream: %s", filestore, fullPath, maxLength, e.getMessage()), e);
                }
            }
        }
    }
}
