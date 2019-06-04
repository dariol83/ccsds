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
 *  The code to handle the MIL 32-48 bits format, as per MIL-STD-1750A, is from David Overeem (dovereem@cox.net),
 *  licensed Apache License 2.0. Optimizations have been added to avoid recomputing the power of two every time and
 *  dropping support for 16 bits MIL values.
 *
 *  Original code at:
 *  https://gitlab.com/dovereem/xtcetools/blob/2dec59f1e5c4770fc077762ea63c049f64b35922/src/org/xtce/math/MilStd1750A.java
 */
public class MilUtil {

    /**
     * Long conversion table
     */
    private static final long[] nativeTransformMap;

    static {
        nativeTransformMap = new long[41];
        for (int i = 0; i < nativeTransformMap.length; ++i) {
            nativeTransformMap[i] = 1L << i;
        }
    }

    public static double fromMil32Real(long rawBits) {
        long mantissa = convertToNative( ( rawBits & 0xffffff00L ) >> 8, 24 );
        long exponent = convertToNative( ( rawBits & 0x000000ffL ), 8 );
        return (double) mantissa * Math.pow( 2.0, ( exponent - 23.0 ) );
    }

    public static double fromMil48Real(long rawBits) {
        long exponent = convertToNative( ( rawBits & 0x000000ff0000L ) >> 16, 8 );
        long mantissa = convertToNative( ( ( rawBits & 0x00000000ffffL ) ) +
                ( ( rawBits & 0xffffff000000L ) >> 8 ), 40 );
        return (double)mantissa * Math.pow( 2.0, ( exponent - 39.0 ) );
    }

    // I believe this can be done better
    private static long convertToNative(long value, int bits ) {
        long halfValue = nativeTransformMap[ bits - 1 ];
        long fullValue = nativeTransformMap[ bits ];
        if ( value >= halfValue ) {
            return value - fullValue;
        } else {
            return value;
        }
    }

    public static long toMil32Real(double d) {
        long exponent = (long)( Math.ceil(
                Math.log( Math.abs( d ) ) / Math.log( 2.0 ) ) );
        long mantissa = (long)
                ( d / ( Math.pow( 2.0, ( exponent - 23 ) ) ) );
        if ( mantissa == 0x800000L ) { // 24th bit
            mantissa = mantissa / 2;
            exponent++;
        }
        return ( exponent & 0xff ) +
                ( ( mantissa & 0xffffff ) << 8 );
    }

    public static long toMil48Real(double d) {
        long exponent = (long)( Math.ceil(
                Math.log( Math.abs( d ) ) / Math.log( 2.0 ) ) );
        long mantissa = (long)
                ( d / ( Math.pow( 2.0, ( exponent - 39 ) ) ) );
        if ( mantissa == 0x8000000000L ) { // 40th bit
            mantissa = mantissa / 2;
            exponent++;
        }
        return ( ( mantissa & 0xffffff0000L ) << 8  )  +
                ( ( exponent & 0xffL ) << 16 ) +
                ( mantissa & 0xffffL );
    }
}
