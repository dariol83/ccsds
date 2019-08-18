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

package eu.dariolucia.ccsds.encdec.value;

/**
 * This class contains a set of utility functions to work with String objects.
 */
public class StringUtil {

    /**
     * Convert an hex dump string into a byte array.
     *
     * @param hexDump the hex dump string
     * @return the byte array
     */
    public static byte[] toByteArray(String hexDump) {
        int length = hexDump.length();
        byte[] toReturn = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            toReturn[i / 2] = (byte) ((Character.digit(hexDump.charAt(i), 16) << 4) + Character.digit(hexDump.charAt(i + 1), 16));
        }
        return toReturn;
    }

    // Stackoverflow snippet (Creative Common License):
    // https://stackoverflow.com/questions/9655181/how-to-convert-a-byte-array-to-a-hex-string-in-java
    private final static char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    /**
     * Convert a byte array into an hex dump string.
     *
     * @param data the byte array
     * @return the string (hex dump)
     */
    public static String toHexDump(byte[] data) {
        // Number of characters is twice the number of bytes, 0xdd
        char[] charOfOutput = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            int v = data[i] & 0xFF;
            // MSbits (4) value
            charOfOutput[i * 2] = HEX_ARRAY[v >>> 4];
            // LSbits (4) value
            charOfOutput[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(charOfOutput);
    }
}
