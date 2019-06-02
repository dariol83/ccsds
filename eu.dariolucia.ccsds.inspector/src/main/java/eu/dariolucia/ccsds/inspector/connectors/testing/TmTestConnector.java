/*
 * Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package eu.dariolucia.ccsds.inspector.connectors.testing;

import eu.dariolucia.ccsds.inspector.api.AbstractConnector;
import eu.dariolucia.ccsds.inspector.api.ConnectorConfiguration;
import eu.dariolucia.ccsds.inspector.api.IConnectorObserver;
import eu.dariolucia.ccsds.inspector.api.SeverityEnum;
import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.AbstractSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.AosSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.IVirtualChannelSenderOutput;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.TmSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.ocf.builder.ClcwBuilder;
import eu.dariolucia.ccsds.tmtc.ocf.pdu.AbstractOcf;
import eu.dariolucia.ccsds.tmtc.transport.builder.SpacePacketBuilder;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TmTestConnector extends AbstractConnector implements IVirtualChannelSenderOutput {

    private volatile boolean running;
    private volatile Thread generator;

    private final Map<Integer, AtomicInteger> apid2counter = new HashMap<>();
    private final Map<Integer, AbstractSenderVirtualChannel<?>> vcid2sender = new HashMap<>();
    private final AtomicLong masterChannelSeq = new AtomicLong(0);
    private final int msecBetweenPackets;
    private final int bitrate;
    private final TmFrameSelection frameType;
    private int[] selectedVcIds;

    protected TmTestConnector(String name, String description, String version, ConnectorConfiguration configuration, IConnectorObserver observer) {
        super(name, description, version, configuration, observer);
        // Create the required VCs
        frameType = (TmFrameSelection) getConfiguration().getProperty(TmTestConnectorFactory.FRAME_TYPE_ID).getValue();
        selectedVcIds = (int[]) getConfiguration().getProperty(TmTestConnectorFactory.VCIDS_ID).getValue();
        if (selectedVcIds == null || selectedVcIds.length == 0) {
            if (frameType == TmFrameSelection.TM) {
                selectedVcIds = new int[]{0, 1, 2, 3, 4, 5, 6, 7};
            } else {
                selectedVcIds = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 40, 63};
            }
        }
        // Create the virtual channel
        for (int i : selectedVcIds) {
            createVirtualChannel(frameType, i);
        }
        bitrate = getConfiguration().getIntProperty(TmTestConnectorFactory.BITRATE_ID);
        // Assuming space packets with 450 bytes each
        msecBetweenPackets = (int) (1000.0 / (bitrate / (450.0 * 8)));
    }

    @Override
    protected void doStart() {
        if (running) {
            notifyInfo(SeverityEnum.WARNING, "Connector already started");
            return;
        }
        running = true;
        generator = new Thread(this::generate);
        generator.setDaemon(true);
        generator.start();

        notifyInfo(SeverityEnum.INFO, "Connector started");
    }

    private void generate() {
        while (running) {
            try {
                Thread.sleep(msecBetweenPackets);
            } catch (InterruptedException e) {
                Thread.interrupted();
                continue;
            }
            // Generate packet: select random apid (between 900 and 1200) and VC
            int apid = (int) Math.floor(900 + Math.random() * 300);
            int vc = selectedVcIds[(apid % selectedVcIds.length)];

            if(vc == 7 && frameType == TmFrameSelection.TM) {
                // Idle frame
                generateIdle(vc);
            } else if(vc == 63 && frameType == TmFrameSelection.AOS) {
                // Idle frame
                generateIdle(vc);
            } else {
                // Normal packet
                generateSpacePacket(vc, apid);
            }
        }
    }

    private void createVirtualChannel(TmFrameSelection tfs, int i) {
        int scid = getConfiguration().getIntProperty(TmTestConnectorFactory.SC_ID);
        boolean fecfPresent = (Boolean) getConfiguration().getProperty(TmTestConnectorFactory.FECF_PRESENT_ID).getValue();
        boolean ocfPresent = (Boolean) getConfiguration().getProperty(TmTestConnectorFactory.CLCW_PRESENT_ID).getValue();
        int frameLength = getConfiguration().getIntProperty(TmTestConnectorFactory.LENGTH_ID);
        AbstractSenderVirtualChannel vc;
        if(tfs == TmFrameSelection.TM) {
            vc = new TmSenderVirtualChannel(scid, i, VirtualChannelAccessMode.Packet, fecfPresent, frameLength, this::masterChannelSource, ocfPresent ? this::ocfSource : null);
        } else {
            vc = new AosSenderVirtualChannel(scid, i, VirtualChannelAccessMode.Packet, fecfPresent, frameLength, ocfPresent ? this::ocfSource : null);
        }
        vc.register(this);
        this.vcid2sender.put(i, vc);
    }

    private int masterChannelSource() {
        return (int) masterChannelSeq.getAndIncrement() % 256;
    }

    private AbstractOcf ocfSource(int vcId) {
        return ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(2)
                .setLockoutFlag(false)
                .setNoBitlockFlag(true)
                .setNoRfAvailableFlag(false)
                .setReportValue(121)
                .setRetransmitFlag(false)
                .setVirtualChannelId(1)
                .build();
    }

    private void generateIdle(int vcid) {
        AbstractSenderVirtualChannel<?> vc = this.vcid2sender.get(vcid);
        int remaining = vc.getRemainingFreeSpace();
        if(remaining >= 6) {
            SpacePacketBuilder spp = SpacePacketBuilder.create()
                    .setIdle()
                    .setQualityIndicator(true)
                    .setSecondaryHeaderFlag(false)
                    .setTelemetryPacket();
            spp.addData(new byte[remaining]);
            spp.setPacketSequenceCount(0);
            notifyInfo(SeverityEnum.INFO, "Idle packet generated");
            vc.dispatch(spp.build());
            remaining = vc.getRemainingFreeSpace();
        }
        if(remaining == 0) {
            // No frame ready, good
            vc.dispatchIdle(new byte[]{0x55});
        }
    }

    private void generateSpacePacket(int vcId, int apid) {
        AbstractSenderVirtualChannel<?> vc = this.vcid2sender.get(vcId);
        SpacePacket sp = buildSpacePacket(apid, 450);
        vc.dispatch(sp);
    }

    private SpacePacket buildSpacePacket(int apid, int payload) {
        AtomicInteger currentSeqCount = this.apid2counter.get(apid);
        if(currentSeqCount == null) {
            currentSeqCount = new AtomicInteger(0);
            this.apid2counter.put(apid, currentSeqCount);
        }
        int seqCount = currentSeqCount.getAndIncrement() % 16384;

        SpacePacketBuilder spp = SpacePacketBuilder.create()
                .setApid(apid)
                .setQualityIndicator(true)
                .setSecondaryHeaderFlag(false)
                .setTelemetryPacket();
        spp.addData(new byte[payload]);
        spp.setPacketSequenceCount(seqCount);
        return spp.build();
    }

    @Override
    protected void doStop() {
        if (!running) {
            return;
        }
        running = false;
        if (generator != null) {
            try {
                generator.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.interrupted();
            }
        }
        generator = null;
        notifyInfo(SeverityEnum.INFO, "Connector stopped");
    }

    @Override
    protected void doDispose() {
        // Nothing to be done
    }

    @Override
    public void transferFrameGenerated(AbstractSenderVirtualChannel vc, AbstractTransferFrame generatedFrame, int bufferedBytes) {
        if(generatedFrame != null) {
            notifyData(generatedFrame);
        }
    }
}
