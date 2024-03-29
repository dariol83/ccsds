/*
 *   Copyright (c) 2019 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.encdec.definition;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

/**
 * Integer adapter that can convert a string (hex, octal, binary, decimal) to an integer.
 */
public class IntAdapter extends XmlAdapter<String, Integer> {

    // Java to XML
    @Override
    public String marshal(Integer integer) {
        return String.valueOf(integer);
    }

    // XML to Java
    @Override
    public Integer unmarshal(String s) {
        if(s.startsWith("0x")) {
            return Integer.parseInt(s.substring(2), 16);
        } else if(s.startsWith("0b")) {
            return Integer.parseInt(s.substring(2), 2);
        } else if(s.startsWith("0")) {
            return Integer.parseInt(s, 8);
        } else {
            return Integer.parseInt(s);
        }
    }
}
