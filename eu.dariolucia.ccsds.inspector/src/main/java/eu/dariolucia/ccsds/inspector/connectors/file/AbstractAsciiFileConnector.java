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
import eu.dariolucia.ccsds.tmtc.util.AnnotatedObject;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;

import java.io.*;
import java.time.Instant;

public abstract class AbstractAsciiFileConnector extends AbstractConnector {

	public static final String FILE_PATH_ID = "file";
	public static final String FECF_PRESENT_ID = "fecf";
	public static final String DATA_RATE_ID = "bitrate";
	public static final String CYCLE_ID = "cycle";

	private volatile boolean running;
	private volatile Thread worker; // NOSONAR

	private final File filePath;
	private final boolean cycle;
	private final int bitrate;

	private volatile BufferedReader fileReader; // NOSONAR

	// Readible by subclasses
	protected final boolean fecfPresent;

	public AbstractAsciiFileConnector(String name, String description, String version, ConnectorConfiguration configuration, IConnectorObserver observer) {
		super(name, description, version, configuration, observer);
		this.cycle = configuration.getBooleanProperty(AbstractAsciiFileConnector.CYCLE_ID);
		this.fecfPresent = configuration.getBooleanProperty(AbstractAsciiFileConnector.FECF_PRESENT_ID);
		this.bitrate = configuration.getIntProperty(AbstractAsciiFileConnector.DATA_RATE_ID);
		this.filePath = configuration.getFileProperty(AbstractAsciiFileConnector.FILE_PATH_ID);
	}

	@Override
	protected void doStart() {
		if (running) {
			notifyInfo(SeverityEnum.WARNING, "Connector already started");
			return;
		}
		running = true;
		worker = new Thread(this::generate);
		worker.setDaemon(true);
		worker.start();

		notifyInfo(SeverityEnum.INFO, getName() + " started");
	}

	@Override
	protected void doStep() {
		try {
			openFileReader();
			// Read the next line
			String line = readNextLine();
			if (running && line != null) {
				processFrameLine(line);
			} else {
				closeFileReader();
			}
		} catch (IOException e) {
			notifyInfo(SeverityEnum.ALARM, String.format("Error processing file %s: %s", this.filePath.getAbsolutePath(), e.getMessage()));
			closeFileReader();
		} catch (Exception e) {
			e.printStackTrace();
			notifyInfo(SeverityEnum.ALARM, String.format("Error processing file %s: %s", this.filePath.getAbsolutePath(), e.getMessage()));
			closeFileReader();
		}
	}

	protected void openFileReader() throws FileNotFoundException {
		if(this.fileReader == null) {
			// Open the file
			this.fileReader = new BufferedReader(new InputStreamReader(new FileInputStream(this.filePath)));
		}
	}

	protected void closeFileReader() {
		if (this.fileReader != null) {
			try {
				this.fileReader.close();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				this.fileReader = null;
			}
		}
	}

	protected void generate() {
		try {
			do {
				// Close the file if any
				closeFileReader();
				// Open the file
				openFileReader();
				// Read the first line and use it to compute the frame size
				String line = readNextLine();
				int frameSizeInBits = 1115 * 8;
				if(line != null && !line.isBlank()) {
					frameSizeInBits = line.length() * 4; // line.length() / 2 * 8
				}
				// Compute the interleave time
				int msecBetweenFrames = (int) (1000.0 / ((double) bitrate / (double) (frameSizeInBits)));
				while (running && line != null) {
					processFrameLine(line);
					sleepForBitrate(msecBetweenFrames);
					line = readNextLine();
				}
			} while(this.cycle && this.running);
		} catch (Exception e) {
			e.printStackTrace();
			notifyInfo(SeverityEnum.ALARM, "Error processing file " + this.filePath.getAbsolutePath() + ": " + e.getMessage());
		}
		closeFileReader();
	}

	private void sleepForBitrate(int msecBetweenFrames) {
		try {
			Thread.sleep(msecBetweenFrames);
		} catch (InterruptedException e) {
			Thread.interrupted();
		}
	}

	private void processFrameLine(String line) {
		byte[] frame = StringUtil.toByteArray(line.toUpperCase());
		AnnotatedObject ttf = getData(frame);
		if(!ttf.isAnnotationPresent(AbstractConnector.ANNOTATION_TIME_KEY)) {
			ttf.setAnnotationValue(AbstractConnector.ANNOTATION_TIME_KEY, Instant.now());
		}
		notifyData(ttf);
	}

	private String readNextLine() throws IOException {
		while(true) {
			String line = this.fileReader.readLine();
			if (line == null) {
				return null;
			} else if (!line.isBlank()) {
				return line;
			}
		}
	}

	protected abstract AnnotatedObject getData(byte[] frame);

	@Override
	protected void doStop() {
		if (!running) {
			return;
		}
		running = false;
		if (worker != null) {
			try {
				worker.interrupt();
				worker.join(2000, 0);
			} catch (InterruptedException e) {
				e.printStackTrace();
				Thread.interrupted();
			}
		}
		worker = null;
		notifyInfo(SeverityEnum.INFO, getName() + " stopped");
	}

	@Override
	protected void doDispose() {
		if(getState() != ConnectorState.IDLE) {
			stop();
		}
		if(this.fileReader != null) {
			try {
				this.fileReader.close();
			} catch (IOException e) {
				// What can you do here?
			}
		}
		this.fileReader = null;
	}
}
