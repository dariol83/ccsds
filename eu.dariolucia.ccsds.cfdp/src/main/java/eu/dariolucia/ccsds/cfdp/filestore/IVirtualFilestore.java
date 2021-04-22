package eu.dariolucia.ccsds.cfdp.filestore;

import java.util.List;

public interface IVirtualFilestore {

    void createFile(String fullPath) throws FilestoreException;

    void deleteFile(String fullPath) throws FilestoreException;

    void renameFile(String fullPath, String newFullPath) throws FilestoreException;

    void appendFile(String fullPath, byte[] data) throws FilestoreException;

    void replaceFile(String fullPath, byte[] data) throws FilestoreException;

    byte[] getFile(String fullPath) throws FilestoreException;

    void createDirectory(String fullPath) throws FilestoreException;

    void deleteDirectory(String fullPath) throws FilestoreException;

    List<String> listDirectory(String fullPath, boolean recursive) throws FilestoreException;

    boolean fileExists(String fullPath) throws FilestoreException;

    boolean directoryExists(String fullPath) throws FilestoreException;

}
