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

import eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm;
import eu.dariolucia.ccsds.tmtc.coding.decoder.ReedSolomonDecoder;
import eu.dariolucia.ccsds.tmtc.coding.decoder.TmRandomizerDecoder;
import eu.dariolucia.ccsds.tmtc.coding.encoder.TmAsmEncoder;
import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.AbstractReceiverVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.AosReceiverVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.IVirtualChannelReceiverOutput;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.TmReceiverVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AosTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.Arrays;
import java.util.ResourceBundle;

import static eu.dariolucia.ccsds.viewer.utils.UI.addLine;

public class TmAosTab implements Initializable {

    public static final String YES = "YES";
    public static final String NO = "NO";

    public VBox tmAosViewbox;

    public TextArea tmAosTextArea;
    public ChoiceBox<String> tmAosRandomizedChoicebox;
    public ChoiceBox<String> tmAosOcfChoicebox;
    public ChoiceBox<String> tmAosFecfChoicebox;
    public ChoiceBox<String> tmAosFhcfChoicebox;
    public TextField tmAosInsertZoneTextField;
    public TextField tmAosSecurityHeaderTextField;
    public TextField tmAosSecurityTrailerTextField;
    public TextArea tmAosResultTextArea;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        tmAosRandomizedChoicebox.getItems().addAll(YES, NO);
        tmAosRandomizedChoicebox.getSelectionModel().select(1);

        tmAosOcfChoicebox.getItems().addAll(YES, NO);
        tmAosOcfChoicebox.getSelectionModel().select(0);

        tmAosFecfChoicebox.getItems().addAll(YES, NO);
        tmAosFecfChoicebox.getSelectionModel().select(1);

        tmAosFhcfChoicebox.getItems().addAll(YES, NO);
        tmAosFhcfChoicebox.getSelectionModel().select(1);
    }

    public void onTmAosDecodeButtonClicked(ActionEvent actionEvent) {
        tmAosResultTextArea.clear();
        String data = tmAosTextArea.getText().toUpperCase();
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
            if (data.startsWith(StringUtil.toHexDump(TmAsmEncoder.DEFAULT_ATTACHED_SYNC_MARKER).toUpperCase())) {
                // It is a CADU
                processCadu(bdata);
            } else {
                // Assume frame
                processFrame(bdata, false, false);
            }
        } catch (Exception e) {
            error(e.getMessage());
        }
    }

    private void processFrame(byte[] bdata, boolean reedSolomonVerified, boolean reedSolomonValid) {
        // Check if it is randomised
        if(tmAosRandomizedChoicebox.getSelectionModel().getSelectedItem().equals(YES)) {
            bdata = new TmRandomizerDecoder().apply(bdata);
        }
        // Now the frame is not randomized, and ready to be processed
        String message = "";
        // Check if it is an AOS or a TM frame
        if((bdata[0] & 0xC0) == 0) {
            // TM
            try {
                TmTransferFrame ttf = new TmTransferFrame(bdata,
                        tmAosFecfChoicebox.getSelectionModel().getSelectedItem().equals(YES),
                        Integer.parseInt(tmAosSecurityHeaderTextField.getText()),
                        Integer.parseInt(tmAosSecurityTrailerTextField.getText()));
                processTmFrame(ttf, reedSolomonVerified, reedSolomonValid);
                return;
            } catch (Exception e) {
                // Not a TM frame
                message = e.getMessage();
            }
        } else {
            // AOS
            try {
                AosTransferFrame aosf = new AosTransferFrame(bdata,
                        tmAosFhcfChoicebox.getSelectionModel().getSelectedItem().equals(YES),
                        Integer.parseInt(tmAosInsertZoneTextField.getText()),
                        AosTransferFrame.UserDataType.M_PDU,
                        tmAosOcfChoicebox.getSelectionModel().getSelectedItem().equals(YES),
                        tmAosFecfChoicebox.getSelectionModel().getSelectedItem().equals(YES),
                        Integer.parseInt(tmAosSecurityHeaderTextField.getText()),
                        Integer.parseInt(tmAosSecurityTrailerTextField.getText()));
                processAosFrame(aosf, reedSolomonVerified, reedSolomonValid);
                return;
            } catch (Exception e) {
                // Not a AOS frame
                message = e.getMessage();
            }
        }
        error("Provided dump is not a AOS/TM Frame: " + message);
    }

    private void processAosFrame(AosTransferFrame aosf, boolean reedSolomonVerified, boolean reedSolomonValid) {
        StringBuilder sb = new StringBuilder("");
        if(reedSolomonVerified) {
            addLine(sb, "Reed Solomon Check Result", reedSolomonValid ? "OK" : "Errors");
            addLine(sb, "Extracted AOS frame", StringUtil.toHexDump(aosf.getFrame()));
            addLine(sb,"-----------------------------", "");
        }
        addLine(sb, "Transfer Frame Version Number", aosf.getTransferFrameVersionNumber());
        addLine(sb, "Spacecraft ID", aosf.getSpacecraftId());
        addLine(sb, "Virtual Channel ID", aosf.getVirtualChannelId());
        addLine(sb, "Virtual Channel Frame Count", aosf.getVirtualChannelFrameCount());
        addLine(sb, "Virtual Channel Frame Count Usage Flag", aosf.isVirtualChannelFrameCountUsageFlag());
        addLine(sb, "Virtual Channel Frame Count Cycle", Byte.toUnsignedInt(aosf.getVirtualChannelFrameCountCycle()));
        addLine(sb, "Replay Flag", aosf.isReplayFlag());
        if(aosf.isFrameHeaderErrorControlPresent()) {
            addLine(sb, "FHEC Value", String.format("%04X", aosf.getFhec()));
            addLine(sb, "Valid Header", aosf.isValidHeader());
        }
        if(aosf.getInsertZoneLength() > 0) {
            addLine(sb, "Insert Zone", StringUtil.toHexDump(aosf.getInsertZoneCopy()));
        }
        if(aosf.isOcfPresent()) {
            addLine(sb, "OCF", StringUtil.toHexDump(aosf.getOcfCopy()));
        }
        if(aosf.isFecfPresent()) {
            addLine(sb, "FECF", String.format("%04X", aosf.getFecf()));
            addLine(sb, "Frame Validity", aosf.isValid() ? "OK" : "Error");
        }
        if(aosf.getSecurityHeaderLength() > 0) {
            addLine(sb, "Security Header", StringUtil.toHexDump(aosf.getSecurityHeaderCopy()));
        }
        if(aosf.getSecurityTrailerLength() > 0) {
            addLine(sb, "Security Trailer", StringUtil.toHexDump(aosf.getSecurityTrailerCopy()));
        }
        addLine(sb, "Idle Frame", aosf.isIdleFrame());
        addLine(sb, "No start of packet", aosf.isNoStartPacket());
        addLine(sb, "First Header Pointer", aosf.getFirstHeaderPointer());
        addLine(sb, "Data Field", StringUtil.toHexDump(aosf.getDataFieldCopy()));
        if(!aosf.isIdleFrame() && !aosf.isNoStartPacket()) {
            sb.append("\n");
            extractPacketsFrom(sb, aosf);
        }

        tmAosResultTextArea.setText(sb.toString());
    }

    private void extractPacketsFrom(StringBuilder sb, AosTransferFrame aosf) {
        AosReceiverVirtualChannel vc = new AosReceiverVirtualChannel(aosf.getVirtualChannelId(), VirtualChannelAccessMode.PACKET, false);
        vc.register(new IVirtualChannelReceiverOutput() {
            @Override
            public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
                addLine(sb, "Packet", StringUtil.toHexDump(packet));
            }
        });
        vc.accept(aosf);
    }

    private void extractPacketsFrom(StringBuilder sb, TmTransferFrame ttf) {
        TmReceiverVirtualChannel vc = new TmReceiverVirtualChannel(ttf.getVirtualChannelId(), VirtualChannelAccessMode.PACKET, false);
        vc.register(new IVirtualChannelReceiverOutput() {
            @Override
            public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
                addLine(sb, "Packet", StringUtil.toHexDump(packet));
            }
        });
        vc.accept(ttf);
    }

    private void processTmFrame(TmTransferFrame ttf, boolean reedSolomonVerified, boolean reedSolomonValid) {
        StringBuilder sb = new StringBuilder("");
        if(reedSolomonVerified) {
            addLine(sb, "Reed Solomon Check Result", reedSolomonValid ? "OK" : "Errors");
            addLine(sb, "Extracted TM frame", StringUtil.toHexDump(ttf.getFrame()));
            addLine(sb,"-----------------------------", "");
        }
        addLine(sb, "Transfer Frame Version Number", ttf.getTransferFrameVersionNumber());
        addLine(sb, "Spacecraft ID", ttf.getSpacecraftId());
        addLine(sb, "Virtual Channel ID", ttf.getVirtualChannelId());
        addLine(sb, "Master Channel Frame Count", ttf.getMasterChannelFrameCount());
        addLine(sb, "Virtual Channel Frame Count", ttf.getVirtualChannelFrameCount());
        addLine(sb, "Packet Order Flag", ttf.isPacketOrderFlag());
        addLine(sb, "Secondary Header Present", ttf.isSecondaryHeaderPresent());
        addLine(sb, "Synchronisation Flag", ttf.isSynchronisationFlag());
        addLine(sb, "Segment Length Identifier", ttf.getSegmentLengthIdentifier());

        if(ttf.isOcfPresent()) {
            addLine(sb, "OCF", StringUtil.toHexDump(ttf.getOcfCopy()));
        }
        if(ttf.isFecfPresent()) {
            addLine(sb, "FECF", String.format("%04X", ttf.getFecf()));
            addLine(sb, "Frame Validity", ttf.isValid() ? "OK" : "Error");
        }
        if(ttf.getSecurityHeaderLength() > 0) {
            addLine(sb, "Security Header", StringUtil.toHexDump(ttf.getSecurityHeaderCopy()));
        }
        if(ttf.getSecurityTrailerLength() > 0) {
            addLine(sb, "Security Trailer", StringUtil.toHexDump(ttf.getSecurityTrailerCopy()));
        }
        addLine(sb, "Idle Frame", ttf.isIdleFrame());
        addLine(sb, "No start of packet", ttf.isNoStartPacket());
        addLine(sb, "First Header Pointer", ttf.getFirstHeaderPointer());
        addLine(sb, "Data Field", StringUtil.toHexDump(ttf.getDataFieldCopy()));
        if(!ttf.isIdleFrame() && !ttf.isNoStartPacket()) {
            sb.append("\n");
            extractPacketsFrom(sb, ttf);
        }

        tmAosResultTextArea.setText(sb.toString());
    }

    private void processCadu(byte[] bdata) {
        if(Arrays.equals(bdata, 0, 4, TmAsmEncoder.DEFAULT_ATTACHED_SYNC_MARKER, 0, 4)) {
            // Try to guess the frame size
            int idepth = (bdata.length - 4) / 255;
            // Extract n blocks of 223 bytes (assuming RS 223/255 is used)
            byte[] framePlusRs = Arrays.copyOfRange(bdata, 4, 4 + (223 * idepth));
            byte[] toCheck = Arrays.copyOfRange(bdata, 4, bdata.length);
            // Check if it is randomised
            if(tmAosRandomizedChoicebox.getSelectionModel().getSelectedItem().equals(YES)) {
                toCheck = new TmRandomizerDecoder().apply(toCheck);
            }
            // Check status
            ReedSolomonDecoder decoder = new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223, idepth, true);
            byte[] resultingFrame = decoder.apply(toCheck);

            // Now process the frame
            processFrame(framePlusRs, true, resultingFrame != null);
        } else {
            error("Data does not start with expected sync marker " + StringUtil.toHexDump(TmAsmEncoder.DEFAULT_ATTACHED_SYNC_MARKER).toUpperCase());
        }
    }

    private void error(String error) {
        tmAosResultTextArea.setText(tmAosResultTextArea.getText() + "\nERROR: " + error);
    }

    public void onTmAosClearButtonClicked(ActionEvent actionEvent) {
        tmAosTextArea.clear();
        tmAosResultTextArea.clear();
    }

}
