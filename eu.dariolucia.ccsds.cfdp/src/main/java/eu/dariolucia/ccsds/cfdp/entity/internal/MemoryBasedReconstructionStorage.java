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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

public class MemoryBasedReconstructionStorage implements IFileReconstructionStorage {

    private final Map<Long, FileDataPdu> fileReconstructionMap;

    public MemoryBasedReconstructionStorage() throws IOException {
        this.fileReconstructionMap = new TreeMap<>();
    }

    @Override
    public void writeData(FileDataPdu pdu) {
        // Check if there is already a FileData PDU like you in the map
        FileDataPdu existing = this.fileReconstructionMap.get(pdu.getOffset());
        // We cannot rely on the behaviour of other implementations, therefore here we need to overwrite the entry,
        // if the length of the data is greater than the one currently in the map, or skip it if it is a subset of what
        // we already have
        if(existing != null && existing.getFileData().length >= pdu.getFileData().length) {
            // 4.6.1.2.7 any repeated data shall be discarded
            return;
        }
        // Add the PDU in the map
        this.fileReconstructionMap.put(pdu.getOffset(), pdu);
    }

    @Override
    public void computeChecksum(ICfdpChecksum checksum) {
        long currentlyWritten = 0;
        for(Map.Entry<Long, FileDataPdu> e : this.fileReconstructionMap.entrySet()) {
            byte[] toWrite = e.getValue().getFileData();
            long offset = e.getKey();
            if(offset == currentlyWritten) {
                // Proceed as usual
                checksum.checksum(toWrite, offset);
                currentlyWritten += toWrite.length;
            } else if(offset < currentlyWritten) {
                // There is an overlap with what you already wrote... compute the good data that you can still write
                long bytesToDiscard = currentlyWritten - offset;
                long newOffset = offset + bytesToDiscard;
                long numBytesToWrite = toWrite.length - bytesToDiscard;
                if(numBytesToWrite > 0) {
                    checksum.checksum(Arrays.copyOfRange(toWrite, (int) bytesToDiscard, (int) (bytesToDiscard + numBytesToWrite)), newOffset);
                    currentlyWritten += numBytesToWrite;
                }
            } else {
                long fakeBytes = offset - currentlyWritten;
                checksum.checksum(new byte[(int) fakeBytes], currentlyWritten);
                currentlyWritten += fakeBytes;
                // Proceed as usual, now offset is equal to currentlyWritten
                checksum.checksum(toWrite, offset);
                currentlyWritten += toWrite.length;
            }
        }
    }

    @Override
    public long writeFileToStorage(OutputStream os) throws IOException {
        long currentlyWritten = 0;
        for (Map.Entry<Long, FileDataPdu> e : this.fileReconstructionMap.entrySet()) {
            byte[] toWrite = e.getValue().getFileData();
            long offset = e.getKey();
            if (offset == currentlyWritten) {
                // Proceed as usual
                os.write(toWrite);
                currentlyWritten += toWrite.length;
            } else if (offset < currentlyWritten) {
                // There is an overlap with what you already wrote... compute the good data that you can still write
                long bytesToDiscard = currentlyWritten - offset;
                long numBytesToWrite = toWrite.length - bytesToDiscard;
                if (numBytesToWrite > 0) {
                    os.write(Arrays.copyOfRange(toWrite, (int) bytesToDiscard, (int) (bytesToDiscard + numBytesToWrite)));
                    currentlyWritten += numBytesToWrite;
                }
            } else {
                long fakeBytes = offset - currentlyWritten;
                os.write(new byte[(int) fakeBytes]);
                currentlyWritten += fakeBytes;
                // Proceed as usual, now offset is equal to currentlyWritten
                os.write(toWrite);
                currentlyWritten += toWrite.length;
            }
        }
        return currentlyWritten;
    }

    @Override
    public void handlePreDispose() {
        this.fileReconstructionMap.clear();
    }
}
