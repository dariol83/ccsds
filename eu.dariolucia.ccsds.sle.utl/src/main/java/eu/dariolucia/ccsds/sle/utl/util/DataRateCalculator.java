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

public class DataRateCalculator {

	private Date lastSamplingTime;
	private long totalInUnitsSinceReset;
	private long totalOutUnitsSinceReset;
	
	private long tempInUnits;
	private long tempOutUnits;
	
	public synchronized void reset() {
		this.lastSamplingTime = new Date();
		this.totalInUnitsSinceReset = 0;
		this.totalOutUnitsSinceReset = 0;
		this.tempInUnits = 0;
		this.tempOutUnits = 0;
	}
	
	public synchronized void addIn(long units) {
		this.tempInUnits += units;
		this.totalInUnitsSinceReset += units;
	}
	
	public synchronized void addOut(long units) {
		this.tempOutUnits += units;
		this.totalOutUnitsSinceReset += units;
	}
	
	public synchronized DataRateSample sample() {
		Date now = new Date();
		if(this.lastSamplingTime == null) {
			this.lastSamplingTime = now;
			return new DataRateSample(now, 0, 0, this.totalInUnitsSinceReset, this.totalOutUnitsSinceReset);
		} else {
			double inRate = ((double) this.tempInUnits / (double) (now.getTime() - this.lastSamplingTime.getTime())) * 1000;
			double outRate = ((double) this.tempOutUnits / (double) (now.getTime() - this.lastSamplingTime.getTime())) * 1000;
			this.tempInUnits = 0;
			this.tempOutUnits = 0;
			this.lastSamplingTime = now;
			return new DataRateSample(now, inRate, outRate, this.totalInUnitsSinceReset, this.totalOutUnitsSinceReset);
		}
	}
}
