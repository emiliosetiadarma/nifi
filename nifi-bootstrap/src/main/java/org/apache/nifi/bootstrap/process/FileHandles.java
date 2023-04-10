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

public class FileHandles implements OperatingSystemConfiguration {
    private static final String DIRECTORY = String.format("/proc/%s/limits", ProcessHandle.current().pid());
    private static final String MAX_OPEN_FILES_REGEX = "Max open files\\s+(\\d+)\\s+(\\d+)\\s+files\\s+";
    private static final Pattern PATTERN = Pattern.compile(MAX_OPEN_FILES_REGEX);
    private static final int DESIRED_SOFT_LIMIT = 50000;
    private static final int DESIRED_HARD_LIMIT = 50000;

    private final File configurationFile;

    public FileHandles() {
        configurationFile = new File(DIRECTORY);
    }

    FileHandles(final File configurationFile) {
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
            final String fileHandlesString = new String(Files.readAllBytes(configurationFile.toPath()));
            final Matcher matcher = PATTERN.matcher(fileHandlesString);
            if (matcher.find()) {
                final int softLimit = Integer.valueOf(matcher.group(1));
                final int hardLimit = Integer.valueOf(matcher.group(2));
                if (softLimit < DESIRED_SOFT_LIMIT) {
                    resultBuilder
                            .satisfactory(false)
                            .explanation(String.format("Soft limit for file handles [%d] is less than desired soft limit [%d]", softLimit, DESIRED_SOFT_LIMIT));
                }
                if (hardLimit < DESIRED_HARD_LIMIT) {
                    resultBuilder
                            .satisfactory(false)
                            .explanation(String.format("Hard limit for file handles [%d] is less than desired hard limit [%d]", hardLimit, DESIRED_HARD_LIMIT));
                }
            } else {
                resultBuilder
                        .satisfactory(false)
                        .explanation(String.format("Configuration file [%s] cannot be parsed", DIRECTORY));
            }
        } catch (final IOException e) {
            resultBuilder
                    .satisfactory(false)
                    .explanation(String.format("Configuration file [%s] cannot be read", DIRECTORY));
        }
        return resultBuilder.build();
    }
}
