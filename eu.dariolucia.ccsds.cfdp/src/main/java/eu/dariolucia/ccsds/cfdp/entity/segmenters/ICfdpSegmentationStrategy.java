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

package eu.dariolucia.ccsds.cfdp.entity.segmenters;

import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import eu.dariolucia.ccsds.cfdp.filestore.IVirtualFilestore;
import eu.dariolucia.ccsds.cfdp.mib.Mib;

public interface ICfdpSegmentationStrategy {

    /**
     * This method returns whether this strategy can support the segmentation of the indicated file in the provider filestore.
     *
     * @param mib the MIB of the CFDP entity
     * @param filestore the filestore that has the file
     * @param fullPath the full path of the file
     * @return true if segmentation is supported, false otherwise
     */
    boolean support(Mib mib, IVirtualFilestore filestore, String fullPath);

    /**
     * This method returns a new object that can provide segments using an iterator-like fashion.
     *
     * @param mib the MIB of the CFDP entity
     * @param filestore the filestore that has the file
     * @param fullPath the full path of the file
     * @param destinationEntityId the ID of the destination entity
     * @return the {@link ICfdpFileSegmenter} related to the provided file
     * @throws FilestoreException case of problems when retrieving the data from the filestore
     */
    ICfdpFileSegmenter newSegmenter(Mib mib, IVirtualFilestore filestore, String fullPath, long destinationEntityId) throws FilestoreException;
}
