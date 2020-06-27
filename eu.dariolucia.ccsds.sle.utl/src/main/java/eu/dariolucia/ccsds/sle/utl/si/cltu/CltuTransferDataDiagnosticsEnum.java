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

package eu.dariolucia.ccsds.sle.utl.si.cltu;

public enum CltuTransferDataDiagnosticsEnum {
    UNABLE_TO_PROCESS (0),
    UNABLE_TO_STORE (1),
    OUT_OF_SEQUENCE (2),
    INCONSISTENT_TIME_RANGE (3),
    INVALID_TIME (4),
    LATE_SLDU (5),
    INVALID_DELAY_TIME (6),
    CLTU_ERROR (7);

    private final int code;

    CltuTransferDataDiagnosticsEnum(int code) {
        this.code = code;
    }

    public int getCode() {
        return this.code;
    }

    public static CltuTransferDataDiagnosticsEnum getDiagnostics(int intValue) {
        for(CltuTransferDataDiagnosticsEnum b : CltuTransferDataDiagnosticsEnum.values()) {
            if(b.getCode() == intValue) {
                return b;
            }
        }
        throw new IllegalArgumentException("Cannot decode value: " + intValue);
    }
}
