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

package eu.dariolucia.ccsds.encdec.extension.internal;

import eu.dariolucia.ccsds.encdec.extension.*;

import java.util.Iterator;
import java.util.ServiceLoader;

public class ExtensionRegistry {

    private static ILengthMapper lengthMapper;

    public static ILengthMapper lengthMapper() {
        synchronized (ExtensionRegistry.class) {
            if (lengthMapper == null) {
                ServiceLoader<ILengthMapper> sl = ServiceLoader.load(ILengthMapper.class);
                lengthMapper = sl.findFirst().orElse(null);
            }
        }
        if (lengthMapper == null) {
            throw new IllegalStateException("Access to extension ILengthMapper required, but extension not found");
        } else {
            return lengthMapper;
        }
    }

    private static ITypeMapper typeMapper;

    public static ITypeMapper typeMapper() {
        synchronized (ExtensionRegistry.class) {
            if (typeMapper == null) {
                ServiceLoader<ITypeMapper> sl = ServiceLoader.load(ITypeMapper.class);
                typeMapper = sl.findFirst().orElse(null);
            }
        }
        if (typeMapper == null) {
            throw new IllegalStateException("Access to extension ITypeMapper required, but extension not found");
        } else {
            return typeMapper;
        }
    }

    public static IEncoderExtension extensionEncoder(String id) {
        return extension(IEncoderExtension.class, id);
    }

    public static IDecoderExtension extensionDecoder(String id) {
        return extension(IDecoderExtension.class, id);
    }

    private static <T> T extension(Class<T> clazz, String id) {
        ServiceLoader<T> loader = ServiceLoader.load(clazz);
        T ext = loader.stream()
                .filter(o -> o.type().getAnnotation(ExtensionId.class) != null) // only providers that are annotated
                .filter(o -> o.type().getAnnotation(ExtensionId.class).id().equals(id)) // only providers that have the annotation id matching with the required extension id
                .findFirst() // the first that you get
                .map(ServiceLoader.Provider::get) // you map it
                .orElse(null); // if none available, then null

        if (ext == null) {
            throw new IllegalStateException("Access to extension " + clazz.getSimpleName() + " with id " + id + " required, but extension not found");
        } else {
            return ext;
        }
        /*
        for (Iterator<T> it = loader.iterator(); it.hasNext(); ) {
            T object = it.next();
            ExtensionId annotatedId = object.getClass().getAnnotation(ExtensionId.class);
            if (annotatedId != null && annotatedId.id().equals(id)) {
                return object;
            }
        }
        throw new IllegalStateException("Access to extension " + clazz.getSimpleName() + " with id " + id + " required, but extension not found");
        */
    }

}
