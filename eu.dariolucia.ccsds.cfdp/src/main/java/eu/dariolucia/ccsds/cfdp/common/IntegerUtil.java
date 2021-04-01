package eu.dariolucia.ccsds.cfdp.common;

public class IntegerUtil {

    private IntegerUtil() {
        // Nothing here
    }

    public static Long readInteger(byte[] data, int offset, int size) {
        if(size > 8) {
            throw new CfdpRuntimeException("Cannot read an unsigned integer larger than 8, actual is " + size);
        }
        if(size == 0) {
            return null;
        }
        long tempAccumulator = 0;
        for(int i = 0; i < size; ++i) {
            tempAccumulator |= Byte.toUnsignedLong(data[offset + i]);
            tempAccumulator <<= 8;
        }
        return tempAccumulator;
    }
}
