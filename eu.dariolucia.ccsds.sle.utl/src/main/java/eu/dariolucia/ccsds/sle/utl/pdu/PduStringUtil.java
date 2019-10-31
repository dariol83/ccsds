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

package eu.dariolucia.ccsds.sle.utl.pdu;

import javax.xml.bind.DatatypeConverter;
import java.util.regex.Pattern;

/**
 * This class is meant to collect printer functions for all the SLE PDUs.
 */
public class PduStringUtil {

	private static final Pattern OID_PATTERN_SPLITTER = Pattern.compile("\\.");

	private PduStringUtil() {
		// No constructor
	}

	public static String toHexDump(byte[] encodedPdu) {
		return DatatypeConverter.printHexBinary(encodedPdu);
	}

	public static byte[] fromHexDump(String dump) {
		return DatatypeConverter.parseHexBinary(dump);
	}

	public static String toOIDString(int[] oid) {
		if(oid == null || oid.length == 0) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for(int i : oid) {
			sb.append(i).append('.');
		}
		sb.deleteCharAt(sb.length() - 1);
		return sb.toString();
	}

	public static int[] fromOIDString(String oid) {
		if(oid == null || oid.isBlank()) {
			return new int[0];
		} else {
			String[] splt = OID_PATTERN_SPLITTER.split(oid, -1);
			int[] toReturn = new int[splt.length];
			for(int i = 0; i < splt.length; ++i) {
				toReturn[i] = Integer.parseInt(splt[i]);
			}
			return toReturn;
		}
	}
}
