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

package eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs;

import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import eu.dariolucia.ccsds.cfdp.filestore.IVirtualFilestore;
import eu.dariolucia.ccsds.cfdp.filestore.impl.FilesystemBasedFilestore;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FilestoreRequestTLVTest {

    @Test
    public void testFilestoreNegativeRequests() throws IOException, FilestoreException {
        File fs1Folder = Files.createTempDirectory("cfdp").toFile();
        FilesystemBasedFilestore fs1 = new FilesystemBasedFilestore(fs1Folder);

        fs1.createFile("test1");
        fs1.createFile("test11");

        fs1.createDirectory("dir1");
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.CREATE, "test1", null);
            assertEquals(FilestoreRequestTLV.TLV_TYPE, req.getType());
            assertEquals(FilestoreResponseTLV.StatusCode.CREATE_NOT_ALLOWED, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.APPEND, "test1", "test2");
            assertEquals(FilestoreResponseTLV.StatusCode.FILE_2_DOES_NOT_EXIST, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.APPEND, "test2", "test1");
            assertEquals(FilestoreResponseTLV.StatusCode.FILE_1_DOES_NOT_EXIST, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.CREATE_DIRECTORY, "test1", null);
            assertEquals(FilestoreResponseTLV.StatusCode.DIRECTORY_CANNOT_BE_CREATED, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.CREATE_DIRECTORY, "dir1", null);
            assertEquals(FilestoreResponseTLV.StatusCode.DIRECTORY_CANNOT_BE_CREATED, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.RENAME, "dir2", "dir4");
            assertEquals(FilestoreResponseTLV.StatusCode.OLD_FILE_DOES_NOT_EXIST, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.RENAME, "test11", "test1");
            assertEquals(FilestoreResponseTLV.StatusCode.NEW_FILE_ALREADY_EXISTS, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.RENAME, "test1", "dir1");
            assertEquals(FilestoreResponseTLV.StatusCode.RENAME_NOT_ALLOWED, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.REMOVE_DIRECTORY, "dir2", null);
            assertEquals(FilestoreResponseTLV.StatusCode.DIRECTORY_DOES_NOT_EXIST, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.REPLACE, "test1", "test2");
            assertEquals(FilestoreResponseTLV.StatusCode.FILE_2_NOT_EXIST, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.REPLACE, "test2", "test1");
            assertEquals(FilestoreResponseTLV.StatusCode.FILE_1_NOT_EXIST, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.DENY_FILE, "test111", null);
            assertEquals(FilestoreResponseTLV.StatusCode.SUCCESSFUL, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.DENY_DIRECTORY, "test2", null);
            assertEquals(FilestoreResponseTLV.StatusCode.SUCCESSFUL, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.DENY_DIRECTORY, "dir1", null);
            assertEquals(FilestoreResponseTLV.StatusCode.SUCCESSFUL, req.execute(fs1).getStatusCode());
        }
    }

    @Test
    public void testFilestoreExceptionRequests() {
        IVirtualFilestore fs1 = new IVirtualFilestore() {
            @Override
            public void createFile(String fullPath) throws FilestoreException {
                throw new FilestoreException("Operation failed due to test");
            }

            @Override
            public void deleteFile(String fullPath) throws FilestoreException {
                throw new FilestoreException("Operation failed due to test");
            }

            @Override
            public void renameFile(String fullPath, String newFullPath) throws FilestoreException {
                throw new FilestoreException("Operation failed due to test");
            }

            @Override
            public void appendContentsToFile(String fullPath, byte[] data) throws FilestoreException {
                throw new FilestoreException("Operation failed due to test");
            }

            @Override
            public void replaceFileContents(String fullPath, byte[] data) throws FilestoreException {
                throw new FilestoreException("Operation failed due to test");
            }

            @Override
            public void appendFileToFile(String targetFilePath, String fileToAddPath) throws FilestoreException {
                throw new FilestoreException("Operation failed due to test");
            }

            @Override
            public void replaceFileWithFile(String targetFilePath, String fileToAddPath) throws FilestoreException {
                throw new FilestoreException("Operation failed due to test");
            }

            @Override
            public byte[] getFile(String fullPath) throws FilestoreException {
                throw new FilestoreException("Operation failed due to test");
            }

            @Override
            public void createDirectory(String fullPath) throws FilestoreException {
                throw new FilestoreException("Operation failed due to test");
            }

            @Override
            public void deleteDirectory(String fullPath) throws FilestoreException {
                throw new FilestoreException("Operation failed due to test");
            }

            @Override
            public List<String> listDirectory(String fullPath, boolean recursive) throws FilestoreException {
                throw new FilestoreException("Operation failed due to test");
            }

            @Override
            public boolean fileExists(String fullPath) throws FilestoreException {
                throw new FilestoreException("Operation failed due to test");
            }

            @Override
            public boolean directoryExists(String fullPath) throws FilestoreException {
                throw new FilestoreException("Operation failed due to test");
            }

            @Override
            public long fileSize(String fullPath) throws FilestoreException {
                throw new FilestoreException("Operation failed due to test");
            }

            @Override
            public boolean isUnboundedFile(String fullPath) throws FilestoreException {
                throw new FilestoreException("Operation failed due to test");
            }

            @Override
            public InputStream readFile(String fullPath) throws FilestoreException {
                throw new FilestoreException("Operation failed due to test");
            }

            @Override
            public OutputStream writeFile(String fullPath, boolean append) throws FilestoreException {
                throw new FilestoreException("Operation failed due to test");
            }
        };
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.CREATE, "test1", null);
            assertEquals(FilestoreResponseTLV.StatusCode.CREATE_NOT_ALLOWED, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.DELETE, "test1", null);
            assertEquals(FilestoreResponseTLV.StatusCode.DELETE_FILE_NOT_ALLOWED, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.APPEND, "test1", "test2");
            assertEquals(FilestoreResponseTLV.StatusCode.APPEND_NOT_ALLOWED, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.APPEND, "test2", "test1");
            assertEquals(FilestoreResponseTLV.StatusCode.APPEND_NOT_ALLOWED, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.CREATE_DIRECTORY, "test1", null);
            assertEquals(FilestoreResponseTLV.StatusCode.DIRECTORY_CANNOT_BE_CREATED, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.CREATE_DIRECTORY, "dir1", null);
            assertEquals(FilestoreResponseTLV.StatusCode.DIRECTORY_CANNOT_BE_CREATED, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.RENAME, "dir2", "dir4");
            assertEquals(FilestoreResponseTLV.StatusCode.RENAME_NOT_ALLOWED, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.RENAME, "test11", "test1");
            assertEquals(FilestoreResponseTLV.StatusCode.RENAME_NOT_ALLOWED, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.RENAME, "test1", "dir1");
            assertEquals(FilestoreResponseTLV.StatusCode.RENAME_NOT_ALLOWED, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.REMOVE_DIRECTORY, "dir2", null);
            assertEquals(FilestoreResponseTLV.StatusCode.REMOVE_DIRECTORY_NOT_ALLOWED, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.REPLACE, "test1", "test2");
            assertEquals(FilestoreResponseTLV.StatusCode.REPLACE_NOT_ALLOWED, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.REPLACE, "test2", "test1");
            assertEquals(FilestoreResponseTLV.StatusCode.REPLACE_NOT_ALLOWED, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.DENY_FILE, "test111", null);
            assertEquals(FilestoreResponseTLV.StatusCode.DENY_FILE_NOT_ALLOWED, req.execute(fs1).getStatusCode());
        }
        {
            FilestoreRequestTLV req = new FilestoreRequestTLV(ActionCode.DENY_DIRECTORY, "test2", "test1");
            assertEquals(FilestoreResponseTLV.StatusCode.DENY_DIRECTORY_NOT_ALLOWED, req.execute(fs1).getStatusCode());
        }
    }
}