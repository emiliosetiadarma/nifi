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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Swappiness implements RuntimeValidator {
    private static final String DIRECTORY = "/proc/sys/vm/swappiness";
    private static final String DIGITS_REGEX = "\\d+";
    private static final Pattern PATTERN = Pattern.compile(DIGITS_REGEX);
    private static final int DESIRED_SWAPPINESS = 0;
    private static final Logger logger = LoggerFactory.getLogger(Swappiness.class);

    private final File configurationFile;

    public Swappiness() {
        configurationFile = new File(DIRECTORY);
    }

    Swappiness(final File configurationFile) {
        this.configurationFile = configurationFile;
    }

    @Override
    public List<RuntimeValidatorResult> check() {
        final List<RuntimeValidatorResult> results = new ArrayList<>();
        if (configurationFile == null) {
            final RuntimeValidatorResult result = new RuntimeValidatorResult.Builder()
                    .subject(this.getClass().getName())
                    .outcome(RuntimeValidatorResult.Outcome.SKIPPED)
                    .explanation("Configuration file is null")
                    .build();
            results.add(result);
            return results;
        }
        if (!configurationFile.canRead()) {
            final RuntimeValidatorResult result = new RuntimeValidatorResult.Builder()
                    .subject(this.getClass().getName())
                    .outcome(RuntimeValidatorResult.Outcome.SKIPPED)
                    .explanation(String.format("Configuration file [%s] cannot be read", configurationFile.getAbsolutePath()))
                    .build();
            results.add(result);
            return results;
        }

        try {
            final String swappinessString = new String(Files.readAllBytes(configurationFile.toPath()));
            logger.warn("Read file: {}", swappinessString);
            final Matcher matcher = PATTERN.matcher(swappinessString);
            if (matcher.find()) {
                final int swappiness = Integer.valueOf(matcher.group());
                if (swappiness > DESIRED_SWAPPINESS) {
                    final RuntimeValidatorResult result =  new RuntimeValidatorResult.Builder()
                            .subject(this.getClass().getName())
                            .outcome(RuntimeValidatorResult.Outcome.FAILED)
                            .explanation(String.format("Swappiness [%d] is more than the desired swappiness [%d]", swappiness, DESIRED_SWAPPINESS))                            .build();
                    results.add(result);
                }
            } else {
                final RuntimeValidatorResult result = new RuntimeValidatorResult.Builder()
                        .subject(this.getClass().getName())
                        .outcome(RuntimeValidatorResult.Outcome.FAILED)
                        .explanation(String.format("Configuration file [%s] cannot be parsed", configurationFile.getAbsolutePath()))
                        .build();
                results.add(result);
            }
        } catch (final IOException e) {
            final RuntimeValidatorResult result = new RuntimeValidatorResult.Builder()
                    .subject(this.getClass().getName())
                    .outcome(RuntimeValidatorResult.Outcome.FAILED)
                    .explanation(String.format("Configuration file [%s] cannot be read", configurationFile.getAbsolutePath()))
                    .build();
            results.add(result);
        }

        if (results.isEmpty()) {
            final RuntimeValidatorResult result = new RuntimeValidatorResult.Builder()
                    .subject(this.getClass().getName())
                    .outcome(RuntimeValidatorResult.Outcome.SUCCESSFUL)
                    .build();
            results.add(result);
        }
        return results;
    }
}
