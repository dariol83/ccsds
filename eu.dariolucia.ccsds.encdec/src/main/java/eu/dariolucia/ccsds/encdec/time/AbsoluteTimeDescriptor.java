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
 * This class allows to specify a CUC or CDS time descriptor, with the related characteristics, for absolute time encoding.
 */
public final class AbsoluteTimeDescriptor implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Create a CUC descriptor with the provided characteristics.
     *
     * @param coarseTime the size of coarse time in bytes
     * @param fineTime the size of fine time in bytes
     * @return the CUC descriptor
     */
    public static AbsoluteTimeDescriptor newCucDescriptor(int coarseTime, int fineTime) {
        return new AbsoluteTimeDescriptor(true, coarseTime, fineTime, false, 0);
    }

    /**
     * Create a CDS descriptor with the provided characteristics.
     *
     * @param use16bits true if two bytes are used for the day field, false if three bytes are used
     * @param subMsPart the size of sub-millisecond field (0: not used, 1: microseconds, 2: picoseconds).
     * @return the CDS descriptor
     */
    public static AbsoluteTimeDescriptor newCdsDescriptor(boolean use16bits, int subMsPart) {
        return new AbsoluteTimeDescriptor(false, 0, 0, use16bits, subMsPart);
    }

    public final boolean cuc;
    public final int coarseTime;
    public final int fineTime;
    public final boolean use16bits;
    public final int subMsPart;

    private AbsoluteTimeDescriptor(boolean cuc, int coarseTime, int fineTime, boolean use16bits, int subMsPart) {
        this.cuc = cuc;
        this.coarseTime = coarseTime;
        this.fineTime = fineTime;
        this.use16bits = use16bits;
        this.subMsPart = subMsPart;
    }
}
