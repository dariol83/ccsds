/*
 *   Copyright (c) 2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package eu.dariolucia.ccsds.encdec.definition;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import java.util.Objects;

/**
 * This class defines how the generation time linked to associated encoded parameter must be computed.
 * The field absoluteTimeReference (absolute_ref) contains the name of the encoded parameter (type AbsoluteTime), which contains the
 * generation time. If not present, the generation time shall be provided by other means.
 * The field relativeTimeReference (offset_ref) contains the name of the encoded parameter (of type RelativeTime), which contains the
 * offset to be applied by a reference generation time. If the absoluteTimeReference is specified, then the offset is
 * applied to this time. Otherwise the reference generation time shall be provided by other means.
 * The field offset (offset) indicates a fixed number of milliseconds (positive or negative) in terms of generation
 * time offset.
 *
 * How the information is combined to obtain the final generation time is not part of this specification. An implementation
 * of the {@link eu.dariolucia.ccsds.encdec.time.IGenerationTimeProcessor} must be provided to the {@link eu.dariolucia.ccsds.encdec.structure.IPacketDecoder}
 * in order to enable the generation time derivation process.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class GenerationTime {

    @XmlAttribute(name = "offset_ref")
    private String relativeTimeReference = null;

    @XmlAttribute(name = "absolute_ref")
    private String absoluteTimeReference = null;

    @XmlAttribute(name="offset")
    private Integer offset = 0;

    public GenerationTime() {
    }

    public GenerationTime(String absoluteTimeReference, String relativeTimeReference, Integer offset) {
        this.relativeTimeReference = relativeTimeReference;
        this.absoluteTimeReference = absoluteTimeReference;
        this.offset = offset;
    }

    public String getRelativeTimeReference() {
        return relativeTimeReference;
    }

    public void setRelativeTimeReference(String relativeTimeReference) {
        this.relativeTimeReference = relativeTimeReference;
    }

    public String getAbsoluteTimeReference() {
        return absoluteTimeReference;
    }

    public void setAbsoluteTimeReference(String absoluteTimeReference) {
        this.absoluteTimeReference = absoluteTimeReference;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GenerationTime that = (GenerationTime) o;
        return Objects.equals(relativeTimeReference, that.relativeTimeReference) &&
                Objects.equals(absoluteTimeReference, that.absoluteTimeReference) &&
                Objects.equals(offset, that.offset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relativeTimeReference, absoluteTimeReference, offset);
    }
}
