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

package eu.dariolucia.ccsds.cfdp.fx.application;
	
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import eu.dariolucia.ccsds.cfdp.entity.ICfdpEntity;
import eu.dariolucia.ccsds.cfdp.filestore.impl.FilesystemBasedFilestore;
import eu.dariolucia.ccsds.cfdp.fx.dialogs.DialogUtils;
import eu.dariolucia.ccsds.cfdp.mib.Mib;
import eu.dariolucia.ccsds.cfdp.mib.RemoteEntityConfigurationInformation;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayer;
import eu.dariolucia.ccsds.cfdp.ut.UtLayerException;
import eu.dariolucia.ccsds.cfdp.ut.impl.TcpLayer;
import eu.dariolucia.ccsds.cfdp.ut.impl.UdpLayer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;


public class CfdpFxTestTool extends Application {
	
	private static final Logger LOG = Logger.getLogger(CfdpFxTestTool.class.getName());
	
	public static final String NAME = "CFDP FX Application";
	public static final String VERSION = "1.0.0";
	public static final String AUTHOR = "Dario Lucia (dario.lucia@gmail.com) - https://www.dariolucia.eu";
	public static final String YEARS = "(c) 2021-2023";

	private static final String MIB_FLAG = "--mib";
	private static final String UDP_PORT_FLAG = "--udp";
	private static final String TCP_PORT_FLAG = "--tcp";
	private static final String FILESTORE_FLAG = "--filestore";
	private static final String HELP_FLAG = "--help";

	private static final String DEFAULT_MIB = "mib.xml";
	private static final int DEFAULT_UDP_PORT = 34555;
	private static final int DEFAULT_TCP_PORT = 34555;
	private static final String DEFAULT_FILESTORE = "filestore";

	private static int udpPort;
	private static int tcpPort;
	private static String mibPath;
	private static String filestorePath;
	private static ICfdpEntity cfdpEntity;

	@Override
	public void start(Stage primaryStage) {
		try {
			VBox root = FXMLLoader.load(CfdpFxTestTool.class.getResource("/eu/dariolucia/ccsds/cfdp/fx/fxml/Main.fxml"));
			Scene scene = new Scene(root,1150,768);
			primaryStage.getIcons().add(new Image(CfdpFxTestTool.class.getResourceAsStream("/eu/dariolucia/ccsds/cfdp/fx/images/monitor.png")));
			primaryStage.setScene(scene);
			primaryStage.setTitle(NAME + " " + VERSION + " - CFDP Entity: " + cfdpEntity.getMib().getLocalEntity().getLocalEntityId() + " - Filestore: " + filestorePath);
			primaryStage.setOnCloseRequest(CfdpFxTestTool::performExit);
			primaryStage.show();

		} catch(Exception e) {
			LOG.log(Level.SEVERE, "Cannot start the application", e);
		}
	}
	
	public static void main(String[] args) {
		// First, copy the mandatory arguments so that they are accessible everywhere
		// Check arguments
		if(args.length == 1 && args[0].equals(HELP_FLAG)) {
			printHelp();
			System.exit(1);
		}

		udpPort = DEFAULT_UDP_PORT;
		tcpPort = DEFAULT_TCP_PORT;
		mibPath = DEFAULT_MIB;
		filestorePath = DEFAULT_FILESTORE;
		for(int i = 0; i < args.length; ++i) {
			switch (args[i]) {
				case MIB_FLAG:
					mibPath = args[++i];
					break;
				case UDP_PORT_FLAG:
					udpPort = Integer.parseInt(args[++i]);
					break;
				case TCP_PORT_FLAG:
					tcpPort = Integer.parseInt(args[++i]);
					break;
				case FILESTORE_FLAG:
					filestorePath = args[++i];
					break;
				default:
					System.err.println("Cannot recognise argument: " + args[i]); // NOSONAR
					printHelp();
					System.exit(1);
			}
		}
		// Init the entity
		try {
			initialiseCfdpEntity();
		} catch (IOException | UtLayerException e) {
			e.printStackTrace();
			System.exit(1);
		}

		launch(args);
	}

	private static void initialiseCfdpEntity() throws IOException, UtLayerException {
		Mib conf1File = Mib.load(new FileInputStream(CfdpFxTestTool.getMibPath()));
		File fs1Folder = new File(CfdpFxTestTool.getFilestorePath());
		if(!fs1Folder.exists()) {
			fs1Folder.mkdirs();
		}
		FilesystemBasedFilestore fs1 = new FilesystemBasedFilestore(fs1Folder);
		TcpLayer tcpLayer = new TcpLayer(conf1File, CfdpFxTestTool.getTcpPort());
		tcpLayer.activate();
		UdpLayer udpLayer = new UdpLayer(conf1File, CfdpFxTestTool.getUdpPort());
		udpLayer.activate();
		cfdpEntity = ICfdpEntity.create(conf1File, fs1, tcpLayer, udpLayer);
		// RX/TX availability
		for(RemoteEntityConfigurationInformation r : conf1File.getRemoteEntities()) {
			tcpLayer.setRxAvailability(true, r.getRemoteEntityId());
			tcpLayer.setTxAvailability(true, r.getRemoteEntityId());
			udpLayer.setRxAvailability(true, r.getRemoteEntityId());
			udpLayer.setTxAvailability(true, r.getRemoteEntityId());
		}
	}

	private static void printHelp() {
		System.out.println("Usage: CfdpFxTestTool " +       // NOSONAR
				"[--udp <port: default 34555>] " +
				"[--tcp <port: default 34555>] " +
				"[--mib <path to mib file: default 'mib.xml'>] " +
				"[--filestore <path to filestore folder: default 'filestore'>]");
	}

	public static void performExit(Event e) {
		Optional<ButtonType> result = DialogUtils.showConfirmation(NAME + " - Confirm Exit", "Are you sure you want to exit?", null, null);
		if (result.isPresent() && result.get() == ButtonType.OK) {
			// Dispose the UT layers and the entity
			for(IUtLayer utl : cfdpEntity.getUtLayers()) {
				utl.dispose();
			}
			cfdpEntity.dispose();

			Platform.exit();
			System.exit(0);
		} else {
			if (e != null) {
				e.consume();
			}
		}
	}

	public static int getUdpPort() {
		return udpPort;
	}

	public static int getTcpPort() {
		return tcpPort;
	}

	public static String getMibPath() {
		return mibPath;
	}

	public static String getFilestorePath() {
		return filestorePath;
	}

	public static ICfdpEntity getCfdpEntity() {
		return cfdpEntity;
	}
}
