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

import java.io.*;
import java.time.Instant;

public abstract class AbstractBinaryFileConnector extends AbstractConnector {

	public static final String FILE_PATH_ID = "file";
	public static final String FECF_PRESENT_ID = "fecf";
	public static final String DATA_RATE_ID = "bitrate";
	public static final String CYCLE_ID = "cycle";

	private volatile boolean running;
	private volatile Thread worker;

	private final File filePath;
	private final boolean cycle;
	private final int bitrate;

	private volatile InputStream fileReader;

	// Readible by subclasses
	protected final boolean fecfPresent;

	public AbstractBinaryFileConnector(String name, String description, String version, ConnectorConfiguration configuration, IConnectorObserver observer) {
		super(name, description, version, configuration, observer);
		this.cycle = configuration.getBooleanProperty(AbstractBinaryFileConnector.CYCLE_ID);
		this.fecfPresent = configuration.getBooleanProperty(AbstractBinaryFileConnector.FECF_PRESENT_ID);
		this.bitrate = configuration.getIntProperty(AbstractBinaryFileConnector.DATA_RATE_ID);
		this.filePath = configuration.getFileProperty(AbstractBinaryFileConnector.FILE_PATH_ID);
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
			byte[] block = readNextBlock(this.fileReader);
			if (running && block != null) {
				processFrameBlock(block);
			} else {
				closeFileReader();
			}
		} catch (Exception e) {
			e.printStackTrace();
			notifyInfo(SeverityEnum.ALARM, String.format("Error processing file %s: %s", this.filePath.getAbsolutePath(), e.getMessage()));
			closeFileReader();
		}
	}

	protected void openFileReader() throws FileNotFoundException {
		if(this.fileReader == null) {
			// Open the file
			this.fileReader = new FileInputStream(this.filePath);
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
				// Read the next block
				byte[] block = readNextBlock(this.fileReader);
				int frameSizeInBits = block.length * 8;
				// Compute the interleave time
				int msecBetweenFrames = (int) (1000.0 / ((double) bitrate / (double)(frameSizeInBits)));
				while (running && block != null) {
					processFrameBlock(block);
					try {
						Thread.sleep(msecBetweenFrames);
					} catch (InterruptedException e) {
						Thread.interrupted();
					}
					block = readNextBlock(this.fileReader);
				}
			} while(this.cycle && this.running);
		} catch (IOException e) {
			notifyInfo(SeverityEnum.ALARM, "Error processing file " + this.filePath.getAbsolutePath() + ": " + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			notifyInfo(SeverityEnum.ALARM, "Error processing file " + this.filePath.getAbsolutePath() + ": " + e.getMessage());
		}
		closeFileReader();
	}

	private void processFrameBlock(byte[] block) {
		AnnotatedObject ttf = getData(block);
		if(!ttf.isAnnotationPresent(IConnector.ANNOTATION_TIME_KEY)) {
			ttf.setAnnotationValue(IConnector.ANNOTATION_TIME_KEY, Instant.now());
		}
		notifyData(ttf);
	}

	protected abstract byte[] readNextBlock(InputStream is) throws IOException;

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
