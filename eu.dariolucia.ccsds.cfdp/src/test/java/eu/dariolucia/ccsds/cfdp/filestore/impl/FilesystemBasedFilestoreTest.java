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
    }
}