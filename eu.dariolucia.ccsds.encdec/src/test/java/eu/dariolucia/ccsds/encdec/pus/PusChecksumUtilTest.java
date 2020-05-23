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

package eu.dariolucia.ccsds.encdec.pus;

import eu.dariolucia.ccsds.encdec.value.StringUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PusChecksumUtilTest {

    private static final String LONG_BYTE_TEST = "D1CEA1C0CEA5E4A4B29DEFCDCCE4F0AD99E2C1A7ABA9B9E0C7B0E9B8A5E6E1ADB89ECD9DAF99F8F6D6B09CB4CDE6C6DBA9C8A1A6EA99F6C9DFECCA9FDCBFB0CAF0C0C4EDB1F3DBCBBFDAD7E297F9A8F9D1C1F3ADAAA89AF0C4BDA7F3F5EEA6C599F8BB9A";

    @Test
    void crcChecksum() {
        {
            byte[] d = new byte[]{0, 0};
            short chk = PusChecksumUtil.crcChecksum(d, 0, d.length);
            assertEquals((short) 0x1D0F, chk);
        }
        {
            byte[] d = new byte[]{0, 0, 0};
            short chk = PusChecksumUtil.crcChecksum(d, 0, d.length);
            assertEquals((short) 0xCC9C, chk);
        }
        {
            byte[] d = new byte[]{(byte) 0xAB, (byte) 0xCD, (byte) 0xEF, 0x01};
            short chk = PusChecksumUtil.crcChecksum(d, 0, d.length);
            assertEquals((short) 0x04A2, chk);
        }
        {
            byte[] d = new byte[]{0x14, 0x56, (byte) 0xF8, (byte) 0x9A, 0x00, 0x01};
            short chk = PusChecksumUtil.crcChecksum(d, 0, d.length);
            assertEquals((short) 0x7FD5, chk);
        }
        {
            byte[] d = new byte[]{0, 0, 0, (byte) 0xCC, (byte) 0x9C};
            short chk = PusChecksumUtil.crcChecksum(d, 0, d.length);
            assertEquals((short) 0, chk);
        }
        {
            byte[] d = new byte[]{0x14, 0x56, (byte) 0xF8, (byte) 0x9A, 0x00, 0x01, (byte)0x7F ,(byte)0xD5};
            short chk = PusChecksumUtil.crcChecksum(d, 0, d.length);
            assertEquals((short) 0, chk);
        }
    }

    @Test
    void isoChecksum() {
        {
            byte[] d = new byte[]{0, 0};
            short chk = PusChecksumUtil.isoChecksum(d, 0, d.length);
            assertEquals((short) 0xFFFF, chk);
        }
        {
            byte[] d = new byte[]{0, 0, 0};
            short chk = PusChecksumUtil.isoChecksum(d, 0, d.length);
            assertEquals((short) 0xFFFF, chk);
        }
        {
            byte[] d = new byte[]{(byte) 0xAB, (byte) 0xCD, (byte) 0xEF, 0x01};
            short chk = PusChecksumUtil.isoChecksum(d, 0, d.length);
            assertEquals((short) 0x9CF8, chk);
        }
        {
            byte[] d = new byte[]{0x14, 0x56, (byte) 0xF8, (byte) 0x9A, 0x00, 0x01};
            short chk = PusChecksumUtil.isoChecksum(d, 0, d.length);
            assertEquals((short) 0x24DC, chk);
        }
        {
            byte[] d = new byte[]{ (byte) 0x01, (byte) 0x02 };
            short chk = PusChecksumUtil.isoChecksum(d, 0, d.length);
            assertEquals((short) 0xF804, chk);
        }
        {
            byte[] d = new byte[]{ (byte) 0x01, (byte) 0x02, (byte) 0xF8, 0x04 };
            assertTrue(PusChecksumUtil.verifyIsoChecksum(d, 0, d.length));
        }
        {
            byte[] d = StringUtil.toByteArray(LONG_BYTE_TEST);
            short chk = PusChecksumUtil.isoChecksum(d, 0, d.length);
            assertEquals((short) 0x165A, chk);
            byte[] ext = new byte[d.length + 2];
            System.arraycopy(d, 0, ext, 0, d.length);
            ext[ext.length - 2] = 0x16;
            ext[ext.length - 1] = 0x5A;
            assertTrue(PusChecksumUtil.verifyIsoChecksum(ext, 0, ext.length));
        }
    }
}