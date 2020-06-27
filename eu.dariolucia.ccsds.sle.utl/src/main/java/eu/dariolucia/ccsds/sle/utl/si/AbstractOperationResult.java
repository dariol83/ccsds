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

package eu.dariolucia.ccsds.sle.utl.si;

/**
 * Extensions of this class are used by the service provider operation handlers to return the status of a specific
 * user request.
 *
 * @param <T> the diagnostics enumeration (SLE type and operation specific)
 */
public abstract class AbstractOperationResult<T extends Enum<T>> {

    private final boolean error;
    private final DiagnosticsEnum common;
    private final T specific;

    protected AbstractOperationResult(boolean error, DiagnosticsEnum common, T specific) {
        this.error = error;
        this.common = common;
        this.specific = specific;
    }

    /**
     * This method returns if the operation was not processed (i.e. negative response to be sent to the user).
     *
     * @return true if there was an error, otherwise false
     */
    public boolean isError() {
        return error;
    }

    /**
     * This method returns the common diagnostic to be provided to the user in case of negative response.
     *
     * @return the common diagnostic, otherwise null if not set
     */
    public DiagnosticsEnum getCommon() {
        return common;
    }

    /**
     * This method returns the specific diagnostic to be provided to the user in case of negative response.
     *
     * @return the specific diagnostic, otherwise null if not set
     */
    public T getSpecific() {
        return specific;
    }
}
