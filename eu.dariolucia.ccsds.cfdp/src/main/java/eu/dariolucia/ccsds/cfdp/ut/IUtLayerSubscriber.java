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

/**
 * UT Layer subscription interface.
 */
public interface IUtLayerSubscriber {

    /**
     * Inform about the reception of the provided {@link CfdpPdu} by the provided {@link IUtLayer}.
     *
     * @param layer the UT layer implementation that received the pdu
     * @param pdu the received CFDP PDU
     */
    void indication(IUtLayer layer, CfdpPdu pdu);

    /**
     * Inform the subscriber that the TX is available for the specified entity.
     *
     * @param layer the UT layer implementation reporting the availability information
     * @param entityId the remote entity ID
     */
    void startTxPeriod(IUtLayer layer, long entityId);

    /**
     * Inform the subscriber that the TX is not available for the specified entity.
     *
     * @param layer the UT layer implementation reporting the availability information
     * @param entityId the remote entity ID
     */
    void endTxPeriod(IUtLayer layer, long entityId);

    /**
     * Inform the subscriber that the RX is available for the specified entity.
     *
     * @param layer the UT layer implementation reporting the availability information
     * @param entityId the remote entity ID
     */
    void startRxPeriod(IUtLayer layer, long entityId);

    /**
     * Inform the subscriber that the RX is not available for the specified entity.
     *
     * @param layer the UT layer implementation reporting the availability information
     * @param entityId the remote entity ID
     */
    void endRxPeriod(IUtLayer layer, long entityId);
}
