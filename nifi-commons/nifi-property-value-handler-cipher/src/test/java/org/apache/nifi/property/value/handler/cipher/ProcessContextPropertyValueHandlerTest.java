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

import org.apache.nifi.encrypt.EncryptionException;
import org.apache.nifi.encrypt.PropertyEncryptor;
import org.apache.nifi.encrypt.PropertyEncryptorFactory;
import org.apache.nifi.security.util.EncryptionMethod;
import org.apache.nifi.util.NiFiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ProcessContextPropertyValueHandlerTest {
    private PropertyEncryptor encryptor;

    private ProcessContextPropertyValueHandler handler;

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
    private static final String[] UNWRAPPED_ENCRYPTED_VALUES = new String[] {
            "169e5994307dc130b922ee46dda7775a6dd5d1f579e7244b0c4df3ebff8e9403",
            "66d5f2721903ea7e56587e39ea1b8b0024a6fa578f9677a6344d31145d81eee737d5d41a7adf6f53981045a57bd100fe",
            "e47d703203c24ec21a643129e019f294c1d2131006282c701b8d7a7eec05b2e9"
    };

    @BeforeEach
    public void setUp() {
        final Properties properties = new Properties();
        properties.setProperty(NiFiProperties.SENSITIVE_PROPS_ALGORITHM, EncryptionMethod.SHA256_256AES.getAlgorithm());
        properties.setProperty(NiFiProperties.SENSITIVE_PROPS_KEY, String.class.getName());
        final NiFiProperties nifiProperties = NiFiProperties.createBasicNiFiProperties(null, properties);
        encryptor = PropertyEncryptorFactory.getPropertyEncryptor(nifiProperties);
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
