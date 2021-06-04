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

package eu.dariolucia.ccsds.cfdp.ut;

import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;

public interface IUtLayerSubscriber {

    void indication(IUtLayer layer, CfdpPdu pdu);

    void startTxPeriod(IUtLayer layer, long entityId);

    void endTxPeriod(IUtLayer layer, long entityId);

    void startRxPeriod(IUtLayer layer, long entityId);

    void endRxPeriod(IUtLayer layer, long entityId);
}
