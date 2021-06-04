/*
 *   Copyright (c) 2021 Dario Lucia (https://www.dariolucia.eu)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

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
