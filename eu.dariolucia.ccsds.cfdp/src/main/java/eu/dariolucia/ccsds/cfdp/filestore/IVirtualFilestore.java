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

package eu.dariolucia.ccsds.cfdp.filestore;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public interface IVirtualFilestore {

    void createFile(String fullPath) throws FilestoreException;

    void deleteFile(String fullPath) throws FilestoreException;

    void renameFile(String fullPath, String newFullPath) throws FilestoreException;

    void appendContentsToFile(String fullPath, byte[] data) throws FilestoreException;

    void replaceFileContents(String fullPath, byte[] data) throws FilestoreException;

    void appendFileToFile(String targetFilePath, String fileToAddPath) throws FilestoreException;

    void replaceFileWithFile(String targetFilePath, String fileToAddPath) throws FilestoreException;

    byte[] getFile(String fullPath) throws FilestoreException;

    void createDirectory(String fullPath) throws FilestoreException;

    void deleteDirectory(String fullPath) throws FilestoreException;

    List<String> listDirectory(String fullPath, boolean recursive) throws FilestoreException;

    boolean fileExists(String fullPath) throws FilestoreException;

    boolean directoryExists(String fullPath) throws FilestoreException;

    long fileSize(String fullPath) throws FilestoreException;

    boolean isUnboundedFile(String fullPath) throws FilestoreException;

    InputStream readFile(String fullPath) throws FilestoreException;

    OutputStream writeFile(String fullPath, boolean append) throws FilestoreException;
}
