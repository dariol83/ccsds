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

public class CltuTransferDataResult extends AbstractOperationResult<CltuTransferDataDiagnosticsEnum> {

    public static CltuTransferDataResult noError(int availableBuffer) {
        return new CltuTransferDataResult(false, availableBuffer,null, null);
    }

    public static CltuTransferDataResult errorCommon(DiagnosticsEnum error) {
        return new CltuTransferDataResult(true, -1, error, null);
    }

    public static CltuTransferDataResult errorSpecific(CltuTransferDataDiagnosticsEnum error) {
        return new CltuTransferDataResult(true, -1,null, error);
    }

    private final int availableBuffer;

    private CltuTransferDataResult(boolean error, int availableBuffer, DiagnosticsEnum common, CltuTransferDataDiagnosticsEnum specific) {
        super(error, common, specific);
        this.availableBuffer = availableBuffer;
    }

    public int getAvailableBuffer() {
        return availableBuffer;
    }
}
