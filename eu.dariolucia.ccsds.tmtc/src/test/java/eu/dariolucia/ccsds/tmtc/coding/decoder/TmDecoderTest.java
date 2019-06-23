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

package eu.dariolucia.ccsds.tmtc.coding.decoder;

import eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm;
import eu.dariolucia.ccsds.tmtc.coding.encoder.ReedSolomonEncoder;
import eu.dariolucia.ccsds.tmtc.coding.encoder.TmAsmEncoder;
import eu.dariolucia.ccsds.tmtc.coding.encoder.TmRandomizerEncoder;
import eu.dariolucia.ccsds.tmtc.datalink.builder.TmTransferFrameBuilder;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class TmDecoderTest {

    public static final String EXPECTED_TM = "1ACFFC1DF8FB18CEDDF270BC8E2C93ADA7B746CE5A977DCC32A2BF3E0A10F18894CDEAB1FE901D81341AE1791C59275B4F6E8D9CB52EFB9865457E7C1421E311299BD563FD203B026835C2F238B24EB69EDD1B396A5DF730CA8AFCF82843C6225337AAC7FA407604D06B85E471649D6D3DBA3672D4BBEE619515F9F050878C44A66F558FF480EC09A0D70BC8E2C93ADA7B746CE5A977DCC32A2BF3E0A10F18894CDEAB1FE901D81341AE1791C59275B4F6E8D9CB52EFB9865457E7C1421E311299BD563FD203B026835C2F238B24EB69EDD1B396A5DF730CA8AFCF82843C6225337AAC7FA407604D06B85E471649D6D3DBA3672D4BBEE619515F9F050878C44A66F558FF480EC09A0D70BC8E2C93ADA7B746CE5A977DCC32A2BF3E0A10F18894CDEAB1FE901D81341AE1791C59275B4F6E8D9CB52EFB9865457E7C1421E311299BD563FD203B026835C2F238B24EB69EDD1B396A5DF730CA8AFCF82843C6225337AAC7FA407604D06B85E471649D6D3DBA3672D4BBEE619515F9F050878C44A66F558FF480EC09A0D70BC8E2C93ADA7B746CE5A977DCC32A2BF3E0A10F18894CDEAB1FE901D81341AE1791C59275B4F6E8D9CB52EFB9865457E7C1421E311299BD563FD203B026835C2F238B24EB69EDD1B396A5DF730CA8AFCF82843C6225337AAC7FA407604D06B85E471649D6D3DBA3672D4BBEE619515F9F050878C44A66F558FF480EC09A0D70BC8E2C93ADA7B746CE5A977DCC32A2BF3E0A10F18894CDEAB1FE901D81341AE1791C59275B4F6E8D9CB52EFB9865457E7C1421E311299BD563FD203B026835C2F238B24EB69EDD1B396A5DF730CA8AFCF82843C6225337AAC7FA407604D06B85E471649D6D3DBA3672D4BBEE619515F9F050878C44A66F558FF480EC09A0D70BC8E2C93ADA7B746CE5A977DCC32A2BF3E0A10F18894CDEAB1FE901D81341AE1791C59275B4F6E8D9CB52EFB9865457E7C1421E311299BD563FD203B026835C2F238B24EB69EDD1B396A5DF730CA8AFCF82843C6225337AAC7FA407604D06B85E471649D6D3DBA3672D4BBEE619515F9F050878C44A66F558FF480EC09A0D70BC8E2C93ADA7B746CE5A977DCC32A2BF3E0A10F18894CDEAB1FE901D81341AE1791C59275B4F6E8D9CB52EFB9865457E7C1421E311299BD563FD203B026835C2F238B24EB69EDD1B396A5DF730CA8AFCF82843C6225337AAC7FA407604D06B85E471649D6D3DBA3672D4BBEE619515F9F050878C44A66F558FF480EC09A0D70BC8E2C93ADA7B746CE5A977DCC32A2BF3E0A10F18894CDEAB1FE901D81341AE1791C59275B4F6E8D9CB52EFB9865457E7C1421E311299BD563FD203B026835C2F238B24EB69EDD1B396A5DF730CA8AFCF82843C6225337AAC7FA407604D06B85E471649D6D3DBA3672D4BBEE619515F9F050878C44A66F558FF480EC09A0D70BC8E2C93ADA7B746CE5A977DCC32A2BF3E0A10F18894CDEAB1FE901D81341AE1791C59275B4F6E8D9CB52EFB9865457E7C1421E311299BD563FD203B026835C2F238B24EB69EDD1B396A5DF730CA8AFCF82843C6225337AAE249567843CA1921B540FCCF81769D6A4628A02A22B3949C3E58B92ADF0B5013D7BE755698B916EDDD8E83EA508ADC278C5767F7ECFC39F6D8CEEB6DE1A77E6E16D635240885C3A7FD520F714ADF606FCE18240CC2B0A9CE49F4D6817E0E203A0B2E1874A668FCEA2AB71C848D98D398B973CD59A4E3C63186E876B2AF30EBB5BE424F26154C4D3879B0D27B0CFD2202E3418A1A0295F8CDE9435F2638C2A469";

    public static final String EXPECTED_TM_2 = "034776C7272895B0F8FB18CEDDF270BC8E2C93ADA7B746CE5A977DCC32A2BF3E0A10F18894CDEAB1FE901D81341AE1791C59275B4F6E8D9CB52EFB9865457E7C1421E311299BD563FD203B026835C2F238B24EB69EDD1B396A5DF730CA8AFCF82843C6225337AAC7FA407604D06B85E471649D6D3DBA3672D4BBEE619515F9F050878C44A66F558FF480EC09A0D70BC8E2C93ADA7B746CE5A977DCC32A2BF3E0A10F18894CDEAB1FE901D81341AE1791C59275B4F6E8D9CB52EFB9865457E7C1421E311299BD563FD203B026835C2F238B24EB69EDD1B396A5DF730CA8AFCF82843C6225337AAC7FA407604D06B85E471649D6D3DBA3672D4BBEE619515F9F050878C44A66F558FF480EC09A0D70BC8E2C93ADA7B746CE5A977DCC32A2BF3E0A10F18894CDEAB1FE901D81341AE1791C59275B4F6E8D9CB52EFB9865457E7C1421E311299BD563FD203B026835C2F238B24EB69EDD1B396A5DF730CA8AFCF82843C6225337AAC7FA407604D06B85E471649D6D3DBA3672D4BBEE619515F9F050878C44A66F558FF480EC09A0D70BC8E2C93ADA7B746CE5A977DCC32A2BF3E0A10F18894CDEAB1FE901D81341AE1791C59275B4F6E8D9CB52EFB9865457E7C1421E311299BD563FD203B026835C2F238B24EB69EDD1B396A5DF730CA8AFCF82843C6225337AAC7FA407604D06B85E471649D6D3DBA3672D4BBEE619515F9F050878C44A66F558FF480EC09A0D70BC8E2C93ADA7B746CE5A977DCC32A2BF3E0A10F18894CDEAB1FE901D81341AE1791C59275B4F6E8D9CB52EFB9865457E7C1421E311299BD563FD203B026835C2F238B24EB69EDD1B396A5DF730CA8AFCF82843C6225337AAC7FA407604D06B85E471649D6D3DBA3672D4BBEE619515F9F050878C44A66F558FF480EC09A0D70BC8E2C93ADA7B746CE5A977DCC32A2BF3E0A10F18894CDEAB1FE901D81341AE1791C59275B4F6E8D9CB52EFB9865457E7C1421E311299BD563FD203B026835C2F238B24EB69EDD1B396A5DF730CA8AFCF82843C6225337AAC7FA407604D06B85E471649D6D3DBA3672D4BBEE619515F9F050878C44A66F558FF480EC09A0D70BC8E2C93ADA7B746CE5A977DCC32A2BF3E0A10F18894CDEAB1FE901D81341AE1791C59275B4F6E8D9CB52EFB9865457E7C1421E311299BD563FD203B026835C2F238B24EB69EDD1B396A5DF730CA8AFCF82843C6225337AAC7FA407604D06B85E471649D6D3DBA3672D4BBEE619515F9F050878C44A66F558FF480EC09A0D70BC8E2C93ADA7B746CE5A977DCC32A2BF3E0A10F18894CDEAB1FE901D81341AE1791C59275B4F6E8D9CB52EFB9865457E7C1421E311299BD563FD203B026835C2F238B24EB69EDD1B396A5DF730CA8AFCF82843C6225337AAC7FA407604D06B85E471649D6D3DBA3672D4BBEE619515F9F050878C44A66F558FF480EC09A0D70BC8E2C93ADA7B746CE5A977DCC32A2BF3E0A10F18894CDEAB1FE901D81341AE1791C59275B4F6E8D9CB52EFB9865457E7C1421E311299BD563FD203B026835C2F238B24EB69EDD1B396A5DF730CA8AFCF82843C6225337AAE249567843CA1921B540FCCF81769D6A4628A02A22B3949C3E58B92ADF0B5013D7BE755698B916EDDD8E83EA508ADC278C5767F7ECFC39F6D8CEEB6DE1A77E6E16D635240885C3A7FD520F714ADF606FCE18240CC2B0A9CE49F4D6817E0E203A0B2E1874A668FCEA2AB71C848D98D398B973CD59A4E3C63186E876B2AF30EBB5BE424F26154C4D3879B0D27B0CFD2202E3418A1A0295F8CDE9435F2638C2A469";

    @Test
    public void testTmDecoding() {
        // Create TM as result
        TmTransferFrameBuilder builder = TmTransferFrameBuilder.create(1115, 0, true, false)
                .setSpacecraftId(123)
                .setVirtualChannelId(1)
                .setMasterChannelFrameCount(22)
                .setVirtualChannelFrameCount(14)
                .setPacketOrderFlag(false)
                .setSynchronisationFlag(true)
                .setOcf(new byte[] { 0, 0, 0, 0 });
        builder.addData(new byte[TmTransferFrameBuilder.computeUserDataLength(1115, 0, true, false)]);

        TmTransferFrame tmtf = builder.build();

        byte[] input = StringUtil.toByteArray(EXPECTED_TM);

        // RS, randomize and ASM
        ReedSolomonDecoder rsDecoder = new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223, 5, true);
        TmRandomizerDecoder randomizerDecoder = new TmRandomizerDecoder();
        TmAsmDecoder asmDecoder = new TmAsmDecoder();

        Optional<byte[]> decoded = List.of(input).stream().map(asmDecoder).map(randomizerDecoder).map(rsDecoder).findFirst();
        assertTrue(decoded.isPresent());
        assertEquals(1115, decoded.get().length);

        TmTransferFrame frame = new TmTransferFrame(decoded.get(), false);
        assertEquals(tmtf.getSpacecraftId(), frame.getSpacecraftId());
        assertEquals(tmtf.getMasterChannelFrameCount(), frame.getMasterChannelFrameCount());
        assertEquals(tmtf.getVirtualChannelId(), frame.getVirtualChannelId());
        assertEquals(tmtf.getVirtualChannelFrameCount(), frame.getVirtualChannelFrameCount());
        assertEquals(tmtf.getTransferFrameVersionNumber(), frame.getTransferFrameVersionNumber());
    }

    @Test
    public void testTmDecodingLongerSyncMarker() {
        // Create TM as result
        TmTransferFrameBuilder builder = TmTransferFrameBuilder.create(1115, 0, true, false)
                .setSpacecraftId(123)
                .setVirtualChannelId(1)
                .setMasterChannelFrameCount(22)
                .setVirtualChannelFrameCount(14)
                .setPacketOrderFlag(false)
                .setSynchronisationFlag(true)
                .setOcf(new byte[] { 0, 0, 0, 0 });
        builder.addData(new byte[TmTransferFrameBuilder.computeUserDataLength(1115, 0, true, false)]);

        TmTransferFrame tmtf = builder.build();

        byte[] input = StringUtil.toByteArray(EXPECTED_TM_2);

        // RS, randomize and ASM
        ReedSolomonDecoder rsDecoder = new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223, 5, true);
        TmRandomizerDecoder randomizerDecoder = new TmRandomizerDecoder();
        TmAsmDecoder asmDecoder = new TmAsmDecoder(new byte[] {0x03, 0x47, 0x76, (byte) 0xC7, 0x27, 0x28, (byte) 0x95, (byte) 0xB0});

        Optional<byte[]> decoded = List.of(input).stream().map(asmDecoder).map(randomizerDecoder).map(rsDecoder).findFirst();
        assertTrue(decoded.isPresent());
        assertEquals(1115, decoded.get().length);

        TmTransferFrame frame = TmTransferFrame.decodingFunction(false).decode(decoded.get());
        assertEquals(tmtf.getSpacecraftId(), frame.getSpacecraftId());
        assertEquals(tmtf.getMasterChannelFrameCount(), frame.getMasterChannelFrameCount());
        assertEquals(tmtf.getVirtualChannelId(), frame.getVirtualChannelId());
        assertEquals(tmtf.getVirtualChannelFrameCount(), frame.getVirtualChannelFrameCount());
        assertEquals(tmtf.getTransferFrameVersionNumber(), frame.getTransferFrameVersionNumber());
    }


    @Test
    public void testTmDecodingWrongSyncMarker() {
        byte[] input = StringUtil.toByteArray(EXPECTED_TM_2);

        // RS, randomize and ASM
        ReedSolomonDecoder rsDecoder = new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223, 5, true);
        TmRandomizerDecoder randomizerDecoder = new TmRandomizerDecoder();
        // This asmDecoded cannot recognize the initial pattern
        TmAsmDecoder asmDecoder = new TmAsmDecoder(new byte[] {0x04, 0x47, 0x76, (byte) 0xC7, 0x27, 0x28, (byte) 0x95, (byte) 0xB0});
        try {
            Optional<byte[]> decoded = List.of(input).stream().map(asmDecoder).map(randomizerDecoder).map(rsDecoder).findFirst();
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            // Good
        }
    }

    @Test
    public void testNullInput() {
        try {
            ReedSolomonDecoder enc = new ReedSolomonDecoder(null, 4, false);
            fail("NullPointerException expected");
        } catch(NullPointerException e) {
            // Good
        }

        try {
            ReedSolomonDecoder enc = new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223, 4, false);
            enc.apply(null);
            fail("NullPointerException expected");
        } catch(NullPointerException e) {
            // Good
        }

        try {
            TmRandomizerDecoder enc = new TmRandomizerDecoder();
            enc.apply(null);
            fail("NullPointerException expected");
        } catch(NullPointerException e) {
            // Good
        }

        try {
            TmAsmDecoder enc = new TmAsmDecoder();
            enc.apply(null);
            fail("NullPointerException expected");
        } catch(NullPointerException e) {
            // Good
        }
    }
}
