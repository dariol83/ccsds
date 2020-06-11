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
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.util.AnnotatedObject;

import java.util.Arrays;

public class TmCaduFileConnector extends AbstractFileConnector {

	public static final String CADU_ASM_LENGTH = "asm-length";
	public static final String CADU_RS_LENGTH = "rs-length";

	private final int asmLength;
	private final int rsLength;

	public TmCaduFileConnector(String name, String description, String version, ConnectorConfiguration configuration, IConnectorObserver observer) {
		super(name, description, version, configuration, observer);
		this.asmLength = configuration.getIntProperty(TmCaduFileConnector.CADU_ASM_LENGTH);
		this.rsLength = configuration.getIntProperty(TmCaduFileConnector.CADU_RS_LENGTH);
	}

	@Override
	protected AnnotatedObject getData(byte[] frame) {
		return new TmTransferFrame(Arrays.copyOfRange(frame, asmLength, frame.length - rsLength), this.fecfPresent);
	}
}
