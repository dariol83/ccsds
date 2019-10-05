/*
 *  Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.sle.utl.si;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import java.util.LinkedList;
import java.util.List;

/**
 * This class contains the information related to a permitted, requested or selected Global Virtual Channel Identifier,
 * or GVCID. This information includes:
 * <ul>
 * <li>the spacecraft ID</li>
 * <li>the transfer frame version number (typically 0, TM frames, or 1, AOS frames)</li>
 * <li>the virtual channel id (0 to 7 for TM frames, 0 to 63 for AOS frames, or null to indicate the master channel</li>
 *</ul>
 * This class is also used for JAXB serialisation as part of the SLE User Test Library configuration.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class GVCID {

	@XmlAttribute(name = "scid", required = true)
	private int spacecraftId;
	@XmlAttribute(name = "tfvn", required = true)
	private int transferFrameVersionNumber;
	@XmlAttribute(name = "vcid")
	private Integer virtualChannelId = null;

	public GVCID() {
	}

	public GVCID(int spacecraftId, int transferFrameVersionNumber, Integer virtualChannelId) {
		super();
		this.spacecraftId = spacecraftId;
		this.transferFrameVersionNumber = transferFrameVersionNumber;
		this.virtualChannelId = virtualChannelId;
	}

	public final int getSpacecraftId() {
		return spacecraftId;
	}

	public final int getTransferFrameVersionNumber() {
		return transferFrameVersionNumber;
	}

	public final Integer getVirtualChannelId() {
		return virtualChannelId;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + spacecraftId;
		result = prime * result + transferFrameVersionNumber;
		result = prime * result + ((virtualChannelId == null) ? 0 : virtualChannelId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		GVCID other = (GVCID) obj;
		if (spacecraftId != other.spacecraftId)
			return false;
		if (transferFrameVersionNumber != other.transferFrameVersionNumber)
			return false;
		if (virtualChannelId == null) {
			if (other.virtualChannelId != null)
				return false;
		} else if (!virtualChannelId.equals(other.virtualChannelId)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return "(" + spacecraftId + ", " + transferFrameVersionNumber + ", "
				+ (virtualChannelId == null ? "*" : virtualChannelId) + ")";
	}

}
