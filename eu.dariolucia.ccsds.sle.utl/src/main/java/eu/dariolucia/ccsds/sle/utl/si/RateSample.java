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

import java.time.Instant;

import eu.dariolucia.ccsds.sle.utl.util.DataRateSample;

public class RateSample {
	
	private final Instant instant;
	private final DataRateSample pduSample;
	private final DataRateSample byteSample;
	
	public RateSample(Instant instant, DataRateSample pduSample, DataRateSample byteSample) {
		this.instant = instant;
		this.pduSample = pduSample;
		this.byteSample = byteSample;
	}

	public final Instant getInstant() {
		return instant;
	}

	public final DataRateSample getPduSample() {
		return pduSample;
	}

	public final DataRateSample getByteSample() {
		return byteSample;
	}

	public String toCompactByteRateString() {
		return "IN: " + byteSample.getInRate() + " OUT: " + byteSample.getOutRate();
	}

	@Override
	public String toString() {
		return "{" +
				"TIME=" + instant +
				", PDU=" + pduSample +
				", BYTE=" + byteSample +
				'}';
	}
}
