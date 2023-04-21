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

public class RuntimeValidatorChecker {
    private final List<RuntimeValidator> configurationClasses;
    private static final Logger logger = LoggerFactory.getLogger(RuntimeValidatorChecker.class);

    public RuntimeValidatorChecker() {
        this.configurationClasses = Arrays.asList(
                new AvailablePorts(),
                new FileHandles(),
                new ForkedProcesses(),
                new Swappiness(),
                new TimedWaitDuration()
        );
    }

    RuntimeValidatorChecker(final List<RuntimeValidator> configurationClasses) {
        this.configurationClasses = configurationClasses;
    }

    /**
     * Checks all the system configuration settings that are supported to be checked
     */
    public List<RuntimeValidatorResult> check() {
        final List<RuntimeValidatorResult> results = new ArrayList<>();
        for (final RuntimeValidator configuration: configurationClasses) {
            results.addAll(configuration.check());
        }
        final List<RuntimeValidatorResult> failures = results
                .stream()
                .filter((result) -> !result.isSatisfactory())
                .collect(Collectors.toList());
        if (!failures.isEmpty()) {
            logWarnings(failures);
        }

        return results;
    }

    private void logWarnings(final List<RuntimeValidatorResult> results) {
        for (final RuntimeValidatorResult result : results) {
            logger.warn("Configuration [{}] not satisfactory due to: {}", result.getSubject(), result.getExplanation());
        }
    }
}
