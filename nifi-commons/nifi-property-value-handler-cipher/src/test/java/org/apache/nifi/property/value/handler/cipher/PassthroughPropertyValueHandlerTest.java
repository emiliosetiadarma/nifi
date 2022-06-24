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
package org.apache.nifi.property.value.handler.cipher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PassthroughPropertyValueHandlerTest {
    private PassthroughPropertyValueHandler handler;

    private static final String[] INVALID_ENCODED_VALUES = new String[]{
            "enc{}",
            "{}",
            "just some random value",
            "",
            "middle of enc{} the string",
            "middle of enc{test} the string"
    };

    private static final String[] SAMPLE_VALUES = new String[] {
      "test 1",
      "NiFi engineer here!",
      " "
    };

    @BeforeEach
    public void setUp() {
        handler = new PassthroughPropertyValueHandler();
    }

    @Test
    public void testEncodeDecode() {
        for (String value: SAMPLE_VALUES) {
            final String encoded = handler.encode(value);
            final String decoded = handler.decode(encoded);

            assertEquals(value, decoded);
            assertEquals(String.format(handler.getFormat(), value), encoded);
            assertTrue(handler.isEncoded(encoded));

            final Matcher matcher = Pattern.compile(handler.getRegex()).matcher(encoded);
            assertTrue(matcher.matches());
        }
    }

    @Test
    public void testDecodeInvalidValues() {
        for (String value: INVALID_ENCODED_VALUES) {
            assertTrue(!handler.isEncoded(value));
            // the current implementation of decode throws an IllegalArgumentException if value is not encoded
            assertThrows(IllegalArgumentException.class, () -> {
                handler.decode(value);
            });
        }
    }
}
