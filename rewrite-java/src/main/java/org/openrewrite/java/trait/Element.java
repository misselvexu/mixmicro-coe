/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.trait;

import org.openrewrite.internal.lang.Nullable;

import java.util.UUID;

public interface Element {
    String getName();

    /**
     * @return An identifier that can be used to uniquely identify this element. Used for equality checking.
     */
    UUID getId();

    static boolean equals(Element e1, @Nullable Object other) {
        if (e1 == other) return true;
        if (other == null || e1.getClass() != other.getClass()) return false;
        Element e2 = (Element) other;
        return e1.getId().equals(e2.getId());
    }
}
