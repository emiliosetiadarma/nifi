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

import org.apache.nifi.encrypt.EncryptionException;
import org.apache.nifi.encrypt.PropertyEncryptor;
import org.apache.nifi.encrypt.PropertyEncryptorBuilder;
import org.apache.nifi.security.util.EncryptionMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ProcessContextPropertyValueHandlerTest {
    private static final EncryptionMethod ENCRYPTION_METHOD = EncryptionMethod.MD5_256AES;

    private PropertyEncryptor encryptor;

    private PropertyValueHandler handler;

    private static final String[] INVALID_ENCODED_VALUES = new String[]{
            "enc{}",
            "{}",
            "just some random value",
            "",
            "middle of enc{} the string",
            "middle of enc{0123456789fedcba} the string"
    };

    private static final String[] SAMPLE_VALUES = new String[] {
            "test 1",
            "NiFi engineer here!",
            " "
    };

    // this corresponds 1:1 with the values in SAMPLE_VALUES above
    // must be changed if the ENCRYPTION_METHOD variable is changed
    private static final String[] UNWRAPPED_ENCRYPTED_VALUES = new String[] {
            "71718cee2021ffc2520a5edc2228cec8e1839b2b9db0f3a4fc7bfb2994141a8d",
            "1610383f6cd25d8e8237b9dffce06388419c5e5e0bca93cf7d40e06b0b98f2344ea1b572cb8728dfb4cb0e69f202102a",
            "bbb2e82a1282a1a97a12044be4f12a4bda6a299d7d310eddf1011762edea31d8"
    };

    @BeforeEach
    public void setUp() {
        encryptor = new PropertyEncryptorBuilder(String.class.getName()).setAlgorithm(ENCRYPTION_METHOD.getAlgorithm()).build();
        handler = new ProcessContextPropertyValueHandler(new DefaultPropertyValueHandler(encryptor));
    }

    @Test
    public void testEncodeDecode() {
        for (String value: SAMPLE_VALUES) {
            final String encoded = handler.encode(value);
            final String decoded = handler.decode(encoded);

            assertEquals(value, decoded);
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
            // and EncryptionException if the value is unable to be decrypted
            Exception exception = assertThrows(Exception.class, () -> {
                handler.decode(value);
            });
            assert exception instanceof IllegalArgumentException || exception instanceof EncryptionException;
        }
    }

    @Test
    public void testDecodeUnformattedValues() {
        for (int i = 0; i < UNWRAPPED_ENCRYPTED_VALUES.length; i++) {
            assertTrue(!handler.isEncoded(UNWRAPPED_ENCRYPTED_VALUES[i]));
            final String decoded = handler.decode(UNWRAPPED_ENCRYPTED_VALUES[i]);
            assertTrue(decoded.equals(SAMPLE_VALUES[i]));
        }
    }
}
