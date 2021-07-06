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

public class TcFileConnectorFactory implements IConnectorFactory {

	@Override
	public IConnector createConnector(IConnectorObserver observer, ConnectorConfiguration configuration) {
		return new TcFileConnector(getName(), getDescription(), getVersion(), configuration, observer);
	}

	@Override
	public String getName() {
		return "TC Frame/CLTU File Connector";
	}

	@Override
	public String getDescription() {
		return "This connector reads TC frames/CLTUs from a file. The file shall contain the hex dump of the TC frame or CLTUs, one unit per line. If the file contains CLTUs, these are decoded and presented as TC Frames.";
	}

	@Override
	public String getVersion() {
		return "1.0";
	}

	@Override
	public ConnectorConfigurationDescriptor getConfigurationDescriptor() {
		ConnectorConfigurationDescriptor ccd = new ConnectorConfigurationDescriptor();
		ccd.add(
				ConnectorPropertyDescriptor.fileDescriptor(AbstractAsciiFileConnector.FILE_PATH_ID, "File Path", "Absolute path to the file containing the TC frames/CLTUs", null),
				ConnectorPropertyDescriptor.booleanDescriptor(AbstractAsciiFileConnector.FECF_PRESENT_ID, "Presence of the FECF", "If selected, the FECF is part of the transfer frame", false),
				ConnectorPropertyDescriptor.booleanDescriptor(TcFileConnector.SEGMENTED_ID, "Segmentation", "If selected, the connector assumes that the TC frames contain TC segments", true),
				ConnectorPropertyDescriptor.booleanDescriptor(TcFileConnector.DERANDOMIZE_ID, "Derandomization", "If selected, the connector will de-randomize the TC frame", false),
				ConnectorPropertyDescriptor.integerDescriptor(TcFileConnector.SEC_HEADER_LENGTH_ID, "Security Header Length", "Number of bytes composing the security header, 0 if not present", 0),
				ConnectorPropertyDescriptor.integerDescriptor(TcFileConnector.SEC_TRAILER_LENGTH_ID, "Security Trailer Length", "Number of bytes composing the security trailer, 0 if not present", 0),
				ConnectorPropertyDescriptor.integerDescriptor(AbstractAsciiFileConnector.DATA_RATE_ID, "Bitrate (bps)", "Number of bits per second that must be read and distributed (approx) by the connector", 8192),
				ConnectorPropertyDescriptor.booleanDescriptor(AbstractAsciiFileConnector.CYCLE_ID, "Cyclic read", "If selected, the connector will loop over the provided file. TC frames will not be updated (e.g. frame counters).", false)
		);
		return ccd;
	}
}
