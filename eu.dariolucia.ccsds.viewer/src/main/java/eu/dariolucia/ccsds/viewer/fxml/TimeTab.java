/*
 *   Copyright (c) 2021 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.viewer.fxml;

import eu.dariolucia.ccsds.encdec.pus.TcPusHeader;
import eu.dariolucia.ccsds.encdec.pus.TmPusHeader;
import eu.dariolucia.ccsds.encdec.time.AbsoluteTimeDescriptor;
import eu.dariolucia.ccsds.encdec.value.TimeUtil;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.time.Instant;
import java.util.ResourceBundle;

import static eu.dariolucia.ccsds.viewer.utils.UI.addLine;

public class TimeTab implements Initializable {

    public static final String YES = "YES";
    public static final String NO = "NO";
    public static final String CUC = "CUC";
    public static final String CDS = "CDS";
    public static final String SHORT_16BITS = "Days - 16 bits";
    public static final String LONG_24BITS = "Days - 24 bits";
    public static final String NONE = "None";
    public static final String MICRO = "Micro";
    public static final String PICO = "Pico";

    public VBox timeViewbox;

    public ChoiceBox<String> timePFieldChoicebox;
    public ChoiceBox<String> timeTypeChoicebox;
    public ChoiceBox<String> timeCdsTypeChoicebox;
    public ChoiceBox<String> timeCdsSubmillisChoicebox;
    public TextField timeCucCoarseTextField;
    public TextField timeCucFineTextField;
    public TextField timeAgencyEpochTextField;

    public TextArea timeTextArea;
    public TextArea timeResultTextArea;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        timePFieldChoicebox.getItems().addAll(YES, NO);
        timePFieldChoicebox.getSelectionModel().select(1);

        timeTypeChoicebox.getItems().addAll(CUC, CDS);
        timeTypeChoicebox.getSelectionModel().select(1);

        timeCdsTypeChoicebox.getItems().addAll(SHORT_16BITS, LONG_24BITS);
        timeCdsTypeChoicebox.getSelectionModel().select(1);

        timeCdsSubmillisChoicebox.getItems().addAll(NONE, MICRO, PICO);
        timeCdsSubmillisChoicebox.getSelectionModel().select(1);
    }

    public void onTimeDecodeButtonClicked(ActionEvent actionEvent) {
        timeResultTextArea.clear();
        String data = timeTextArea.getText().toUpperCase();
        data = data.trim();
        data = data.replace("\n", "");
        data = data.replace("\t", "");
        data = data.replace(" ", "");
        if(data.isBlank()) {
            return;
        }
        // Let's try to see what we have to do
        try {
            byte[] bdata = StringUtil.toByteArray(data);
            processTime(bdata);
        } catch (Exception e) {
            error(e.getMessage());
        }
    }

    private void processTime(byte[] bdata) {
        String message = "";

        // Time
        try {
            StringBuilder sb = new StringBuilder("");
            Instant time = null;
            if(timeTypeChoicebox.getSelectionModel().getSelectedItem().equals(CUC)) {
                if (timePFieldChoicebox.getSelectionModel().getSelectedItem().equals(YES)) {
                    time = TimeUtil.fromCUC(bdata, toInstant(timeAgencyEpochTextField.getText()));
                } else {
                    time = TimeUtil.fromCUC(bdata, toInstant(timeAgencyEpochTextField.getText()),
                            Integer.parseInt(timeCucCoarseTextField.getText()),
                            Integer.parseInt(timeCucFineTextField.getText()));
                }
                // CUC is TAI but the conversion is done already in the parsing function
            } else {
                if (timePFieldChoicebox.getSelectionModel().getSelectedItem().equals(YES)) {
                    time = TimeUtil.fromCDS(bdata, toInstant(timeAgencyEpochTextField.getText()));
                } else {
                    time = TimeUtil.fromCDS(bdata, toInstant(timeAgencyEpochTextField.getText()),
                            timeCdsTypeChoicebox.getSelectionModel().getSelectedItem().equals(SHORT_16BITS),
                            timeCdsSubmillisChoicebox.getSelectionModel().getSelectedIndex());
                }
            }
            addLine(sb, "Time UTC", time);
            addLine(sb, "Time TAI (seconds from 1st Jan 1970)", TimeUtil.toTAI(time.getEpochSecond()));
            timeResultTextArea.setText(sb.toString());
            return;
        } catch (Exception e) {
            // Not a space packet
            message = e.getMessage();
        }

        error("Provided dump is not a CCSDS time: " + message);
    }

    private Instant toInstant(String text) {
        if(text.trim().isBlank()) {
            return null;
        } else {
            return Instant.parse(text.trim());
        }
    }

    private void error(String error) {
        timeResultTextArea.setText(timeResultTextArea.getText() + "\nERROR: " + error);
    }

    public void onTimeClearButtonClicked(ActionEvent actionEvent) {
        timeTextArea.clear();
        timeResultTextArea.clear();
    }

}
