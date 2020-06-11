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

package eu.dariolucia.ccsds.tmtc.cop1.fop;

public enum FopDirective {
    INIT_AD_WITHOUT_CLCW,
    INIT_AD_WITH_CLCW,
    INIT_AD_WITH_UNLOCK,
    INIT_AD_WITH_SET_V_R,
    TERMINATE,
    RESUME,
    SET_V_S,
    SET_FOP_SLIDING_WINDOW,
    SET_T1_INITIAL,
    SET_TRANSMISSION_LIMIT,
    SET_TIMEOUT_TYPE
}
