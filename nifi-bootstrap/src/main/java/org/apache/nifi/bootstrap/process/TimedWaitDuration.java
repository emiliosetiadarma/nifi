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

public class TimedWaitDuration implements OperatingSystemConfiguration {
    private static final String[] POSSIBLE_DIRECTORIES = new String[] {
            "/proc/sys/net/ipv4/tcp_tw_timeout",
            "/proc/sys/net/netfilter/nf_conntrack_tcp_timeout_time_wait",
            "/proc/sys/net/ipv4/netfilter/ip_conntrack_tcp_timeout_time_wait"
    };
    private static final String DIGITS_REGEX = "\\d+";
    private static final Pattern PATTERN = Pattern.compile(DIGITS_REGEX);
    private static final int DESIRED_TIMED_WAIT_DURATION = 1;

    private File configurationFile;

    public TimedWaitDuration() {
    }

    TimedWaitDuration(final File configurationFile) {
        this.configurationFile = configurationFile;
    }

    @Override
    public OperatingSystemConfigurationCheckResult check() {
        final OperatingSystemConfigurationCheckResult.Builder resultBuilder = new OperatingSystemConfigurationCheckResult.Builder()
                .subject(this.getClass().getName())
                .satisfactory(true);

        if (configurationFile == null) {
            determineConfigurationFile();
            if (configurationFile == null) {
                return resultBuilder
                        .satisfactory(false)
                        .explanation("Configuration file for TimedWaitDuration cannot be read")
                        .build();
            }
        }

        if (!configurationFile.canRead()) {
            return resultBuilder
                    .satisfactory(false)
                    .explanation(String.format("Do not have read permissions for configuration file [%s]", configurationFile.getAbsolutePath()))
                    .build();
        }
        try {
            final String timedWaitDurationString = new String(Files.readAllBytes(configurationFile.toPath()));
            final Matcher matcher = PATTERN.matcher(timedWaitDurationString);
            if (matcher.find()) {
                final int timedWaitDuration = Integer.valueOf(matcher.group());
                if (timedWaitDuration > DESIRED_TIMED_WAIT_DURATION) {
                    resultBuilder
                            .satisfactory(false)
                            .explanation(String.format("Timed wait duration [%ds] is higher than the advised timed wait duration [%ds]", timedWaitDuration, DESIRED_TIMED_WAIT_DURATION));
                }
            } else {
                resultBuilder
                        .satisfactory(false)
                        .explanation(String.format("Configuration file [%s] cannot be parsed", configurationFile.getAbsolutePath()));
            }
        } catch (final IOException e) {
            resultBuilder
                    .satisfactory(false)
                    .explanation(String.format("Configuration file [%s] cannot be read", configurationFile.getAbsolutePath()));
        }
        return resultBuilder.build();
    }

    private void determineConfigurationFile() {
        for (final String directory: POSSIBLE_DIRECTORIES) {
            final File file = new File(directory);
            if (file.canRead()) {
                configurationFile = file;
                return;
            }
        }

    }
}
