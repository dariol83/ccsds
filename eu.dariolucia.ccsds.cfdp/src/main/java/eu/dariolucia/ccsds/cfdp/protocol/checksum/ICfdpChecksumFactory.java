package eu.dariolucia.ccsds.cfdp.protocol.checksum;

/**
 * Each file conveyed by CFDP shall be accompanied by a checksum, the purpose of
 * which is to protect the integrity of the file.
 *
 * The set of available checksum computation algorithms shall comprise all checksum
 * computation algorithms that:
 * a) are implemented by the checksum computing entity; and
 * b) are also among the first 16 algorithms enumerated in the SANA Checksum Identifiers
 * registry.
 *
 * Ref: CCSDS 727.0-B-5, 4.2
 */
public interface ICfdpChecksumFactory {

    /**
     * Return the type of the checksum (as in the SANA Checksum Identifiers registry)
     *
     * @return the type of the checksum
     */
    int type();

    /**
     * Construct a new instance of the specific checksum.
     *
     * @return a new {@link ICfdpChecksum} instance
     */
    ICfdpChecksum build();
}
