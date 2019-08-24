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
 * Abstract class used to implement {@link IChannelReader} that read frames from {@link InputStream} objects.
 */
public abstract class AbstractChannelReader implements IChannelReader {

    protected final InputStream stream;

    protected AbstractChannelReader(InputStream stream) {
        if(stream == null) {
            throw new NullPointerException("Null stream provided");
        }
        this.stream = stream;
    }

    /**
     * This method is used to close the underlying {@link InputStream}.
     *
     * @throws IOException in case the stream raises an exception on close
     */
    public void close() throws IOException {
        this.stream.close();
    }
}
