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

import eu.dariolucia.ccsds.encdec.value.MilUtil;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * This class is used to manage the reading/writing of a stream of bits on top of an underlying byte array.
 *
 * The code in this class is an extension of the original Bit-lib4j (https://github.com/devnied/Bit-lib4j)
 * BitUtils class by Millau Julien, licensed Apache License 2.0. The code was modified to add the following
 * capabilities:
 * - access an offset and given length to be used for the byte array that is backing up the storage
 * - keep track of the used bits
 * - improve handling of unsigned integers
 * - pre-compute a bitmask lookup table to speed up extractions
 * - support MIL-STD-1750A format for real numbers
 *
 * Original code at:
 * https://github.com/devnied/Bit-lib4j
 */
public final class BitEncoderDecoder {
    /**
     * Constant for byte size
     */
    private static final int BYTE_SIZE = Byte.SIZE;
    /**
     * Constant for byte size (float)
     */
    private static final float BYTE_SIZE_F = Byte.SIZE;
    /**
     * 255 init value
     */
    private static final int DEFAULT_VALUE = 0xFF;
    /**
     * Constant for the default charset
     */
    private static final Charset DEFAULT_CHARSET = Charset.forName("ASCII");
    /**
     * Lookup table for masks
     */
    private static final byte[][] maskTable = new byte[8][65];
    /**
     * Marker for table initialisation
     */
    private static volatile boolean tablesInitialised = false;

    /**
     * Table of read byte
     */
    private final byte[] byteTab;

    /**
     * Offset from the beginning of the table
     */
    private final int offset;

    /**
     * Number of the available bytes in the table
     */
    private final int length;

    /**
     * Current index
     */
    private int currentBitIndex;

    /**
     * Maximum value reached by currentBitIndex
     */
    private int maxCurrentBitIndex;

    /**
     * Size in bit of the byte tab
     */
    private final int size;

    /**
     * Constructor of the class. No data copy performed for performance reasons.
     *
     * @param pByte byte read
     */
    public BitEncoderDecoder(byte[] pByte) {
        this(pByte, 0, pByte.length);
    }

    public BitEncoderDecoder(byte[] pByte, int offset, int length) {
        if (offset + length > pByte.length) {
            throw new IllegalArgumentException("Size of provided byte array is " + pByte.length + ", but offset + length is " + (offset + length));
        }
        this.byteTab = pByte;
        this.offset = offset;
        this.length = length;
        this.size = length * BYTE_SIZE;
        initTables();
    }

    /**
     * Constructor for empty byte tab
     *
     * @param pSize the size of the tab in bit
     */
    public BitEncoderDecoder(final int pSize) {
        byteTab = new byte[(int) Math.ceil(pSize / BYTE_SIZE_F)];
        offset = 0;
        length = byteTab.length;
        size = pSize;
        initTables();
    }

    /**
     * This method initialises the mask table, if not already done
     */
    private static void initTables() {
        // Let's spare a lock... useful?
        if(tablesInitialised) {
            return;
        }
        synchronized (BitEncoderDecoder.class) {
            if(tablesInitialised) {
                return;
            }
            for (int i = 0; i < Byte.SIZE; ++i) {
                for (int j = 0; j <= Long.SIZE; ++j) {
                    maskTable[i][j] = computeMask(i, j);
                }
            }
            tablesInitialised = true;
        }
    }

    /**
     * This method is used to compute a mask dynamically
     *
     * @param pIndex  start index of the mask
     * @param pLength size of mask
     * @return the mask in byte
     */
    private static byte computeMask(final int pIndex, final int pLength) {
        byte ret = (byte) DEFAULT_VALUE;
        // Add X 0 to the left
        ret = (byte) (ret << pIndex);
        ret = (byte) ((ret & DEFAULT_VALUE) >> pIndex);
        // Add X 0 to the right
        int dec = BYTE_SIZE - (pLength + pIndex);
        if (dec > 0) {
            ret = (byte) (ret >> dec);
            ret = (byte) (ret << dec);
        }
        return ret;
    }

    /**
     * Add pIndex to the current value of bitIndex
     *
     * @param pIndex the value to add to bitIndex
     */
    public void addCurrentBitIndex(final int pIndex) {
        incrementBitIndex(pIndex);
        if (currentBitIndex < 0) {
            currentBitIndex = 0;
        }
    }

    /**
     * Getter for the currentBitIndex
     *
     * @return the currentBitIndex
     */
    public int getCurrentBitIndex() {
        return currentBitIndex;
    }

    /**
     * Method to get all data. Direct reference due to performance reasons.
     *
     * @return a byte tab which contain all data
     */
    public byte[] getData() {
        return byteTab;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public int getMaxCurrentBitIndex() {
        return maxCurrentBitIndex;
    }

    /**
     * This method is used to get a mask dynamically
     *
     * @param pIndex  start index of the mask
     * @param pLength size of mask
     * @return the mask in byte
     */
    public byte getMask(final int pIndex, final int pLength) {
        return maskTable[pIndex][pLength];
    }

    /**
     * Get the Next boolean (read 1 bit)
     *
     * @return true or false
     */
    public boolean getNextBoolean() {
        boolean ret = false;
        if (getNextIntegerUnsigned(1) == 1) {
            ret = true;
        }
        return ret;
    }

    /**
     * Get the next float (read 32 bits)
     *
     * @return the next float
     */
    public float getNextFloat() {
        return Float.intBitsToFloat(getNextIntegerUnsigned(Float.SIZE));
    }

    /**
     * Get the next double (read 64 bits)
     *
     * @return the next float
     */
    public double getNextDouble() {
        return Double.longBitsToDouble(getNextLongUnsigned(Double.SIZE));
    }

    /**
     * Get the next real encoded as MIL-STD-1750A (read 32 bits)
     *
     * @return the next float
     */
    public double getNextMil32Real() {
        long rawBits = getNextLongUnsigned(32);
        return MilUtil.fromMil32Real(rawBits);
    }

    /**
     * Get the next real encoded as MIL-STD-1750A (read 48 bits)
     *
     * @return the next float
     */
    public double getNextMil48Real() {
        long rawBits = getNextLongUnsigned(48);
        return MilUtil.fromMil48Real(rawBits);
    }

    /**
     * Method used to get the next byte and shift read data to the beginning of
     * the array.<br>
     * (Ex 00110000b if we start read 2 bit at index 2 the data returned will be
     * 11000000b)
     *
     * @param pSize the size in bit to read
     * @return the byte array read
     */
    public byte[] getNextByte(final int pSize) {
        return getNextByte(pSize, true);
    }

    /**
     * Method to get The next bytes with the specified size
     *
     * @param pSize  the size in bit to read
     * @param pShift boolean to indicate if the data read will be shift to the
     *               left.<br>
     *               <ul>
     *               <li>if true : (Ex 10110000b if we start read 2 bit at index 2
     *               the returned data will be 11000000b)</li>
     *               <li>if false : (Ex 10110000b if we start read 2 bit at index 2
     *               the returned data will be 00110000b)</li>
     *               </ul>
     * @return a byte array
     */
    public byte[] getNextByte(final int pSize, final boolean pShift) {
        byte[] tab = new byte[(int) Math.ceil(pSize / BYTE_SIZE_F)];

        if (currentBitIndex % BYTE_SIZE != 0) {
            int index = 0;
            int max = currentBitIndex + pSize;
            while (currentBitIndex < max) {
                int mod = currentBitIndex % BYTE_SIZE;
                int modTab = index % BYTE_SIZE;
                int length = Math.min(max - currentBitIndex, Math.min(BYTE_SIZE - mod, BYTE_SIZE - modTab));
                byte val = (byte) (byteTab[offset + currentBitIndex / BYTE_SIZE] & getMask(mod, length));
                if (pShift || pSize % BYTE_SIZE == 0) {
                    if (mod != 0) {
                        val = (byte) (val << Math.min(mod, BYTE_SIZE - length));
                    } else {
                        val = (byte) ((val & DEFAULT_VALUE) >> modTab);
                    }
                }
                tab[index / BYTE_SIZE] |= val;
                incrementBitIndex(length);
                index += length;
            }
            if (!pShift && pSize % BYTE_SIZE != 0) {
                tab[tab.length - 1] = (byte) (tab[tab.length - 1] & getMask((max - pSize - 1) % BYTE_SIZE, BYTE_SIZE));
            }
        } else {
            System.arraycopy(byteTab, offset + currentBitIndex / BYTE_SIZE, tab, 0, tab.length);
            int val = pSize % BYTE_SIZE;
            if (val == 0) {
                val = BYTE_SIZE;
            }
            tab[tab.length - 1] = (byte) (tab[tab.length - 1] & getMask(currentBitIndex % BYTE_SIZE, val));
            incrementBitIndex(pSize);
        }

        return tab;
    }

    private void incrementBitIndex(long amount) {
        this.currentBitIndex += amount;
        if(this.currentBitIndex > this.maxCurrentBitIndex) {
            this.maxCurrentBitIndex = this.currentBitIndex;
        }
    }

    /**
     * Method used to get get a signed long with the specified size
     *
     * @param pLength length of long to get (must be lower than 64)
     * @return the long value
     */
    public long getNextLongSigned(final int pLength) {
        if (pLength > Long.SIZE) {
            throw new IllegalArgumentException("Long overflow with length > 64");
        }
        long decimal = getNextLongUnsigned(pLength);
        long signMask = 1 << pLength - 1;

        if ((decimal & signMask) != 0) {
            return -(signMask - (signMask ^ decimal));
        }
        return decimal;
    }

    /**
     * Method used to get get a signed integer with the specified size
     *
     * @param pLength the length of the integer (must be lower than 32)
     * @return the integer value
     */
    public int getNextIntegerSigned(final int pLength) {
        if (pLength > Integer.SIZE) {
            throw new IllegalArgumentException("Integer overflow with length > 32");
        }
        return (int) getNextLongSigned(pLength);
    }

    /**
     * This method is used to get a long with the specified size
     * <p>
     * Be careful with java long bit sign. This method doesn't handle signed values.<br>
     * For that, @see BitUtils.getNextLongSigned()
     *
     * @param pLength the length of the data to read in bit
     * @return an long
     */
    public long getNextLongUnsigned(final int pLength) {
        // allocate Size of Integer
        ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE);
        // final value
        long finalValue = 0;
        // Incremental value
        long currentValue;
        // Size to read
        int readSize = pLength;
        // length max of the index
        int max = currentBitIndex + pLength;
        while (currentBitIndex < max) {
            int mod = currentBitIndex % BYTE_SIZE;
            // apply the mask to the selected byte
            currentValue = byteTab[offset + currentBitIndex / BYTE_SIZE] & getMask(mod, readSize) & DEFAULT_VALUE;
            // Shift right the read value
            int dec = Math.max(BYTE_SIZE - (mod + readSize), 0);
            currentValue = (currentValue & DEFAULT_VALUE) >>> dec & DEFAULT_VALUE;
            // Shift left the previously read value and add the current value
            finalValue = finalValue << Math.min(readSize, BYTE_SIZE) | currentValue;
            // calculate read value size
            int val = BYTE_SIZE - mod;
            // Decrease the size left
            readSize = readSize - val;
            currentBitIndex = Math.min(currentBitIndex + val, max);
            if(this.currentBitIndex > this.maxCurrentBitIndex) {
                this.maxCurrentBitIndex = this.currentBitIndex;
            }
        }
        buffer.putLong(finalValue);
        // reset the current bytebuffer index to 0
        buffer.rewind();
        // return integer
        return buffer.getLong();
    }

    /**
     * This method is used to get an integer with the specified size
     * <p>
     * Be careful with java integer bit sign. This method doesn't handle signed values.<br>
     * For that, @see BitUtils.getNextIntegerSigned()
     *
     * @param pLength the length of the data to read in bit
     * @return an integer
     */
    public int getNextIntegerUnsigned(final int pLength) {
        return (int) (getNextLongUnsigned(pLength));
    }

    /**
     * This method is used to get the next String with the specified size with
     * the charset ASCII
     *
     * @param pSize the length of the string in bit
     * @return the string
     */
    public String getNextString(final int pSize) {
        return getNextString(pSize, DEFAULT_CHARSET);
    }

    /**
     * This method is used to get the next String with the specified size
     *
     * @param pSize    the length of the string in bit
     * @param pCharset the charset
     * @return the string
     */
    public String getNextString(final int pSize, final Charset pCharset) {
        return new String(getNextByte(pSize, true), pCharset);
    }

    /**
     * Method used to get the size of the bit array
     *
     * @return the size in bits of the current bit array
     */
    public int getSize() {
        return size;
    }

    /**
     * Reset the current bit index to the initial position
     */
    public void reset() {
        setCurrentBitIndex(0);
    }

    /**
     * Method used to clear data and reset current bit index
     */
    public void clear() {
        Arrays.fill(byteTab, offset, offset + length, (byte) 0);
        reset();
    }

    /**
     * Set to 0 the next N bits
     *
     * @param pLength the number of bit to set at 0
     */
    public void resetNextBits(final int pLength) {
        int max = currentBitIndex + pLength;
        while (currentBitIndex < max) {
            int mod = currentBitIndex % BYTE_SIZE;
            int length = Math.min(max - currentBitIndex, BYTE_SIZE - mod);
            byteTab[offset + currentBitIndex / BYTE_SIZE] &= ~getMask(mod, length);
            incrementBitIndex(length);
        }
    }

    /**
     * Setter currentBitIndex
     *
     * @param pCurrentBitIndex the currentBitIndex to set
     */
    public void setCurrentBitIndex(final int pCurrentBitIndex) {
        currentBitIndex = pCurrentBitIndex;
        if(this.currentBitIndex > this.maxCurrentBitIndex) {
            this.maxCurrentBitIndex = this.currentBitIndex;
        }
    }

    /**
     * Method to set a boolean
     *
     * @param pBoolean the boolean to set
     */
    public void setNextBoolean(final boolean pBoolean) {
        if (pBoolean) {
            setNextIntegerUnsigned(1, 1);
        } else {
            setNextIntegerUnsigned(0, 1);
        }
    }


    public void setNextFloat(float f) {
        setNextIntegerUnsigned(Float.floatToIntBits(f), Float.SIZE);
    }

    public void setNextDouble(double d) {
        setNextLongUnsigned(Double.doubleToLongBits(d), Double.SIZE);
    }

    public void setNextMil32Real(double d) {
        setNextLongUnsigned(MilUtil.toMil32Real(d), 32);
    }

    public void setNextMil48Real(double d) {
        setNextLongUnsigned(MilUtil.toMil48Real(d), 48);
    }

    /**
     * Method to write bytes with the max length
     *
     * @param pValue  the value to write
     * @param pLength the length of the data in bits
     */
    public void setNextByte(final byte[] pValue, final int pLength) {
        setNextByte(pValue, pLength, true);
    }

    /**
     * Method to write bytes with the max length
     *
     * @param pValue     the value to write
     * @param pLength    the length of the data in bits
     * @param pPadBefore if true pad with 0
     */
    public void setNextByte(final byte[] pValue, final int pLength, final boolean pPadBefore) {
        int totalSize = (int) Math.ceil(pLength / BYTE_SIZE_F);
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        int size = Math.max(totalSize - pValue.length, 0);
        if (pPadBefore) {
            for (int i = 0; i < size; i++) {
                buffer.put((byte) 0);
            }
        }
        buffer.put(pValue, 0, Math.min(totalSize, pValue.length));
        if (!pPadBefore) {
            for (int i = 0; i < size; i++) {
                buffer.put((byte) 0);
            }
        }
        byte[] tab = buffer.array();
        if (currentBitIndex % BYTE_SIZE != 0) {
            int index = 0;
            int max = currentBitIndex + pLength;
            while (currentBitIndex < max) {
                int mod = currentBitIndex % BYTE_SIZE;
                int modTab = index % BYTE_SIZE;
                int length = Math.min(max - currentBitIndex, Math.min(BYTE_SIZE - mod, BYTE_SIZE - modTab));
                byte val = (byte) (tab[index / BYTE_SIZE] & getMask(modTab, length));
                if (mod == 0) {
                    val = (byte) (val << Math.min(modTab, BYTE_SIZE - length));
                } else {
                    val = (byte) ((val & DEFAULT_VALUE) >> mod);
                }
                byteTab[offset + currentBitIndex / BYTE_SIZE] |= val;
                incrementBitIndex(length);
                index += length;
            }

        } else {
            System.arraycopy(tab, 0, byteTab, offset + currentBitIndex / BYTE_SIZE, tab.length);
            incrementBitIndex(pLength);
        }
    }

    /**
     * Add Long to the current position with the specified size
     * <p>
     * Be careful with java long bit sign
     *
     * @param pValue  the value to set
     * @param pLength the length of the long
     */
    public void setNextLongUnsigned(final long pValue, final int pLength) {
        if (pLength > Long.SIZE) {
            throw new IllegalArgumentException("Long overflow with length > 64");
        }
        setNextValue(pValue, pLength, Long.SIZE - 1);
    }

    /**
     * Add Value to the current position with the specified size
     *
     * @param pValue   value to add
     * @param pLength  length of the value
     * @param pMaxSize max size in bits
     */
    protected void setNextValue(final long pValue, final int pLength, final int pMaxSize) {
        long value = pValue;
        // Set to max value if pValue cannot be stored on pLength bits.
        long bitMax = (long) Math.pow(2, Math.min(pLength, pMaxSize));
        if (pValue > bitMax) {
            value = bitMax - 1;
        }
        // size to write
        int writeSize = pLength;
        while (writeSize > 0) {
            // modulo
            int mod = currentBitIndex % BYTE_SIZE;
            byte ret;
            if (mod == 0 && writeSize <= BYTE_SIZE || pLength < BYTE_SIZE - mod) {
                // shift left value
                ret = (byte) (value << BYTE_SIZE - (writeSize + mod));
            } else {
                // shift right
                // long length = Long.toBinaryString(value).length();
                long length = Long.SIZE - Long.numberOfLeadingZeros(value);
                ret = (byte) (value >> writeSize - length - (BYTE_SIZE - length - mod));
            }
            byteTab[offset + currentBitIndex / BYTE_SIZE] |= ret;
            long val = Math.min(writeSize, BYTE_SIZE - mod);
            writeSize -= val;
            incrementBitIndex(val);
        }
    }

    /**
     * Add Integer to the current position with the specified size
     * <p>
     * Be careful with java integer bit sign
     *
     * @param pValue  the value to set
     * @param pLength the length of the integer
     */
    public void setNextIntegerUnsigned(final int pValue, final int pLength) {

        if (pLength > Integer.SIZE) {
            throw new IllegalArgumentException("Integer overflow with length > 32");
        }

        setNextValue(pValue, pLength, Integer.SIZE - 1);
    }

    /**
     * Add Integer to the current position with the specified size and sign
     *
     * @param pValue  the value to set
     * @param pLength the length of the integer
     */
    public void setNextIntegerSigned(final int pValue, final int pLength) {

        if (pLength > Integer.SIZE) {
            throw new IllegalArgumentException("Integer overflow with length > 32");
        }
        // Move the sign bit to fit in pLength
        int mask = -1;
        mask <<= Integer.SIZE - (pLength - 1);
        mask >>>= Integer.SIZE - (pLength - 1);
        int newVal = pValue & mask;
        // Check the sign
        if(pValue < 0) {
            mask = 1;
            mask <<= pLength - 1;
            newVal |= mask;
        }
        setNextValue(newVal, pLength, Integer.SIZE);
    }

    /**
     * Add Long to the current position with the specified size and sign
     *
     * @param pValue  the value to set
     * @param pLength the length of the long
     */
    public void setNextLongSigned(final long pValue, final int pLength) {

        if (pLength > Long.SIZE) {
            throw new IllegalArgumentException("Long overflow with length > 64");
        }
        // Move the sign bit to fit in pLength
        long mask = -1;
        mask <<= Long.SIZE - (pLength - 1);
        mask >>>= Long.SIZE - (pLength - 1);
        long newVal = pValue & mask;
        // Check the sign
        if(pValue < 0) {
            mask = 1;
            mask <<= pLength - 1;
            newVal |= mask;
        }
        setNextValue(newVal, pLength, Long.SIZE);
    }

    /**
     * Method to write String
     *
     * @param pValue  the string to write
     * @param pLength the length of the integer
     */
    public void setNextString(final String pValue, final int pLength) {
        setNextString(pValue, pLength, true);
    }

    /**
     * Method to write a String
     *
     * @param pValue        the string to write
     * @param pLength       the string length
     * @param pPaddedBefore indicate if the string is padded before or after
     */
    public void setNextString(final String pValue, final int pLength, final boolean pPaddedBefore) {
        setNextByte(pValue.getBytes(DEFAULT_CHARSET), pLength, pPaddedBefore);
    }

}