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

package eu.dariolucia.ccsds.tmtc.ocf.pdu;

import java.io.Serializable;

/**
 * This class represents an abstract Operational Control Field class. Upon creation the class checks if the provided
 * argument is a CLCW and sets the flag returned by isClcw() accordingly.
 *
 * This class is immutable but it might return direct references for performance reasons.
 */
public abstract class AbstractOcf implements Serializable {

    /**
     * The OCF data
     */
    protected final byte[] ocf;
    /**
     * Flag for CLCW identification
     */
    protected final boolean clcw;

    /**
     * The constructor takes a byte array and tries to check if the provided array is a CLCW. If the array is null or
     * not large enough, an exception is returned. The constructor DOES NOT copy the provided input for performance
     * reasons. It is responsibility of the caller application to ensure that either the ownership of the byte array is transferred
     * to this class or that no modifications will be further performed to the byte array.
     *
     * @param ocf the byte array containing the encoded OCF
     */
    protected AbstractOcf(byte[] ocf) {
        if(ocf == null || ocf.length == 0) {
            throw new IllegalArgumentException("OCF array null or with size 0");
        }
        this.ocf = ocf;
        this.clcw = (ocf[0] & 0x80) == 0;
    }

    /**
     * This method returns the underlying array.
     *
     * Direct reference returned.
     *
     * @return the OCF
     */
    public byte[] getOcf() {
        return ocf;
    }

    /**
     * This method returns whether the OCF is a CLCW or not.
     *
     * @return true if the OCF is a CLCW, false otherwise
     */
    public boolean isClcw() {
        return clcw;
    }
}
