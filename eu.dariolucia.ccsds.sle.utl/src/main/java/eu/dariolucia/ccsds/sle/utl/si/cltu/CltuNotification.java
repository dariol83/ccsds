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

package eu.dariolucia.ccsds.sle.utl.si.cltu;

public class CltuNotification {

	private final CltuNotificationTypeEnum type;
	private final Long eventInvocationId;
	
	public CltuNotification(CltuNotificationTypeEnum type, Long eventInvocationId) {
		super();
		this.type = type;
		this.eventInvocationId = eventInvocationId;
	}

	public final CltuNotificationTypeEnum getType() {
		return type;
	}

	public final Long getEventInvocationId() {
		return eventInvocationId;
	}

	@Override
	public String toString() {
		return type + (eventInvocationId != null ? " [" + eventInvocationId + "]" : "");
	}

	public enum CltuNotificationTypeEnum {
		cltuRadiated,
		slduExpired,
		productionInterrupted, 
		productionHalted,
		productionOperational, 
		bufferEmpty, 
		actionListCompleted, 
		actionListNotCompleted,
		eventConditionEvFalse
	}
}
