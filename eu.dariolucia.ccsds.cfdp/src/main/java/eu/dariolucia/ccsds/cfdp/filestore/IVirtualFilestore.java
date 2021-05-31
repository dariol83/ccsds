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
