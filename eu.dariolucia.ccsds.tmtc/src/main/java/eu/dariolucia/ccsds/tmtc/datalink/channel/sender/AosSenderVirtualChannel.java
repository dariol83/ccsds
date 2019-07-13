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

package eu.dariolucia.ccsds.tmtc.datalink.channel.sender;

import eu.dariolucia.ccsds.tmtc.datalink.builder.AosTransferFrameBuilder;
import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AosTransferFrame;
import eu.dariolucia.ccsds.tmtc.ocf.pdu.AbstractOcf;
import eu.dariolucia.ccsds.tmtc.transport.pdu.BitstreamData;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * This class allows to send AOS frames built from space packets, bit streams or using the VCA mode. It can work in pull and push mode.
 *
 * For additional details, refers to the parent {@link AbstractSenderVirtualChannel} documentation.
 */
public class AosSenderVirtualChannel extends AbstractSenderVirtualChannel<AosTransferFrame> {

	private final Function<Integer, AbstractOcf> ocfSupplier;

	private final Function<Integer, byte[]> insertZoneSupplier;

	private final int insertZoneLength;

	private final int frameLength;

	private final boolean fhecfPresent;

	private final boolean virtualChannelFrameCountCycleInUse;

	private final AtomicInteger virtualChannelFrameCountCycle = new AtomicInteger(-1);

	private volatile boolean replayFlag = false;

	public AosSenderVirtualChannel(int spacecraftId, int virtualChannelId, VirtualChannelAccessMode mode, boolean fecfPresent, int frameLength, Function<Integer, AbstractOcf> ocfSupplier) {
		this(spacecraftId, virtualChannelId, mode, fecfPresent, frameLength, ocfSupplier, false, false, 0, null);
	}

	public AosSenderVirtualChannel(int spacecraftId, int virtualChannelId, VirtualChannelAccessMode mode, boolean fecfPresent, int frameLength, Function<Integer, AbstractOcf> ocfSupplier, boolean virtualChannelFrameCountCycleInUse, boolean fhecfPresent, int insertZoneLength, Function<Integer, byte[]> insertZoneSupplier) {
		this(spacecraftId, virtualChannelId, mode, fecfPresent, frameLength, ocfSupplier, virtualChannelFrameCountCycleInUse, fhecfPresent, insertZoneLength, insertZoneSupplier, null);
	}

	public AosSenderVirtualChannel(int spacecraftId, int virtualChannelId, VirtualChannelAccessMode mode, boolean fecfPresent, int frameLength, Function<Integer, AbstractOcf> ocfSupplier, IVirtualChannelDataProvider dataProvider) {
		this(spacecraftId, virtualChannelId, mode, fecfPresent, frameLength, ocfSupplier, false, false, 0, null, dataProvider);
	}

	public AosSenderVirtualChannel(int spacecraftId, int virtualChannelId, VirtualChannelAccessMode mode, boolean fecfPresent, int frameLength, Function<Integer, AbstractOcf> ocfSupplier, boolean virtualChannelFrameCountCycleInUse, boolean fhecfPresent, int insertZoneLength, Function<Integer, byte[]> insertZoneSupplier, IVirtualChannelDataProvider dataProvider) {
		super(spacecraftId, virtualChannelId, mode, fecfPresent, dataProvider);
		this.frameLength = frameLength;
		this.ocfSupplier = ocfSupplier;
		this.insertZoneSupplier = insertZoneSupplier;
		this.insertZoneLength = insertZoneLength;
		this.fhecfPresent = fhecfPresent;
		this.virtualChannelFrameCountCycleInUse = virtualChannelFrameCountCycleInUse;
	}

	public boolean isReplayFlag() {
		return replayFlag;
	}

	public void setReplayFlag(boolean replayFlag) {
		this.replayFlag = replayFlag;
	}

	public boolean isVirtualChannelFrameCountCycleInUse() {
		return virtualChannelFrameCountCycleInUse;
	}

	public Function<Integer, AbstractOcf> getOcfSupplier() {
		return ocfSupplier;
	}

	public boolean isOcfPresent() {
		return ocfSupplier != null;
	}

	public Function<Integer, byte[]> getInsertZoneSupplier() {
		return insertZoneSupplier;
	}

	public int getInsertZoneLength() {
		return insertZoneLength;
	}

	public int getFrameLength() {
		return frameLength;
	}

	public boolean isFhecfPresent() {
		return fhecfPresent;
	}

	public int getVirtualChannelFrameCountCycle() {
		return this.virtualChannelFrameCountCycle.get();
	}

	public void setVirtualChannelFrameCountCycle(int num) {
		this.virtualChannelFrameCountCycle.set(num);
	}

	private int incrementVirtualChannelFrameCountCycle() {
		int toReturn = this.virtualChannelFrameCountCycle.incrementAndGet() % 8;
		if (toReturn == 0) {
			this.virtualChannelFrameCountCycle.set(0);
		}
		return toReturn;
	}

	public void dispatchIdle(byte[] idlePattern) {
		dispatchIdle(isReplayFlag(), idlePattern);
	}

	public void dispatchIdle(boolean replay, byte[] idlePattern) {
		if (this.currentFrame != null) {
			throw new IllegalStateException("Pending frame prevents generation of idle frame for virtual channel " + getVirtualChannelId());
		}
		if (deriveAosType() != AosTransferFrame.UserDataType.IDLE) {
			throw new IllegalStateException("Virtual channel " + getVirtualChannelId() + " set to " + deriveAosType() + ", cannot send idle frames on AOS virtual channels different from 63");
		}
		this.currentFrame = createFrameBuilder(replay);
		// Add idle pattern: fill the frame
		int remaining = this.currentFrame.getFreeUserDataLength();
		while (remaining > 0) {
			((AosTransferFrameBuilder) this.currentFrame).addData(idlePattern);
			remaining = this.currentFrame.getFreeUserDataLength();
		}
		// Set idle value in the first header pointer
		((AosTransferFrameBuilder) this.currentFrame).setIdle();
		// Build
		AosTransferFrame toSend = finalizeFullFrame();
		// Dispatch
		this.currentFrame = null;
		notifyTransferFrameGenerated(toSend, 0);
	}

	public int dispatch(SpacePacket isp) {
		return dispatch(isReplayFlag(), isp);
	}

	public int dispatch(boolean replay, SpacePacket isp) {
		return dispatch(replay, Collections.singletonList(isp));
	}

	public int dispatch(byte[] userData) {
		return dispatch(isReplayFlag(), userData);
	}

	public int dispatch(boolean replay, byte[] userData) {
		if (getMode() != VirtualChannelAccessMode.Data) {
			throw new IllegalStateException("Virtual channel " + getVirtualChannelId() + " access mode set to mode " + getMode() + ", but requested User Data access");
		}
		int notWrittenData = userData.length;
		while (notWrittenData > 0) {
			// If there is no pending frame, create the frame builder
			if (this.currentFrame == null) {
				this.currentFrame = createFrameBuilder(replay);
			}
			// Add the data
			notWrittenData = ((AosTransferFrameBuilder) this.currentFrame).addData(userData, userData.length - notWrittenData, notWrittenData);
			// Check full frame
			if (((AosTransferFrameBuilder) this.currentFrame).isFull()) {
				// Build
				AosTransferFrame toSend = finalizeFullFrame();
				// Dispatch
				this.currentFrame = null;
				notifyTransferFrameGenerated(toSend, notWrittenData);
			}
		}
		return getRemainingFreeSpace();
	}

	public int dispatch(SpacePacket... pkts) {
		return dispatch(isReplayFlag(), pkts);
	}

	public int dispatch(boolean replay, SpacePacket... pkts) {
		return dispatch(replay, Arrays.asList(pkts));
	}

	public int dispatch(Collection<SpacePacket> pkts) {
		return dispatch(isReplayFlag(), pkts);
	}

	public int dispatch(boolean replay, Collection<SpacePacket> pkts) {
		if (getMode() != VirtualChannelAccessMode.Packet) {
			throw new IllegalStateException("Virtual channel " + getVirtualChannelId() + " access mode set to mode " + getMode() + ", but requested Packet access");
		}
		List<SpacePacket> packets = new ArrayList<>(pkts);
		// Strategy: fill in a transfer frame as much as you can, till the end. Do segmentation if needed.
		for (int i = 0; i < packets.size(); ++i) {
			SpacePacket isp = packets.get(i);
			int notWrittenData = isp.getLength();
			while (notWrittenData > 0) {
				// If there is no pending frame, create the frame builder
				if (this.currentFrame == null) {
					this.currentFrame = createFrameBuilder(replay);
				}
				// Add the packet: if the packet is not written yet, then write it and get the remaining data.
				if (notWrittenData == isp.getLength()) {
					notWrittenData = ((AosTransferFrameBuilder) this.currentFrame).addSpacePacket(isp.getPacket());
				} else {
					// Otherwise it means that this is a segmented packet that spilled over: write what you can
					notWrittenData = ((AosTransferFrameBuilder) this.currentFrame).addData(isp.getPacket(), isp.getLength() - notWrittenData, notWrittenData);
				}
				if (((AosTransferFrameBuilder) this.currentFrame).isFull()) {
					// Build
					AosTransferFrame toSend = finalizeFullFrame();
					// Dispatch
					this.currentFrame = null;
					notifyTransferFrameGenerated(toSend, notWrittenData + calculateRemainingData(packets, i + 1));
				}
			}
		}
		// Return free space
		return getRemainingFreeSpace();
	}

	public int dispatch(BitstreamData data) {
		return dispatch(isReplayFlag(), data);
	}

	public int dispatch(boolean replayFlag, BitstreamData data) {
		if (getMode() != VirtualChannelAccessMode.Bitstream) {
			throw new IllegalStateException("Virtual channel " + getVirtualChannelId() + " access mode set to mode " + getMode() + ", but requested Bitstream access");
		}
		// No pending frame allowed
		if (this.currentFrame != null) {
			throw new IllegalStateException("Virtual channel " + getVirtualChannelId() + " has pending frames, B-PDU cannot be built");
		}
		// Current implementation assumes that the BitstreamData object does not contain more bytes than
		// the max amount the frame can contain. If so, an exception is thrown.
		if (data.getData().length > getMaxUserDataLength()) {
			throw new IllegalArgumentException("Provided bitstream data size " + data.getData().length +
					" exceeds maximum user data for AOS frame on virtual channel " + getVirtualChannelId() + ": " + getMaxUserDataLength());
		}

		this.currentFrame = createFrameBuilder(replayFlag);

		// Add the data
		((AosTransferFrameBuilder) this.currentFrame).addBitstreamData(data.getData(), data.getNumBits());
		// Build
		AosTransferFrame toSend = finalizeFullFrame();
		// Dispatch
		this.currentFrame = null;
		notifyTransferFrameGenerated(toSend, 0);

		return 0;
	}

	@Override
	public int getMaxUserDataLength() {
		return AosTransferFrameBuilder.computeUserDataLength(getFrameLength(), isFhecfPresent(), getInsertZoneLength(), deriveAosType(), isOcfPresent(), isFecfPresent());
	}

	private AosTransferFrame.UserDataType deriveAosType() {
		if (getVirtualChannelId() == 63) {
			return AosTransferFrame.UserDataType.IDLE;
		} else {
			switch (getMode()) {
				case Packet:
					return AosTransferFrame.UserDataType.M_PDU;
				case Data:
					return AosTransferFrame.UserDataType.VCA;
				case Bitstream:
					return AosTransferFrame.UserDataType.B_PDU;
			}
		}
		throw new IllegalStateException("Cannot derive AOS virtual channel access: virtual channel " + getVirtualChannelId() + ", access mode " + getMode());
	}

	protected AosTransferFrame finalizeFullFrame() {
		// VC frame counter and cycle
		int vcCount = incrementVirtualChannelFrameCounter(16777216);
		((AosTransferFrameBuilder) this.currentFrame)
				.setVirtualChannelFrameCount(vcCount);
		if (isVirtualChannelFrameCountCycleInUse()) {
			((AosTransferFrameBuilder) this.currentFrame)
					.setVirtualChannelFrameCountUsageFlag(true);
			if (vcCount == 0) {
				((AosTransferFrameBuilder) this.currentFrame)
						.setVirtualChannelFrameCountCycle(incrementVirtualChannelFrameCountCycle());
			} else {
				((AosTransferFrameBuilder) this.currentFrame)
						.setVirtualChannelFrameCountCycle(this.virtualChannelFrameCountCycle.get());
			}
		} else {
			((AosTransferFrameBuilder) this.currentFrame)
					.setVirtualChannelFrameCountUsageFlag(false)
					.setVirtualChannelFrameCountCycle(0);
		}
		// Add insertZone
		if (this.insertZoneLength > 0) {
			((AosTransferFrameBuilder) this.currentFrame).setInsertZone(this.insertZoneSupplier.apply(getVirtualChannelId()));
		}
		// Add OCF
		if (this.ocfSupplier != null) {
			((AosTransferFrameBuilder) this.currentFrame).setOcf(this.ocfSupplier.apply(getVirtualChannelId()).getOcf());
		}
		return this.currentFrame.build();
	}

	protected AosTransferFrameBuilder createFrameBuilder(boolean isReplay) {
		return AosTransferFrameBuilder.create(getFrameLength(), isFhecfPresent(), getInsertZoneLength(), deriveAosType(), isOcfPresent(), isFecfPresent())
				.setSpacecraftId(getSpacecraftId())
				.setVirtualChannelId(getVirtualChannelId())
				.setReplayFlag(isReplay);
	}
}
