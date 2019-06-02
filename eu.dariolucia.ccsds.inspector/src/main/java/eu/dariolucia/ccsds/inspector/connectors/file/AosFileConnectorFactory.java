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

import eu.dariolucia.ccsds.inspector.api.*;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AosTransferFrame;

public class AosFileConnectorFactory implements IConnectorFactory {

	@Override
	public IConnector createConnector(IConnectorObserver observer, ConnectorConfiguration configuration) {
		return new AosFileConnector(getName(), getDescription(), getVersion(), configuration, observer);
	}

	@Override
	public String getName() {
		return "AOS Frame File Connector";
	}

	@Override
	public String getDescription() {
		return "This connector reads AOS frames from a file. The file shall contain the hex dump of the AOS frame, one frame per line.";
	}

	@Override
	public String getVersion() {
		return "1.0";
	}

	@Override
	public ConnectorConfigurationDescriptor getConfigurationDescriptor() {
		ConnectorConfigurationDescriptor ccd = new ConnectorConfigurationDescriptor();
		ccd.add(
				ConnectorPropertyDescriptor.fileDescriptor(AbstractFileConnector.FILE_PATH_ID, "File Path", "Absolute path to the file containing the AOS frames", null),
				ConnectorPropertyDescriptor.booleanDescriptor(AbstractFileConnector.FECF_PRESENT_ID, "Presence of the FECF", "If selected, the FECF is part of the transfer frame", false),
				ConnectorPropertyDescriptor.booleanDescriptor(AosFileConnector.OCF_PRESENT_ID, "Presence of the OCF", "If selected, the OCF is part of the transfer frame", true),
				ConnectorPropertyDescriptor.booleanDescriptor(AosFileConnector.FHCF_PRESENT_ID, "Presence of the FHEC", "If selected, the FHEC is part of the transfer frame primary header", false),
				ConnectorPropertyDescriptor.integerDescriptor(AosFileConnector.INSERT_ZONE_LENGTH, "Insert Zone Length", "Number of bytes of the Insert Zone", 0),
				ConnectorPropertyDescriptor.enumDescriptor(AosFileConnector.USER_TYPE_ID, "Frame User Data Type", "Content type of the AOS frame", AosTransferFrame.UserDataType.class, AosTransferFrame.UserDataType.M_PDU),
				ConnectorPropertyDescriptor.integerDescriptor(AbstractFileConnector.DATA_RATE_ID, "Bitrate (bps)", "Number of bits per second that must be read and distributed (approx) by the connector", 8192),
				ConnectorPropertyDescriptor.booleanDescriptor(AbstractFileConnector.CYCLE_ID, "Cyclic read", "If selected, the connector will loop over the provided file. AOS frames will not be updated (e.g. frame counters).", false)
		);
		return ccd;
	}
}
