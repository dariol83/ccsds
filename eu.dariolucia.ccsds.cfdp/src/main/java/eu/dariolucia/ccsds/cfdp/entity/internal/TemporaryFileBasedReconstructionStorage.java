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

import eu.dariolucia.ccsds.cfdp.protocol.checksum.ICfdpChecksum;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDataPdu;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;

public class TemporaryFileBasedReconstructionStorage implements IFileReconstructionStorage {

    private RandomAccessFile temporaryReconstructionFileMap;
    private final File temporaryReconstructionFile;

    public TemporaryFileBasedReconstructionStorage(long sourceEntityId, long transactionSequenceNumber, CfdpEntity entity) throws IOException {
        String tempFolder = entity.getMib().getLocalEntity().getTempFolder();
        if(tempFolder == null) {
            this.temporaryReconstructionFile = Files.createTempFile("cfdp_in_file_" + sourceEntityId + "_" + transactionSequenceNumber + "_", ".tmp").toFile();
        } else {
            this.temporaryReconstructionFile = Files.createTempFile(new File(tempFolder).toPath(), "cfdp_in_file_" + sourceEntityId + "_" + transactionSequenceNumber + "_", ".tmp").toFile();
        }
        this.temporaryReconstructionFileMap = new RandomAccessFile(this.temporaryReconstructionFile, "rw");
    }

    @Override
    public void writeData(FileDataPdu pdu) throws IOException {
        this.temporaryReconstructionFileMap.seek(pdu.getOffset());
        this.temporaryReconstructionFileMap.write(pdu.getFileData());
    }

    @Override
    public void computeChecksum(ICfdpChecksum checksum) throws IOException {
        long length = this.temporaryReconstructionFileMap.length();
        this.temporaryReconstructionFileMap.seek(0);
        byte[] tmpBuffer = new byte[1024 * 64]; // 64KB
        long position = 0;
        while (position < length) {
            // read(...) advances the position of the file pointer, no seek needed after that
            int read = this.temporaryReconstructionFileMap.read(tmpBuffer);
            checksum.checksum(tmpBuffer, 0, read, position);
            position += read;
        }
    }

    @Override
    public long writeFileToStorage(OutputStream os) throws IOException {
        long length = this.temporaryReconstructionFileMap.length();
        this.temporaryReconstructionFileMap.seek(0);
        byte[] tmpBuffer = new byte[1024 * 64]; // 64KB
        long position = 0;
        while (position < length) {
            // read(...) advances the position of the file pointer, no seek needed after that
            int read = this.temporaryReconstructionFileMap.read(tmpBuffer);
            os.write(tmpBuffer, 0, read);
            position += read;
        }
        return position;
    }

    @Override
    public void handlePreDispose() throws IOException {
        if(this.temporaryReconstructionFileMap != null) {
            try {
                this.temporaryReconstructionFileMap.close();
            } finally {
                this.temporaryReconstructionFile.delete(); // NOSONAR no need to check the boolean value here
                this.temporaryReconstructionFileMap = null;
            }
        }
    }
}
