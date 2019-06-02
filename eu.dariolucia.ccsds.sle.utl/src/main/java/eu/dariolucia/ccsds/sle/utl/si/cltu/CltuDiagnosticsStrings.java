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

import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.DiagnosticCltuGetParameter;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.DiagnosticCltuStart;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.DiagnosticCltuThrowEvent;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.DiagnosticCltuTransferData;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.DiagnosticScheduleStatusReport;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.Diagnostics;

public class CltuDiagnosticsStrings {

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

	public static String getStartDiagnostic(DiagnosticCltuStart negativeResult) {
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
			return "productionTimeExpired";
		case 3:
			return "invalidCltu";
		}
		return "<unknown value> " + intValue;
	}

	public static String getGetParameterDiagnostic(DiagnosticCltuGetParameter negativeResult) {
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

	public static String getThrowEventDiagnostic(DiagnosticCltuThrowEvent negativeResult) {
		if (negativeResult.getCommon() != null) {
			return "COMMON - " + getCommonDiagnostics(negativeResult.getCommon().intValue());
		} else {
			return "SPECIFIC - " + getThrowEventSpecificDiagnostic(negativeResult.getSpecific().intValue());
		}
	}

	private static String getThrowEventSpecificDiagnostic(int intValue) {
		switch (intValue) {
		case 0:
			return "operationNotSupported";
		case 1:
			return "eventInvocIdOutOfSequence";
		case 2:
			return "noSuchEvent";
		}
		return "<unknown value> " + intValue;
	}

	public static String getTransferDataDiagnostic(DiagnosticCltuTransferData negativeResult) {
		if (negativeResult.getCommon() != null) {
			return "COMMON - " + getCommonDiagnostics(negativeResult.getCommon().intValue());
		} else {
			return "SPECIFIC - " + getTransferDataSpecificDiagnostic(negativeResult.getSpecific().intValue());
		}
	}

	private static String getTransferDataSpecificDiagnostic(int intValue) {
		switch (intValue) {
		case 0:
			return "unableToProcess";
		case 1:
			return "unableToStore";
		case 2:
			return "outOfSequence";
		case 3:
			return "inconsistentTimeRange";
		case 4:
			return "invalidTime";
		case 5:
			return "lateSldu";
		case 6:
			return "invalidDelayTime";
		case 7:
			return "cltuError";
		}
		return "<unknown value> " + intValue;
	}
}
