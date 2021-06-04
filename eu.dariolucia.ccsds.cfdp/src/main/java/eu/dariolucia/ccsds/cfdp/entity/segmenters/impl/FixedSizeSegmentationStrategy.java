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

package eu.dariolucia.ccsds.cfdp.entity.segmenters.impl;

import eu.dariolucia.ccsds.cfdp.entity.segmenters.ICfdpFileSegmenter;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.ICfdpSegmentationStrategy;
import eu.dariolucia.ccsds.cfdp.filestore.IVirtualFilestore;
import eu.dariolucia.ccsds.cfdp.mib.Mib;

public class FixedSizeSegmentationStrategy implements ICfdpSegmentationStrategy {

    @Override
    public boolean support(Mib mib, IVirtualFilestore filestore, String fullPath) {
        // All files can be segmented
        return true;
    }

    @Override
    public ICfdpFileSegmenter newSegmenter(Mib mib, IVirtualFilestore filestore, String fullPath, long destinationEntityId) {
        return new FixedSizeSegmenter(filestore, fullPath, mib.getRemoteEntityById(destinationEntityId).getMaximumFileSegmentLength());
    }
}
