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
 * - the spacecraft ID
 * - the transfer frame version number (typically 0, TM frames, or 1, AOS frames)
 * - the virtual channel id (0 to 7 for TM frames, 0 to 63 for AOS frames, or null to indicate the master channel
 *
 * This class is also used for JAXB serialisation as part of the SLE User Test Library configuration.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class GVCID {

	/**
	 * This method parses a string with format (xxx,0|1,yyy|*)[,(xxx,0|1,yyy|*)]+ into a list of GVCIDs.
	 * // TODO move away
	 * @param string the string to parse
	 * @return the list of parsed GVCIDs
	 */
	public static List<GVCID> parsePermittedGvcid(String string) {
		List<GVCID> theList = new LinkedList<>();
		fillGvcidRecursive(theList, string.trim());
		return theList;
	}

	/**
	 * This method parses a string with format (xxx,0|1,yyy|*) into a GVCID.
	 * // TODO move away
	 * @param string the string to parse
	 * @return the parsed GVCID
	 */
	public static GVCID parseGvcid(String string) {
		List<GVCID> theList = new LinkedList<>();
		fillGvcidRecursive(theList, string.trim());
		return theList.get(0);
	}

	// TODO move away
	private static void fillGvcidRecursive(List<GVCID> theList, String trim) {
		if (trim.isEmpty()) {
			return;
		}
		if (trim.charAt(0) != ',' && trim.charAt(0) != '(') {
			throw new IllegalArgumentException(trim + " is not a valid GVCID string");
		}
		int idx1 = trim.charAt(0) == ',' ? 2 : 1;
		int idx2 = trim.indexOf(')');
		String subs = trim.substring(idx1, idx2);
		String[] spl = subs.split(",", -1);
		if (spl.length != 3) {
			throw new IllegalArgumentException(trim + " is not a valid GVCID string: block '" + subs + "' is invalid");
		}
		theList.add(new GVCID(Integer.parseInt(spl[0]), Integer.parseInt(spl[1]),
				spl[2].trim().equals("*") ? null : Integer.parseInt(spl[2].trim())));
		fillGvcidRecursive(theList, trim.substring(idx2 + 1));
	}

	// -----------------------------------------------------------------------------------

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
		} else if (!virtualChannelId.equals(other.virtualChannelId))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "(" + spacecraftId + ", " + transferFrameVersionNumber + ", "
				+ (virtualChannelId == null ? "*" : virtualChannelId) + ")";
	}

}
