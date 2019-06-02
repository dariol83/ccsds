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

import eu.dariolucia.ccsds.tmtc.util.StringUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * This class is an implementation of the {@link IChannelReader} capable to read transfer units from a line-terminated
 * hex dump of bytes. One line is one transfer unit.
 */
public class LineHexDumpChannelReader extends AbstractChannelReader {

    private final BufferedReader br;

    public LineHexDumpChannelReader(InputStream stream) {
        super(stream);
        this.br = new BufferedReader(new InputStreamReader(stream));
    }

    @Override
    public int readNext(byte[] b, int offset, int maxLength) throws IOException {
        byte[] data = readNext();
        if(data == null) {
            return -1;
        }
        if (maxLength < data.length) {
            throw new IOException("Buffer too small: got " + maxLength + " but read " + (data.length));
        } else {
            System.arraycopy(data, 0, b, offset, data.length);
        }
        return data.length;
    }

    @Override
    public byte[] readNext() throws IOException {
        String line;
        do {
            line = br.readLine();
            if(line == null) {
                return null;
            } else if(line.isEmpty()) {
                line = null;
            }
        } while(line == null);
        return StringUtil.toByteArray(line);
    }
}
