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

/**
 * This class contains a snapshot of the statistics of a given service instance. The unit is not defined and it depends
 * on the usage of this class. In the SLE User Test Library implementation, a unit can be an SLE PDU or a byte.
 *
 * Instances of this class are immutable.
 */
public class DataRateSample {
	
	private final Date date;
	private final double inRate;
	private final double outRate;
	private final long totalInUnits;
	private final long totalOutUnits;

	/**
	 * The object constructor.
	 *
	 * @param date timestamp of the snapshot
	 * @param inRate incoming data rate in units per second
	 * @param outRate outgoing data rate in units per second
	 * @param totalInUnits total number of units received since the start of the service instance
	 * @param totalOutUnits total number of units sent since the start of the service instance
	 */
	public DataRateSample(Date date, double inRate, double outRate, long totalInUnits, long totalOutUnits) {
		this.date = date;
		this.inRate = inRate;
		this.outRate = outRate;
		this.totalInUnits = totalInUnits;
		this.totalOutUnits = totalOutUnits;
	}

	/**
	 * Timestamp of the snapshot.
	 *
	 * @return the time when the statistics was computed
	 */
	public final Date getDate() {
		return date;
	}

	/**
	 * Incoming data rate in units per second.
	 *
	 * @return the measurement of the incoming data rate
	 */
	public final double getInRate() {
		return inRate;
	}

	/**
	 * Outgoing data rate in units per second.
	 *
	 * @return the measurement of the outgoing data rate
	 */
	public final double getOutRate() {
		return outRate;
	}

	/**
	 * Total number of units received since the start of the service instance.
	 *
	 * @return the total number of units received
	 */
	public final long getTotalInUnits() {
		return totalInUnits;
	}

	/**
	 * Total number of units sent since the start of the service instance.
	 *
	 * @return the total number of units sent
	 */
	public final long getTotalOutUnits() {
		return totalOutUnits;
	}
	
}
