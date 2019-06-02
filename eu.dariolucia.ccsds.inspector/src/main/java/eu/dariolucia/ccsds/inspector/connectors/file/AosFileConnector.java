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
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.util.AnnotatedObject;

public class AosFileConnector extends AbstractFileConnector {

	public static final String OCF_PRESENT_ID = "ocf";
	public static final String FHCF_PRESENT_ID = "fhcf";
	public static final String USER_TYPE_ID = "userType";
	public static final String INSERT_ZONE_LENGTH = "insertZoneLength";

	private final boolean ocfPresent;
	private final boolean fhcfPresent;
	private final AosTransferFrame.UserDataType userDataType;
	private final int insertZoneLength;

	public AosFileConnector(String name, String description, String version, ConnectorConfiguration configuration, IConnectorObserver observer) {
		super(name, description, version, configuration, observer);
		ocfPresent = configuration.getBooleanProperty(OCF_PRESENT_ID);
		fhcfPresent = configuration.getBooleanProperty(FHCF_PRESENT_ID);
		userDataType = configuration.getEnumProperty(USER_TYPE_ID);
		insertZoneLength = configuration.getIntProperty(INSERT_ZONE_LENGTH);
	}

	@Override
	protected AnnotatedObject getData(byte[] frame) {
		return new AosTransferFrame(frame, fhcfPresent, insertZoneLength, userDataType, ocfPresent, fecfPresent);
	}
}
