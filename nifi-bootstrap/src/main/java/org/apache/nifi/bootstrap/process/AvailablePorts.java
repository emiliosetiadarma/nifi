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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AvailablePorts implements OperatingSystemConfiguration {
    private static final String DIRECTORY = "/proc/sys/net/ipv4/ip_local_port_range";
    private static final String DIGITS_REGEX = "(\\d+)\\s+(\\d+)";
    private static final Pattern PATTERN = Pattern.compile(DIGITS_REGEX);
    private static final int DESIRED_AVAILABLE_PORTS = 55000;

    private final File configurationFile;

    public AvailablePorts() {
        configurationFile = new File(DIRECTORY);
    }

    AvailablePorts(final File configurationFile) {
        this.configurationFile = configurationFile;
    }

    @Override
    public OperatingSystemConfigurationCheckResult check() {
        final OperatingSystemConfigurationCheckResult.Builder resultBuilder = new OperatingSystemConfigurationCheckResult.Builder()
                .subject(this.getClass().getName())
                .satisfactory(true);
        if (configurationFile == null) {
            return resultBuilder
                    .satisfactory(false)
                    .explanation("Configuration file is null")
                    .build();
        }
        if (!configurationFile.canRead()) {
            return resultBuilder
                    .satisfactory(false)
                    .explanation(String.format("Configuration file [%s] cannot be read", configurationFile.getAbsolutePath()))
                    .build();
        }

        try {
            final String portRangeString = new String(Files.readAllBytes(configurationFile.toPath()));
            final Matcher matcher = PATTERN.matcher(portRangeString);
            if (matcher.find()) {
                final int lowerPort = Integer.valueOf(matcher.group(1));
                final int higherPort = Integer.valueOf(matcher.group(2));
                final int availablePorts = higherPort - lowerPort;
                if (availablePorts < DESIRED_AVAILABLE_PORTS) {
                    resultBuilder
                            .satisfactory(false)
                            .explanation(String.format("Number of available ports [%d] is less than the desired number of available ports [%d]", availablePorts, DESIRED_AVAILABLE_PORTS));
                }
            } else {
                resultBuilder
                        .satisfactory(false)
                        .explanation(String.format("Configuration file [%s] cannot be parsed", configurationFile.getAbsolutePath()));
                return resultBuilder.build();
            }
        } catch (final IOException e) {
            resultBuilder
                    .satisfactory(false)
                    .explanation(String.format("Configuration file [%s] cannot be read", configurationFile.getAbsolutePath()));
        }
        return resultBuilder.build();
    }
}
