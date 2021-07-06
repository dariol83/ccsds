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
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AosTransferFrame;
import eu.dariolucia.ccsds.tmtc.util.AnnotatedObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class AosCaduBinaryFileConnector extends AbstractBinaryFileConnector {

	private final boolean ocfPresent;
	private final boolean fhcfPresent;
	private final AosTransferFrame.UserDataType userDataType;
	private final int insertZoneLength;

	private final int asmLength;
	private final int rsLength;
	private final int caduLength;

	public AosCaduBinaryFileConnector(String name, String description, String version, ConnectorConfiguration configuration, IConnectorObserver observer) {
		super(name, description, version, configuration, observer);
		this.ocfPresent = configuration.getBooleanProperty(AosFileConnector.OCF_PRESENT_ID);
		this.fhcfPresent = configuration.getBooleanProperty(AosFileConnector.FHCF_PRESENT_ID);
		this.userDataType = configuration.getEnumProperty(AosFileConnector.USER_TYPE_ID);
		this.insertZoneLength = configuration.getIntProperty(AosFileConnector.INSERT_ZONE_LENGTH);

		this.asmLength = configuration.getIntProperty(TmCaduBinaryFileConnector.CADU_ASM_LENGTH);
		this.rsLength = configuration.getIntProperty(TmCaduBinaryFileConnector.CADU_RS_LENGTH);
		this.caduLength = configuration.getIntProperty(TmCaduBinaryFileConnector.CADU_LENGTH);
	}

	@Override
	protected AnnotatedObject getData(byte[] cadu) {
		return new AosTransferFrame(Arrays.copyOfRange(cadu, asmLength, cadu.length - rsLength), fhcfPresent, insertZoneLength, userDataType, ocfPresent, fecfPresent);
	}

	@Override
	protected byte[] readNextBlock(InputStream is) throws IOException {
		return is.readNBytes(this.caduLength);
	}
}
