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

import eu.dariolucia.ccsds.sle.utl.si.AbstractOperationResult;
import eu.dariolucia.ccsds.sle.utl.si.DiagnosticsEnum;

public class CltuThrowEventResult extends AbstractOperationResult<CltuThrowEventDiagnosticsEnum> {

    public static CltuThrowEventResult noError() {
        return new CltuThrowEventResult(false, null, null);
    }

    public static CltuThrowEventResult errorCommon(DiagnosticsEnum error) {
        return new CltuThrowEventResult(true, error, null);
    }

    public static CltuThrowEventResult errorSpecific(CltuThrowEventDiagnosticsEnum error) {
        return new CltuThrowEventResult(true, null, error);
    }

    private CltuThrowEventResult(boolean error, DiagnosticsEnum common, CltuThrowEventDiagnosticsEnum specific) {
        super(error, common, specific);
    }

}
