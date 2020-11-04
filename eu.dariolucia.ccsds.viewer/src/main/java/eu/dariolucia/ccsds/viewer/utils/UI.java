package eu.dariolucia.ccsds.viewer.utils;

import java.util.Objects;

public class UI {

    public static void addLine(StringBuilder sb, String key, Object value) {
        String objValue = Objects.toString(value, "");
        sb.append(String.format("%40s", key)).append("    ").append(objValue).append("\n");
    }
}
