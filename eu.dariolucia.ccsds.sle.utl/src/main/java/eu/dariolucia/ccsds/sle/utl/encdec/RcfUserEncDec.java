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

package eu.dariolucia.ccsds.sle.utl.encdec;

import com.beanit.jasn1.ber.types.BerType;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleBindInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleBindReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleUnbindInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleUnbindReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleScheduleStatusReportInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleStopInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.incoming.pdus.RcfGetParameterInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.incoming.pdus.RcfStartInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.incoming.pdus.RcfUserToProviderPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.outgoing.pdus.RcfProviderToUserPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.outgoing.pdus.RcfProviderToUserPduV1;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.outgoing.pdus.RcfProviderToUserPduV2toV4;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * RCF encoding/decoding extension class.
 */
public class RcfUserEncDec extends CommonEncDec {

    private final List<Function<RcfProviderToUserPduV1, BerType>> unwrapFunctionV1List;
    private final List<Function<RcfProviderToUserPduV2toV4, BerType>> unwrapFunctionV2V4List;
    private final List<Function<RcfProviderToUserPdu, BerType>> unwrapFunctionV5List;

    public RcfUserEncDec() {
        register(1, RcfProviderToUserPduV1::new);
        register(2, RcfProviderToUserPduV2toV4::new);
        register(3, RcfProviderToUserPduV2toV4::new);
        register(4, RcfProviderToUserPduV2toV4::new);
        register(5, RcfProviderToUserPdu::new);

        // V1 unwrappers
        unwrapFunctionV1List = new ArrayList<>();
        unwrapFunctionV1List.add(RcfProviderToUserPduV1::getRcfTransferBuffer);
        unwrapFunctionV1List.add(RcfProviderToUserPduV1::getRcfStatusReportInvocation);
        unwrapFunctionV1List.add(RcfProviderToUserPduV1::getRcfGetParameterReturn);
        unwrapFunctionV1List.add(RcfProviderToUserPduV1::getRcfScheduleStatusReportReturn);
        unwrapFunctionV1List.add(RcfProviderToUserPduV1::getRcfBindInvocation);
        unwrapFunctionV1List.add(RcfProviderToUserPduV1::getRcfBindReturn);
        unwrapFunctionV1List.add(RcfProviderToUserPduV1::getRcfUnbindInvocation);
        unwrapFunctionV1List.add(RcfProviderToUserPduV1::getRcfUnbindReturn);
        unwrapFunctionV1List.add(RcfProviderToUserPduV1::getRcfStartReturn);
        unwrapFunctionV1List.add(RcfProviderToUserPduV1::getRcfStopReturn);

        // V2 V4 unwrappers
        unwrapFunctionV2V4List = new ArrayList<>();
        unwrapFunctionV2V4List.add(RcfProviderToUserPduV2toV4::getRcfTransferBuffer);
        unwrapFunctionV2V4List.add(RcfProviderToUserPduV2toV4::getRcfStatusReportInvocation);
        unwrapFunctionV2V4List.add(RcfProviderToUserPduV2toV4::getRcfGetParameterReturn);
        unwrapFunctionV2V4List.add(RcfProviderToUserPduV2toV4::getRcfScheduleStatusReportReturn);
        unwrapFunctionV2V4List.add(RcfProviderToUserPduV2toV4::getRcfBindInvocation);
        unwrapFunctionV2V4List.add(RcfProviderToUserPduV2toV4::getRcfBindReturn);
        unwrapFunctionV2V4List.add(RcfProviderToUserPduV2toV4::getRcfUnbindInvocation);
        unwrapFunctionV2V4List.add(RcfProviderToUserPduV2toV4::getRcfUnbindReturn);
        unwrapFunctionV2V4List.add(RcfProviderToUserPduV2toV4::getRcfStartReturn);
        unwrapFunctionV2V4List.add(RcfProviderToUserPduV2toV4::getRcfStopReturn);

        // V5 unwrappers
        unwrapFunctionV5List = new ArrayList<>();
        unwrapFunctionV5List.add(RcfProviderToUserPdu::getRcfTransferBuffer);
        unwrapFunctionV5List.add(RcfProviderToUserPdu::getRcfStatusReportInvocation);
        unwrapFunctionV5List.add(RcfProviderToUserPdu::getRcfGetParameterReturn);
        unwrapFunctionV5List.add(RcfProviderToUserPdu::getRcfScheduleStatusReportReturn);
        unwrapFunctionV5List.add(RcfProviderToUserPdu::getRcfBindInvocation);
        unwrapFunctionV5List.add(RcfProviderToUserPdu::getRcfBindReturn);
        unwrapFunctionV5List.add(RcfProviderToUserPdu::getRcfUnbindInvocation);
        unwrapFunctionV5List.add(RcfProviderToUserPdu::getRcfUnbindReturn);
        unwrapFunctionV5List.add(RcfProviderToUserPdu::getRcfStartReturn);
        unwrapFunctionV5List.add(RcfProviderToUserPdu::getRcfStopReturn);
    }

    @Override
    protected Supplier<BerType> getDefaultDecodingProvider() {
        return RcfProviderToUserPdu::new;
    }

    @Override
    protected BerType wrapPdu(BerType toEncode) throws EncodingException {
        RcfUserToProviderPdu wrapper = new RcfUserToProviderPdu();
        if (toEncode instanceof SleBindInvocation) {
            wrapper.setRcfBindInvocation((SleBindInvocation) toEncode);
        } else if (toEncode instanceof SleUnbindInvocation) {
            wrapper.setRcfUnbindInvocation((SleUnbindInvocation) toEncode);
        } else if (toEncode instanceof SleUnbindReturn) {
            wrapper.setRcfUnbindReturn((SleUnbindReturn) toEncode);
        } else if (toEncode instanceof SleBindReturn) {
            wrapper.setRcfBindReturn((SleBindReturn) toEncode);
        } else if (toEncode instanceof SleScheduleStatusReportInvocation) {
            wrapper.setRcfScheduleStatusReportInvocation((SleScheduleStatusReportInvocation) toEncode);
        } else if (toEncode instanceof RcfStartInvocation) {
            wrapper.setRcfStartInvocation((RcfStartInvocation) toEncode);
        } else if (toEncode instanceof SleStopInvocation) {
            wrapper.setRcfStopInvocation((SleStopInvocation) toEncode);
        } else if (toEncode instanceof RcfGetParameterInvocation) {
            wrapper.setRcfGetParameterInvocation((RcfGetParameterInvocation) toEncode);
        } else {
            throw new EncodingException("Type " + toEncode + " not supported by encoder " + getClass().getSimpleName());
        }
        return wrapper;
    }

    @Override
    protected BerType unwrapPdu(BerType toDecode) throws DecodingException {
        switch (getVersion()) {
            case 1:
                return returnOrThrow(this.unwrapFunctionV1List.parallelStream().map(o -> o.apply((RcfProviderToUserPduV1) toDecode)).filter(Objects::nonNull).findFirst(), toDecode);
            case 2:
            case 3:
            case 4:
                return returnOrThrow(this.unwrapFunctionV2V4List.parallelStream().map(o -> o.apply((RcfProviderToUserPduV2toV4) toDecode)).filter(Objects::nonNull).findFirst(), toDecode);
            default:
                return returnOrThrow(this.unwrapFunctionV5List.parallelStream().map(o -> o.apply((RcfProviderToUserPdu) toDecode)).filter(Objects::nonNull).findFirst(), toDecode);
        }
    }
}
