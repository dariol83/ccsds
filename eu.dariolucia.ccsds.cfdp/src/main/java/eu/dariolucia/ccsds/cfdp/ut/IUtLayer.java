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
 * This interface represents the implementation of a UT layer as defined in the CCSDS standard.
 */
public interface IUtLayer {

    /**
     * The name of the UT Layer implementation. This name identifies the specific implementation and must match
     * the string provided in the MIB in the &lt;ut-layer&gt; tag, in order for the CFDP implementation to link a remote
     * destination ID with the specified UT layer implementation.
     *
     * @return the name of the UT layer implementation
     */
    String getName();

    /**
     * Request the transmission of the provided CFDP PDU to the specified remote entity.
     *
     * @param pdu the {@link CfdpPdu} unit to transmit
     * @param destinationEntityId the destination entity ID
     * @throws UtLayerException in case of transmission problems
     */
    void request(CfdpPdu pdu, long destinationEntityId) throws UtLayerException;

    /**
     * Register a {@link IUtLayerSubscriber} to the UT layer implementation.
     *
     * @param s the subscriber to register
     */
    void register(IUtLayerSubscriber s);

    /**
     * Deregister a {@link IUtLayerSubscriber} from the UT layer implementation.
     *
     * @param s the subscriber to deregister
     */
    void deregister(IUtLayerSubscriber s);

    /**
     * Enquiry for the RX availability from the specified remote entity.
     * @param destinationId the ID of the remote entity to check
     * @return true if the reception is declared available, otherwise false
     */
    boolean getRxAvailability(long destinationId);

    /**
     * Enquiry for the TX availability to the specified remote entity.
     * @param destinationId the ID of the remote entity to check
     * @return true if the transmission is declared available, otherwise false
     */
    boolean getTxAvailability(long destinationId);

    /**
     * Dispose the UT layer implementation, freeing all allocated resources. The UT layer implementation shall not be
     * used after that this method is called.
     */
    void dispose();
}
