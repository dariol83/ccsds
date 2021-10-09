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

/**
 * This interface defines all the operations that a virtual filestore implementation must have available to the CFDP
 * entity. All operations to the filestore are synchronous and the CFDP entity do not use any means to check/enforce access control or
 * synchronisation to the filestore. Therefore, implementations should care about concurrency and avoidance of race conditions.
 *
 * The representation of the fullPath as well as the support for directory-nesting is implementation-dependent.
 *
 * Null-values shall be never returned.
 */
public interface IVirtualFilestore {

    /**
     * Create an empty file in the specified location.
     *
     * @param fullPath the full path of the file to be created
     * @throws FilestoreException in case the file cannot be created
     */
    void createFile(String fullPath) throws FilestoreException;

    /**
     * Delete the file in the specified location.
     *
     * @param fullPath the full path of the file to be deleted
     * @throws FilestoreException in case the file cannot be deleted, the full path points to a directory or the file does not exist
     */
    void deleteFile(String fullPath) throws FilestoreException;

    /**
     * Rename/move the first file/directory to the new location/name.
     *
     * @param fullPath the full path of the file/directory to be renamed/moved
     * @param newFullPath the full path of the file/directory after being renamed/moved
     * @throws FilestoreException in case the file cannot be renamed/moved
     */
    void renameFile(String fullPath, String newFullPath) throws FilestoreException;

    /**
     * Append the provided contents to the (existing) file.
     *
     * @param fullPath the full path of the file, the data shall be appended to
     * @param data the data to append
     * @throws FilestoreException in case the data cannot be appended to the specified file
     */
    void appendContentsToFile(String fullPath, byte[] data) throws FilestoreException;

    /**
     * Replace the (existing) file contents with the provided content.
     *
     * @param fullPath the full path of the file, whose content shall be replaced
     * @param data the new content of the file
     * @throws FilestoreException in case the file content cannot be replaced with the provided data
     */
    void replaceFileContents(String fullPath, byte[] data) throws FilestoreException;

    /**
     * Append the content of the file pointed by filetoAddPath to the content of the file pointed by targetFilePath.
     *
     * @param targetFilePath the full path of the file to be modified by appending new content
     * @param fileToAddPath the full path of the file containing the content to be appended
     * @throws FilestoreException in case of issues when dealing with the two files
     */
    void appendFileToFile(String targetFilePath, String fileToAddPath) throws FilestoreException;

    /**
     * Replace the content of the file pointed by targetFilePath with the content of the file pointed by fileToAddPath.
     *
     * @param targetFilePath the full path of the file to be modified by replacing its content
     * @param fileToAddPath the full path of the file containing the content to be used for replacement
     * @throws FilestoreException in case of issues when dealing with the two files
     */
    void replaceFileWithFile(String targetFilePath, String fileToAddPath) throws FilestoreException;

    /**
     * Return the content of the specified file.
     *
     * @param fullPath the full path of the file to read
     * @return the content of the specified file
     * @throws FilestoreException in case of issues reading the file
     */
    byte[] getFile(String fullPath) throws FilestoreException;

    /**
     * Create a directory in the specified location.
     *
     * @param fullPath the full path of the directory to create
     * @throws FilestoreException in case of issues creating the directory
     */
    void createDirectory(String fullPath) throws FilestoreException;

    /**
     * Delete the directory in the specified location.
     *
     * @param fullPath the full path of the directory to delete
     * @throws FilestoreException in case of issues deleting the directory
     */
    void deleteDirectory(String fullPath) throws FilestoreException;

    /**
     * List all the files/directories contained in the specified directory, using optional recursion.
     *
     * @param fullPath the full path of the directory to list
     * @param recursive true if recursion is needed, otherwise false
     * @return the list of paths of all items - directories, files - contained in the specified directory
     * @throws FilestoreException in case of issues retrieving the list of files/directories
     */
    List<String> listDirectory(String fullPath, boolean recursive) throws FilestoreException;

    /**
     * Check if a file exists.
     *
     * @param fullPath the full path of the file to check
     * @return true if the full path points to a file and the file exists, false otherwise
     * @throws FilestoreException in case of issues when accessing the file
     */
    boolean fileExists(String fullPath) throws FilestoreException;

    /**
     * Check if a directory exists.
     *
     * @param fullPath the full path of the directory to check
     * @return true if the full path points to a directory and the directory exists, false otherwise
     * @throws FilestoreException in case of issues when accessing the directory
     */
    boolean directoryExists(String fullPath) throws FilestoreException;

    /**
     * Return the file size in octets of the specified file.
     *
     * @param fullPath the full path of the file
     * @return the file size in octets
     * @throws FilestoreException in case of issues when accessing the file or the file does not exist
     */
    long fileSize(String fullPath) throws FilestoreException;

    /**
     * Check for file unbounded property. This is filestore-implementation dependant.
     *
     * @param fullPath the full path of the file
     * @return the bounding status of the file
     * @throws FilestoreException in case of issues when accessing the file
     */
    boolean isUnboundedFile(String fullPath) throws FilestoreException;

    /**
     * Return an {@link InputStream} object that can be used to read a file.
     *
     * @param fullPath the full path of the file to read
     * @return the {@link InputStream} that delivers the file content
     * @throws FilestoreException in case of issues when accessing the file
     */
    InputStream readFile(String fullPath) throws FilestoreException;

    /**
     * Return an {@link OutputStream} object that can be used to write to an existing file.
     *
     * @param fullPath the full path of the file to write
     * @param append true if the new content shall be appended, otherwise false (content is replaced)
     * @return the {@link OutputStream} that can write the file content
     * @throws FilestoreException in case of issues when accessing the file or if the file does not exist
     */
    OutputStream writeFile(String fullPath, boolean append) throws FilestoreException;
}
