/*
 *  Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.sle.utlfx.controller;

import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafTransferDataInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.outgoing.pdus.RcfTransferDataInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.outgoing.pdus.RocfTransferDataInvocation;
import eu.dariolucia.ccsds.sle.utl.pdu.PduStringUtil;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstance;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.DialogUtils;
import eu.dariolucia.ccsds.sle.utlfx.manager.PduInspectorManager;
import eu.dariolucia.ccsds.sle.utlfx.manager.RawDataViewerManager.RawDataEntry;
import eu.dariolucia.ccsds.sle.utlfx.manager.SlePdu;
import eu.dariolucia.ccsds.sle.utlfx.manager.SlePduAttribute;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PduInspectorController implements Initializable {

	// PDU Inspector
	@FXML
	protected TableView<SlePdu> pduTableView;
	@FXML
	protected TreeTableView<SlePduAttribute> pduDetailsTreeTableView;
	@FXML
	protected Label pduDescriptionText;

	@FXML
	private ToggleButton enablePduInspectionButton;

	@FXML
	private Button clearPduInspectionButton;

	@FXML
	private ToggleButton enablePduRecordingButton;

	@FXML
	protected TitledPane pduInspectionTitledPane;

	// PDU Inspector accordion - Raw Data
	@FXML
	protected Label rawDataDetailsDescriptionText;
	@FXML
	protected TableView<RawDataEntry> rawDataDetailsTableView;

	// PDU Inspector - PDU manager
	protected PduInspectorManager pduInspectorManager;

	protected volatile boolean monitorPdus = true;

	private volatile ExecutorService fileRecorder;
	private volatile PrintStream fileWriter;
	private File lastSelectedFolderForRecording;
	private volatile DumpType dumpType;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		this.pduInspectorManager = new PduInspectorManager(pduTableView, pduDetailsTreeTableView, pduDescriptionText,
				rawDataDetailsDescriptionText, rawDataDetailsTableView);

		{
			Image image = new Image(getClass()
					.getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/play-circle.png"));
			this.enablePduInspectionButton.setGraphic(new ImageView(image));
		}
		{
			Image image = new Image(getClass()
					.getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/trash-2.png"));
			this.clearPduInspectionButton.setGraphic(new ImageView(image));
		}
		{
			Image image = new Image(getClass()
					.getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/activity.png"));
			this.pduInspectionTitledPane.setGraphic(new ImageView(image));
		}
		{
			Image image = new Image(getClass()
					.getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/save.png"));
			this.enablePduRecordingButton.setGraphic(new ImageView(image));
		}
	}

	@FXML
	protected void enablePduInspectionCheckMenuItemSelected(ActionEvent e) {
		this.monitorPdus = ((ToggleButton) e.getSource()).isSelected();
	}

	@FXML
	protected void clearPduButtonSelected(ActionEvent e) {
		Optional<ButtonType> result = DialogUtils.showConfirmation("Clear service instance PDUs",
				"Are you sure to remove all the collected PDUs for the selected service instance?", null, null);
		if (result.isPresent() && result.get() == ButtonType.OK) {
			this.pduInspectorManager.clear();
		}
	}

	public void onPduReceived(ServiceInstance si, Object operation, String name, byte[] encodedOperation) {
		if (this.monitorPdus) {
			Platform.runLater(() -> {
				this.pduInspectorManager.addPdu(new SlePdu(operation, encodedOperation, "IN", false, name));
				// Recording
				if(this.enablePduRecordingButton.isSelected() && this.fileWriter != null) {
					recordData(operation, this.dumpType, this.fileWriter);
				}
			});
		}
	}

	private void recordData(Object operation, DumpType dumpType, PrintStream fileWriter) {
		ExecutorService service = this.fileRecorder;
		if(service != null && !service.isShutdown()) {
			service.submit(() -> store(fileWriter, operation, dumpType));
		}
	}

	private void store(PrintStream fileWriter, Object operation, DumpType dumpType) {
		try {
			switch (dumpType) {
				case HEX_DATA: {
					byte[] data = extractDataFrom(operation);
					if(data != null) {
						fileWriter.println(PduStringUtil.toHexDump(data));
					}
				}
				break;
				case BIN_DATA: {
					byte[] data = extractDataFrom(operation);
					if(data != null) {
						fileWriter.write(data, 0, data.length);
					}
				}
				break;
			}
			fileWriter.flush();
		} catch (Exception e) {
			e.printStackTrace();
			// Ignore
		}
	}

	private byte[] extractDataFrom(Object operation) {
		if(operation instanceof RafTransferDataInvocation) {
			return ((RafTransferDataInvocation) operation).getData().value;
		} else if(operation instanceof RcfTransferDataInvocation) {
			return ((RcfTransferDataInvocation) operation).getData().value;
		} else if(operation instanceof RocfTransferDataInvocation) {
			return ((RocfTransferDataInvocation) operation).getData().value;
		} else {
			return null;
		}
	}

	public void onPduSent(ServiceInstance si, Object operation, String name, byte[] encodedOperation) {
		if (this.monitorPdus) {
			Platform.runLater(() -> this.pduInspectorManager.addPdu(new SlePdu(operation, encodedOperation, "OUT", false, name)));
		}
	}

	public void onPduSentError(ServiceInstance si, Object operation, String name, byte[] encodedOperation, String error,
			Exception exception) {
		if (this.monitorPdus) {
			Platform.runLater(() -> this.pduInspectorManager.addPdu(new SlePdu(operation, encodedOperation, "OUT", true, name)));
		}
	}

	public void onPduDecodingError(ServiceInstance si, byte[] encodedOperation) {
		if (this.monitorPdus) {
			Platform.runLater(() -> this.pduInspectorManager.addPdu(new SlePdu(null, encodedOperation, "IN", true, "<DECODING ERROR>")));
		}
	}

	public void onPduHandlingError(ServiceInstance si, Object operation, byte[] encodedOperation) {
		if (this.monitorPdus) {
			Platform.runLater(() -> this.pduInspectorManager.addPdu(new SlePdu(operation, encodedOperation, "IN", true, "<UNKNOWN>")));
		}
	}

	public void deactivate() {
		// Deregister log console
		this.pduInspectorManager.deactivate();
		// File recording deactivation
		deactivateFileRecording();
	}

	public void enablePduRecordingSelected(ActionEvent actionEvent) {
		if(this.enablePduRecordingButton.isSelected()) {
			// Open file selection
			FileChooser fc = new FileChooser();
			FileChooser.ExtensionFilter selected = new FileChooser.ExtensionFilter("Transfer-Data content dump", "*.dat");
			fc.getExtensionFilters().add(selected);
			fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Hex Transfer-Data content dump", "*.hexdat"));
			fc.setSelectedExtensionFilter(selected);
			if(this.lastSelectedFolderForRecording != null) {
				fc.setInitialDirectory(this.lastSelectedFolderForRecording);
			}
			File file = fc.showSaveDialog(this.enablePduRecordingButton.getScene().getWindow());
			// If file is selected, create file and activate recording
			if(file != null) {
				activateFileRecording(file);
			} else {
				deactivateFileRecording();
			}
		} else {
			Optional<ButtonType> result = DialogUtils.showConfirmation("Stop file recording", "Do you really want to stop the recording of the data?", null, null);
			if(result.isPresent() && result.get() == ButtonType.OK) {
				// If file was opened, stop recording and close file
				deactivateFileRecording();
			} else {
				// Keep the button pressed
				this.enablePduRecordingButton.setSelected(true);
			}
		}
	}

	private void activateFileRecording(File destination) {
		this.lastSelectedFolderForRecording = destination.getParentFile();
		if(destination.getName().endsWith("hexdat")) {
			this.dumpType = DumpType.HEX_DATA;
		} else {
			this.dumpType = DumpType.BIN_DATA;
		}
		try {
			this.fileWriter = new PrintStream(new FileOutputStream(destination));
			if (this.fileRecorder == null) {
				this.fileRecorder = Executors.newFixedThreadPool(1);
			}
		} catch (Exception e) {
			e.printStackTrace();
			deactivateFileRecording();
		}
	}

	private void deactivateFileRecording() {
		this.enablePduRecordingButton.setSelected(false);
		this.dumpType = null;
		// File recording cleanup
		if(this.fileRecorder != null) {
			this.fileRecorder.shutdownNow();
			this.fileRecorder = null;
		}
		if(this.fileWriter != null) {
			this.fileWriter.close();
			this.fileWriter = null;
		}
	}

	public enum DumpType {
		BIN_DATA,
		HEX_DATA
	}
}
