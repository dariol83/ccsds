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

package eu.dariolucia.ccsds.inspector.connectors.testing;

import eu.dariolucia.ccsds.inspector.api.*;

public class TmTestConnectorFactory implements IConnectorFactory {

    public static final String FRAME_TYPE_ID = "type";
    public static final String CLCW_PRESENT_ID = "clcw";
    public static final String FECF_PRESENT_ID = "fecf";
    public static final String LENGTH_ID = "length";
    public static final String SC_ID = "scid";
    public static final String VCIDS_ID = "vcids";
    public static final String BITRATE_ID = "bitrate";

    @Override
    public IConnector createConnector(IConnectorObserver observer, ConnectorConfiguration configuration) {
        return new TmTestConnector(getName(), getDescription(), getVersion(), configuration, observer);
    }

    @Override
    public String getName() {
        return "Test TM Connector";
    }

    @Override
    public String getDescription() {
        return "This connector generates random TM/AOS frames (with random space packets) on specified virtual channels.";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public ConnectorConfigurationDescriptor getConfigurationDescriptor() {
        ConnectorConfigurationDescriptor ccd = new ConnectorConfigurationDescriptor();
        ccd.add(
                ConnectorPropertyDescriptor.enumDescriptor(FRAME_TYPE_ID, "Transfer Frame Type", "The transfer frame type to be generated", TmFrameSelection.class, TmFrameSelection.TM),
                ConnectorPropertyDescriptor.booleanDescriptor(CLCW_PRESENT_ID, "Presence of the CLCW", "If selected, the CLCW is generated and attached to the transfer frame", true),
                ConnectorPropertyDescriptor.booleanDescriptor(FECF_PRESENT_ID, "Presence of the FECF", "If selected, the FECF is generated and attached to the transfer frame", false),
                ConnectorPropertyDescriptor.integerDescriptor(LENGTH_ID, "Transfer Frame Size", "Total size of the transfer frame to be generated", 1115),
                ConnectorPropertyDescriptor.integerDescriptor(BITRATE_ID, "Bitrate (bps)", "Number of bits per second that must be generated (approx) by the connector", 8192),
                ConnectorPropertyDescriptor.integerDescriptor(SC_ID, "Spacecraft ID", "ID of the spacecraft that must be generated", 43),
                ConnectorPropertyDescriptor.descriptor(VCIDS_ID, "VC IDs", "Virtual Channels to be generated: comma-separated list of integers. If not set, all virtual channels will be generated" +
                        " with a uniform distribution", new int[0], false, int[].class, null, null, this::validateVcidString, this::vcidsToString, this::stringToVcdis)
        );
        return ccd;
    }

    private int[] stringToVcdis(String s) {
        if (s.isEmpty()) {
            return new int[0];
        }
        String[] split = s.split(",", -1);
        int[] toReturn = new int[split.length];
        int idx = 0;
        for (String ss : split) {
            try {
                int i = Integer.parseInt(ss);
                toReturn[idx++] = i;
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }
        return toReturn;
    }

    private String vcidsToString(int[] t) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length; ++i) {
            sb.append(i);
            if (i != t.length - 1) {
                sb.append(',');
            }
        }
        return sb.toString();
    }

    private String validateVcidString(String s) {
        if (s.isEmpty()) {
            return null;
        }
        String[] split = s.split(",", -1);
        for (String ss : split) {
            try {
                int i = Integer.parseInt(ss);
                if (i < 0 || i > 63) {
                    return "Virtual channel " + i + " out of range";
                }
            } catch (Exception e) {
                return "Virtual channel " + ss + " is not a number";
            }
        }
        return null;
    }
}

