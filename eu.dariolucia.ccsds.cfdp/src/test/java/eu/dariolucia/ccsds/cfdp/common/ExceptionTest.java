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

package eu.dariolucia.ccsds.cfdp.common;

import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ExceptionTest {

    @Test
    public void testExceptions() {
        assertThrows(CfdpException.class, () -> {
            throw new CfdpException("Test");
        });
        assertThrows(CfdpException.class, () -> {
            throw new CfdpException(new RuntimeException());
        });
        assertThrows(CfdpException.class, () -> {
            throw new CfdpException("Test", new RuntimeException());
        });
        assertThrows(CfdpRuntimeException.class, () -> {
            throw new CfdpRuntimeException("Test");
        });
        assertThrows(CfdpRuntimeException.class, () -> {
            throw new CfdpRuntimeException(new RuntimeException());
        });
        assertThrows(CfdpRuntimeException.class, () -> {
            throw new CfdpRuntimeException("Test", new RuntimeException());
        });
        assertThrows(CfdpStandardComplianceError.class, () -> {
            throw new CfdpStandardComplianceError("Test");
        });
        assertThrows(FilestoreException.class, () -> {
            throw new FilestoreException("Test");
        });
        assertThrows(FilestoreException.class, () -> {
            throw new FilestoreException(new RuntimeException());
        });
        assertThrows(FilestoreException.class, () -> {
            throw new FilestoreException("Test", new RuntimeException());
        });
    }
}
