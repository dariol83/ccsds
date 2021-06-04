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

import eu.dariolucia.ccsds.cfdp.entity.request.PutRequest;
import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class CfdpEntityTest {

    @Test
    public void testAcknowledgedTransaction() throws IOException, FilestoreException { // NOSONAR work in progress
        ICfdpEntity e1 = TestUtils.createTcpEntity("configuration_entity_1.xml", 23001);
        ICfdpEntity e2 = TestUtils.createTcpEntity("configuration_entity_2.xml", 23002);

        // Create file in filestore
        String path = TestUtils.createRandomFileIn(e1.getFilestore(), "testfile_ack.bin", 10); // 10 KB

        PutRequest fduTxReq = PutRequest.build(2, "testfile_ack.bin", "recv_testfile_ack.bin", false, null);
        // e1.request(fduTxReq);
    }

    @Test
    public void testUnacknowledgedTransactionWithClosure() { // NOSONAR work in progress

    }

    @Test
    public void testUnacknowledgedTransactionWithoutClosure() { // NOSONAR work in progress

    }
}
