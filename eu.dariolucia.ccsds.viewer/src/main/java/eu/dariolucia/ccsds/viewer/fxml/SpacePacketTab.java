package eu.dariolucia.ccsds.viewer.fxml;

import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ResourceBundle;

import static eu.dariolucia.ccsds.viewer.utils.UI.addLine;

public class SpacePacketTab implements Initializable {

    public VBox spViewbox;

    public TextArea spTextArea;
    public TextArea spResultTextArea;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
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

            SpacePacket ttf = new SpacePacket(bdata,true);
            processSpacePacket(ttf, sb);
            return;
        } catch (Exception e) {
            // Not a space packet
            message = e.getMessage();
        }

        error("Provided dump is not a space packet: " + message);
    }

    private void processSpacePacket(SpacePacket ttf, StringBuilder sb) {
        addLine(sb, "Type", ttf.isTelemetryPacket() ? "TM" : "TC");
        addLine(sb, "APID", ttf.getApid());
        addLine(sb, "SSC", ttf.getPacketSequenceCount());
        addLine(sb, "Sequence Flag", ttf.getSequenceFlag());
        addLine(sb, "Secondary Header Flag", ttf.isSecondaryHeaderFlag());
        addLine(sb, "Idle Packet", ttf.isIdle());
        addLine(sb, "Length", ttf.getLength());
        addLine(sb, "Data Field", StringUtil.toHexDump(ttf.getDataFieldCopy()));

        // TODO: add PUS TM/TC secondary header parsing
        spResultTextArea.setText(sb.toString());
    }

    private void error(String error) {
        spResultTextArea.setText(spResultTextArea.getText() + "\nERROR: " + error);
    }

    public void onSpClearButtonClicked(ActionEvent actionEvent) {
        spResultTextArea.clear();
        spResultTextArea.clear();
    }

}
