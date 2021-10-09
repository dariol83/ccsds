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

package eu.dariolucia.ccsds.cfdp.filestore;

import eu.dariolucia.ccsds.cfdp.common.CfdpException;

/**
 * Exception used by {@link IVirtualFilestore} implementations to report issues when operating on the underlying filestore.
 */
public class FilestoreException extends CfdpException {

    public FilestoreException(String message) {
        super(message);
    }

    public FilestoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public FilestoreException(Throwable cause) {
        super(cause);
    }
}
