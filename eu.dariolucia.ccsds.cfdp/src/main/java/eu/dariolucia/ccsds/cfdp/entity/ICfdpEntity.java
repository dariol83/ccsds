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

package eu.dariolucia.ccsds.cfdp.entity;

import eu.dariolucia.ccsds.cfdp.entity.internal.CfdpEntity;
import eu.dariolucia.ccsds.cfdp.entity.request.ICfdpRequest;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.ICfdpSegmentationStrategy;
import eu.dariolucia.ccsds.cfdp.filestore.IVirtualFilestore;
import eu.dariolucia.ccsds.cfdp.mib.Mib;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayer;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * CFDP entity main interface, which can be used to create the internal CFDP entity implementation.
 */
public interface ICfdpEntity {

    /**
     * Create a {@link ICfdpEntity} with the provided {@link Mib}, {@link IVirtualFilestore} and list of {@link IUtLayer}.
     *
     * @param mib the MIB to be used by the entity
     * @param filestore the filestore to be used by the entity
     * @param layers the UT layers to be used by the entity
     * @return the {@link ICfdpEntity}
     */
    static ICfdpEntity create(Mib mib, IVirtualFilestore filestore, IUtLayer... layers) {
        return create(mib, filestore, Arrays.asList(layers));
    }

    /**
     * Create a {@link ICfdpEntity} with the provided {@link Mib}, {@link IVirtualFilestore} and a collection of {@link IUtLayer}.
     *
     * @param mib the MIB to be used by the entity
     * @param filestore the filestore to be used by the entity
     * @param layers the UT layers to be used by the entity
     * @return the {@link ICfdpEntity}
     */
    static ICfdpEntity create(Mib mib, IVirtualFilestore filestore,  Collection<IUtLayer> layers) {
        return new CfdpEntity(mib, filestore, null, layers);
    }

    /**
     * Create a {@link ICfdpEntity} with the provided {@link Mib}, {@link IVirtualFilestore}, {@link ITransactionIdGenerator} and list of {@link IUtLayer}.
     *
     * @param mib the MIB to be used by the entity
     * @param filestore the filestore to be used by the entity
     * @param transactionIdGenerator the transaction ID generator
     * @param layers the UT layers to be used by the entity
     * @return the {@link ICfdpEntity}
     */
    static ICfdpEntity create(Mib mib, IVirtualFilestore filestore, ITransactionIdGenerator transactionIdGenerator, IUtLayer... layers) {
        return create(mib, filestore, transactionIdGenerator, Arrays.asList(layers));
    }

    /**
     * Create a {@link ICfdpEntity} with the provided {@link Mib}, {@link IVirtualFilestore}, {@link ITransactionIdGenerator} and a collection of {@link IUtLayer}.
     *
     * @param mib the MIB to be used by the entity
     * @param filestore the filestore to be used by the entity
     * @param transactionIdGenerator the transaction ID generator
     * @param layers the UT layers to be used by the entity
     * @return the {@link ICfdpEntity}
     */
    static ICfdpEntity create(Mib mib, IVirtualFilestore filestore, ITransactionIdGenerator transactionIdGenerator, Collection<IUtLayer> layers) {
        return new CfdpEntity(mib, filestore, transactionIdGenerator, layers);
    }

    /**
     * Add a segmentation strategy to be taken into consideration by the entity in all subsequent {@link eu.dariolucia.ccsds.cfdp.entity.request.PutRequest}.
     *
     * When sending a file, segmentation strategies are evaluated in sequence: the first strategy that can be used to segment the file, will be used and the
     * evaluation is stopped. If not suitable strategy is found, the implementation must fallback a the basic segmentation strategy.
     *
     * @param strategy the segmentation strategy to add to the entity.
     */
    void addSegmentationStrategy(ICfdpSegmentationStrategy strategy);

    /**
     * Return the MIB used by this entity.
     *
     * @return the MIB
     */
    Mib getMib();

    /**
     * Return the {@link IUtLayer} having the provided name.
     *
     * @param name the name of the UT layer to retrieve
     * @return the {@link IUtLayer} or null if not present
     */
    IUtLayer getUtLayerByName(String name);

    /**
     * Return the {@link IUtLayer} used to send data to the provided destination entity.
     *
     * @param destinationEntityId the ID of the destination entity
     * @return the {@link IUtLayer} or null if not present
     */
    IUtLayer getUtLayerByDestinationEntity(long destinationEntityId);

    /**
     * Return the filestore used by this entity
     *
     * @return the filestore
     */
    IVirtualFilestore getFilestore();

    /**
     * Return the set of transaction IDs currently managed by this entity. This set includes also completed, cancelled and
     * abandoned transaction IDs, not yet purged by the implementation.
     *
     * @return the set of transaction IDs known by this entity
     */
    Set<Long> getTransactionIds();

    /**
     * Register a {@link ICfdpEntitySubscriber} to this entity.
     *
     * @param s the subscriber to add
     */
    void register(ICfdpEntitySubscriber s);

    /**
     * Deregister a {@link ICfdpEntitySubscriber} from this entity.
     *
     * @param s the subscriber to remove
     */
    void deregister(ICfdpEntitySubscriber s);

    /**
     * Provide this entity with a {@link ICfdpRequest}.
     *
     * @param request the request to be satisfied
     */
    void request(ICfdpRequest request);

    /**
     * Dispose this entity: all active transactions are cancelled and purged, the UT layers are deregistered and the
     * subscribers are removed. No further operations can be performed on this entity anymore.
     */
    void dispose();

    /**
     * Return the list of registered UT layers.
     *
     * @return the registered UT layers to this entity
     */
    List<IUtLayer> getUtLayers();
}
