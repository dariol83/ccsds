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

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * Interface used to read transfer frame data unit from the transfer layer.
 */
public interface IChannelReader extends Closeable, Supplier<byte[]> {

    /**
     * This method reads a transfer unit (as identified by the implementation of this interface)
     * and puts it in the provided buffer, starting from the given offset. If the buffer is not large enough
     * to accommodate the contents of the transfer unit, an {@link IOException} is thrown.
     *
     * @param b the output buffer
     * @param offset the offset wrt the output buffer
     * @param maxLength the maximum number of bytes that the output buffer can accommodate, starting from offset
     * @return the number of written bytes in the output buffer, starting from offset, or -1 if no more transfer units are available
     * @throws SynchronizationLostException if the reader lost the synchronisation with the transfer layer
     * @throws IOException if the output buffer is too small, the transfer layer closes unexpectedly or wrong data is read
     */
    int readNext(byte[] b, int offset, int maxLength) throws IOException;

    /**
     * This method reads a transfer unit and puts it in a new buffer, created on purpose by the implementation of this
     * interface. The ownership of the buffer is transferred to the called of this method, i.e. a new buffer shall be
     * allocated by each call to this operation.
     *
     * @return the transfer unit as read from the transfer layer, or null if no more transfer units are available
     * @throws SynchronizationLostException if the reader lost the synchronisation with the transfer layer
     * @throws IOException if the buffer is too small, the transfer layer closes unexpectedly or wrong data is read
     */
    byte[] readNext() throws IOException;

    /**
     * Default implementation of the get() method for suppliers. Since Supplier::get() does not foresee exceptions,
     * potential {@link IOException} are caught and null is returned. If external exception handling is required, then the
     * readNext() method shall be called directly.
     *
     * @return the next frame or null if no frame is available (expected or due to errors)
     */
    default byte[] get() {
        try {
            return readNext();
        } catch (IOException e) {
            return null; // NOSONAR what the hell is this rule? null is a very specific value, with a specific meaning, I cannot replace with byte[0]
        }
    }
}
