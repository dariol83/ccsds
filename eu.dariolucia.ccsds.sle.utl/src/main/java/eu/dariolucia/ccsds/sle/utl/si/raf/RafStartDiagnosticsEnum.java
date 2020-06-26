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

package eu.dariolucia.ccsds.sle.utl.si.raf;

public enum RafStartDiagnosticsEnum {
    OUT_OF_SERVICE (0),
    UNABLE_TO_COMPLY (1),
    INVALID_START_TIME (2),
    INVALID_STOP_TIME (3),
    MISSING_TIME_VALUE (4);

    private final int code;

    RafStartDiagnosticsEnum(int code) {
        this.code = code;
    }

    public int getCode() {
        return this.code;
    }

    public static RafStartDiagnosticsEnum getDiagnostics(int intValue) {
        for(RafStartDiagnosticsEnum b : RafStartDiagnosticsEnum.values()) {
            if(b.getCode() == intValue) {
                return b;
            }
        }
        throw new IllegalArgumentException("Cannot decode value: " + intValue);
    }
}
