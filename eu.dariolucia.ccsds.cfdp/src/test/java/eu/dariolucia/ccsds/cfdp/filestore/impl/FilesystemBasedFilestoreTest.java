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

package eu.dariolucia.ccsds.cfdp.filestore.impl;

import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FilesystemBasedFilestoreTest {

    @Test
    public void testFileStoreOperations() throws IOException, FilestoreException {
        Path tempPath = Files.createTempDirectory("CFDP_VFS_");
        File root = tempPath.toFile();

        FilesystemBasedFilestore fs = new FilesystemBasedFilestore(root.getAbsolutePath());

        assertDoesNotThrow(() -> fs.createFile("whatever"));
        assertDoesNotThrow(() -> fs.createFile("whatever2"));
        assertDoesNotThrow(() -> fs.createDirectory("dir1"));
        assertDoesNotThrow(() -> fs.createFile("dir1/whatever2"));
        assertDoesNotThrow(() -> fs.createDirectory("dir2/"));
        assertTrue(fs.fileExists("whatever"));
        assertTrue(fs.directoryExists("dir1"));
        assertTrue(fs.directoryExists("dir2"));
        assertTrue(fs.fileExists("dir1/whatever2"));
        assertFalse(fs.fileExists("dir1/whatever3"));
        assertThrows(FilestoreException.class, () -> fs.deleteFile("nofile"));
        assertThrows(FilestoreException.class, () -> fs.deleteDirectory("nofolder"));
        assertDoesNotThrow(() -> fs.deleteFile("whatever"));
        assertFalse(fs.fileExists("whatever"));
        List<String> dirList1 = fs.listDirectory("dir1", false);
        assertEquals(Collections.singletonList("dir1/whatever2"), dirList1);
        Set<String> dirList2 = new TreeSet<>(fs.listDirectory("", true));
        assertEquals(new TreeSet<>(Arrays.asList("dir1/", "dir2/", "whatever2", "dir1/whatever2")), dirList2);

        byte[] data = new byte[] {1,2,3,4};
        assertDoesNotThrow(() -> fs.appendContentsToFile("whatever2", data));
        assertArrayEquals(data, fs.getFile("whatever2"));
        assertDoesNotThrow(() -> fs.appendContentsToFile("whatever2", data));
        assertArrayEquals(new byte[] {1,2,3,4,1,2,3,4}, fs.getFile("whatever2"));
        assertDoesNotThrow(() -> fs.replaceFileContents("whatever2", data));
        assertArrayEquals(data, fs.getFile("whatever2"));
        assertDoesNotThrow(() -> fs.renameFile("whatever2", "dir2/w"));
        assertTrue(fs.fileExists("dir2/w"));
        assertFalse(fs.fileExists("whatever2"));
        assertArrayEquals(data, fs.getFile("dir2/w"));
        assertDoesNotThrow(() -> fs.deleteFile("dir2/w"));
        assertDoesNotThrow(() -> fs.deleteDirectory("dir2"));

        assertNotNull(fs.toString());
    }

    @Test
    public void testExceptions() throws IOException {
        assertThrows(NullPointerException.class, () -> new FilesystemBasedFilestore((File) null));
        assertThrows(IllegalArgumentException.class, () -> new FilesystemBasedFilestore(new File("IdoubtThisWillEverExist")));

        Path tempPath = Files.createTempDirectory("CFDP_VFS_");
        File root = tempPath.toFile();
        File testFile = new File(root.getAbsolutePath() + "/testfile.bin");
        testFile.createNewFile();
        assertThrows(IllegalArgumentException.class, () -> new FilesystemBasedFilestore(testFile));
        testFile.delete();

        FilesystemBasedFilestore fs = new FilesystemBasedFilestore(root.getAbsolutePath());

        assertDoesNotThrow(() -> fs.createDirectory("testDir"));
        assertThrows(FilestoreException.class, () -> {
            fs.createFile("testDir");
        });
        assertThrows(FilestoreException.class, () -> {
            fs.deleteFile("testDir");
        });
        assertThrows(FilestoreException.class, () -> {
            fs.renameFile("testDir2", "whatever");
        });
        assertThrows(FilestoreException.class, () -> {
            fs.appendContentsToFile("testDir2", "whatever".getBytes());
        });
        assertThrows(FilestoreException.class, () -> {
            fs.replaceFileContents("testDir2", "whatever".getBytes());
        });
        assertThrows(FilestoreException.class, () -> {
            fs.appendFileToFile("testDir2", "whatever");
        });
        testFile.createNewFile();
        assertThrows(FilestoreException.class, () -> {
            fs.appendFileToFile("testfile.bin", "whatever");
        });
        assertThrows(FilestoreException.class, () -> {
            fs.getFile("whatever");
        });
        assertThrows(FilestoreException.class, () -> {
            fs.deleteDirectory("whatever");
        });
        assertThrows(FilestoreException.class, () -> {
            fs.fileSize("whatever");
        });
        assertThrows(FilestoreException.class, () -> {
            fs.readFile("whatever");
        });
        assertThrows(FilestoreException.class, () -> {
            fs.writeFile("whatever", false);
        });
    }
}