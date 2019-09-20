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

package eu.dariolucia.ccsds.encdec.bit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BitEncoderDecoderTest {

    @Test
    public void testLongEncoding() {
        BitEncoderDecoder bed = new BitEncoderDecoder(new byte[2]);
        bed.setNextLongSigned(-3, 5);
        bed.setCurrentBitIndex(0);

        long n = bed.getNextLongSigned(5);
        assertEquals(-3, n);

        bed = new BitEncoderDecoder(new byte[2]);
        bed.setNextLongUnsigned(29, 5);
        bed.setCurrentBitIndex(0);
        n = bed.getNextLongUnsigned(5);
        assertEquals(29, n);

        bed = new BitEncoderDecoder(new byte[2]);
        bed.setNextLongUnsigned(29, 5);
        bed.setNextLongUnsigned(29, 5);
        bed.setCurrentBitIndex(0);

        bed = new BitEncoderDecoder(new byte[2]);
        bed.setNextLongSigned(-3, 5);

        bed.setNextLongSigned(-3, 5);
        bed.setCurrentBitIndex(0);

        byte[] data = bed.getData();
        byte[] expected = new byte[] {
                (byte) 0b11101111,
                (byte) 0b01000000
        };
        assertArrayEquals(expected, data);

        long n1 = bed.getNextLongSigned(5);
        assertEquals(-3, n1);
        n1 = bed.getNextLongSigned(5);
        assertEquals(-3, n1);
    }

    @Test
    public void testIntegerEncoding() {
        BitEncoderDecoder bed = new BitEncoderDecoder(new byte[10]);
        bed.setNextIntegerSigned(-3, 5);
        bed.setCurrentBitIndex(0);
        long n = bed.getNextIntegerSigned(5);
        assertEquals(-3, n);
    }

    @Test
    public void testReset() {
        BitEncoderDecoder bed = new BitEncoderDecoder(new byte[] { 0x55 });
        bed.resetNextBits(6);
        bed.setCurrentBitIndex(0);
        assertEquals(1, bed.getNextIntegerUnsigned(8));
    }

    @Test
    public void testOperations() {
        BitEncoderDecoder bed = new BitEncoderDecoder(80);

        bed.addCurrentBitIndex(-2);
        assertEquals(0, bed.getCurrentBitIndex());

        assertEquals(0, bed.getOffset());
        assertEquals(0, bed.getMaxCurrentBitIndex());
        assertEquals(10, bed.getLength());
        assertEquals(80, bed.getSize());

        try {
            bed.getNextIntegerSigned(33);
            fail("Exception expected");
        } catch(IllegalArgumentException e) {
            // OK
        }

        bed.setNextIntegerSigned(0x55555555, 32);
        bed.clear();
        assertEquals(0, bed.getNextIntegerSigned(32));
    }

    @Test
    public void testDoubleEncoding() {
        double toEnc = 21321.2321232;
        BitEncoderDecoder bed = new BitEncoderDecoder(80);
        bed.setNextDouble(toEnc);
        bed.setCurrentBitIndex(0);
        double n = bed.getNextDouble();
        assertEquals(toEnc, n, 0.0000001);

        toEnc = 21321.2321232;
        bed = new BitEncoderDecoder(new byte[10]);
        bed.setNextFloat((float)toEnc);
        bed.setCurrentBitIndex(0);
        n = bed.getNextFloat();
        assertEquals(toEnc, n, 0.001);

        toEnc = -21321.2321232;
        bed = new BitEncoderDecoder(new byte[10]);
        bed.setNextDouble(toEnc);
        bed.setCurrentBitIndex(0);
        n = bed.getNextDouble();
        assertEquals(toEnc, n, 0.0000001);

        toEnc = -21321.2321232;
        bed = new BitEncoderDecoder(new byte[10]);
        bed.setNextFloat((float)toEnc);
        bed.setCurrentBitIndex(0);
        n = bed.getNextFloat();
        assertEquals(toEnc, n, 0.001);

        toEnc = 21321.2321232;
        bed = new BitEncoderDecoder(new byte[10]);
        bed.setNextMil32Real(toEnc);
        bed.setCurrentBitIndex(0);
        n = bed.getNextMil32Real();
        assertEquals(toEnc, n, 0.01);

        toEnc = -21321.2321232;
        bed = new BitEncoderDecoder(new byte[10]);
        bed.setNextMil32Real(toEnc);
        bed.setCurrentBitIndex(0);
        n = bed.getNextMil32Real();
        assertEquals(toEnc, n, 0.01);

        toEnc = -21321.2321232;
        bed = new BitEncoderDecoder(new byte[10]);
        bed.setNextMil48Real(toEnc);
        bed.setCurrentBitIndex(0);
        n = bed.getNextMil48Real();
        assertEquals(toEnc, n, 0.00001);

        toEnc = -21321.2321232;
        bed = new BitEncoderDecoder(new byte[10]);
        bed.setNextMil48Real(toEnc);
        bed.setCurrentBitIndex(0);
        n = bed.getNextMil48Real();
        assertEquals(toEnc, n, 0.00001);
    }
}