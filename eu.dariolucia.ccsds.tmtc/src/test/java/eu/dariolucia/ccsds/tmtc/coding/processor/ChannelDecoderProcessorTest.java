/*
 * Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package eu.dariolucia.ccsds.tmtc.coding.processor;

import eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm;
import eu.dariolucia.ccsds.tmtc.coding.ChannelDecoder;
import eu.dariolucia.ccsds.tmtc.coding.decoder.ReedSolomonDecoder;
import eu.dariolucia.ccsds.tmtc.coding.decoder.TmAsmDecoder;
import eu.dariolucia.ccsds.tmtc.coding.reader.LineHexDumpChannelReader;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.util.processor.ConsumerWrapper;
import eu.dariolucia.ccsds.tmtc.util.processor.SupplierWrapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChannelDecoderProcessorTest {

    private static String FILE_TM1 = "dumpFile_tm_1.hex";

    @Test
    public void testTmDecodingCompleteSync() throws InterruptedException {
        // Build the reader (as supplier)
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1));
        // Wrap the reader in a Flow.Publisher
        SupplierWrapper<byte[]> rawFramePublisher = new SupplierWrapper<>(reader);
        // Build the decoder: TM Frame decoding function, no FECF
        ChannelDecoder<TmTransferFrame> cd = ChannelDecoder.create(TmTransferFrame.decodingFunction(false))
                .addDecodingFunction(new TmAsmDecoder()) // Add ASM removal with default ASM
                .addDecodingFunction(new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223)) // Add R-S symbol block removal 255/223
                .configure();
        // Create a ChannelDecoderProcessor, no asynchronous processing, complete
        ChannelDecoderProcessor<TmTransferFrame> p = new ChannelDecoderProcessor<>(cd);
        // Chain the processor with the published
        rawFramePublisher.subscribe(p);
        // Build a data collector
        List<TmTransferFrame> retrievedFrames = new CopyOnWriteArrayList<>();
        // Wrap the collector in a specific consumer
        ConsumerWrapper<TmTransferFrame> collector = new ConsumerWrapper<>(retrievedFrames::add);
        // Link the consumer to the processor
        p.subscribe(collector);
        // Start the processing by activating the publisher, the processing chain is in place
        rawFramePublisher.activate(false);
        // When the activate returns, all frames should be in the processing queue. The SupplierWrapper is in fact
        // asynchronous. So we need to wait a bit. Let's use active wait. Max wait is 10 seconds.
        for (int i = 0; i < 100; ++i) {
            Thread.sleep(100);
            if (retrievedFrames.size() == 135) {
                break;
            }
        }
        rawFramePublisher.deactivate();
        assertEquals(152, retrievedFrames.size());
    }

    @Test
    public void testTmDecodingCompleteAsync() throws InterruptedException {
        // Build the reader (as supplier)
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1));
        // Wrap the reader in a Flow.Publisher
        SupplierWrapper<byte[]> rawFramePublisher = new SupplierWrapper<>(reader);
        // Build the decoder: TM Frame decoding function, no FECF
        ChannelDecoder<TmTransferFrame> cd = ChannelDecoder.create(TmTransferFrame.decodingFunction(false))
                .addDecodingFunction(new TmAsmDecoder()) // Add ASM removal with default ASM
                .addDecodingFunction(new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223)) // Add R-S symbol block removal 255/223
                .configure();
        // Create a ChannelDecoderProcessor, asynchronous processing, complete
        ChannelDecoderProcessor<TmTransferFrame> p = new ChannelDecoderProcessor<>(cd, Executors.newFixedThreadPool(1), false);
        // Chain the processor with the published
        rawFramePublisher.subscribe(p);
        // Build a data collector
        List<TmTransferFrame> retrievedFrames = new CopyOnWriteArrayList<>();
        // Wrap the collector in a specific consumer
        ConsumerWrapper<TmTransferFrame> collector = new ConsumerWrapper<>(retrievedFrames::add);
        // Link the consumer to the processor
        p.subscribe(collector);
        // Start the processing by activating the publisher, the processing chain is in place
        rawFramePublisher.activate(true);
        // When the activate returns, all frames should be in the processing queue. The SupplierWrapper is in fact
        // asynchronous. So we need to wait a bit. Let's use active wait. Max wait is 10 seconds.
        for (int i = 0; i < 100; ++i) {
            Thread.sleep(100);
            if (retrievedFrames.size() == 135) {
                break;
            }
        }
        assertEquals(152, retrievedFrames.size());
    }

    @Test
    public void testTmDecodingTimelyAsync() throws InterruptedException {
        // Build the reader (as supplier)
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1));
        // Wrap the reader in a Flow.Publisher
        SupplierWrapper<byte[]> rawFramePublisher = new SupplierWrapper<>(reader, true);
        // Build the decoder: TM Frame decoding function, no FECF
        ChannelDecoder<TmTransferFrame> cd = ChannelDecoder.create(TmTransferFrame.decodingFunction(false))
                .addDecodingFunction(new TmAsmDecoder()) // Add ASM removal with default ASM
                .addDecodingFunction(new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223)) // Add R-S symbol block removal 255/223
                .configure();
        // Create a ChannelDecoderProcessor, asynchronous processing, timely: if a subscription is requesting slowly, the remaining
        // buffer is discarded and only new frames are sent.
        ChannelDecoderProcessor<TmTransferFrame> p = new ChannelDecoderProcessor<>(cd, Executors.newFixedThreadPool(1), true);
        // Chain the processor with the published
        rawFramePublisher.subscribe(p);
        // Build a data collector
        List<TmTransferFrame> retrievedFrames = new CopyOnWriteArrayList<>();
        // Build an artificial back pressure: using an atomic integer because it is mutable
        AtomicInteger requests = new AtomicInteger(10);
        // Wrap the collector in a specific consumer: this consumer will report 10 free slots only once.
        ConsumerWrapper<TmTransferFrame> collector = new ConsumerWrapper<>(o -> {
            retrievedFrames.add(o);
            requests.decrementAndGet();
        }, del -> {
            if (requests.get() == 10) {
                // First request, so return a number in order to start the requests to the processor
                return requests.get();
            } else {
                // Only an initial request of 10, then drop the rest
                return null;
            }
        });
        // Link the consumer to the processor
        p.subscribe(collector);
        // Start the processing by activating the publisher, the processing chain is in place
        rawFramePublisher.activate(true);
        // When the activate returns, all frames should be in the processing queue. The SupplierWrapper is in fact
        // asynchronous. We wait a fixed amount (1 second) and then we check.
        Thread.sleep(1000);
        //
        rawFramePublisher.deactivate();
        // Only 10 items in the list
        assertEquals(10, retrievedFrames.size());
    }
}