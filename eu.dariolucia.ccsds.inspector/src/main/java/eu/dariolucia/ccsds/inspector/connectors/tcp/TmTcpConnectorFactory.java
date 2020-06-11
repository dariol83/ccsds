/*
 *   Copyright (c) 2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package eu.dariolucia.ccsds.inspector.connectors.tcp;

import eu.dariolucia.ccsds.inspector.api.*;

public class TmTcpConnectorFactory implements IConnectorFactory {

	@Override
	public IConnector createConnector(IConnectorObserver observer, ConnectorConfiguration configuration) {
		return new TmTcpConnector(getName(), getDescription(), getVersion(), configuration, observer);
	}

	@Override
	public String getName() {
		return "TM Frame TCP Connector";
	}

	@Override
	public String getDescription() {
		return "This connector reads TM frames from a server socket, delivered in binary format.";
	}

	@Override
	public String getVersion() {
		return "1.0";
	}

	@Override
	public ConnectorConfigurationDescriptor getConfigurationDescriptor() {
		ConnectorConfigurationDescriptor ccd = new ConnectorConfigurationDescriptor();
		ccd.add(
				ConnectorPropertyDescriptor.stringDescriptor(AbstractTcpConnector.HOST_ID, "Host", "Host name to connect to", "localhost"),
				ConnectorPropertyDescriptor.integerDescriptor(AbstractTcpConnector.PORT_ID, "TCP Port", "Port number to connect to", null),
				ConnectorPropertyDescriptor.booleanDescriptor(AbstractTcpConnector.FECF_PRESENT_ID, "Presence of the FECF", "If selected, the FECF is part of the transfer frame", false),
				ConnectorPropertyDescriptor.booleanDescriptor(TmTcpConnector.ASM_PRESENT_ID, "Presence of the ASM", "If selected, the ASM is delivered together to the transfer frame", false),
				ConnectorPropertyDescriptor.booleanDescriptor(TmTcpConnector.RS_PRESENT_ID, "Presence of the RS block", "If selected, the RS block is delivered together to the transfer frame", false),
				ConnectorPropertyDescriptor.integerDescriptor(TmTcpConnector.SEC_HEADER_LENGTH_ID, "Security Header Length", "Security header length in bytes, 0 if not present", 0),
				ConnectorPropertyDescriptor.integerDescriptor(TmTcpConnector.SEC_TRAILER_LENGTH_ID, "Security Trailer Length", "Security trailer length in bytes, 0 if not present", 0),
				ConnectorPropertyDescriptor.integerDescriptor(TmTcpConnector.FRAME_LENGTH_ID, "Frame Length", "Total bytes composing a TM frame", 1115)
		);
		return ccd;
	}
}
