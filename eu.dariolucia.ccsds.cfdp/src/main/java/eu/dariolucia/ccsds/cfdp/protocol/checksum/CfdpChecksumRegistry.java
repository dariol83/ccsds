package eu.dariolucia.ccsds.cfdp.protocol.checksum;

import eu.dariolucia.ccsds.cfdp.common.CfdpStandardComplianceError;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class CfdpChecksumRegistry {

    public static final int MODULAR_CHECKSUM_TYPE = 0;
    public static final int NULL_CHECKSUM_TYPE = 15;

    private CfdpChecksumRegistry() {
        // Private constructor
    }

    private static final Map<Integer, ICfdpChecksumFactory> type2factory = new HashMap<>();

    public static synchronized ICfdpChecksumFactory getChecksum(int type) throws CfdpUnsupportedChecksumType {
        ServiceLoader<ICfdpChecksumFactory> loader = ServiceLoader.load(ICfdpChecksumFactory.class);
        if(type2factory.isEmpty()) {
            // Initialise
            type2factory.putAll(loader.stream().map(ServiceLoader.Provider::get).collect(Collectors.toMap(ICfdpChecksumFactory::type, Function.identity())));
        }
        ICfdpChecksumFactory factory = type2factory.get(type);
        if (factory == null) {
            throw new CfdpUnsupportedChecksumType("Type " + type + " not supported");
        } else {
            return factory;
        }
    }

    public static ICfdpChecksumFactory getNullChecksum() {
        try {
            return getChecksum(NULL_CHECKSUM_TYPE);
        } catch (CfdpUnsupportedChecksumType e) {
            // This should never happen
            throw new CfdpStandardComplianceError("The null checksum is expected to be part of the deployment");
        }
    }

    public static ICfdpChecksumFactory getModularChecksum() {
        try {
            return getChecksum(MODULAR_CHECKSUM_TYPE);
        } catch (CfdpUnsupportedChecksumType e) {
            // This should never happen
            throw new CfdpStandardComplianceError("The modular checksum is expected to be part of the deployment");
        }
    }
}
