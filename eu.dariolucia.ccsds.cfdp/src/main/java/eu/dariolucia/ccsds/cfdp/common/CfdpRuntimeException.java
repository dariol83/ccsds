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
 * Generic base unchecked exception thrown by the CFDP module.
 */
public class CfdpRuntimeException extends RuntimeException {

    /**
     * Construct a {@link CfdpRuntimeException} with the provided message
     * @param message the exception message
     */
    public CfdpRuntimeException(String message) {
        super(message);
    }

    /**
     * Construct a {@link CfdpRuntimeException} with the provided cause
     * @param cause the exception cause
     */
    public CfdpRuntimeException(Throwable cause) {
        super(cause);
    }

    /**
     * Construct a {@link CfdpRuntimeException} with the provided message and cause
     * @param message the exception message
     * @param cause the exception cause
     */
    public CfdpRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
