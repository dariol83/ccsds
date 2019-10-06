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

import eu.dariolucia.ccsds.encdec.bit.BitEncoderDecoder;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Utility class that handles TAI/UTC conversion (including leap second) and time formats as defined by the CCSDS
 * 301.0-B-4, 3.1, 3.2, 3.3.
 */
public class TimeUtil {

    private TimeUtil() {
        // Private constructor
    }

    /**
     * Difference between 1st Jan 1970 and 1st Jan 1958 in seconds
     */
    public static final long DIFFERENCE_1958_TO_1970_SECS = 378691200;

    /**
     * UTC leap second table (till 2017), as per https://en.wikipedia.org/wiki/Leap_second
     */
    private static final long[][] UTC_TO_LEAP = {
            {new GregorianCalendar(1972, Calendar.JANUARY, 1, 0, 0).getTimeInMillis()/1000 , 10},
            {new GregorianCalendar(1972, Calendar.JULY, 1, 0, 0).getTimeInMillis()/1000 , 11},
            {new GregorianCalendar(1973, Calendar.JANUARY, 1, 0, 0).getTimeInMillis()/1000 , 12},
            {new GregorianCalendar(1974, Calendar.JANUARY, 1, 0, 0).getTimeInMillis()/1000 , 13},
            {new GregorianCalendar(1975, Calendar.JANUARY, 1, 0, 0).getTimeInMillis()/1000 , 14},
            {new GregorianCalendar(1976, Calendar.JANUARY, 1, 0, 0).getTimeInMillis()/1000 , 15},
            {new GregorianCalendar(1977, Calendar.JANUARY, 1, 0, 0).getTimeInMillis()/1000 , 16},
            {new GregorianCalendar(1978, Calendar.JANUARY, 1, 0, 0).getTimeInMillis()/1000 , 17},
            {new GregorianCalendar(1979, Calendar.JANUARY, 1, 0, 0).getTimeInMillis()/1000 , 18},
            {new GregorianCalendar(1980, Calendar.JANUARY, 1, 0, 0).getTimeInMillis()/1000 , 19},
            {new GregorianCalendar(1981, Calendar.JULY, 1, 0, 0).getTimeInMillis()/1000 , 20},
            {new GregorianCalendar(1982, Calendar.JULY, 1, 0, 0).getTimeInMillis()/1000 , 21},
            {new GregorianCalendar(1983, Calendar.JULY, 1, 0, 0).getTimeInMillis()/1000 , 22},
            {new GregorianCalendar(1985, Calendar.JULY, 1, 0, 0).getTimeInMillis()/1000 , 23},
            {new GregorianCalendar(1988, Calendar.JANUARY, 1, 0, 0).getTimeInMillis()/1000 , 24},
            {new GregorianCalendar(1990, Calendar.JANUARY, 1, 0, 0).getTimeInMillis()/1000 , 25},
            {new GregorianCalendar(1991, Calendar.JANUARY, 1, 0, 0).getTimeInMillis()/1000 , 26},
            {new GregorianCalendar(1992, Calendar.JULY, 1, 0, 0).getTimeInMillis()/1000 , 27},
            {new GregorianCalendar(1993, Calendar.JULY, 1, 0, 0).getTimeInMillis()/1000 , 28},
            {new GregorianCalendar(1994, Calendar.JULY, 1, 0, 0).getTimeInMillis()/1000 , 29},
            {new GregorianCalendar(1996, Calendar.JANUARY, 1, 0, 0).getTimeInMillis()/1000 , 30},
            {new GregorianCalendar(1997, Calendar.JULY, 1, 0, 0).getTimeInMillis()/1000 , 31},
            {new GregorianCalendar(1999, Calendar.JANUARY, 1, 0, 0).getTimeInMillis()/1000 , 32},
            {new GregorianCalendar(2006, Calendar.JANUARY, 1, 0, 0).getTimeInMillis()/1000 , 33},
            {new GregorianCalendar(2009, Calendar.JANUARY, 1, 0, 0).getTimeInMillis()/1000 , 34},
            {new GregorianCalendar(2012, Calendar.JULY, 1, 0, 0).getTimeInMillis()/1000 , 35},
            {new GregorianCalendar(2015, Calendar.JULY, 1, 0, 0).getTimeInMillis()/1000 , 36},
            {new GregorianCalendar(2017, Calendar.JANUARY, 1, 0, 0).getTimeInMillis()/1000 , 37}
    };

    public static boolean isCDS(byte firstBytePcode) {
        return (firstBytePcode & (byte) 0x40) != 0;
    }

    /**
     * Encode the provided instant (in UTC time, epoch 1st Jan 1970) to CUC. If an agencyEpoch is provided, then
     * the time will be encoded with reference to the provided agency epoch. If the agencyEpoch is null, then the epoch
     * used to encode the time will be Level 1 (as specified by CCSDS 301.0-B-4).
     * coarseOctets and fineOctets specify the number of octets used by the basic time unit and fractional time unit.
     *
     * If encodePField is set, then the provided information is encoded in the P Field. Otherwise only the T Field is
     * encoded.
     *
     * @param time the UTC time to encode
     * @param agencyEpoch the agency epoch (Level 2
     * @param coarseOctets the number of octets for basic time unit encoding
     * @param fineOctets the number of octets for fractional time unit encoding
     * @param encodePField true if P Field must be encoded, false if only the T Field must be encoded
     * @return the encoded CUC time
     */
    public static byte[] toCUC(Instant time, Instant agencyEpoch, int coarseOctets, int fineOctets, boolean encodePField) {
        int extraByte = coarseOctets > 4 || fineOctets > 3 ? 1 : 0;
        int fLen = coarseOctets + fineOctets + (encodePField ? 1 + (extraByte) : 0);
        ByteBuffer bb = ByteBuffer.allocate(fLen);
        if(encodePField) {
            boolean secondOctet = coarseOctets > 4 || fineOctets > 3;
            byte pField = secondOctet ? (byte) 0x01 : (byte) 0x00;
            pField <<= 3;
            if(agencyEpoch == null) {
                pField |= 0x01;
            } else {
                pField |= 0x02;
            }
            pField <<= 2;
            if(coarseOctets - 1 <= 3) {
                pField |= (byte) (coarseOctets - 1);
            } else {
                pField |= (byte) 0x03;
            }
            pField <<= 2;
            if(fineOctets <= 3) {
                pField |= (byte) (fineOctets);
            } else {
                pField |= (byte) 0x03;
            }
            bb.put(pField);
            if(secondOctet) {
                byte pField2 = 0;
                int remainingCoarseOctets = Math.max(0, coarseOctets - 4);
                int remainingFineOctets = Math.max(0, fineOctets - 3);
                pField2 |= (byte) (remainingCoarseOctets);
                pField2 <<= 3;
                pField2 |= (byte) (remainingFineOctets);
                pField2 <<= 2;
                bb.put(pField2);
            }
        }

        // Convert the time to TAI time
        long tai = toTAI(time.getEpochSecond());
        long nanosec = time.getNano();
        // If agencyEpoch is specified, then shift everything to agency epoch (seconds only)
        if(agencyEpoch != null) {
            tai -= toTAI(agencyEpoch.getEpochSecond());
        } else {
            // If not specified, then epoch is 1st Jan 1958
            tai += DIFFERENCE_1958_TO_1970_SECS;
        }
        // Encode tai in coarseOctets
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(tai);
        byte[] encodedCoarse = buffer.array();
        bb.put(encodedCoarse, Long.BYTES - coarseOctets, coarseOctets);
        // Encode nanosec in fine octets: it is assumed that the maximum resolution of the fine octets determine
        // the quantization of the fractional time unit, i.e. a value of all 1s (in binary) would be equivalent to a
        // second - quantum. Therefore 1 quantum is equal to 1.000.000.000 nanosecond divided by 2^bit_resolution.
        double quantum = 1000000000.0 / (int) Math.pow(2, fineOctets * 8.0);
        // Let's compute how many quantums are in nanosec. The maximum error is equal to quantum - 1 nanosec.
        long quantums = Math.round(nanosec / quantum);
        // Encode quantums in fine octets
        ByteBuffer buffer2 = ByteBuffer.allocate(Long.BYTES);
        buffer2.putLong(quantums);
        byte[] encodedFine = buffer2.array();
        bb.put(encodedFine, Long.BYTES - fineOctets, fineOctets);

        return bb.array();
    }

    /**
     * Decode a CUC time to UTC instant, assuming the presence of the P Field and an agency epoch. If the agency
     * epoch is null, then a Level 1 Time Code is assumed. If Level 1 Time Code is reported by the P Field, the agency
     * epoch is ignored. If the P Field reports a Level 2 Time Code and the agency epoch is null, an exception is thrown.
     *
     * @param decoder the bit decoder that is handling the raw data
     * @param agencyEpoch the agency epoch, can be null
     * @return the corresponding UTC time
     */
    public static Instant fromCUC(BitEncoderDecoder decoder, Instant agencyEpoch) {
        byte first = (byte) decoder.getNextIntegerSigned(Byte.SIZE);
        boolean additionalPfield = false;
        if(first >> 7 != 0) {
            additionalPfield = true;
        }
        int timeCodeLevel = (first & (byte) 0x70) >> 4;
        if(timeCodeLevel != 1 && agencyEpoch == null) {
            throw new IllegalArgumentException("P Field reports Level 2 Time Code but no agency epoch supplied, cannot decode");
        } else if(timeCodeLevel == 1) {
            agencyEpoch = null;
        }
        int coarseOctets = ((first & (byte) 0x0C) >> 2) + 1;
        int fineOctets = (first & (byte) 0x03);
        if(additionalPfield) {
            byte second = (byte) decoder.getNextIntegerUnsigned(Byte.SIZE);
            coarseOctets += (second & 0x60) >> 5;
            fineOctets += (second & 0x1C) >> 2;
        }
        // Read coarse + fine octets
        byte[] tField = decoder.getNextByte(Byte.SIZE * (coarseOctets + fineOctets));
        return fromCUC(ByteBuffer.wrap(tField), agencyEpoch, coarseOctets, fineOctets);
    }

    /**
     * Decode a CUC time to UTC instant, assuming the presence of the P Field and Level 1 Time Code. If the P Field
     * reports a Level 2 Time Code, an exception is thrown.
     *
     * @param cuc the CUC encoded time with P Field
     * @return the corresponding UTC time
     */
    public static Instant fromCUC(byte[] cuc) {
        return fromCUC(cuc, null);
    }

    /**
     * Decode a CUC time to UTC instant, assuming the presence of the P Field and an agency epoch. If the agency
     * epoch is null, then a Level 1 Time Code is assumed. If Level 1 Time Code is reported by the P Field, the agency
     * epoch is ignored. If the P Field reports a Level 2 Time Code and the agency epoch is null, an exception is thrown.
     *
     * @param cuc the CUC encoded time with P Field
     * @param agencyEpoch the agency epoch, can be null
     * @return the corresponding UTC time
     */
    public static Instant fromCUC(byte[] cuc, Instant agencyEpoch) {
        ByteBuffer bb = ByteBuffer.wrap(cuc);
        byte first = bb.get();
        boolean additionalPfield = false;
        if(first >> 7 != 0) {
            additionalPfield = true;
        }
        int timeCodeLevel = (first & (byte) 0x70) >> 4;
        if(timeCodeLevel != 1 && agencyEpoch == null) {
            throw new IllegalArgumentException("P Field reports Level 2 Time Code but no agency epoch supplied, cannot decode");
        } else if(timeCodeLevel == 1) {
            agencyEpoch = null;
        }
        int coarseOctets = ((first & (byte) 0x0C) >> 2) + 1;
        int fineOctets = (first & (byte) 0x03);
        if(additionalPfield) {
            byte second = bb.get();
            coarseOctets += (second & 0x60) >> 5;
            fineOctets += (second & 0x1C) >> 2;
        }
        return fromCUC(bb, agencyEpoch, coarseOctets, fineOctets);
    }

    /**
     * Decode a CUC time to UCT instant, assuming no presence of P Field. If the agency epoch is not present, then
     * the code assumes a Level 1 Time Code.
     *
     * @param cuc the CUC encoded time without P Field
     * @param agencyEpoch the agency epoch, can be null
     * @param coarseOctets the number of octets used for basic time unit encoding
     * @param fineOctets the number of octets used for fractional time unit encoding
     * @return the corresponding UTC time
     */
    public static Instant fromCUC(byte[] cuc, Instant agencyEpoch, int coarseOctets, int fineOctets) {
        return fromCUC(ByteBuffer.wrap(cuc), agencyEpoch, coarseOctets, fineOctets);
    }

    private static Instant fromCUC(ByteBuffer tFieldWrapped, Instant agencyEpoch, int coarseOctets, int fineOctets) {
        // Translate the coarseOctets into a long number
        long basicTimeUnit = 0;
        int power = coarseOctets - 1;
        for(int i = 0; i < coarseOctets; ++i) {
            basicTimeUnit += Byte.toUnsignedInt(tFieldWrapped.get()) * Math.pow(256, power);
            --power;
        }
        // Compute the quantum
        double quantum = 1000000000.0 / (int) Math.pow(2, fineOctets * 8.0);
        // Now, translate the fineOctets into a long number
        long fractionalTimeUnit = 0;

        power = fineOctets - 1;
        for(int i = 0; i < fineOctets; ++i) {
            fractionalTimeUnit += Byte.toUnsignedInt(tFieldWrapped.get()) * Math.pow(256, power);
            --power;
        }
        // Calculate the time in nanoseconds
        fractionalTimeUnit = (long) (fractionalTimeUnit * quantum);

        if(agencyEpoch == null) {
            // Epoch is 1st Jan 1958
            // Now the basicTimeUnit is the TAI time as number of seconds from 1st Jan 1958, change epoch to 1st Jan 1970
            basicTimeUnit -= DIFFERENCE_1958_TO_1970_SECS;
        } else {
            // Epoch is agency based (number of TAI seconds from the epoch(TAI))
            // Change the epoch to 1st Jan 1970 (TAI)
            long taiEpoch = toTAI(agencyEpoch.getEpochSecond());
            basicTimeUnit += taiEpoch;
        }

        // Now convert the basicTimeUnit to UTC
        basicTimeUnit = toUTC(basicTimeUnit);

        // Finally, build the Instant
        return Instant.ofEpochSecond(basicTimeUnit, fractionalTimeUnit);
    }

    /**
     * Encode the provided instant (in UTC time, epoch 1st Jan 1970) to CDS. If an agencyEpoch is provided, then
     * the time will be encoded with reference to the provided agency epoch. If the agencyEpoch is null, then the epoch
     * used to encode the time will be Level 1 (as specified by CCSDS 301.0-B-4).
     * coarseOctets and fineOctets specify the number of octets used by the basic time unit and fractional time unit.
     *
     * If encodePField is set, then the provided information is encoded in the P Field. Otherwise only the T Field is
     * encoded.
     *
     * @param time the UTC time to encode
     * @param agencyEpoch the agency epoch, can be null (Level 2 if present, otherwise Level 1)
     * @param bit16daySegment true if the number of days is encoded in 16 bits
     * @param subMilliSegmentLength 0: no field, 1: 16 bits field, 2: 32 bits field
     * @param encodePField true if P Field must be encoded, false if only the T Field must be encoded
     * @return the encoded CDS time
     */
    public static byte[] toCDS(Instant time, Instant agencyEpoch, boolean bit16daySegment, int subMilliSegmentLength, boolean encodePField) {
        int fLen = (encodePField ? 1 : 0) + (bit16daySegment ? 2 : 3) + 4;
        if(subMilliSegmentLength == 1) {
            fLen += 2;
        } else if(subMilliSegmentLength == 2) {
            fLen += 4;
        } else if(subMilliSegmentLength == 3) {
            throw new IllegalArgumentException("subMilliSegmentLength == 3 is reserved for future use");
        } else if(subMilliSegmentLength != 0) {
            throw new IllegalArgumentException("subMilliSegmentLength == " + subMilliSegmentLength + " is not supported (values can be 0, 1, 2)");
        }
        ByteBuffer bb = ByteBuffer.allocate(fLen);
        // P Field?
        if(encodePField) {
            byte pField = (byte) 0x40;
            if(agencyEpoch != null) {
                pField |= (byte) 0x08;
            }
            if(!bit16daySegment) {
                pField |= (byte) 0x04;
            }
            pField |= (byte) subMilliSegmentLength;
            bb.put(pField);
        }
        // day segment
        long daysFromEpoch = time.getEpochSecond() / 86400; // compute the number of days from Java epoch
        long remainderSecs;
        if(agencyEpoch == null) {
            daysFromEpoch += 4383; // to be compliant with the CCSDS epoch, Level 1 Time Code
            remainderSecs = time.getEpochSecond() % 86400; // compute the number of seconds within the day (remainder)
        } else {
            daysFromEpoch = (time.getEpochSecond() - agencyEpoch.getEpochSecond()) / 86400; // Level 2 Time Code, starts from 00:00
            remainderSecs = (time.getEpochSecond() - agencyEpoch.getEpochSecond()) % 86400;
        }
        // millisecs in day
        long millisecs = remainderSecs * 1000 + time.getNano()/1000000;
        // remaining
        int remaining = 0;
        if(subMilliSegmentLength == 1) {
            remaining = (time.getNano() % 1000000) / 1000; // nanosecs within millisec divided 1000 = microsecs
        } else if(subMilliSegmentLength == 2) {
            remaining = (time.getNano() % 1000000) * 1000; // nanosecs within millisec * 1000 = picosecs
        }
        // Now encode everything
        if(bit16daySegment) {
            bb.putShort((short) daysFromEpoch);
        } else {
            bb.put((byte)(daysFromEpoch >> 16));
            bb.putShort((short)daysFromEpoch);
        }
        bb.putInt((int) millisecs);
        if(subMilliSegmentLength == 1) {
            bb.putShort((short) remaining);
        } else if(subMilliSegmentLength == 2) {
            bb.putInt(remaining);
        }
        return bb.array();
    }

    /**
     * Decode a CDS time to UTC instant, assuming the presence of the P Field and an agency epoch. If the agency
     * epoch is null, then a Level 1 Time Code is assumed. If Level 1 Time Code is reported by the P Field, the agency
     * epoch is ignored. If the P Field reports a Level 2 Time Code and the agency epoch is null, an exception is thrown.
     *
     * @param decoder the decoder handling the raw data
     * @param agencyEpoch the agency epoch, can be null
     * @return the corresponding UTC time
     */
    public static Instant fromCDS(BitEncoderDecoder decoder, Instant agencyEpoch) {
        byte pField = (byte) decoder.getNextIntegerUnsigned(Byte.SIZE);
        boolean agencyEpochUsed = (pField & (byte) 0x08) != 0;
        if(agencyEpochUsed && agencyEpoch == null) {
            throw new IllegalArgumentException("P Field reports Level 2 Time Code but no agency epoch supplied, cannot decode");
        } else if(!agencyEpochUsed) {
            agencyEpoch = null;
        }
        boolean bit16daySegment = (pField & (byte) 0x04) == 0;
        int subMilliSegmentLength = (pField & (byte) 0x03);

        byte[] tField = decoder.getNextByte(Byte.SIZE * (4 + (bit16daySegment ? 2 : 3) + (subMilliSegmentLength * 2)));

        ByteBuffer bb = ByteBuffer.wrap(tField);

        return fromCDS(bb, agencyEpoch, bit16daySegment, subMilliSegmentLength);
    }

    /**
     * Decode a CDS time to UTC instant, assuming the presence of the P Field and Level 1 Time Code. If the P Field
     * reports a Level 2 Time Code, an exception is thrown.
     *
     * @param cds the CDS encoded time with P Field
     * @return the corresponding UTC time
     */
    public static Instant fromCDS(byte[] cds) {
        return fromCDS(cds, null);
    }

    /**
     * Decode a CDS time to UTC instant, assuming the presence of the P Field and an agency epoch. If the agency
     * epoch is null, then a Level 1 Time Code is assumed. If Level 1 Time Code is reported by the P Field, the agency
     * epoch is ignored. If the P Field reports a Level 2 Time Code and the agency epoch is null, an exception is thrown.
     *
     * @param cds the CDS encoded time with P Field
     * @param agencyEpoch the agency epoch, can be null
     * @return the corresponding UTC time
     */
    public static Instant fromCDS(byte[] cds, Instant agencyEpoch) {
        byte pField = cds[0];
        boolean agencyEpochUsed = (pField & (byte) 0x08) != 0;
        if(agencyEpochUsed && agencyEpoch == null) {
            throw new IllegalArgumentException("P Field reports Level 2 Time Code but no agency epoch supplied, cannot decode");
        } else if(!agencyEpochUsed) {
            agencyEpoch = null;
        }
        boolean bit16daySegment = (pField & (byte) 0x04) == 0;
        int subMilliSegmentLength = (pField & (byte) 0x03);

        ByteBuffer bb = ByteBuffer.wrap(cds, 1, cds.length - 1);

        return fromCDS(bb, agencyEpoch, bit16daySegment, subMilliSegmentLength);
    }

    /**
     * Decode a CDS time to UTC instant, with no P Field. The expected encoding parameters shall be provided as method
     * arguments
     *
     * @param cds the encoded T Field
     * @param agencyEpoch the agency epoch. If null, the CCSDS recommended epoch is used
     * @param bit16daySegment true if 16 bits are used for the day field encoding, otherwise false (24 bits are used)
     * @param subMilliSegmentLength possible values are 0 (not present), 1 (16 bits), 2 (32 bits)
     * @return the corresponding UTC time
     */
    public static Instant fromCDS(byte[] cds, Instant agencyEpoch, boolean bit16daySegment, int subMilliSegmentLength) {
        return fromCDS(ByteBuffer.wrap(cds), agencyEpoch, bit16daySegment, subMilliSegmentLength);
    }

    /**
     * Decode a CDS time to UTC instant, with no P Field. The expected encoding parameters shall be provided as method
     * arguments
     *
     * @param wrappedTField the encoded T Field in a ByteBuffer
     * @param agencyEpoch the agency epoch. If null, the CCSDS recommended epoch is used
     * @param bit16daySegment true if 16 bits are used for the day field encoding, otherwise false (24 bits are used)
     * @param subMilliSegmentLength possible values are 0 (not present), 1 (16 bits), 2 (32 bits)
     * @return the corresponding UTC time
     */
    public static Instant fromCDS(ByteBuffer wrappedTField, Instant agencyEpoch, boolean bit16daySegment, int subMilliSegmentLength) {
        int days;
        int millis;
        int remaining;

        if(bit16daySegment) {
            days = Short.toUnsignedInt(wrappedTField.getShort());
        } else {
            days = Byte.toUnsignedInt(wrappedTField.get());
            days <<= 16;
            days |= Short.toUnsignedInt(wrappedTField.getShort());
        }

        millis = wrappedTField.getInt();

        if(subMilliSegmentLength == 1) {
            remaining = Short.toUnsignedInt(wrappedTField.getShort()); // microsecs in millisec
            remaining *= 1000; // nanosecs in millisecs
        } else if(subMilliSegmentLength == 2) {
            remaining = wrappedTField.getInt(); // picosecs in millisecs
            remaining /= 1000; // nanosecs in millisecs
        } else {
            remaining = 0;
        }

        if(agencyEpoch == null) {
            // Remove 4383 days to go to Java epoch
            days -= 4383;
        } else {
            // Add the number of days from the provided epoch to Java epoch
            days += agencyEpoch.getEpochSecond() / 86400;
            millis += (agencyEpoch.getEpochSecond() % 86400) * 1000;
        }

        long secs = days * 86400L + millis / 1000;
        long nanosecs = (millis % 1000) * 1000000 + (long) remaining;

        return Instant.ofEpochSecond(secs, nanosecs);
    }

    /**
     * This method converts a UTC time with epoch 1st Jan 1970 to TAI time with the same epoch.
     * @param utcTime the UTC time (seconds) since 1st Jan 1970
     * @return the TAI time (seconds) since 1st Jan 1970
     */
    public static long toTAI(long utcTime) {
        for(int i = 0; i < UTC_TO_LEAP.length; ++i) {
            long[] t = UTC_TO_LEAP[i];
            if(utcTime <= t[0]) {
                if(i == 0) {
                    return utcTime;
                } else {
                    return utcTime + UTC_TO_LEAP[i - 1][1];
                }
            }
        }
        return utcTime + UTC_TO_LEAP[UTC_TO_LEAP.length - 1][1];
    }

    /**
     * This method converts a TAI time with epoch 1st Jan 1970 to UTC time with the same epoch.
     * @param taiTime the TAI time (seconds) since 1st Jan 1970
     * @return the UTC time (seconds) since 1st Jan 1970
     */
    public static long toUTC(long taiTime) {
        for(int i = 0; i < UTC_TO_LEAP.length; ++i) {
            long[] t = UTC_TO_LEAP[i];
            if(taiTime <= t[0] + t[1]) {
                if (i == 0) {
                    return taiTime;
                } else {
                    return taiTime - UTC_TO_LEAP[i - 1][1];
                }
            }
        }
        return taiTime - UTC_TO_LEAP[UTC_TO_LEAP.length - 1][1];
    }

    /**
     * Decode a CUC duration (relative time) from the provided byte array.
     *
     * @param encoded the byte array storing the data
     * @return the duration
     */
    public static Duration fromCUCduration(byte[] encoded) {
        return fromCUCduration(new BitEncoderDecoder(encoded));
    }

    /**
     * Decode a CUC duration (relative time) from the provided {@link BitEncoderDecoder}.
     *
     * @param decoder the {@link BitEncoderDecoder} storing the data
     * @return the duration
     */
    public static Duration fromCUCduration(BitEncoderDecoder decoder) {
        byte first = (byte) decoder.getNextIntegerUnsigned(Byte.SIZE);
        boolean additionalPfield = false;
        if(first >> 7 != 0) {
            additionalPfield = true;
        }
        int coarseOctets = ((first & (byte) 0x0C) >> 2) + 1;
        int fineOctets = (first & (byte) 0x03);
        if(additionalPfield) {
            byte second = (byte) decoder.getNextIntegerUnsigned(Byte.SIZE);
            coarseOctets += (second & 0x60) >> 5;
            fineOctets += (second & 0x1C) >> 2;
        }
        // Read coarse + fine octets
        byte[] tField = decoder.getNextByte(Byte.SIZE * (coarseOctets + fineOctets));
        return fromCUCduration(tField, coarseOctets, fineOctets);
    }

    /**
     * Decode a CUC duration (relative time) from the provided T field.
     *
     * @param tField the T Field
     * @param coarseOctets the number of coarse octets
     * @param fineOctets the number of fine octets
     * @return the duration
     */
    public static Duration fromCUCduration(byte[] tField, int coarseOctets, int fineOctets) {
        ByteBuffer tFieldWrapped = ByteBuffer.wrap(tField);
        // Translate the coarseOctets into a long number
        long basicTimeUnit = 0;
        int power = coarseOctets - 1;
        boolean negative = false;
        for(int i = 0; i < coarseOctets; ++i) {
            if(i == 0) {
                byte firstCoarse = tFieldWrapped.get();
                negative = firstCoarse < 0;
                basicTimeUnit += Byte.toUnsignedInt((byte) (firstCoarse & (byte) 0x7F)) * Math.pow(256, power);
            } else {
                basicTimeUnit += Byte.toUnsignedInt(tFieldWrapped.get()) * Math.pow(256, power);
            }
            --power;
        }
        // Compute the quantum
        double quantum = 1000000000.0 / (int) Math.pow(2, fineOctets * 8.0);
        // Now, translate the fineOctets into a long number
        long fractionalTimeUnit = 0;

        power = fineOctets - 1;
        for(int i = 0; i < fineOctets; ++i) {
            fractionalTimeUnit += Byte.toUnsignedInt(tFieldWrapped.get()) * Math.pow(256, power);
            --power;
        }
        // Calculate the time in nanoseconds
        fractionalTimeUnit = (long) (fractionalTimeUnit * quantum);

        // Finally, build the Duration
        return Duration.ofSeconds((negative) ? basicTimeUnit * -1 : basicTimeUnit, fractionalTimeUnit);
    }

    /**
     * Encode a duration to CUC format with the provided characteristics.
     *
     * @param t the duration to encode
     * @param coarseOctets the number of coarse octets
     * @param fineOctets the number of fine octets
     * @param encodePField true if the P Field shall be encoded
     * @return the encoded relative time in CUC format
     */
    public static byte[] toCUCduration(Duration t, int coarseOctets, int fineOctets, boolean encodePField) {
        int extraByte = coarseOctets > 4 || fineOctets > 3 ? 1 : 0;
        int fLen = coarseOctets + fineOctets + (encodePField ? 1 + (extraByte) : 0);
        ByteBuffer bb = ByteBuffer.allocate(fLen);
        if(encodePField) {
            boolean secondOctet = coarseOctets > 4 || fineOctets > 3;
            byte pField = secondOctet ? (byte) 0x01 : (byte) 0x00;
            pField <<= 3;
            pField |= 0x01; // No epoch
            pField <<= 2;
            if(coarseOctets - 1 <= 3) {
                pField |= (byte) (coarseOctets - 1);
            } else {
                pField |= (byte) 0x03;
            }
            pField <<= 2;
            if(fineOctets <= 3) {
                pField |= (byte) (fineOctets);
            } else {
                pField |= (byte) 0x03;
            }
            bb.put(pField);
            if(secondOctet) {
                byte pField2 = 0;
                int remainingCoarseOctets = Math.max(0, coarseOctets - 4);
                int remainingFineOctets = Math.max(0, fineOctets - 3);
                pField2 |= (byte) (remainingCoarseOctets);
                pField2 <<= 3;
                pField2 |= (byte) (remainingFineOctets);
                pField2 <<= 2;
                bb.put(pField2);
            }
        }

        // Get the time
        long tai = t.getSeconds();
        long nanosec = t.getNano();
        // Encode time in coarseOctets
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(tai);
        byte[] encodedCoarse = buffer.array();
        bb.put(encodedCoarse, Long.BYTES - coarseOctets, coarseOctets);
        // Encode nanosec in fine octets: it is assumed that the maximum resolution of the fine octets determine
        // the quantization of the fractional time unit, i.e. a value of all 1s (in binary) would be equivalent to a
        // second - quantum. Therefore 1 quantum is equal to 1.000.000.000 nanosecond divided by 2^bit_resolution.
        double quantum = 1000000000.0 / (int) Math.pow(2, fineOctets * 8.0);
        // Let's compute how many quantums are in nanosec. The maximum error is equal to quantum - 1 nanosec.
        long quantums = Math.round(nanosec / quantum);
        // Encode quantums in fine octets
        ByteBuffer buffer2 = ByteBuffer.allocate(Long.BYTES);
        buffer2.putLong(quantums);
        byte[] encodedFine = buffer2.array();
        bb.put(encodedFine, Long.BYTES - fineOctets, fineOctets);

        return bb.array();
    }
}
