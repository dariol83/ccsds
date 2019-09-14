/*
 *   Copyright (c) 2019 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.tmtc.coding.reader;

import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * This class allows to read a stream of TC frames (of variable length) from an {@link InputStream}.
 *
 * This class is not thread-safe.
 */
public class TcFrameChannelReader extends AbstractChannelReader {

    private static final int TC_FRAME_BUFFER_SIZE = TcTransferFrame.MAX_TC_FRAME_LENGTH + TcTransferFrame.TC_PRIMARY_HEADER_LENGTH;

    private final byte[] buffer = new byte[TC_FRAME_BUFFER_SIZE];

    public TcFrameChannelReader(InputStream stream) {
        super(stream);
    }

    @Override
    public int readNext(byte[] b, int offset, int maxLength) throws IOException {
        stream.mark(TcTransferFrame.MAX_TC_FRAME_LENGTH);
        if(maxLength < TcTransferFrame.TC_PRIMARY_HEADER_LENGTH) {
            throw new IOException("Provided buffer cannot contain a TC transfer frame primary header: maxLength is " + maxLength + ", shall be at least " + TcTransferFrame.TC_PRIMARY_HEADER_LENGTH);
        }
        // Read the first TC_PRIMARY_HEADER_LENGTH bytes (5)
        int read = 0;
        while(read < TcTransferFrame.TC_PRIMARY_HEADER_LENGTH) {
            int justRead = stream.read(b, offset + read, TcTransferFrame.TC_PRIMARY_HEADER_LENGTH - read);
            if(justRead == -1) {
                // Nothing was read before, OK, stop
                if(read == 0) {
                    return -1;
                } else {
                    // Exception here
                    throw new IOException("End of stream reached");
                }
            } else {
                read += justRead;
            }
        }
        // Decode the length
        int len = ((b[2] & 0x03) & 0xFF);
        len <<= 8;
        len |= (b[3] & 0xFF);
        ++len;
        if(maxLength < len) {
            stream.reset();
            throw new IOException("Provided buffer cannot contain a TC transfer frame primary header: maxLength is " + maxLength + " bytes, shall the frame is " + len + " bytes");
        }
        // Read the rest
        while(read < len) {
            int justRead = stream.read(b, read, len - read);
            if(justRead == -1) {
                throw new IOException("End of stream reached");
            } else {
                read += justRead;
            }
        }
        return len;
    }

    @Override
    public byte[] readNext() throws IOException {
        int read = readNext(buffer, 0, buffer.length);
        if(read != -1) {
            return Arrays.copyOfRange(buffer, 0, read);
        } else {
            return null;
        }
    }
}
