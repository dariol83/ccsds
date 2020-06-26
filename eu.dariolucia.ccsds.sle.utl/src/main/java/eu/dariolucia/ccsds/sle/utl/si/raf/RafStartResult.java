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

import eu.dariolucia.ccsds.sle.utl.si.AbstractOperationResult;
import eu.dariolucia.ccsds.sle.utl.si.DiagnosticsEnum;

public class RafStartResult extends AbstractOperationResult<RafStartDiagnosticsEnum> {

    public static RafStartResult noError() {
        return new RafStartResult(false, null, null);
    }

    public static RafStartResult errorCommon(DiagnosticsEnum error) {
        return new RafStartResult(true, error, null);
    }

    public static RafStartResult errorSpecific(RafStartDiagnosticsEnum error) {
        return new RafStartResult(true, null, error);
    }

    private RafStartResult(boolean error, DiagnosticsEnum common, RafStartDiagnosticsEnum specific) {
        super(error, common, specific);
    }

}
