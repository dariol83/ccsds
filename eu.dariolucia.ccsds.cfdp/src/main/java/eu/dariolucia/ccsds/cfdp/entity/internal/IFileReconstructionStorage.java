/*
 *   Copyright (c) 2022 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.cfdp.entity.internal;

import eu.dariolucia.ccsds.cfdp.mib.LocalEntityConfigurationInformation;
import eu.dariolucia.ccsds.cfdp.protocol.checksum.ICfdpChecksum;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDataPdu;

import java.io.IOException;
import java.io.OutputStream;

/**
 * This interface is used to provide different implementation strategies with respect to the temporary storage of file
 * chunks delivered by the CFDP protocol. This library provides two implementations:
 * <ul>
 *     <li>A temporary file-based implementation (@link {@link TemporaryFileBasedReconstructionStorage}), default, which retains the received data into a {@link java.io.RandomAccessFile} stored into a
 *     temporary location: the default one of the OS/user or a selected one {@link LocalEntityConfigurationInformation#getTempFolder()}</li>
 *     <li>A memory-based implementation (@link {@link MemoryBasedReconstructionStorage}, which retains the received data in a in-memory structure</li>
 * </ul>
 * In case of many parallel transactions with large files, it is recommended to use the temporary file-based implementation. In case of
 * limited parallel transactions with small files, or in case the CFDP system has plenty of memory available, then the memory-based implementation provides
 * higher performance.
 *
 * By default, the {@link TemporaryFileBasedReconstructionStorage} is configured. To change it to the {@link MemoryBasedReconstructionStorage}, the
 * MIB property {@link LocalEntityConfigurationInformation#isFileBasedTempStorage()} shall be set to false.
 *
 * Note: it is possible to achieve higher performance also with the temporary file-based implementation, if the path to the temporary folder as specified with
 * {@link LocalEntityConfigurationInformation#getTempFolder()} is a memory-mapped drive (e.g. ramfs partition in Linux).
 */
public interface IFileReconstructionStorage {

    /**
     * Write the received pdu into the temporary buffer.
     *
     * @param pdu the PDU to store
     * @throws IOException in case of issues
     */
    void writeData(FileDataPdu pdu) throws IOException;

    /**
     * Compute the checksum of the currently received data using the provided checksum object.
     *
     * @param checksum the checksum object to use
     * @throws IOException in case of issues
     */
    void computeChecksum(ICfdpChecksum checksum) throws IOException;

    /**
     * Write the currently received data to the provided output stream.
     *
     * @param os the output stream to use to dump the received data
     * @return the last byte offset that was written plus one (i.e. the position of the write pointer in the output stream)
     * @throws IOException in case of issues
     */
    long writeFileToStorage(OutputStream os) throws IOException;

    /**
     * Remove the resources and prepare for disposal. After calling this method, the state of the object becomes invalid.
     *
     * @throws IOException in case of issues
     */
    void handlePreDispose() throws IOException;
}
