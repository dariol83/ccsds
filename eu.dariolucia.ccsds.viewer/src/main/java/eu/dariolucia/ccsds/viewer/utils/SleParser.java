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

package eu.dariolucia.ccsds.viewer.utils;

import com.beanit.jasn1.ber.types.BerType;
import eu.dariolucia.ccsds.viewer.fxml.MainController;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

public class SleParser {

    private final String type;
    private final String sender;
    private final Set<String> versions;

    private final Class<? extends BerType> decodingClass;

    public SleParser(String type, String sender, String[] versions, Class<? extends BerType> decodingClass) {
        this.type = type;
        this.sender = sender;
        this.versions = new TreeSet<>(Arrays.asList(versions));
        this.decodingClass = decodingClass;
    }

    public boolean select(String type, String sender, String version) {
        if(type.equals(MainController.I_DO_NOT_KNOW) || type.equals(this.type)) {
            if(sender.equals(MainController.I_DO_NOT_KNOW) || sender.equals(this.sender)) {
                return version.equals(MainController.I_DO_NOT_KNOW) || versions.contains(version);
            }
        }
        return false;
    }

    public SlePdu parse(byte[] data) throws Exception {
        BerType type = decodingClass.getDeclaredConstructor().newInstance();
        type.decode(new ByteArrayInputStream(data));

        return new SlePdu(type, data, decodingClass.getSimpleName());
    }
}
