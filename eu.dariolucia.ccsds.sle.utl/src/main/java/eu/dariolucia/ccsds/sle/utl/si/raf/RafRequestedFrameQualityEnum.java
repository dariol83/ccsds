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

package eu.dariolucia.ccsds.sle.utl.si.raf;

public enum RafRequestedFrameQualityEnum {
	GOOD_FRAMES_ONLY(0),
	BAD_FRAMES_ONLY(1),
	ALL_FRAMES(2);
	
	private final int code;
	
	RafRequestedFrameQualityEnum(int code) {
		this.code = code;
	}
	
	public int getCode() {
		return this.code;
	}

	public static RafRequestedFrameQualityEnum fromCode(int c) {
		switch(c) {
		case 0: return RafRequestedFrameQualityEnum.GOOD_FRAMES_ONLY;
		case 1: return RafRequestedFrameQualityEnum.BAD_FRAMES_ONLY;
		case 2: return RafRequestedFrameQualityEnum.ALL_FRAMES;
		}
		throw new IllegalArgumentException("Cannot recognize code for RAF RequestedFrameQuality: " + c);
	}
	
	public static RafRequestedFrameQualityEnum fromConfigurationString(String c) {
		if(c.equals("goodFramesOnly")) {
			return RafRequestedFrameQualityEnum.GOOD_FRAMES_ONLY;
		}
		if(c.equals("badFramesOnly")) {
			return RafRequestedFrameQualityEnum.BAD_FRAMES_ONLY;
		}
		if(c.equals("allFrames")) {
			return RafRequestedFrameQualityEnum.ALL_FRAMES;
		}
		throw new IllegalArgumentException("Cannot recognize code for RAF RequestedFrameQuality: " + c);
	}
}
