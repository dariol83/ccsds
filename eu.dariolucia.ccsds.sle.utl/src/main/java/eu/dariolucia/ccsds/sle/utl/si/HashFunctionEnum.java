/*
 *  Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.sle.utl.si;

/**
 * This enumeration is used to specify which hash function shall be used for credentials. Supported functions are:
 * - SHA-1: deprecated, used until SLE version 3
 * - SHA-256: current, used since SLE version 4
 */
public enum HashFunctionEnum {
    SHA_1("SHA-1"),
    SHA_256("SHA-256");

    private String hashFunction;

    HashFunctionEnum(String hashFunction) {
        this.hashFunction = hashFunction;
    }

    public String getHashFunction() {
        return hashFunction;
    }
}
