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

package eu.dariolucia.ccsds.cfdp.entity;

import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import eu.dariolucia.ccsds.cfdp.filestore.IVirtualFilestore;
import eu.dariolucia.ccsds.cfdp.filestore.impl.FilesystemBasedFilestore;
import eu.dariolucia.ccsds.cfdp.mib.Mib;
import eu.dariolucia.ccsds.cfdp.ut.UtLayerException;
import eu.dariolucia.ccsds.cfdp.ut.impl.TcpLayer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

public class TestUtils {

    public static ICfdpEntity createTcpEntity(String mibFile, int port) throws IOException, UtLayerException {
        InputStream in = TestUtils.class.getClassLoader().getResourceAsStream(mibFile);
        Mib conf1File = Mib.load(in);
        File fs1Folder = Files.createTempDirectory("cfdp").toFile();
        FilesystemBasedFilestore fs1 = new FilesystemBasedFilestore(fs1Folder);
        TcpLayer tcpLayer = new TcpLayer(conf1File, port);
        tcpLayer.activate();
        return ICfdpEntity.create(conf1File, fs1, tcpLayer);
    }

    public static String createRandomFileIn(IVirtualFilestore filestore, String file, int numKB) throws FilestoreException, IOException {
        filestore.createFile(file);
        OutputStream os = filestore.writeFile(file, false);
        byte[] b = new byte[1024];
        for(int i = 0; i < 1024; ++i) {
            b[i] = (byte) (((int) (Math.random() * 256)) % 256);
        }
        for(int i = 0; i < numKB; ++i) {
            os.write(b);
        }
        os.close();
        return file;
    }

    public static boolean compareFiles(IVirtualFilestore v1, String p1, IVirtualFilestore v2, String p2) throws FilestoreException, IOException {
        long s1 = v1.fileSize(p1);
        long s2 = v2.fileSize(p2);
        if(s1 != s2) {
            return false;
        } else {
            InputStream is1 = v1.readFile(p1);
            InputStream is2 = v2.readFile(p2);
            for(long i = 0; i < s1; ++i) {
                if(is1.read() != is2.read()) { // Slowwwwwwwwwwwwwwwwwwww ... but I do not care at the moment
                    return false;
                }
            }
            return true;
        }
    }
}
