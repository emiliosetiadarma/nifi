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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class OperatingSystemConfigurationChecker {
    private final List<OperatingSystemConfiguration> configurationClasses;
    private static final Logger logger = LoggerFactory.getLogger(OperatingSystemConfigurationChecker.class);

    public OperatingSystemConfigurationChecker() {
        this.configurationClasses = Arrays.asList(
                new AvailablePorts(),
                new FileHandles(),
                new ForkedProcesses(),
                new Swappiness(),
                new TimedWaitDuration()
        );
    }

    OperatingSystemConfigurationChecker(final List<OperatingSystemConfiguration> configurationClasses) {
        this.configurationClasses = configurationClasses;
    }

    /**
     * Checks all the system configuration settings that are supported to be checked
     */
    public List<OperatingSystemConfigurationCheckResult> check() {
        final List<OperatingSystemConfigurationCheckResult> results = new ArrayList<>();
        for (final OperatingSystemConfiguration configuration: configurationClasses) {
            results.add(configuration.check());
        }
        final List<OperatingSystemConfigurationCheckResult> failures = results
                .stream()
                .filter(OperatingSystemConfigurationCheckResult::isSatisfactory)
                .collect(Collectors.toList());
        if (!failures.isEmpty()) {
            logWarnings(failures);
        }

        return results;
    }

    private void logWarnings(final List<OperatingSystemConfigurationCheckResult> results) {
        for (final OperatingSystemConfigurationCheckResult result : results) {
            for (final String explanation : result.getExplanations()) {
                logger.warn("Configuration [{}] not satisfactory due to: {}", result.getSubject(), explanation);
            }
        }
    }
}
