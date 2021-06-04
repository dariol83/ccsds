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
 * An object capable to compute a checksum. Depending on the implementation, the object can:
 * <ul>
 *     <li>receive an entire byte array and compute the checksum of such byte array. The start of the array
 *     to process (indicated by the offset argument) assumes to start at file offset 0.</li>
 *     <li>receive the information as delivered from a single {@link eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDataPdu} and
 *     build an incremental checksum</li>
 * </ul>
 */
public interface ICfdpChecksum {

    /**
     * Compute the checksum of len bytes in the provided data, starting from the specified offset.
     * The provided offset has no effects on the logical file offset, always considered to be 0.
     *
     * This method is stateless and thread safe.
     *
     * @param data the data to compute the checksum
     * @param offset the offset in the data
     * @param len the number of bytes to checksum
     * @return the value of the checksum as unsigned int (32 bits)
     */
    int checksum(byte[] data, int offset, int len);

    /**
     * Incrementally compute the checksum on the provided {@link eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDataPdu} contents.
     *
     * This method is stateful: checksum objects used in this way should not be called by
     * more than one thread.
     *
     * @param data the part of the file data to add to the current checksum computation
     * @param fileOffset the file offset of the part of the file data
     * @return the current (potentially partial) checksum
     */
    int checksum(byte[] data, long fileOffset);

    /**
     * It can be used only in conjunction with the incremental checksum computation.
     *
     * @return the current value of the checksum
     */
    int getCurrentChecksum();

    /**
     * Return the type of the checksum (as in the SANA Checksum Identifiers registry)
     *
     * @return the type of the checksum
     */
    int type();
}
