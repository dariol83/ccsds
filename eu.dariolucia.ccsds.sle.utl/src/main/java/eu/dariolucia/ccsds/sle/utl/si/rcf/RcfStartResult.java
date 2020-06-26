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

package eu.dariolucia.ccsds.sle.utl.si.rcf;

import eu.dariolucia.ccsds.sle.utl.si.AbstractOperationResult;
import eu.dariolucia.ccsds.sle.utl.si.DiagnosticsEnum;

public class RcfStartResult extends AbstractOperationResult<RcfStartDiagnosticsEnum> {

    public static RcfStartResult noError() {
        return new RcfStartResult(false, null, null);
    }

    public static RcfStartResult errorCommon(DiagnosticsEnum error) {
        return new RcfStartResult(true, error, null);
    }

    public static RcfStartResult errorSpecific(RcfStartDiagnosticsEnum error) {
        return new RcfStartResult(true, null, error);
    }

    private RcfStartResult(boolean error, DiagnosticsEnum common, RcfStartDiagnosticsEnum specific) {
        super(error, common, specific);
    }
}
