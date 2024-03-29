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

package eu.dariolucia.ccsds.cfdp.common;

/**
 * Generic base checked exception thrown by the CFDP module.
 */
public class CfdpException extends Exception {

    /**
     * Construct a {@link CfdpException} with the provided message
     * @param message the exception message
     */
    public CfdpException(String message) {
        super(message);
    }

    /**
     * Construct a {@link CfdpException} with the provided message and cause
     * @param message the exception message
     * @param cause the exception cause
     */
    public CfdpException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Construct a {@link CfdpException} with the provided cause
     * @param cause the exception cause
     */
    public CfdpException(Throwable cause) {
        super(cause);
    }
}
