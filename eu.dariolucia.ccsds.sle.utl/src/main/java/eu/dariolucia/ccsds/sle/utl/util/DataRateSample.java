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

package eu.dariolucia.ccsds.sle.utl.util;

import java.util.Date;

public class DataRateSample {
	
	private final Date date;
	private final double inRate;
	private final double outRate;
	private final long totalInUnits;
	private final long totalOutUnits;
	
	public DataRateSample(Date date, double inRate, double outRate, long totalInUnits, long totalOutUnits) {
		this.date = date;
		this.inRate = inRate;
		this.outRate = outRate;
		this.totalInUnits = totalInUnits;
		this.totalOutUnits = totalOutUnits;
	}

	public final Date getDate() {
		return date;
	}

	public final double getInRate() {
		return inRate;
	}

	public final double getOutRate() {
		return outRate;
	}

	public final long getTotalInUnits() {
		return totalInUnits;
	}

	public final long getTotalOutUnits() {
		return totalOutUnits;
	}
	
}
