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

package eu.dariolucia.ccsds.tmtc.cop1;

import eu.dariolucia.ccsds.tmtc.cop1.farm.FarmEngine;
import eu.dariolucia.ccsds.tmtc.cop1.farm.FarmState;
import eu.dariolucia.ccsds.tmtc.cop1.fop.*;
import eu.dariolucia.ccsds.tmtc.cop1.fop.util.BcFrameCollector;
import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.AbstractReceiverVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.IVirtualChannelReceiverOutput;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.TcReceiverVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.TcSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.util.TransferFrameCollector;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.ocf.pdu.Clcw;
import eu.dariolucia.ccsds.tmtc.transport.builder.SpacePacketBuilder;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FopFarmInteractionTest {

    @Test
    public void testCop1Session() throws InterruptedException {
        // Allocate sending side
        TcSenderVirtualChannel tcVc = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.PACKET, true, false);
        BcFrameCollector bcFactory = new BcFrameCollector(tcVc);
        tcVc.register(bcFactory);
        TransferFrameCollector<TcTransferFrame> collector = new TransferFrameCollector<>(o -> o.getFrameType() == TcTransferFrame.FrameType.BD || o.getFrameType() == TcTransferFrame.FrameType.AD); // Only BD and AD
        tcVc.register(collector);

        // Allocate reception side
        List<SpacePacket> sink = new CopyOnWriteArrayList<>();
        TcReceiverVirtualChannel receiverVirtualChannel = new TcReceiverVirtualChannel(0, VirtualChannelAccessMode.PACKET, false);
        receiverVirtualChannel.register(new IVirtualChannelReceiverOutput() {
            @Override
            public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
                sink.add(new SpacePacket(packet, qualityIndicator));
            }
        });

        // Create FARM-1
        FarmEngine farm = new FarmEngine(0, receiverVirtualChannel, true, 5, 6, FarmState.S3, 0);
        farm.register((e,o) -> {
            // System.out.println("FARM: " + o);
        });

        // Create FOP-1 to transmit frames straight to the FARM, 1.5 seconds delay
        FopEngine fop = new FopEngine(tcVc.getVirtualChannelId(), tcVc::getNextVirtualChannelFrameCounter, tcVc::setVirtualChannelFrameCounter, bcFactory, bcFactory, frame -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                //
            }
            farm.frameArrived(frame);
            return true;
        });
        fop.register(new IFopObserver() {
            @Override
            public void transferNotification(FopEngine engine, FopOperationStatus status, TcTransferFrame frame) {
                // System.out.println("TC frame " + frame.getVirtualChannelFrameCount() + ": " + status);
            }

            @Override
            public void directiveNotification(FopEngine engine, FopOperationStatus status, Object tag, FopDirective directive, int qualifier) {
                // System.out.println("Directive " + directive + ": " + status);
            }

            @Override
            public void alert(FopEngine engine, FopAlertCode code) {

            }

            @Override
            public void suspend(FopEngine engine) {

            }

            @Override
            public void statusReport(FopEngine engine, FopStatus status) {
                // System.out.println("FOP: " + status);
            }
        });
        // Create a poller that sends the CLCW every second from FARM to FOP
        new Thread(() -> {
           while(true) {
               try {
                   Clcw clcw = farm.get();
                   fop.clcw(clcw);
                   Thread.sleep(1000);
               } catch (InterruptedException e) {
                   //
               } catch (Exception e) {
                   break;
               }
           }
        }).start();

        // Initialise COP-1
        fop.directive(1, FopDirective.SET_FOP_SLIDING_WINDOW, 3);
        fop.directive(2, FopDirective.SET_T1_INITIAL, 3);
        fop.directive(3, FopDirective.SET_TIMEOUT_TYPE, 0);
        fop.directive(4, FopDirective.SET_TRANSMISSION_LIMIT, 3);
        fop.directive(null, FopDirective.INIT_AD_WITH_UNLOCK, 0);

        // Wait for UNLOCK: XXX: perhaps add a confirmation mechanism or wrapper classes
        Thread.sleep(10000);

        // Send 20 frames in AD
        for(int i = 0; i < 20; ++i) {
            tcVc.dispatch(true, 0, generateSpacePacket(i));
            TcTransferFrame genFrame = collector.retrieveFirst(true);
            assertTrue(fop.transmit(genFrame, 30000));
            // System.out.println("Space Packet " + i + " sent");
        }

        // Verify confirmation of the 20 frames
        for(int i = 0; i < 10; ++i) {
            if(sink.size() != 20) {
                Thread.sleep(5000);
            }
        }

        assertEquals(20, sink.size());
        for(int i = 0; i < 20; ++i) {
            assertEquals(i, (int) sink.get(i).getPacketSequenceCount());
        }

        farm.dispose();
        fop.dispose();
    }

    private SpacePacket generateSpacePacket(int counter) {
        SpacePacketBuilder spp = SpacePacketBuilder.create()
                .setApid(100)
                .setQualityIndicator(true)
                .setSecondaryHeaderFlag(true)
                .setTelemetryPacket();
        spp.addData(new byte[120]);
        spp.setPacketSequenceCount(counter % 16384);
        return spp.build();
    }
}