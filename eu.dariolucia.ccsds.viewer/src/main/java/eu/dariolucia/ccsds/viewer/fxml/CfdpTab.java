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

import eu.dariolucia.ccsds.cfdp.protocol.decoder.CfdpPduDecoder;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.encdec.pus.TcPusHeader;
import eu.dariolucia.ccsds.encdec.pus.TmPusHeader;
import eu.dariolucia.ccsds.encdec.time.AbsoluteTimeDescriptor;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.time.Instant;
import java.util.ResourceBundle;

import static eu.dariolucia.ccsds.viewer.utils.UI.addLine;

public class CfdpTab implements Initializable {

    public static final String YES = "YES";
    public static final String NO = "NO";

    public static final String CUC_3_2 = "CUC (3,2)";
    public static final String CUC_3_3 = "CUC (3,3)";
    public static final String CUC_3_4 = "CUC (3,4)";
    public static final String CUC_4_1 = "CUC (4,1)";
    public static final String CUC_4_2 = "CUC (4,2)";
    public static final String CUC_4_3 = "CUC (4,3)";
    public static final String CUC_4_4 = "CUC (4,4)";
    public static final String CDS_S_0 = "CDS short - milli";
    public static final String CDS_S_1 = "CDS short - micro";
    public static final String CDS_S_2 = "CDS short - pico";
    public static final String CDS_L_0 = "CDS long - milli";
    public static final String CDS_L_1 = "CDS long - micro";
    public static final String CDS_L_2 = "CDS long - pico";

    @FXML
    private VBox spViewbox;
    @FXML
    private TextArea spTextArea;
    @FXML
    private TextArea spResultTextArea;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
    }

    public void onPduDecodeButtonClicked(ActionEvent actionEvent) {
        spResultTextArea.clear();
        String data = spTextArea.getText().toUpperCase();
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
            processPacket(bdata);
        } catch (Exception e) {
            error(e.getMessage());
        }
    }

    private void processPacket(byte[] bdata) {
        String message = "";

        // CFDP PDU
        try {
            StringBuilder sb = new StringBuilder("");

            CfdpPdu ttf = CfdpPduDecoder.decode(bdata);
            processPdu(ttf, sb);
            return;
        } catch (Exception e) {
            // Not a space packet
            message = e.getMessage();
        }

        error("Provided dump is not a CFDP PDU: " + message);
    }

    private void processPdu(CfdpPdu ttf, StringBuilder sb) {
        String info = ttf.toString();
        info = info.replace("{", " ").replace("}", ",").trim();
        String[] tokens = info.split(",", -1);
        for(String s : tokens) {
            if(s.trim().isEmpty()) {
                continue;
            }
            String[] spl = s.split("=", -1);
            addLine(sb, spl[0], spl[1]);
        }
        spResultTextArea.setText(sb.toString());
    }

    private void error(String error) {
        spResultTextArea.setText(spResultTextArea.getText() + "\nERROR: " + error);
    }

    public void onPduClearButtonClicked(ActionEvent actionEvent) {
        spTextArea.clear();
        spResultTextArea.clear();
    }

}
