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

package eu.dariolucia.ccsds.cfdp.entity.indication;

/**
 * The EntityDisposed.indication primitive is not part of the standard and it is introduced by this implementation
 * to notify subscribers that a given CFDP entity was disposed. As a consequence of the disposal, all active transactions
 * were cancelled.
 */
public class EntityDisposedIndication implements ICfdpIndication {

    @Override
    public String toString() {
        return "EntityDisposedIndication {}";
    }
}
