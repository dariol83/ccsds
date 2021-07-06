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

import eu.dariolucia.ccsds.cfdp.common.BytesUtil;
import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import eu.dariolucia.ccsds.cfdp.filestore.IVirtualFilestore;

import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;

public class FilestoreRequestTLV implements TLV {

    public static final int TLV_TYPE = 0x00;

    private static final Map<ActionCode, IInternalFileOperation> ACTON2OPERATION;

    static {
        ACTON2OPERATION = new EnumMap<>(ActionCode.class);
        ACTON2OPERATION.put(ActionCode.CREATE, FilestoreRequestTLV::createFile);
        ACTON2OPERATION.put(ActionCode.CREATE_DIRECTORY, FilestoreRequestTLV::createDirectory);
        ACTON2OPERATION.put(ActionCode.APPEND, FilestoreRequestTLV::append);
        ACTON2OPERATION.put(ActionCode.DELETE, FilestoreRequestTLV::deleteFile);
        ACTON2OPERATION.put(ActionCode.DENY_DIRECTORY, FilestoreRequestTLV::denyDirectory);
        ACTON2OPERATION.put(ActionCode.DENY_FILE, FilestoreRequestTLV::denyFile);
        ACTON2OPERATION.put(ActionCode.REPLACE, FilestoreRequestTLV::replace);
        ACTON2OPERATION.put(ActionCode.REMOVE_DIRECTORY, FilestoreRequestTLV::deleteDirectory);
        ACTON2OPERATION.put(ActionCode.RENAME, FilestoreRequestTLV::rename);
    }

    private final ActionCode actionCode;

    private final String firstFileName;

    private final String secondFileName;

    private final int encodedLength;

    public FilestoreRequestTLV(ActionCode actionCode, String firstFileName, String secondFileName) {
        this.actionCode = actionCode;
        this.firstFileName = firstFileName;
        this.secondFileName = secondFileName;
        this.encodedLength = 1 + (firstFileName == null ? 1 : 1 + firstFileName.length()) + (secondFileName == null ? 1 : 1 + secondFileName.length());
    }

    public FilestoreRequestTLV(byte[] data, int offset) {
        int originalOffset = offset;
        // Starting from offset, assume that there is an encoded Filestore Request TLV Contents: Table 5-15
        this.actionCode = ActionCode.values()[(data[offset] & 0xF0) >>> 4];
        offset += 1;
        // First file name
        String name1 = BytesUtil.readLVString(data, offset);
        offset += (name1.isEmpty()) ? 1 : 1 + name1.length();
        this.firstFileName = name1.isEmpty() ? null : name1;
        // Second file name
        String name2 = BytesUtil.readLVString(data, offset);
        offset += (name1.isEmpty()) ? 1 : 1 + name2.length();
        this.secondFileName = name2.isEmpty() ? null : name2;
        // Encoded length
        this.encodedLength = offset - originalOffset;
    }

    public ActionCode getActionCode() {
        return actionCode;
    }

    public String getFirstFileName() {
        return firstFileName;
    }

    public String getSecondFileName() {
        return secondFileName;
    }

    @Override
    public int getType() {
        return TLV_TYPE;
    }

    @Override
    public int getLength() {
        return encodedLength;
    }

    @Override
    public byte[] encode() {
        ByteBuffer bb;
        bb = ByteBuffer.allocate(2 + this.encodedLength);
        bb.put((byte) TLV_TYPE);
        bb.put((byte) (this.encodedLength & 0xFF));
        bb.put((byte)((getActionCode().ordinal() << 4) & 0xFF));
        BytesUtil.writeLVString(bb, getFirstFileName());
        BytesUtil.writeLVString(bb, getSecondFileName());
        return bb.array();
    }

    @Override
    public String toString() {
        return "FilestoreRequestTLV{" +
                "actionCode=" + actionCode +
                ", firstFileName='" + firstFileName + '\'' +
                ", secondFileName='" + secondFileName + '\'' +
                ", encodedLength=" + encodedLength +
                '}';
    }

    public FilestoreResponseTLV execute(IVirtualFilestore filestore) {
        IInternalFileOperation task = ACTON2OPERATION.get(getActionCode());
        if(task == null) {
            return new FilestoreResponseTLV(getActionCode(), FilestoreResponseTLV.StatusCode.NOT_PERFORMED, getFirstFileName(), getSecondFileName(), "Operation not supported");
        } else {
            return task.apply(filestore, getFirstFileName(), getSecondFileName());
        }

    }

    private static FilestoreResponseTLV createFile(IVirtualFilestore filestore, String first, String second) {
        try {
            if(filestore.fileExists(first)) {
                return new FilestoreResponseTLV(ActionCode.CREATE,
                        FilestoreResponseTLV.StatusCode.CREATE_NOT_ALLOWED, first, second, null);
            }
            filestore.createFile(first);
            return new FilestoreResponseTLV(ActionCode.CREATE,
                    FilestoreResponseTLV.StatusCode.SUCCESSFUL, first, second, null);
        } catch (FilestoreException e) {
            return new FilestoreResponseTLV(ActionCode.CREATE,
                    FilestoreResponseTLV.StatusCode.CREATE_NOT_ALLOWED, first, second, e.getMessage());
        }
    }

    private static FilestoreResponseTLV createDirectory(IVirtualFilestore filestore, String first, String second) {
        try {
            if(filestore.directoryExists(first)) {
                return new FilestoreResponseTLV(ActionCode.CREATE_DIRECTORY,
                        FilestoreResponseTLV.StatusCode.DIRECTORY_CANNOT_BE_CREATED, first, second, null);
            }
            filestore.createDirectory(first);
            return new FilestoreResponseTLV(ActionCode.CREATE_DIRECTORY,
                    FilestoreResponseTLV.StatusCode.SUCCESSFUL, first, second, null);
        } catch (FilestoreException e) {
            return new FilestoreResponseTLV(ActionCode.CREATE_DIRECTORY,
                    FilestoreResponseTLV.StatusCode.DIRECTORY_CANNOT_BE_CREATED, first, second, e.getMessage());
        }
    }

    private static FilestoreResponseTLV rename(IVirtualFilestore filestore, String first, String second) {
        try {
            if(!filestore.fileExists(first)) {
                return new FilestoreResponseTLV(ActionCode.RENAME,
                        FilestoreResponseTLV.StatusCode.OLD_FILE_DOES_NOT_EXIST, first, second, null);
            }
            if(filestore.fileExists(second)) {
                return new FilestoreResponseTLV(ActionCode.RENAME,
                        FilestoreResponseTLV.StatusCode.NEW_FILE_ALREADY_EXISTS, first, second, null);
            }
            filestore.renameFile(first,second);
            return new FilestoreResponseTLV(ActionCode.RENAME,
                    FilestoreResponseTLV.StatusCode.SUCCESSFUL, first, second, null);
        } catch (FilestoreException e) {
            return new FilestoreResponseTLV(ActionCode.RENAME,
                    FilestoreResponseTLV.StatusCode.RENAME_NOT_ALLOWED, first, second, e.getMessage());
        }
    }

    private static FilestoreResponseTLV deleteFile(IVirtualFilestore filestore, String first, String second) {
        try {
            if(!filestore.fileExists(first)) {
                return new FilestoreResponseTLV(ActionCode.DELETE,
                        FilestoreResponseTLV.StatusCode.FILE_DOES_NOT_EXIST, first, second, null);
            }
            filestore.deleteFile(first);
            return new FilestoreResponseTLV(ActionCode.DELETE,
                    FilestoreResponseTLV.StatusCode.SUCCESSFUL, first, second, null);
        } catch (FilestoreException e) {
            return new FilestoreResponseTLV(ActionCode.DELETE,
                    FilestoreResponseTLV.StatusCode.DELETE_FILE_NOT_ALLOWED, first, second, e.getMessage());
        }
    }

    private static FilestoreResponseTLV deleteDirectory(IVirtualFilestore filestore, String first, String second) {
        try {
            if(!filestore.directoryExists(first)) {
                return new FilestoreResponseTLV(ActionCode.REMOVE_DIRECTORY,
                        FilestoreResponseTLV.StatusCode.DIRECTORY_DOES_NOT_EXIST, first, second, null);
            }
            filestore.deleteDirectory(first);
            return new FilestoreResponseTLV(ActionCode.REMOVE_DIRECTORY,
                    FilestoreResponseTLV.StatusCode.SUCCESSFUL, first, second, null);
        } catch (FilestoreException e) {
            return new FilestoreResponseTLV(ActionCode.REMOVE_DIRECTORY,
                    FilestoreResponseTLV.StatusCode.REMOVE_DIRECTORY_NOT_ALLOWED, first, second, e.getMessage());
        }
    }

    private static FilestoreResponseTLV append(IVirtualFilestore filestore, String first, String second) {
        try {
            if(!filestore.fileExists(first)) {
                return new FilestoreResponseTLV(ActionCode.APPEND,
                        FilestoreResponseTLV.StatusCode.FILE_1_DOES_NOT_EXIST, first, second, null);
            }
            if(!filestore.fileExists(second)) {
                return new FilestoreResponseTLV(ActionCode.APPEND,
                        FilestoreResponseTLV.StatusCode.FILE_2_DOES_NOT_EXIST, first, second, null);
            }
            filestore.appendFileToFile(first,second);
            return new FilestoreResponseTLV(ActionCode.APPEND,
                    FilestoreResponseTLV.StatusCode.SUCCESSFUL, first, second, null);
        } catch (FilestoreException e) {
            return new FilestoreResponseTLV(ActionCode.APPEND,
                    FilestoreResponseTLV.StatusCode.APPEND_NOT_ALLOWED, first, second, e.getMessage());
        }
    }

    private static FilestoreResponseTLV replace(IVirtualFilestore filestore, String first, String second) {
        try {
            if(!filestore.fileExists(first)) {
                return new FilestoreResponseTLV(ActionCode.REPLACE,
                        FilestoreResponseTLV.StatusCode.FILE_1_NOT_EXIST, first, second, null);
            }
            if(!filestore.fileExists(second)) {
                return new FilestoreResponseTLV(ActionCode.REPLACE,
                        FilestoreResponseTLV.StatusCode.FILE_2_NOT_EXIST, first, second, null);
            }
            filestore.replaceFileWithFile(first,second);
            return new FilestoreResponseTLV(ActionCode.REPLACE,
                    FilestoreResponseTLV.StatusCode.SUCCESSFUL, first, second, null);
        } catch (FilestoreException e) {
            return new FilestoreResponseTLV(ActionCode.REPLACE,
                    FilestoreResponseTLV.StatusCode.REPLACE_NOT_ALLOWED, first, second, e.getMessage());
        }
    }

    private static FilestoreResponseTLV denyFile(IVirtualFilestore filestore, String first, String second) {
        try {
            if(!filestore.fileExists(first)) {
                return new FilestoreResponseTLV(ActionCode.DENY_FILE,
                        FilestoreResponseTLV.StatusCode.SUCCESSFUL, first, second, null);
            }
            filestore.deleteFile(first);
            return new FilestoreResponseTLV(ActionCode.DENY_FILE,
                    FilestoreResponseTLV.StatusCode.SUCCESSFUL, first, second, null);
        } catch (FilestoreException e) {
            return new FilestoreResponseTLV(ActionCode.DENY_FILE,
                    FilestoreResponseTLV.StatusCode.DENY_FILE_NOT_ALLOWED, first, second, e.getMessage());
        }
    }

    private static FilestoreResponseTLV denyDirectory(IVirtualFilestore filestore, String first, String second) {
        try {
            if(!filestore.directoryExists(first)) {
                return new FilestoreResponseTLV(ActionCode.DENY_DIRECTORY,
                        FilestoreResponseTLV.StatusCode.SUCCESSFUL, first, second, null);
            }
            filestore.deleteDirectory(first);
            return new FilestoreResponseTLV(ActionCode.DENY_DIRECTORY,
                    FilestoreResponseTLV.StatusCode.SUCCESSFUL, first, second, null);
        } catch (FilestoreException e) {
            return new FilestoreResponseTLV(ActionCode.DENY_DIRECTORY,
                    FilestoreResponseTLV.StatusCode.DENY_DIRECTORY_NOT_ALLOWED, first, second, e.getMessage());
        }
    }

    /**
     * Internal functional interface to be used in the action-to-operation map.
     */
    private interface IInternalFileOperation {

        FilestoreResponseTLV apply(IVirtualFilestore filestore, String first, String second);

    }
}
