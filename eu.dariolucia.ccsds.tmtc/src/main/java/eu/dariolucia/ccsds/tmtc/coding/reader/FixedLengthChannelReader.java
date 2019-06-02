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

/**
 * This class is an implementation of the {@link IChannelReader} capable to read transfer units of the same length.
 */
public class FixedLengthChannelReader extends AbstractChannelReader {

    private final int fixedLength;

    public FixedLengthChannelReader(InputStream stream, int fixedLength) {
        super(stream);
        this.fixedLength = fixedLength;
    }

    @Override
    public int readNext(byte[] b, int offset, int maxLength) throws IOException {
        if(maxLength < fixedLength) {
            throw new IOException("Provided buffer free space " + maxLength + " bytes is less than required " + fixedLength + " bytes");
        }

        int read = 0;
        while(read < fixedLength) {
            int justRead = stream.read(b, offset + read, fixedLength - read);
            if(justRead == -1 && read != 0) {
                throw new IOException("Stream unexpectedly closed: stream read() returned -1 bytes read");
            }
            if(justRead == -1) {
                // read == 0
                return -1;
            }
            read += justRead;
            if(justRead == 0) {
                throw new IOException("Wrong read: stream read() returned 0 bytes read");
            }
        }
        return read;
    }

    @Override
    public byte[] readNext() throws IOException {
        byte[] b = new byte[fixedLength];
        int read = readNext(b, 0, b.length);
        if(read > 0) {
            return b;
        } else {
            return null;
        }
    }

}
