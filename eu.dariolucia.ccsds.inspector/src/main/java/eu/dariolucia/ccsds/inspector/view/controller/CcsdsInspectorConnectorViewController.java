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

package eu.dariolucia.ccsds.inspector.view.controller;

import eu.dariolucia.ccsds.inspector.api.IConnector;
import eu.dariolucia.ccsds.inspector.api.SeverityEnum;
import eu.dariolucia.ccsds.inspector.application.CcsdsInspector;
import eu.dariolucia.ccsds.inspector.manager.ConnectorManager;
import eu.dariolucia.ccsds.inspector.manager.ConnectorManagerState;
import eu.dariolucia.ccsds.inspector.manager.IConnectorManagerObserver;
import eu.dariolucia.ccsds.inspector.view.charts.BitrateManager;
import eu.dariolucia.ccsds.inspector.view.util.DataStringDumper;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AosTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;
import eu.dariolucia.ccsds.tmtc.util.AnnotatedObject;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CcsdsInspectorConnectorViewController implements Initializable, IConnectorManagerObserver {

    private static final ExecutorService DISPATCHER = Executors.newSingleThreadExecutor();

    private static final DateTimeFormatter INSTANT_TIME_FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.GERMAN).withZone(ZoneId.of("UTC"));
    private static final int MAX_DATA_ITEMS_PER_TABLE = 5000;
    private static final int NB_AMOUNT_TO_REMOVE = 100;

    @FXML
    public Label titleLabel;

    @FXML
    public ImageView playPauseImage;
    @FXML
    public ImageView stepImage;

    private Image playImg;
    private Image pauseImg;
    private Image stepImg;

    @FXML
    public ImageView minimizeImage;

    private Image minimizeImg;

    @FXML
    public ImageView closeImage;

    private Image closeImg;

    private ConnectorManager connectorManager;

    @FXML
    public AreaChart<Instant, Number> bitrateChart;

    private BitrateManager bitrateManager;

    @FXML
    public Label transferFramesLabel;
    @FXML
    public Label spacePacketsLabel;
    @FXML
    public StackPane tableStackPane;
    @FXML
    public TableView<SpacePacket> packetTable;
    @FXML
    public TableView<AbstractTransferFrame> frameTable;

    private boolean frameTableVisible = true;

    @FXML
    public ListView<String> logList;

    @FXML
    public Label rawDataLabel;
    @FXML
    public Label decodedDataLabel;
    @FXML
    public VBox rawAreaVBox;
    @FXML
    public TextArea decodedArea;
    @FXML
    public TextArea rawArea;
    @FXML
    public TextArea annotationArea;

    private double rawAreaScrollTop;

    @FXML
    public ImageView lockDataImage;

    private Image lockDataImg;
    private Image unlockDataImg;

    private boolean selectionLocked = false;

    @FXML
    public ImageView exportDataImage;

    private Image exportDataImg;
    private Image noExportDataImg;

    private boolean exportInProgress = false;

    private Point2D initialPoint;
    private boolean dragActive;

    private ScrollBar frameScrollbar;
    private ScrollBar packetScrollbar;


    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        playImg = new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/inspector/view/res/play.png"), 20, 20,
                true, true);
        pauseImg = new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/inspector/view/res/stop.png"), 20, 20,
                true, true);
        stepImg = new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/inspector/view/res/step.png"), 20, 20,
                true, true);
        minimizeImg = new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/inspector/view/res/minus.png"), 20, 20,
                true, true);
        closeImg = new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/inspector/view/res/cancel.png"), 20, 20,
                true, true);

        lockDataImg = new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/inspector/view/res/lock.png"), 20, 20,
                true, true);
        unlockDataImg = new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/inspector/view/res/unlock.png"), 20, 20,
                true, true);

        exportDataImg = new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/inspector/view/res/export.png"), 20, 20,
                true, true);
        noExportDataImg = new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/inspector/view/res/noexport.png"), 20, 20,
                true, true);

        playPauseImage.setImage(playImg);
        stepImage.setImage(stepImg);
        minimizeImage.setImage(minimizeImg);
        closeImage.setImage(closeImg);

        lockDataImage.setImage(unlockDataImg);

        exportDataImage.setImage(noExportDataImg);

        // Disable legend for chart
        bitrateChart.setLegendVisible(false);

        // Set cell rendered for the frame table
        frameTable.getColumns().get(0).setCellValueFactory(o -> new ReadOnlyObjectWrapper(toTimeString((Instant) o.getValue().getAnnotationValue(IConnector.ANNOTATION_TIME_KEY))));
        frameTable.getColumns().get(1).setCellValueFactory(o -> new ReadOnlyObjectWrapper(deriveFrameType(o.getValue())));
        frameTable.getColumns().get(2).setCellValueFactory(o -> new ReadOnlyObjectWrapper(o.getValue().getSpacecraftId()));
        frameTable.getColumns().get(3).setCellValueFactory(o -> new ReadOnlyObjectWrapper(o.getValue().getVirtualChannelId()));
        frameTable.getColumns().get(4).setCellValueFactory(o -> new ReadOnlyObjectWrapper(o.getValue().getVirtualChannelFrameCount()));
        frameTable.getColumns().get(5).setCellValueFactory(o -> new ReadOnlyObjectWrapper(o.getValue().getLength()));
        frameTable.getColumns().get(6).setCellValueFactory(o -> new ReadOnlyObjectWrapper(o.getValue().isValid()));

        // Register selection change
        frameTable.getSelectionModel().selectedItemProperty().addListener(this::frameTableSelectionChanged);

        // Set cell rendered for the packet table
        packetTable.getColumns().get(0).setCellValueFactory(o -> new ReadOnlyObjectWrapper(toTimeString((Instant) o.getValue().getAnnotationValue(IConnector.ANNOTATION_TIME_KEY))));
        packetTable.getColumns().get(1).setCellValueFactory(o -> new ReadOnlyObjectWrapper(o.getValue().isTelemetryPacket() ? "TM" : "TC"));
        packetTable.getColumns().get(2).setCellValueFactory(o -> new ReadOnlyObjectWrapper(o.getValue().getApid()));
        packetTable.getColumns().get(3).setCellValueFactory(o -> new ReadOnlyObjectWrapper(o.getValue().getPacketSequenceCount()));
        packetTable.getColumns().get(4).setCellValueFactory(o -> new ReadOnlyObjectWrapper(o.getValue().getLength()));
        packetTable.getColumns().get(5).setCellValueFactory(o -> new ReadOnlyObjectWrapper(o.getValue().isQualityIndicator()));

        // Register selection change
        packetTable.getSelectionModel().selectedItemProperty().addListener(this::packetTableSelectionChanged);
    }

    private void frameTableSelectionChanged(ObservableValue<? extends AbstractTransferFrame> observableValue, AbstractTransferFrame oldValue, AbstractTransferFrame newValue) {
        AbstractTransferFrame theValue = frameTable.getSelectionModel().getSelectedItem();
        if(theValue == null) {
            if(!rawArea.getText().isEmpty()) {
                // Save right scrollbar position
                rawAreaScrollTop = rawArea.getScrollTop();
            }
            rawArea.setText("");
            decodedArea.setText("");
            annotationArea.setText("");
        } else {
            if(!rawArea.getText().isEmpty()) {
                // Save right scrollbar position
                rawAreaScrollTop = rawArea.getScrollTop();
            }

            // Something was selected so clear the other selection!
            packetTable.getSelectionModel().clearSelection();

            rawArea.setText(DataStringDumper.dumpData(theValue.getFrame()));
            decodedArea.setText(DataStringDumper.dumpTransferFrame(theValue));
            annotationArea.setText(DataStringDumper.dumpAnnotations(theValue));

            // Try to restore rawArea right scrollbar position
            rawArea.setScrollTop(rawAreaScrollTop);
        }
    }

    private void packetTableSelectionChanged(ObservableValue<? extends SpacePacket> observableValue, SpacePacket oldValue, SpacePacket newValue) {
        SpacePacket theValue = packetTable.getSelectionModel().getSelectedItem();
        if(theValue == null) {
            if(!rawArea.getText().isEmpty()) {
                // Save right scrollbar position
                rawAreaScrollTop = rawArea.getScrollTop();
            }
            rawArea.setText("");
            decodedArea.setText("");
            annotationArea.setText("");
        } else {
            if(!rawArea.getText().isEmpty()) {
                // Save right scrollbar position
                rawAreaScrollTop = rawArea.getScrollTop();
            }

            // Something was selected so clear the other selection!
            frameTable.getSelectionModel().clearSelection();

            rawArea.setText(DataStringDumper.dumpData(theValue.getPacket()));
            decodedArea.setText(DataStringDumper.dumpPacket(theValue));
            annotationArea.setText(DataStringDumper.dumpAnnotations(theValue));

            // Try to restore rawArea right scrollbar position
            rawArea.setScrollTop(rawAreaScrollTop);
        }
    }

    private String toTimeString(Instant annotationValue) {
        return INSTANT_TIME_FORMATTER.format(annotationValue);
    }

    private String deriveFrameType(AbstractTransferFrame o) {
        if (o instanceof TmTransferFrame) {
            return "TM";
        } else if (o instanceof TcTransferFrame) {
            return "TC";
        } else if (o instanceof AosTransferFrame) {
            return "AOS";
        } else {
            return "-";
        }
    }

    @Override
    public void stateReported(ConnectorManager connectorManager, ConnectorManagerState currentState) {
        // Nothing to do
    }

    @Override
    public void infoReported(ConnectorManager connectorManager, Instant now, SeverityEnum severity, String message) {
        Platform.runLater(() -> logList.getItems().add(buildEvent(now, severity, message)));
    }

    @Override
    public void dataReported(ConnectorManager connectorManager, AnnotatedObject o) {
        if (o instanceof AbstractTransferFrame) {
            Platform.runLater(() -> {
                this.frameTable.getItems().add((AbstractTransferFrame) o);
                checkTableFull(frameTable);
            });
            if(selectionLocked && frameTableVisible) {
                Platform.runLater(() -> {
                    frameTable.getSelectionModel().select(frameTable.getItems().size() - 1);
                    setFrameScrollbarToBottom();
                });
            }
        } else if (o instanceof SpacePacket) {
            Platform.runLater(() -> {
                this.packetTable.getItems().add((SpacePacket) o);
                checkTableFull(packetTable);
            });
            if(selectionLocked && !frameTableVisible) {
                Platform.runLater(() -> {
                    packetTable.getSelectionModel().select(packetTable.getItems().size() - 1);
                    setPacketScrollbarToBottom();
                });
            }
        }
    }

    private void checkTableFull(TableView<?> table) {
        // Let's be conservative: if the size goes beyond MAX_DATA_ITEMS_PER_TABLE, drop NB_AMOUNT_TO_REMOVE
        while(table.getItems().size() > MAX_DATA_ITEMS_PER_TABLE) {
            table.getItems().remove(0, NB_AMOUNT_TO_REMOVE);
        }
    }

    private void setPacketScrollbarToBottom() {
        // Lookup
        if(packetScrollbar == null) {
            Set<Node> bars = packetTable.lookupAll(".scroll-bar:vertical");
            for(Node n : bars) {
                if(n instanceof  ScrollBar) {
                    ScrollBar sb = (ScrollBar) n;
                    if(sb.getOrientation() == Orientation.VERTICAL) {
                        packetScrollbar = sb;
                        break;
                    }
                }
            }
        }
        if(packetScrollbar != null && packetScrollbar.isVisible()) {
            packetScrollbar.setValue(packetScrollbar.getMax());
        }
    }

    private void setFrameScrollbarToBottom() {
        // Lookup
        if(frameScrollbar == null) {
            Set<Node> bars = frameTable.lookupAll(".scroll-bar:vertical");
            for(Node n : bars) {
                if(n instanceof  ScrollBar) {
                    ScrollBar sb = (ScrollBar) n;
                    if(sb.getOrientation() == Orientation.VERTICAL) {
                        frameScrollbar = sb;
                        break;
                    }
                }
            }
        }
        if(frameScrollbar != null && frameScrollbar.isVisible()) {
            frameScrollbar.setValue(frameScrollbar.getMax());
        }
    }

    @Override
    public void errorReported(ConnectorManager connectorManager, Instant now, Exception e) {
        Platform.runLater(() -> logList.getItems().add(buildEvent(now, SeverityEnum.WARNING, e.getClass().getName() + ": " + e.getMessage())));
    }

    @Override
    public void disposedReported(ConnectorManager connectorManager) {
        if(bitrateManager != null) {
            bitrateManager.deactivate();
        }
        bitrateManager = null;
    }

    private String buildEvent(Instant now, SeverityEnum sev, String s) {
        StringBuilder sb = new StringBuilder();
        sb.append(INSTANT_TIME_FORMATTER.format(now)).append(' ').append('|').append(' ');
        switch (sev) {
            case INFO:
                sb.append("INFO");
                break;
            case WARNING:
                sb.append("WARN");
                break;
            case ALARM:
                sb.append("ALRM");
                break;
            default:
                sb.append("----");
                break;
        }
        sb.append(' ').append('|').append(' ').append(s);
        return sb.toString();
    }

    public void setConnectorManager(ConnectorManager connectorManager) {
        this.connectorManager = connectorManager;
        this.connectorManager.register(this);
        this.titleLabel.setText(this.connectorManager.getName() + " - " + this.connectorManager.getConnectorFactory().getName());
        this.bitrateManager = new BitrateManager(this.connectorManager, this.bitrateChart);
        this.bitrateManager.activate();
    }

    @FXML
    public void onPlayPauseButtonPressed(MouseEvent mouseEvent) {
        if (playPauseImage.getImage() == playImg) {
            startConnector();
            playPauseImage.setImage(pauseImg);
        } else {
            stopConnector();
            playPauseImage.setImage(playImg);
        }
    }

    private void stopConnector() {
        DISPATCHER.execute(connectorManager::stop);
    }

    private void startConnector() {
        DISPATCHER.execute(connectorManager::start);
    }

    @FXML
    public void onStepButtonPressed(MouseEvent mouseEvent) {
        if (playPauseImage.getImage() == pauseImg) {
            stopConnector();
            playPauseImage.setImage(playImg);
        }
        stepConnector();
        playPauseImage.setImage(pauseImg);
    }

    private void stepConnector() {
        DISPATCHER.execute(() -> {
            try {
                connectorManager.step();
            } catch (UnsupportedOperationException e) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle(CcsdsInspector.NAME);
                    alert.setHeaderText(null);
                    alert.setContentText("Step-based processing not supported by connector");
                    alert.show();
                });
            }
        });
    }

    @FXML
    public void onMinimizeButtonPressed(MouseEvent mouseEvent) {
        ((Stage) minimizeImage.getScene().getWindow()).setIconified(true);
    }

    @FXML
    public void onCloseButtonPressed(MouseEvent mouseEvent) {
        closeImage.getScene().getWindow().hide();
    }

    public void onTransferFramesLabelClicked(MouseEvent mouseEvent) {
        if (!this.transferFramesLabel.getFont().getName().equals("Monospaced Bold")) {
            this.transferFramesLabel.setFont(Font.font("Monospaced", FontWeight.BOLD, 11.0));
            this.spacePacketsLabel.setFont(Font.font("Monospaced", FontWeight.NORMAL, 11.0));
            this.frameTable.toFront();
            this.frameTableVisible = true;
        }
    }

    public void onSpacePacketsLabelClicked(MouseEvent mouseEvent) {
        if (!this.spacePacketsLabel.getFont().getName().equals("Monospaced Bold")) {
            this.spacePacketsLabel.setFont(Font.font("Monospaced", FontWeight.BOLD, 11.0));
            this.transferFramesLabel.setFont(Font.font("Monospaced", FontWeight.NORMAL, 11.0));
            this.packetTable.toFront();
            this.frameTableVisible = false;
        }
    }

    public void onLockImageClicked(MouseEvent mouseEvent) {
        if(selectionLocked) {
            selectionLocked = false;
            lockDataImage.setImage(unlockDataImg);
        } else {
            selectionLocked = true;
            lockDataImage.setImage(lockDataImg);
            if(frameTableVisible) {
                setFrameScrollbarToBottom();
            } else {
                setPacketScrollbarToBottom();
            }
        }
    }

    public void onRawDataLabelClicked(MouseEvent mouseEvent) {
        if (!this.rawDataLabel.getFont().getName().equals("Monospaced Bold")) {
            this.rawDataLabel.setFont(Font.font("Monospaced", FontWeight.BOLD, 11.0));
            this.decodedDataLabel.setFont(Font.font("Monospaced", FontWeight.NORMAL, 11.0));
            this.rawAreaVBox.toFront();
        }
    }

    public void onDecodedDataLabelClicked(MouseEvent mouseEvent) {
        if (!this.decodedDataLabel.getFont().getName().equals("Monospaced Bold")) {
            this.decodedDataLabel.setFont(Font.font("Monospaced", FontWeight.BOLD, 11.0));
            this.rawDataLabel.setFont(Font.font("Monospaced", FontWeight.NORMAL, 11.0));
            this.decodedArea.toFront();
        }
    }

    public void onExportImageClicked(MouseEvent mouseEvent) {
        if(this.exportInProgress) {
            this.exportDataImage.setImage(this.noExportDataImg);
            this.connectorManager.setStorageEnabled(false);
            this.exportInProgress = false;
        } else {
            this.exportDataImage.setImage(this.exportDataImg);
            this.connectorManager.setStorageEnabled(true);
            this.exportInProgress = true;
        }
    }

    @FXML
    public void onWindowMouseDragged(MouseEvent mouseEvent) {
        if(dragActive) {
            Point2D newLocation = new Point2D(mouseEvent.getScreenX(), mouseEvent.getScreenY());
            // Compute difference
            Point2D diff = newLocation.subtract(this.initialPoint);
            // Apply to window
            titleLabel.getScene().getWindow().setX(titleLabel.getScene().getWindow().getX() + diff.getX());
            titleLabel.getScene().getWindow().setY(titleLabel.getScene().getWindow().getY() + diff.getY());
            this.initialPoint = newLocation;
        }
    }

    @FXML
    public void onWindowMousePressed(MouseEvent mouseEvent) {
        this.dragActive = true;
        this.initialPoint = new Point2D(mouseEvent.getScreenX(), mouseEvent.getScreenY());
    }

    @FXML
    public void onWindowMouseReleased(MouseEvent mouseEvent) {
        this.dragActive = false;
    }
}
