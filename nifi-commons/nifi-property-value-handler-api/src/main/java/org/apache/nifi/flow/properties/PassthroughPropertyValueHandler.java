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
package org.apache.nifi.flow.properties;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is a mock Property Value Handler that does no actual encryption/decryption, but encode/decode respectively
 * encapsulates into or remove values with the "enc{value}" format.
 */
public class PassthroughPropertyValueHandler implements PropertyValueHandler {
    private static final String REGEX_STRING = "enc\\{([^\\}]+)\\}";
    private static final Pattern PATTERN = Pattern.compile("^" + REGEX_STRING + "$");
    private static final String ENCRYPTED_FORMAT = "enc{%s}";

    public PassthroughPropertyValueHandler() {}

    @Override
    public void onConfigured(final PropertyValueHandlerConfigurationContext context) {}

    @Override
    public boolean isEncoded(final String value) {
        final Matcher matcher = PATTERN.matcher(value);
        return matcher.matches();
    }

    @Override
    public String encode(final String value) {
        return wrap(value);
    }

    @Override
    public String decode(final String value) {
        return unwrap(value);
    }

    @Override
    public String getFormat() {
        return ENCRYPTED_FORMAT;
    }

    @Override
    public String getRegex() {
        return REGEX_STRING;
    }

    private String wrap(final String value) {
        return String.format(ENCRYPTED_FORMAT, value);
    }

    private String unwrap(final String value) {
        final Matcher matcher = PATTERN.matcher(value);
        if (matcher.groupCount() == 1 && matcher.find()) {
            final String encryptedValue = matcher.group(1);
            return encryptedValue;
        }
        final String msg = String.format("Value [%s] does not match the pattern", value);
        throw new IllegalArgumentException(msg);
    }
}
