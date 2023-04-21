/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.bootstrap.process;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RuntimeValidatorCheckerTest {
    private RuntimeValidatorChecker checker;

    @Test
    public void testAllSatisfactory() {
        final List<RuntimeValidator> configurationClasses = getAllTestConfigurationClasses();
        checker = new RuntimeValidatorChecker(configurationClasses);

        final List<RuntimeValidatorResult> results = checker.check();
        assertEquals(5, results.size());
        final List<RuntimeValidatorResult> failures = getFailures(results);
        assertEquals(0, failures.size());
    }

    @Test
    public void testAllFailuresEmptyFiles() {
        final List<RuntimeValidator> configurationClasses = new ArrayList<>();
        configurationClasses.add(new AvailablePorts(getTestFile("empty_file")));
        configurationClasses.add(new FileHandles(getTestFile("empty_file")));
        configurationClasses.add(new ForkedProcesses(getTestFile("empty_file")));
        configurationClasses.add(new Swappiness(getTestFile("empty_file")));
        configurationClasses.add(new TimedWaitDuration(getTestFile("empty_file")));
        checker = new RuntimeValidatorChecker(configurationClasses);

        final List<RuntimeValidatorResult> results = checker.check();
        assertEquals(5, results.size());
        final List<RuntimeValidatorResult> failures = getFailures(results);
        assertEquals(5, failures.size());
        for (final RuntimeValidatorResult failure : failures) {
            assertTrue(failure.getExplanation().contains("parse"));
        }
    }

    @Test
    public void testAllFailuresUnparsable() {
        final List<RuntimeValidator> configurationClasses = new ArrayList<>();
        configurationClasses.add(new AvailablePorts(getTestFile("unparsable")));
        configurationClasses.add(new FileHandles(getTestFile("unparsable")));
        configurationClasses.add(new ForkedProcesses(getTestFile("unparsable")));
        configurationClasses.add(new Swappiness(getTestFile("unparsable")));
        configurationClasses.add(new TimedWaitDuration(getTestFile("unparsable")));
        checker = new RuntimeValidatorChecker(configurationClasses);

        final List<RuntimeValidatorResult> results = checker.check();
        assertEquals(5, results.size());
        final List<RuntimeValidatorResult> failures = getFailures(results);
        assertEquals(5, failures.size());
        for (final RuntimeValidatorResult failure : failures) {
            assertTrue(failure.getExplanation().contains("parse"));
        }
    }

    @Test
    public void testCannotFindFilesForConfiguration() {
        final List<RuntimeValidator> configurationClasses = new ArrayList<>();
        configurationClasses.add(new AvailablePorts(new File("missing_file")));
        configurationClasses.add(new FileHandles(new File("missing_file")));
        configurationClasses.add(new ForkedProcesses(new File("missing_file")));
        configurationClasses.add(new Swappiness(new File("missing_file")));
        configurationClasses.add(new TimedWaitDuration(new File("missing_file")));
        checker = new RuntimeValidatorChecker(configurationClasses);

        final List<RuntimeValidatorResult> results = checker.check();
        assertEquals(5, results.size());
        final List<RuntimeValidatorResult> skipped = getSkipped(results);
        assertEquals(5, skipped.size());
        for (final RuntimeValidatorResult result : skipped) {
            assertTrue(result.getExplanation().contains("read"));
        }
    }

    @Test
    public void testNotEnoughAvailablePorts() {
        final List<RuntimeValidator> configurationClasses = new ArrayList<>();
        configurationClasses.add(new AvailablePorts(getTestFile("available_ports_not_enough")));
        checker = new RuntimeValidatorChecker(configurationClasses);

        final List<RuntimeValidatorResult> results = checker.check();
        assertEquals(1, results.size());
        final List<RuntimeValidatorResult> failures = getFailures(results);
        assertEquals(1, failures.size());
        for (final RuntimeValidatorResult failure : failures) {
            assertTrue(failure.getExplanation().contains("less than"));
        }
    }

    @Test
    public void testNotEnoughFileHandlesAndForkedProcesses() {
        final List<RuntimeValidator> configurationClasses = new ArrayList<>();
        configurationClasses.add(new FileHandles(getTestFile("limits_not_enough")));
        configurationClasses.add(new ForkedProcesses(getTestFile("limits_not_enough")));
        checker = new RuntimeValidatorChecker(configurationClasses);

        final List<RuntimeValidatorResult> results = checker.check();
        assertEquals(4, results.size());
        final List<RuntimeValidatorResult> failures = getFailures(results);
        assertEquals(4, failures.size());
        for (final RuntimeValidatorResult failure : failures) {
            assertTrue(failure.getExplanation().contains("less than"));
        }
    }

    @Test
    public void testHighSwappiness() {
        final List<RuntimeValidator> configurationClasses = new ArrayList<>();
        configurationClasses.add(new Swappiness(getTestFile("swappiness_high")));
        checker = new RuntimeValidatorChecker(configurationClasses);

        final List<RuntimeValidatorResult> results = checker.check();
        assertEquals(1, results.size());
        final List<RuntimeValidatorResult> failures = getFailures(results);
        assertEquals(1, failures.size());
        for (final RuntimeValidatorResult failure : failures) {
            assertTrue(failure.getExplanation().contains("more than"));
        }
    }

    @Test
    public void testHighTimedWaitDuration () {
        final List<RuntimeValidator> configurationClasses = new ArrayList<>();
        configurationClasses.add(new TimedWaitDuration(getTestFile("tcp_tw_timeout_high")));
        checker = new RuntimeValidatorChecker(configurationClasses);

        final List<RuntimeValidatorResult> results = checker.check();
        assertEquals(1, results.size());
        final List<RuntimeValidatorResult> failures = getFailures(results);
        assertEquals(1, failures.size());
        for (final RuntimeValidatorResult failure : failures) {
            assertTrue(failure.getExplanation().contains("more than"));
        }
    }

    private List<RuntimeValidatorResult> getFailures(final List<RuntimeValidatorResult> results) {
        return results
                .stream()
                .filter((result) -> result.getOutcome().equals(RuntimeValidatorResult.Outcome.FAILED))
                .collect(Collectors.toList());
    }

    private List<RuntimeValidatorResult> getSkipped(final List<RuntimeValidatorResult> results) {
        return results
                .stream()
                .filter((result) -> result.getOutcome().equals(RuntimeValidatorResult.Outcome.SKIPPED))
                .collect(Collectors.toList());
    }

    private File getTestFile(final String filename) {
        final ClassLoader classLoader = this.getClass().getClassLoader();
        final URL url = classLoader.getResource(filename);
        final File file = new File(url.getFile());
        return file;
    }

    private List<RuntimeValidator> getAllTestConfigurationClasses() {
        final List<RuntimeValidator> configurationClasses = new ArrayList<>();
        configurationClasses.add(new AvailablePorts(getTestFile("available_ports")));
        configurationClasses.add(new FileHandles(getTestFile("limits")));
        configurationClasses.add(new ForkedProcesses(getTestFile("limits")));
        configurationClasses.add(new Swappiness(getTestFile("swappiness")));
        configurationClasses.add(new TimedWaitDuration(getTestFile("tcp_tw_timeout")));
        return configurationClasses;
    }
}
