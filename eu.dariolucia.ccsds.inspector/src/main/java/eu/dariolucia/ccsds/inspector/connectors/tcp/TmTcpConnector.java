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

import eu.dariolucia.ccsds.inspector.api.ConnectorConfiguration;
import eu.dariolucia.ccsds.inspector.api.IConnectorObserver;
import eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm;
import eu.dariolucia.ccsds.tmtc.coding.ChannelDecoder;
import eu.dariolucia.ccsds.tmtc.coding.decoder.ReedSolomonDecoder;
import eu.dariolucia.ccsds.tmtc.coding.decoder.TmAsmDecoder;
import eu.dariolucia.ccsds.tmtc.coding.reader.FixedLengthChannelReader;
import eu.dariolucia.ccsds.tmtc.coding.reader.IChannelReader;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.util.AnnotatedObject;

import java.io.IOException;
import java.io.InputStream;

public class TmTcpConnector extends AbstractTcpConnector {

	public static final String ASM_PRESENT_ID = "asm";
	public static final String RS_PRESENT_ID = "rs";
	public static final String SEC_HEADER_LENGTH_ID = "sec_header_length";
	public static final String SEC_TRAILER_LENGTH_ID = "sec_trailer_length";
	public static final String FRAME_LENGTH_ID = "frame_length";

	private final boolean asmPresent;
	private final boolean rsPresent;
	private final int securityHeaderLength;
	private final int securityTrailerLength;
	private final int frameLength;

	private final ChannelDecoder<TmTransferFrame> decoder;
	private volatile IChannelReader reader; // NOSONAR

	public TmTcpConnector(String name, String description, String version, ConnectorConfiguration configuration, IConnectorObserver observer) {
		super(name, description, version, configuration, observer);
		this.asmPresent = configuration.getBooleanProperty(TmTcpConnector.ASM_PRESENT_ID);
		this.rsPresent = configuration.getBooleanProperty(TmTcpConnector.RS_PRESENT_ID);
		this.securityHeaderLength = configuration.getIntProperty(TmTcpConnector.SEC_HEADER_LENGTH_ID);
		this.securityTrailerLength = configuration.getIntProperty(TmTcpConnector.SEC_TRAILER_LENGTH_ID);
		this.frameLength = configuration.getIntProperty(TmTcpConnector.FRAME_LENGTH_ID);

		this.decoder = ChannelDecoder.create(TmTransferFrame.decodingFunction(super.fecfPresent, this.securityHeaderLength, this.securityTrailerLength));
		if(this.asmPresent) {
			this.decoder.addDecodingFunction(new TmAsmDecoder());
		}
		if(this.rsPresent) {
			this.decoder.addDecodingFunction(new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223, this.frameLength/223, false));
		}
		this.decoder.configure();
	}

	@Override
	protected AnnotatedObject getData(InputStream is) throws IOException {
		if(this.reader == null) {
			// Compute total frame length (easy way, might not work always)
			int bytesToRead = this.frameLength + (this.asmPresent ? 4 : 0) + (this.rsPresent ? (this.frameLength/223) * 32: 0);
			this.reader = new FixedLengthChannelReader(is, bytesToRead);
		}
		byte[] data = reader.readNext();
		return this.decoder.apply(data);
	}
}
