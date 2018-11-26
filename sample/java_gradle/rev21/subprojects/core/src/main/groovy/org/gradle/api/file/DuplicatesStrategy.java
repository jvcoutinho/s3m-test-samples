/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.file;

/**
 * The possible strategies for handling files with the same relative
 * path during a copy (or archive) operation.
 *
 * @author Kyle Mahan
 */
public enum DuplicatesStrategy {

    /**
     * FileCopyDetails should use the strategy of its CopySpec, and CopySpec
     * should use the strategy of its parent. If no DuplicatesStrategy is
     * explicitly defined for any parent, this will act as 'include'.
     */
    INHERIT,

    /**
     * Files with the same relative path should be included. For Copy
     * operations this will generate a warning.
     */
    INCLUDE,

    /**
     * Only the first file with a given relative path will be
     * included.
     */
    EXCLUDE;

    /**
     * Convert a string in the form 'include'/'exclude' to a DuplicatesStrategy
     * @param str the string to convert
     * @return a DuplicatesStrategy
     */
    public static DuplicatesStrategy fromString(String str) {
        return str == null ? INHERIT : valueOf(str.toUpperCase());
    }

}
