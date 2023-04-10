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

public class OperatingSystemConfigurationCheckerTest {
    private OperatingSystemConfigurationChecker checker;

    @Test
    public void testAllSatisfactory() {
        final List<OperatingSystemConfiguration> configurationClasses = getAllTestConfigurationClasses();
        checker = new OperatingSystemConfigurationChecker(configurationClasses);

        final List<OperatingSystemConfigurationCheckResult> results = checker.check();
        assertEquals(5, results.size());
        final List<OperatingSystemConfigurationCheckResult> failures = getFailures(results);
        assertEquals(0, failures.size());
    }

    @Test
    public void testAllFailuresEmptyFiles() {
        final List<OperatingSystemConfiguration> configurationClasses = new ArrayList<>();
        configurationClasses.add(new AvailablePorts(getTestFile("empty_file")));
        configurationClasses.add(new FileHandles(getTestFile("empty_file")));
        configurationClasses.add(new ForkedProcesses(getTestFile("empty_file")));
        configurationClasses.add(new Swappiness(getTestFile("empty_file")));
        configurationClasses.add(new TimedWaitDuration(getTestFile("empty_file")));
        checker = new OperatingSystemConfigurationChecker(configurationClasses);

        final List<OperatingSystemConfigurationCheckResult> results = checker.check();
        assertEquals(5, results.size());
        final List<OperatingSystemConfigurationCheckResult> failures = getFailures(results);
        assertEquals(5, failures.size());
        for (final OperatingSystemConfigurationCheckResult failure : failures) {
            assertEquals(1, failure.getExplanations().size());
            assertTrue(failure.getExplanations().get(0).contains("parse"));
        }
    }

    @Test
    public void testAllFailuresUnparsable() {
        final List<OperatingSystemConfiguration> configurationClasses = new ArrayList<>();
        configurationClasses.add(new AvailablePorts(getTestFile("unparsable")));
        configurationClasses.add(new FileHandles(getTestFile("unparsable")));
        configurationClasses.add(new ForkedProcesses(getTestFile("unparsable")));
        configurationClasses.add(new Swappiness(getTestFile("unparsable")));
        configurationClasses.add(new TimedWaitDuration(getTestFile("unparsable")));
        checker = new OperatingSystemConfigurationChecker(configurationClasses);

        final List<OperatingSystemConfigurationCheckResult> results = checker.check();
        assertEquals(5, results.size());
        final List<OperatingSystemConfigurationCheckResult> failures = getFailures(results);
        assertEquals(5, failures.size());
        for (final OperatingSystemConfigurationCheckResult failure : failures) {
            assertEquals(1, failure.getExplanations().size());
            assertTrue(failure.getExplanations().get(0).contains("parse"));
        }
    }

    @Test
    public void testCannotFindFilesForConfiguration() {
        final List<OperatingSystemConfiguration> configurationClasses = new ArrayList<>();
        configurationClasses.add(new AvailablePorts(new File("missing_file")));
        configurationClasses.add(new FileHandles(new File("missing_file")));
        configurationClasses.add(new ForkedProcesses(new File("missing_file")));
        configurationClasses.add(new Swappiness(new File("missing_file")));
        configurationClasses.add(new TimedWaitDuration(new File("missing_file")));
        checker = new OperatingSystemConfigurationChecker(configurationClasses);

        final List<OperatingSystemConfigurationCheckResult> results = checker.check();
        assertEquals(5, results.size());
        final List<OperatingSystemConfigurationCheckResult> failures = getFailures(results);
        assertEquals(5, failures.size());
        for (final OperatingSystemConfigurationCheckResult failure : failures) {
            assertEquals(1, failure.getExplanations().size());
            assertTrue(failure.getExplanations().get(0).contains("read"));
        }
    }

    private List<OperatingSystemConfigurationCheckResult> getFailures(final List<OperatingSystemConfigurationCheckResult> results) {
        return results
                .stream()
                .filter((result) -> !result.isSatisfactory())
                .collect(Collectors.toList());
    }

    private List<OperatingSystemConfiguration> getAllTestConfigurationClasses() {
        final List<OperatingSystemConfiguration> configurationClasses = new ArrayList<>();
        configurationClasses.add(new AvailablePorts(getTestFile("available_ports")));
        configurationClasses.add(new FileHandles(getTestFile("limits")));
        configurationClasses.add(new ForkedProcesses(getTestFile("limits")));
        configurationClasses.add(new Swappiness(getTestFile("swappiness")));
        configurationClasses.add(new TimedWaitDuration(getTestFile("tcp_tw_timeout")));
        return configurationClasses;
    }


    private File getTestFile(final String filename) {
        final ClassLoader classLoader = this.getClass().getClassLoader();
        final URL url = classLoader.getResource(filename);
        final File file = new File(url.getFile());
        return file;
    }
}
