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
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.*;

class TimeUtilTest {

    @Test
    public void testTaiUtcConversions() {
        GregorianCalendar gc = new GregorianCalendar(1983, 3, 12, 12,14,15);
        long utcSecs = gc.toInstant().getEpochSecond();
        long taiSecs = TimeUtil.toTAI(utcSecs);
        assertEquals(utcSecs + 21, taiSecs);

        utcSecs = TimeUtil.toUTC(taiSecs);
        assertEquals(taiSecs - 21, utcSecs);

        gc = new GregorianCalendar(2018, 3, 12, 12,14,15);
        utcSecs = gc.toInstant().getEpochSecond();
        taiSecs = TimeUtil.toTAI(utcSecs);
        assertEquals(utcSecs + 37, taiSecs);

        utcSecs = TimeUtil.toUTC(taiSecs);
        assertEquals(taiSecs - 37, utcSecs);

        gc = new GregorianCalendar(1970, 3, 12, 12,14,15);
        utcSecs = gc.toInstant().getEpochSecond();
        taiSecs = TimeUtil.toTAI(utcSecs);
        assertEquals(utcSecs, taiSecs);

        utcSecs = TimeUtil.toUTC(taiSecs);
        assertEquals(taiSecs, utcSecs);
    }

    @Test
    public void testCUCtime() {
        GregorianCalendar gc = new GregorianCalendar(1983, 3, 12, 12,14,15);
        Instant t = gc.toInstant();
        byte[] cuc = TimeUtil.toCUC(t, null, 4, 3, true);
        assertEquals(8, cuc.length);
        Instant back = TimeUtil.fromCUC(cuc);
        assertEquals(t, back);
        Instant back2 = TimeUtil.fromCUC(cuc, 0, cuc.length, null);
        assertEquals(t, back2);

        back = TimeUtil.fromCUC(new BitEncoderDecoder(cuc), null);
        assertEquals(t, back);

        cuc = TimeUtil.toCUC(t, null, 5, 5, true);
        assertEquals(12, cuc.length);
        back = TimeUtil.fromCUC(cuc);
        assertEquals(t, back);

        back = TimeUtil.fromCUC(new BitEncoderDecoder(cuc), null);
        assertEquals(t, back);

        cuc = TimeUtil.toCUC(t, Instant.ofEpochSecond(100000), 5, 5, true);
        assertEquals(12, cuc.length);
        back = TimeUtil.fromCUC(cuc, Instant.ofEpochSecond(100000));
        assertEquals(t, back);

        cuc = TimeUtil.toCUC(t, null, 4, 3, false);
        back = TimeUtil.fromCUC(cuc, 0, cuc.length, null, 4, 3);
        assertEquals(t, back);

    }

    @Test
    public void testCDStime() {
        GregorianCalendar gc = new GregorianCalendar(1983, 3, 12, 12,14,15);
        Instant t = gc.toInstant();
        byte[] cds = TimeUtil.toCDS(t, null, false, 0, true);
        assertEquals(8, cds.length);
        Instant back = TimeUtil.fromCDS(cds);
        assertEquals(t, back);

        back = TimeUtil.fromCDS(new BitEncoderDecoder(cds), null);
        assertEquals(t, back);

        cds = TimeUtil.toCDS(t, null, false, 2, true);
        assertEquals(12, cds.length);
        back = TimeUtil.fromCDS(cds);
        assertEquals(t, back);

        cds = TimeUtil.toCDS(t, null, false, 1, true);
        assertEquals(10, cds.length);
        back = TimeUtil.fromCDS(cds);
        assertEquals(t, back);

        cds = TimeUtil.toCDS(t, Instant.ofEpochSecond(100000), false, 2, true);
        assertEquals(12, cds.length);
        back = TimeUtil.fromCDS(cds, Instant.ofEpochSecond(100000));
        assertEquals(t, back);
    }

    @Test
    public void testCUCduration() {
        Duration t = Duration.ofSeconds(234, 23232112);
        byte[] cuc = TimeUtil.toCUCduration(t, 4, 3, true);
        assertEquals(8, cuc.length);
        Duration back = TimeUtil.fromCUCduration(new BitEncoderDecoder(cuc));
        assertEquals(t.getSeconds(), back.getSeconds());
        // Error less than quantisation error
        assertTrue(Math.abs(t.getNano() - back.getNano()) < 1000000000.0 / (3 * Byte.SIZE));

        cuc = TimeUtil.toCUCduration(t, 5, 5, true);
        assertEquals(12, cuc.length);
        back = TimeUtil.fromCUCduration(new BitEncoderDecoder(cuc));
        assertEquals(t.getSeconds(), back.getSeconds());
        assertTrue(Math.abs(t.getNano() - back.getNano()) < 1000000000.0 / (5 * Byte.SIZE));

        t = Duration.ofSeconds(127, 0);
        cuc = TimeUtil.toCUCduration(t, 1, 1, false);
        back = TimeUtil.fromCUCduration(cuc, 1, 1);
        assertEquals(t.getSeconds(), back.getSeconds());
        assertTrue(Math.abs(t.getNano() - back.getNano()) < 1000000000.0 / (Byte.SIZE));
    }
}