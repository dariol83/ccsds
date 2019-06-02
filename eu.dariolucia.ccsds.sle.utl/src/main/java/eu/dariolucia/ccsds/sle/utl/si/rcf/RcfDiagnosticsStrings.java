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

package eu.dariolucia.ccsds.sle.utl.si.rcf;

import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.DiagnosticScheduleStatusReport;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.Diagnostics;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.structures.DiagnosticRcfGet;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.structures.DiagnosticRcfStart;

public class RcfDiagnosticsStrings {

	public static String getScheduleStatusReportDiagnostic(DiagnosticScheduleStatusReport negativeResult) {
		if (negativeResult.getCommon() != null) {
			return "COMMON - " + getCommonDiagnostics(negativeResult.getCommon().intValue());
		} else {
			return "SPECIFIC - " + getScheduleStatusReportSpecificDiagnostics(negativeResult.getSpecific().intValue());
		}
	}

	private static String getCommonDiagnostics(int intValue) {
		switch (intValue) {
		case 100:
			return "duplicateInvokeId";
		case 127:
			return "otherReason";
		}
		return "<unknown value> " + intValue;
	}

	private static String getScheduleStatusReportSpecificDiagnostics(int intValue) {
		switch (intValue) {
		case 0:
			return "notSupportedInThisDeliveryMode";
		case 1:
			return "alreadyStopped";
		case 2:
			return "invalidReportingCycle";
		}
		return "<unknown value> " + intValue;
	}

	public static String getDiagnostic(Diagnostics negativeResult) {
		return getCommonDiagnostics(negativeResult.intValue());
	}

	public static String getStartDiagnostic(DiagnosticRcfStart negativeResult) {
		if (negativeResult.getCommon() != null) {
			return "COMMON - " + getCommonDiagnostics(negativeResult.getCommon().intValue());
		} else {
			return "SPECIFIC - " + getStartSpecificDiagnostic(negativeResult.getSpecific().intValue());
		}
	}

	private static String getStartSpecificDiagnostic(int intValue) {
		switch (intValue) {
		case 0:
			return "outOfService";
		case 1:
			return "unableToComply";
		case 2:
			return "invalidStartTime";
		case 3:
			return "invalidStopTime";
		case 4:
			return "missingTimeValue";
		case 5:
			return "invalidGvcId";
		}
		return "<unknown value> " + intValue;
	}

	public static String getGetParameterDiagnostic(DiagnosticRcfGet negativeResult) {
		if (negativeResult.getCommon() != null) {
			return "COMMON - " + getCommonDiagnostics(negativeResult.getCommon().intValue());
		} else {
			return "SPECIFIC - " + getGetParameterSpecificDiagnostic(negativeResult.getSpecific().intValue());
		}
	}

	private static String getGetParameterSpecificDiagnostic(int intValue) {
		switch (intValue) {
		case 0:
			return "unknownParameter";
		}
		return "<unknown value> " + intValue;
	}

}
