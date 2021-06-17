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
import java.util.Set;

public interface ICfdpEntity {

    static ICfdpEntity create(Mib mib, IVirtualFilestore filestore, IUtLayer... layers) {
        return create(mib, filestore, Arrays.asList(layers));
    }

    static ICfdpEntity create(Mib mib, IVirtualFilestore filestore,  Collection<IUtLayer> layers) {
        return new CfdpEntity(mib, filestore, layers);
    }

    void addSegmentationStrategy(ICfdpSegmentationStrategy strategy);

    Mib getMib();

    IUtLayer getUtLayerByName(String name);

    IUtLayer getUtLayerByDestinationEntity(long destinationEntityId);

    IVirtualFilestore getFilestore();

    Set<Long> getTransactionIds();

    void register(ICfdpEntitySubscriber s);

    void deregister(ICfdpEntitySubscriber s);

    void request(ICfdpRequest request);

    void dispose();

}
