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
package org.apache.nifi.encrypt;

import org.apache.nifi.security.util.EncryptionMethod;
import org.apache.nifi.security.util.crypto.AESKeyedCipherProvider;
import org.apache.nifi.security.util.crypto.KeyedCipherProvider;
import org.apache.nifi.util.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DefaultPropertyHandlerTest {
    private static final KeyedCipherProvider CIPHER_PROVIDER = new AESKeyedCipherProvider();

    private static final EncryptionMethod ENCRYPTION_METHOD = EncryptionMethod.AES_GCM;

    private static final String KEY_ALGORITHM = "AES";

    private static final byte[] STATIC_KEY = StringUtils.repeat("KEY", 8).getBytes(StandardCharsets.UTF_8);

    private static final SecretKey SECRET_KEY = new SecretKeySpec(STATIC_KEY, KEY_ALGORITHM);

    private KeyedCipherPropertyEncryptor encryptor;

    private DefaultPropertyValueHandler handler;

    private static final String SAMPLE_VALUE = "NiFi engineer here!";

    private static final String[] INVALID_ENCODED_VALUES = new String[]{
            "enc{}",
            "{}",
            "just some random value",
            "",
            "middle of enc{} the string",
            "middle of enc{fedcba0123456789} the string"
    };

    @BeforeEach
    public void setUp() {
        encryptor = new KeyedCipherPropertyEncryptor(CIPHER_PROVIDER, ENCRYPTION_METHOD, SECRET_KEY);
        handler = new DefaultPropertyValueHandler(encryptor);
    }

    @Test
    public void testEncodeDecode() {
        final String encoded = handler.encode(SAMPLE_VALUE);
        final String decoded = handler.decode(encoded);

        assertEquals(SAMPLE_VALUE, decoded);
        assertTrue(handler.isEncoded(encoded));

        final Matcher matcher = Pattern.compile(handler.getRegex()).matcher(encoded);
        assertTrue(matcher.matches());
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
