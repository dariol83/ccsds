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

public class TmCaduFileConnectorFactory implements IConnectorFactory {

	@Override
	public IConnector createConnector(IConnectorObserver observer, ConnectorConfiguration configuration) {
		return new TmCaduFileConnector(getName(), getDescription(), getVersion(), configuration, observer);
	}

	@Override
	public String getName() {
		return "TM Frame CADU File Connector";
	}

	@Override
	public String getDescription() {
		return "This connector reads TM CADUs from a file. The file shall contain the hex dump of the TM CADUs, one frame per line.";
	}

	@Override
	public String getVersion() {
		return "1.0";
	}

	@Override
	public ConnectorConfigurationDescriptor getConfigurationDescriptor() {
		ConnectorConfigurationDescriptor ccd = new ConnectorConfigurationDescriptor();
		ccd.add(
				ConnectorPropertyDescriptor.fileDescriptor(AbstractAsciiFileConnector.FILE_PATH_ID, "File Path", "Absolute path to the file containing the TM frames", null),
				ConnectorPropertyDescriptor.booleanDescriptor(AbstractAsciiFileConnector.FECF_PRESENT_ID, "Presence of the FECF", "If selected, the FECF is part of the transfer frame", false),
				ConnectorPropertyDescriptor.integerDescriptor(AbstractAsciiFileConnector.DATA_RATE_ID, "Bitrate (bps)", "Number of bits per second that must be read and distributed (approx) by the connector", 8192),
				ConnectorPropertyDescriptor.booleanDescriptor(AbstractAsciiFileConnector.CYCLE_ID, "Cyclic read", "If selected, the connector will loop over the provided file. TM frames will not be updated (e.g. frame counters).", false),
				ConnectorPropertyDescriptor.integerDescriptor(TmCaduFileConnector.CADU_ASM_LENGTH, "ASM length", "Length of the Attached Sync Marker", 4),
				ConnectorPropertyDescriptor.integerDescriptor(TmCaduFileConnector.CADU_RS_LENGTH, "Cyclic read", "Length of the RS codeblock", 160)
		);
		return ccd;
	}
}
