/*
 * Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package eu.dariolucia.ccsds.tmtc.coding.reader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * This class is an implementation of the {@link IChannelReader} capable to read transfer units of variable lengths,
 * preceded by a synchronisation marker and closed by a trailer. The implementation verifies the presence of the
 * synchronisation markers and, depending on its construction, can try to recover a synchronisation loss or can throw
 * an exception.
 *
 * When the readNext(): byte[] method is intended to be used, the defaultMaxBufferSize shall be correctly dimensioned
 * in a conservative way: default is 4096 bytes.
 */
public class SyncMarkerVariableLengthChannelReader extends AbstractChannelReader {

    private final byte[] startSyncMarker;

    private final byte[] endSyncMarker;

    private final boolean includeStartMarker;

    private final boolean throwExceptionOnSyncLoss;

    private final int defaultMaxBufferSize;

    public SyncMarkerVariableLengthChannelReader(InputStream stream, byte[] startSyncMarker, byte[] endSyncMarker) {
        this(stream, startSyncMarker, endSyncMarker, true, false, 4096);
    }

    public SyncMarkerVariableLengthChannelReader(InputStream stream, byte[] startSyncMarker, byte[] endSyncMarker, boolean includeStartMarker, boolean throwExceptionOnSyncLoss, int defaultMaxBufferSize) {
        super(stream);
        this.startSyncMarker = startSyncMarker.clone();
        this.endSyncMarker = endSyncMarker.clone();
        this.includeStartMarker = includeStartMarker;
        this.throwExceptionOnSyncLoss = throwExceptionOnSyncLoss;
        this.defaultMaxBufferSize = defaultMaxBufferSize;
    }

    @Override
    public int readNext(byte[] b, int offset, int maxLength) throws IOException {
        int smCurrIdx = 0;
        boolean smFound = false;
        // Seeking the start sync marker (byte after byte)
        while(!smFound) {
            // Read one byte
            int readByte = stream.read();
            if(readByte == -1) {
                if(smCurrIdx != 0) {
                    throw new IOException("Stream unexpectedly closed (-1) while looking for sync marker");
                } else {
                    // No more data
                    return -1;
                }
            }
            // Check if byte is the expected one in the SM location (expected SM index: smCurrIdx)
            if((byte) readByte == startSyncMarker[smCurrIdx]) {
                // If so, increment smCurrIdx by one, and check if the SM is over
                ++smCurrIdx;
                if(smCurrIdx == startSyncMarker.length) {
                    // If so, read the fixed length (exit the while)
                    smFound = true;
                }
            } else {
                // If not, repeat or throw exception
                if(throwExceptionOnSyncLoss) {
                    throw new SynchronizationLostException("Synchronization lost: expected " + startSyncMarker[smCurrIdx] + ", got " + readByte);
                }
                // Reset SM index to zero before retrying
                smCurrIdx = 0;
            }
        }

        int read = 0;
        // Copy the start sync marker if required
        if(includeStartMarker) {
            System.arraycopy(startSyncMarker, 0, b, offset, startSyncMarker.length);
            read += startSyncMarker.length;
        }

        // Seeking the end sync marker (byte after byte)
        smFound = false;
        smCurrIdx = 0;
        while(!smFound) {
            // Check size
            // Read one byte
            byte readByte = (byte) stream.read();
            if(readByte == -1) {
                throw new IOException("Stream unexpectedly closed (-1) while looking for trailer");
            }
            // Add it to the buffer
            if(offset + read >= maxLength) {
                throw new IOException("Buffer too small: got " + maxLength + " but read at least " + (read + 1));
            }
            b[offset + read] = readByte;
            ++read;
            // Check if readByte is the expected one in the SM location (expected SM index: smCurrIdx)
            if(readByte == endSyncMarker[smCurrIdx]) {
                // If so, increment smCurrIdx by one, and check if the SM is over
                ++smCurrIdx;
                if(smCurrIdx == endSyncMarker.length) {
                    // If so, read the fixed length (exit the while)
                    smFound = true;
                }
            } else {
                // If not, go ahead (reset SM index to zero)
                smCurrIdx = 0;
            }
        }

        // Return the buffer
        return read;
    }

    @Override
    public byte[] readNext() throws IOException {
        byte[] b = new byte[defaultMaxBufferSize];
        int read = readNext(b, 0, b.length);
        if(read > 0) {
            return Arrays.copyOfRange(b, 0, read);
        } else {
            return null;
        }
    }

}
