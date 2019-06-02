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

package eu.dariolucia.ccsds.inspector.connectors.file;

import eu.dariolucia.ccsds.inspector.api.ConnectorConfiguration;
import eu.dariolucia.ccsds.inspector.api.IConnectorObserver;
import eu.dariolucia.ccsds.tmtc.algorithm.BchCltuAlgorithm;
import eu.dariolucia.ccsds.tmtc.algorithm.RandomizerAlgorithm;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.util.AnnotatedObject;

public class TcFileConnector extends AbstractFileConnector {

	public static final String SEGMENTED_ID = "segmented";
	public static final String DERANDOMIZE_ID = "derandomize";
	public static final String SEC_HEADER_LENGTH_ID = "securityHeaderLength";
	public static final String SEC_TRAILER_LENGTH_ID = "securityTrailerLength";

	private final boolean segmented;
	private final boolean derandomize;
	private final int secHeaderLength;
	private final int secTrailerLength;

	public TcFileConnector(String name, String description, String version, ConnectorConfiguration configuration, IConnectorObserver observer) {
		super(name, description, version, configuration, observer);
		segmented = configuration.getBooleanProperty(SEGMENTED_ID);
		derandomize = configuration.getBooleanProperty(DERANDOMIZE_ID);
		secHeaderLength = configuration.getIntProperty(SEC_HEADER_LENGTH_ID);
		secTrailerLength = configuration.getIntProperty(SEC_TRAILER_LENGTH_ID);
	}

	@Override
	protected AnnotatedObject getData(byte[] frame) {
		byte[] decodedFrame;
		// First check if it is a CLTU
		if(frame[0] == (byte) 0xEB && frame[1] == (byte) 0x90) {
			// CLTU
			decodedFrame = BchCltuAlgorithm.decode(frame);
		} else {
			decodedFrame = frame;
		}
		if(derandomize) {
			RandomizerAlgorithm.randomizeFrameCltu(decodedFrame);
		}
		return new TcTransferFrame(decodedFrame, segmented, fecfPresent, secHeaderLength, secTrailerLength);
	}
}
