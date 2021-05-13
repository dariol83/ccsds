package eu.dariolucia.ccsds.cfdp.entity.segmenters;

public interface ICfdpFileSegmenter {

    FileSegment nextSegment();

    void close();

}
