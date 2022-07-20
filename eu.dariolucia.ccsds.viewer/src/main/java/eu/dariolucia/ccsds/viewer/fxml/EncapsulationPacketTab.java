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
import eu.dariolucia.ccsds.tmtc.transport.pdu.EncapsulationPacket;
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

public class EncapsulationPacketTab implements Initializable {


    public VBox spViewbox;

    public TextArea spTextArea;
    public TextArea spResultTextArea;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        //
    }

    public void onSpDecodeButtonClicked(ActionEvent actionEvent) {
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

        // Space Packet
        try {
            StringBuilder sb = new StringBuilder("");

            EncapsulationPacket ttf = new EncapsulationPacket(bdata,true);
            processSpacePacket(ttf, sb);
            return;
        } catch (Exception e) {
            // Not a space packet
            message = e.getMessage();
        }

        error("Provided dump is not an encapsulation packet: " + message);
    }

    private void processSpacePacket(EncapsulationPacket ttf, StringBuilder sb) {
        addLine(sb, "Primary Header Length", ttf.getPrimaryHeaderLength());
        addLine(sb, "Protocol ID", ttf.getEncapsulationProtocolId());
        if(ttf.isEncapsulationProtocolIdExtensionPresent()) {
            addLine(sb, "Protocol ID Ext.", ttf.getEncapsulationProtocolIdExtension());
        }
        if(ttf.isUserDefinedFieldPresent()) {
            addLine(sb, "User Defined Field", "0x" + StringUtil.toHexDump(new byte[] { ttf.getUserDefinedField() }));
        }
        if(ttf.isCcsdsDefinedFieldPresent()) {
            addLine(sb, "CCSDS Defined Field", "0x" + StringUtil.toHexDump(ttf.getCcsdsDefinedField()));
        }
        addLine(sb, "Encapsulated Data Field Length", ttf.getEncapsulatedDataFieldLength());
        addLine(sb, "Idle Packet", ttf.isIdle());
        addLine(sb, "Length", ttf.getLength());
        addLine(sb, "Data Field", StringUtil.toHexDump(ttf.getDataFieldCopy()));

        spResultTextArea.setText(sb.toString());
    }

    private void error(String error) {
        spResultTextArea.setText(spResultTextArea.getText() + "\nERROR: " + error);
    }

    public void onSpClearButtonClicked(ActionEvent actionEvent) {
        spTextArea.clear();
        spResultTextArea.clear();
    }

}
