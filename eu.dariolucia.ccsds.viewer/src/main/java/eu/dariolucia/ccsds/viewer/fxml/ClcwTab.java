package eu.dariolucia.ccsds.viewer.fxml;

import eu.dariolucia.ccsds.tmtc.ocf.pdu.Clcw;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ResourceBundle;

import static eu.dariolucia.ccsds.viewer.utils.UI.addLine;

public class ClcwTab implements Initializable {

    public static final String YES = "YES";
    public static final String NO = "NO";
    private static final byte[] DEFAULT_CLTU_PREFIX = new byte[]{(byte) 0xEB, (byte) 0x90};

    public VBox clcwViewbox;

    public TextArea clcwTextArea;
    public TextArea clcwResultTextArea;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
    }

    public void onClcwDecodeButtonClicked(ActionEvent actionEvent) {
        clcwResultTextArea.clear();
        String data = clcwTextArea.getText().toUpperCase();
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
            processClcw(bdata);
        } catch (Exception e) {
            error(e.getMessage());
        }
    }

    private void processClcw(byte[] bdata) {
        // Now the frame is not randomized, and ready to be processed
        String message = "";

        //
        try {
            Clcw clcw = new Clcw(bdata);
            StringBuilder sb = new StringBuilder("");
            addLine(sb, "Version Number", clcw.getVersionNumber());
            addLine(sb, "Virtual Channel", clcw.getVirtualChannelId());
            addLine(sb, "COP in Effect", clcw.getCopInEffect());
            addLine(sb, "Status Field", clcw.getStatusField());
            addLine(sb, "Reserved Spare", clcw.getReservedSpare());
            addLine(sb, "Lockout Flag", clcw.isLockoutFlag());
            addLine(sb, "No Bitlock Flag", clcw.isNoBitlockFlag());
            addLine(sb, "No RF Available Flag", clcw.isNoRfAvailableFlag());
            addLine(sb, "Retransmit Flag", clcw.isRetransmitFlag());
            addLine(sb, "Wait Flag", clcw.isWaitFlag());
            addLine(sb, "Report Value", clcw.getReportValue());
            clcwResultTextArea.setText(sb.toString());
            return;
        } catch (Exception e) {
            // Not a TC frame
            message = e.getMessage();
        }

        error("Provided dump is not a CLCW: " + message);
    }

    private void error(String error) {
        clcwResultTextArea.setText(clcwResultTextArea.getText() + "\nERROR: " + error);
    }

    public void onClcwClearButtonClicked(ActionEvent actionEvent) {
        clcwTextArea.clear();
        clcwResultTextArea.clear();
    }

}
