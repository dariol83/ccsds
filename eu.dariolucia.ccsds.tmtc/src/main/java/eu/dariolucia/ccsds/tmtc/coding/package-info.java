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

/**
 * This package contains classes and interfaces that allow the implementation of object-based, stream-based or
 * reactor-based reading, encoding and decoding of transfer frames:
 * <ul>
 * <li>Reader: binary fixed length, binary ASM + fixed length, binary ASM + variable length + trailer, ASCII line-based</li>
 * <li>Encoder: CLTU, CLTU randomizer, TM ASM, TM randomizer, Reed-Solomon</li>
 * <li>Decoder: CLTU, CLTU derandomizer, TM ASM removal, Reed-Solomon (symbols removal, check only, no error correction)</li>
 * </ul>
 *
 */
package eu.dariolucia.ccsds.tmtc.coding;