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

/**
 * This interface allows to extract segments from a specified files in an iterator-like fashion.
 */
public interface ICfdpFileSegmenter {

    /**
     * This method returns a segment delivering data or EOF indication. In case of problem, it throws an exception.
     *
     * @return a {@link FileSegment} with EOF or data. It shall never return null.
     * @throws FilestoreException in case of filestore-related issues.
     */
    FileSegment nextSegment() throws FilestoreException;

    /**
     * Close the segmented and releases all the underlying resources.
     */
    void close();

}
