package eu.dariolucia.ccsds.cfdp.entity;

import eu.dariolucia.ccsds.cfdp.entity.internal.CfdpEntity;
import eu.dariolucia.ccsds.cfdp.entity.request.ICfdpRequest;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.ICfdpSegmentationStrategy;
import eu.dariolucia.ccsds.cfdp.filestore.IVirtualFilestore;
import eu.dariolucia.ccsds.cfdp.mib.Mib;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayer;

import java.util.Arrays;
import java.util.Collection;

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

    void register(ICfdpEntitySubscriber s);

    void deregister(ICfdpEntitySubscriber s);

    void request(ICfdpRequest request);

    void dispose();
}
