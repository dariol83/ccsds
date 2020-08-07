/*
 *   Copyright (c) 2019 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.encdec.time;

import java.io.Serializable;

/**
 * This class allows to specify a CUC time descriptor, with the related characteristics, for relative time encoding.
 */
public final class RelativeTimeDescriptor implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Create a CUC descriptor with the provided characteristics.
     *
     * @param coarseTime the size of coarse time in bytes
     * @param fineTime the size of fine time in bytes
     * @return the CUC descriptor
     */
    public static RelativeTimeDescriptor newCucDescriptor(int coarseTime, int fineTime) {
        return new RelativeTimeDescriptor(coarseTime, fineTime);
    }

    public final int coarseTime;
    public final int fineTime;

    private RelativeTimeDescriptor(int coarseTime, int fineTime) {
        this.coarseTime = coarseTime;
        this.fineTime = fineTime;
    }
}
