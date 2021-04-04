package eu.dariolucia.ccsds.cfdp.common;

public class IntegerUtil {

    private IntegerUtil() {
        // Nothing here
    }

    /**
     * This method reads an (unsigned) natural number from an array, starting from the given offset, MSB.
     * If the size is 0, this method returns a null Long.
     * If the size is above 8 or negative, this method throws an exception.
     *
     * If you set the size equal to 8, be aware that this method will provide you with a (potentially) signed long:
     * even if this is a mistake, I doubt that someone will ever use an entity ID or transaction ID with a value larger than 2^63.
     * It that happens, this method will still work, but please don't complain if you are not considering this when your
     * transaction ID will wrap around/becomes negative, ok? :)
     *
     * To be clear before you complain about my lack of precision and professionalism:
     * if you started 1 million transactions per second (yes, 1 million), it would take ca. 292.471 YEARS before you wrap around.
     *
     * @param data the array to read from
     * @param offset the starting offset
     * @param size the length of the encoded integer in bytes
     * @return the integer (as long)
     * @throws CfdpRuntimeException if size > 8 or negative
     */
    public static Long readInteger(byte[] data, int offset, int size) {
        if(size > 8) {
            throw new CfdpRuntimeException("Cannot read an unsigned integer larger than 8, actual is " + size);
        }
        if(size < -1) {
            throw new CfdpRuntimeException("Cannot read an unsigned integer with a negative size, actual is " + size);
        }
        if(size == 0) {
            return null;
        }
        long tempAccumulator = 0;
        for(int i = 0; i < size; ++i) {
            tempAccumulator <<= 8;
            tempAccumulator |= Byte.toUnsignedLong(data[offset + i]);
        }
        return tempAccumulator;
    }

    /**
     * This method encodes an (unsigned) natural number to an array, MSB, using the provided number of octets.
     * If the size is 0, this method returns a 0-length array.
     * If the size is above 8 or negative, this method throws an exception.
     *
     * If you set the size equal to 8, be aware that this method will provide you with a (potentially) signed long:
     * even if this is a mistake, I doubt that someone will ever use an entity ID or transaction ID with a value larger than 2^63.
     * It that happens, this method will still work, but please don't complain if you are not considering this when your
     * transaction ID will wrap around/becomes negative, ok? :)
     *
     * To be clear before you complain about my lack of precision and professionalism:
     * if you started 1 million transactions per second (yes, 1 million), it would take ca. 292.471 YEARS before you wrap around.
     *
     * @param value the value to encode
     * @param size the length of the encoded integer in bytes
     * @return the array containing the encoded integer
     * @throws CfdpRuntimeException if size > 8 or negative
     */
    public static byte[] encodeInteger(long value, int size) {
        if(size > 8) {
            throw new CfdpRuntimeException("Cannot encode an unsigned integer larger than 8, actual is " + size);
        }
        if(size < -1) {
            throw new CfdpRuntimeException("Cannot encode an unsigned integer with a negative size, actual is " + size);
        }
        byte[] toReturn = new byte[size];
        long maskSelector = 0x00000000000000FF;
        for(int i = 0; i < size; ++i) {
            toReturn[size - 1 - i] = (byte) ((value & maskSelector) >>> (i * 8));
            maskSelector <<= 8;
        }
        return toReturn;
    }
}
