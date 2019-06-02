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

package eu.dariolucia.ccsds.inspector.view.util;

import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AosTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.ocf.pdu.Clcw;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;
import eu.dariolucia.ccsds.tmtc.util.AnnotatedObject;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;

import java.util.Objects;

public class DataStringDumper {

    public static String dumpData(byte[] data) {
        StringBuilder result = new StringBuilder();
        // Stupid algorithm
        int startOfLinePrintout = 0;
        for(int i = 0; i < data.length; ++i) {
            // If start of line, print address
            if( i % 16 == 0) {
                result.append(String.format("%02X|", i / 16));
                startOfLinePrintout = i;
            }
            // Print the byte and add a space
            result.append(String.format("%02X ", data[i]));
            // Every 4 bytes add an additional space
            if (i % 4 == 3) {
                result.append(' ');
            }
            if (i % 16 == 15) {
                // Add the ASCII printout from startOfLinePrintout to here
                for(int j = startOfLinePrintout; j <= i; ++j) {
                    if(Byte.toUnsignedInt(data[j]) < 32) {
                        result.append('.');
                    } else {
                        result.append((char) Byte.toUnsignedInt(data[j]));
                    }
                }
                // Add new line
                result.append('\n');
            } else if(i == data.length - 1) {
                // Fill with spaces
                int numberOfSpaces = 15 - (i % 16);
                // For each missing byte, add 3 spaces
                // In addition, add Math.ceil(numberOfSpaces / 4);
                numberOfSpaces = numberOfSpaces * 3 + (int) ((Math.floor(numberOfSpaces / 4.0)) + (numberOfSpaces % 4 == 0 ? 0 : 1));
                for(int j = 0; j < numberOfSpaces; j++) {
                    result.append(' ');
                }
                // Add the ASCII printout from startOfLinePrintout to here
                for(int j = startOfLinePrintout; j <= i; ++j) {
                    if(Byte.toUnsignedInt(data[j]) < 32) {
                        result.append('.');
                    } else {
                        result.append((char) Byte.toUnsignedInt(data[j]));
                    }
                }
            }
        }
        return result.toString();
    }

    public static String dumpTransferFrame(AbstractTransferFrame theValue) {
        if(theValue instanceof TmTransferFrame) {
            return dumpTransferFrame((TmTransferFrame) theValue);
        } else if(theValue instanceof AosTransferFrame) {
            return dumpTransferFrame((AosTransferFrame) theValue);
        } else if(theValue instanceof TcTransferFrame) {
            return dumpTransferFrame((TcTransferFrame) theValue);
        } else {
            return "Unknown transfer frame type";
        }
    }

    public static String dumpTransferFrame(TmTransferFrame t) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-38s: %d\n", "Spacecraft ID", t.getSpacecraftId()));
        sb.append(String.format("%-38s: %d\n", "Transfer Frame Version Number", t.getTransferFrameVersionNumber()));
        sb.append(String.format("%-38s: %d\n", "Virtual Channel ID", t.getVirtualChannelId()));
        sb.append(String.format("%-38s: %d\n", "Master Channel Frame Count", t.getMasterChannelFrameCount()));
        sb.append(String.format("%-38s: %d\n", "Virtual Channel Frame Count", t.getVirtualChannelFrameCount()));

        sb.append(String.format("%-38s: %b\n", "Sync. Flag Set", t.isSynchronisationFlag()));
        sb.append(String.format("%-38s: %b\n", "Packet Order Flag Set", t.isPacketOrderFlag()));
        sb.append(String.format("%-38s: %d\n", "Segment Length ID", t.getSegmentLengthIdentifier()));

        sb.append(String.format("%-38s: %b\n", "Sec. Header Present", t.isSecondaryHeaderPresent()));
        sb.append(String.format("%-38s: %b\n", "OCF Present", t.isOcfPresent()));
        sb.append(String.format("%-38s: %b\n", "FECF Present", t.isFecfPresent()));

        if(t.isIdleFrame()) {
            sb.append(String.format("%-38s: IDLE\n", "First Header Pointer"));
        } else if(t.isNoStartPacket()) {
            sb.append(String.format("%-38s: NO START OF PACKET IN FRAME\n", "First Header Pointer"));
        } else {
            sb.append(String.format("%-38s: %d\n", "First Header Pointer", t.getFirstHeaderPointer()));
        }

        if(t.isOcfPresent()) {
            sb.append("\nCLCW ---------------------------------------------------------------\n");
            // Assume CLCW
            Clcw clcw = new Clcw(t.getOcfCopy());
            sb.append(String.format("Version: %2d Status Field: %3d VCID:     %2d COP in Effect: %-10s\n", clcw.getVersionNumber(), clcw.getStatusField(), clcw.getVirtualChannelId(), clcw.getCopInEffect().name()));
            sb.append(String.format("No RF:   %2d No Bitlock:   %3d Lockout:  %2d Retransmit:   %2d\n", clcw.isNoRfAvailableFlag() ? 1 : 0, clcw.isNoBitlockFlag() ? 1 : 0, clcw.isLockoutFlag() ? 1 : 0, clcw.isRetransmitFlag() ? 1 : 0));
            sb.append(String.format("FARM-B:  %2d Report:       %3d\n", clcw.getFarmBCounter(), clcw.getReportValue()));
        }

        if(t.isFecfPresent()) {
            sb.append("\nFECF ---------------------------------------------------------------\n");
            sb.append(String.format("%-38s: %d\n", "Frame Error Control Field", Short.toUnsignedInt(t.getFecf())));
        }
        return sb.toString();
    }

    public static String dumpTransferFrame(AosTransferFrame t) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-38s: %d\n", "Spacecraft ID", t.getSpacecraftId()));
        sb.append(String.format("%-38s: %d\n", "Transfer Frame Version Number", t.getTransferFrameVersionNumber()));
        sb.append(String.format("%-38s: %d\n", "Virtual Channel ID", t.getVirtualChannelId()));
        sb.append(String.format("%-38s: %d\n", "Virtual Channel Frame Count", t.getVirtualChannelFrameCount()));
        sb.append(String.format("%-38s: %d\n", "Virtual Channel Frame Count Cycle", t.getVirtualChannelFrameCountCycle()));

        sb.append(String.format("%-38s: %b\n", "Replay Flag Set", t.isReplayFlag()));
        sb.append(String.format("%-38s: %b\n", "VC Frame Count Usage Flag Set", t.isVirtualChannelFrameCountUsageFlag()));
        sb.append(String.format("%-38s: %b\n", "Frame Header Error Control Present", t.isFrameHeaderErrorControlPresent()));

        sb.append(String.format("%-38s: %s\n", "Type", t.getUserDataType().name()));

        sb.append(String.format("%-38s: %b\n", "OCF Present", t.isOcfPresent()));
        sb.append(String.format("%-38s: %b\n", "FECF Present", t.isFecfPresent()));

        if(t.getUserDataType() == AosTransferFrame.UserDataType.M_PDU || t.getUserDataType() == AosTransferFrame.UserDataType.IDLE) {
            if (t.isIdleFrame()) {
                sb.append(String.format("%-38s: IDLE\n", "First Header Pointer"));
            } else if (t.isNoStartPacket()) {
                sb.append(String.format("%-38s: NO START OF PACKET IN FRAME\n", "First Header Pointer"));
            } else {
                sb.append(String.format("%-38s: %d\n", "First Header Pointer", t.getFirstHeaderPointer()));
            }
        }

        if(t.isOcfPresent()) {
            sb.append("\nCLCW ---------------------------------------------------------------\n");
            // Assume CLCW
            Clcw clcw = new Clcw(t.getOcfCopy());
            sb.append(String.format("Version: %2d Status Field: %3d VCID:     %2d COP in Effect: %-10s\n", clcw.getVersionNumber(), clcw.getStatusField(), clcw.getVirtualChannelId(), clcw.getCopInEffect().name()));
            sb.append(String.format("No RF:   %2d No Bitlock:   %3d Lockout:  %2d Retransmit:   %2d\n", clcw.isNoRfAvailableFlag() ? 1 : 0, clcw.isNoBitlockFlag() ? 1 : 0, clcw.isLockoutFlag() ? 1 : 0, clcw.isRetransmitFlag() ? 1 : 0));
            sb.append(String.format("FARM-B:  %2d Report:       %3d\n", clcw.getFarmBCounter(), clcw.getReportValue()));
        }

        if(t.isFecfPresent()) {
            sb.append("\nFECF ---------------------------------------------------------------\n");
            sb.append(String.format("%-38s: %d\n", "Frame Error Control Field", Short.toUnsignedInt(t.getFecf())));
        }
        return sb.toString();
    }

    public static String dumpTransferFrame(TcTransferFrame t) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-38s: %d\n", "Spacecraft ID", t.getSpacecraftId()));
        sb.append(String.format("%-38s: %d\n", "Transfer Frame Version Number", t.getTransferFrameVersionNumber()));
        sb.append(String.format("%-38s: %d\n", "Virtual Channel ID", t.getVirtualChannelId()));
        sb.append(String.format("%-38s: %d\n", "Virtual Channel Frame Count", t.getVirtualChannelFrameCount()));

        sb.append(String.format("%-38s: %b\n", "Bypass Flag Set", t.isBypassFlag()));
        sb.append(String.format("%-38s: %b\n", "Control Command Flag Set", t.isControlCommandFlag()));
        sb.append(String.format("%-38s: %b\n", "Segmented", t.isSegmented()));
        sb.append(String.format("%-38s: %s\n", "Sequence Flag", Objects.toString(t.getSequenceFlag(), "N/A")));

        sb.append(String.format("%-38s: %b\n", "FECF Present", t.isFecfPresent()));

        if(t.isSegmented()) {
            sb.append(String.format("%-38s: %d\n", "Map ID", t.getMapId()));
        }

        if(t.isSecurityUsed()) {
            sb.append(String.format("%-38s: 0x%s\n", "Security Header", StringUtil.toHexDump(t.getSecurityHeaderCopy())));
            sb.append(String.format("%-38s: 0x%s\n", "Security Trailer", StringUtil.toHexDump(t.getSecurityTrailerCopy())));
        }

        if(t.getFrameType() == TcTransferFrame.FrameType.BC) {
            sb.append(String.format("%-38s: %s\n", "Directive Type", t.getControlCommandType().name()));
            if(t.getControlCommandType() == TcTransferFrame.ControlCommandType.SET_VR) {
                sb.append(String.format("%-38s: %s\n", "Set V(R) Value", t.getSetVrValue()));
            }
        }

        if(t.isFecfPresent()) {
            sb.append("\nFECF ---------------------------------------------------------------\n");
            sb.append(String.format("%-38s: %d\n", "Frame Error Control Field", Short.toUnsignedInt(t.getFecf())));
        }
        return sb.toString();
    }

    public static String dumpAnnotations(AnnotatedObject theValue) {
        StringBuilder sb = new StringBuilder();
        for(Object key : theValue.getAnnotationKeys()) {
            Object value = theValue.getAnnotationValue(key);
            sb.append(String.format("%-38s:  %s\n", key.toString(), Objects.toString(value, "")));
        }
        return sb.toString();
    }

    public static String dumpPacket(SpacePacket t) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-38s: %d\n", "Application Process ID", t.getApid()));
        sb.append(String.format("%-38s: %d\n", "Packet Sequence Count", t.getPacketSequenceCount()));
        sb.append(String.format("%-38s: %d\n", "Packet Data Length", t.getPacketDataLength()));
        sb.append(String.format("%-38s: %s\n", "Sequence Flag", t.getSequenceFlag().name()));

        sb.append(String.format("%-38s: %b\n", "TM Packet Flag Set", t.isTelemetryPacket()));
        sb.append(String.format("%-38s: %b\n", "Secondary Header Flag Set", t.isSecondaryHeaderFlag()));
        sb.append(String.format("%-38s: %b\n", "Idle Packet", t.isIdle()));

        sb.append(String.format("%-38s: %s\n", "Quality Indicator", t.isQualityIndicator() ? "valid" : "invalid" ));
        return sb.toString();
    }
}
