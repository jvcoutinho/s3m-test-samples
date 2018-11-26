/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit.result;

import org.gradle.api.Action;
import org.gradle.api.tasks.testing.TestOutputEvent;

import java.io.File;
import java.io.Writer;

public class BinaryResultBackedTestResultsProvider implements TestResultsProvider {
    private final File resultsDir;
    private final TestOutputStore.Reader outputReader;
    private final TestResultSerializer resultSerializer = new TestResultSerializer();

    public BinaryResultBackedTestResultsProvider(File resultsDir, TestOutputStore.Reader outputReader) {
        this.resultsDir = resultsDir;
        this.outputReader = outputReader;
    }

    public boolean hasOutput(String className, TestOutputEvent.Destination destination) {
        return outputReader.hasOutput(className, destination);
    }

    public void writeAllOutput(String className, TestOutputEvent.Destination destination, Writer writer) {
        outputReader.writeAllOutput(className, destination, writer);
    }

    public void writeNonTestOutput(String className, TestOutputEvent.Destination destination, Writer writer) {
        outputReader.writeNonTestOutput(className, destination, writer);
    }

    public void writeTestOutput(String className, Object testId, TestOutputEvent.Destination destination, Writer writer) {
        outputReader.writeTestOutput(className, testId, destination, writer);
    }

    public void visitClasses(final Action<? super TestClassResult> visitor) {
        resultSerializer.read(resultsDir, visitor);
    }
}
