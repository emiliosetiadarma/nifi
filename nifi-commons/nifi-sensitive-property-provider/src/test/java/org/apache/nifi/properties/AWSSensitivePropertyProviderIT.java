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
package org.apache.nifi.properties;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.internal.util.io.IOUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * To run this test, make sure to first configure sensitive credential information as in the following link
 * https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html
 *
 * Then run:
 * aws kms create-key
 *
 * Take note of the key id or arn.
 *
 * Then, set the system property -Daws.kms.key.id to the either key id value or arn value
 * set the system property -Daws.access.key.id to the access key id
 * set the system property -Daws.secret.key.id to the secret key id
 *
 * After you are satisfied with the test, and you don't need the key, you may schedule key deletion with:
 * aws kms schedule-key-deletion --key-id <"KeyId from earlier create-key"> --pending-window-in-days <no. of days>
 *
 */

public class AWSSensitivePropertyProviderIT {
    private static final String SAMPLE_PLAINTEXT = "AWSSensitivePropertyProviderIT SAMPLE-PLAINTEXT";
    private static final String ACCESS_KEY_PROPS_NAME = "aws.access.key.id";
    private static final String SECRET_KEY_PROPS_NAME = "aws.secret.key.id";
    private static final String KMS_KEY_PROPS_NAME = "aws.kms.key.id";

    private static AWSSensitivePropertyProvider spp;

    private static BootstrapProperties props;

    private static Path tempConfDir;
    private static Path mockBootstrapConf;

    private static void initializeBootstrapProperties() throws IOException {
        tempConfDir = Files.createTempDirectory("conf");
        mockBootstrapConf = Files.createTempFile("bootstrap-aws", ".conf").toAbsolutePath();

        mockBootstrapConf = Files.move(mockBootstrapConf, tempConfDir.resolve("bootstrap-aws.conf"));

        String accessKey = System.getProperty(ACCESS_KEY_PROPS_NAME);
        String secretKey = System.getProperty(SECRET_KEY_PROPS_NAME);
        String keyId = System.getProperty(KMS_KEY_PROPS_NAME);

        StringBuilder bootstrapConfText = new StringBuilder();
        bootstrapConfText.append(ACCESS_KEY_PROPS_NAME + "=" + accessKey);
        bootstrapConfText.append("\n" + KMS_KEY_PROPS_NAME + "=" + secretKey);
        bootstrapConfText.append("\n" + KMS_KEY_PROPS_NAME + "=" + keyId);
        IOUtil.writeText(bootstrapConfText.toString(), mockBootstrapConf.toFile());

        final Properties bootstrapProperties = new Properties();
        try (final InputStream inputStream = Files.newInputStream(mockBootstrapConf)) {
            bootstrapProperties.load(inputStream);
            props = new BootstrapProperties("aws", bootstrapProperties, mockBootstrapConf);
        }
    }

    @BeforeClass
    public static void initOnce() throws IOException {
        initializeBootstrapProperties();
        Assert.assertNotNull(props);
        spp = new AWSSensitivePropertyProvider(props);
        Assert.assertNotNull(spp);
    }

    @AfterClass
    public static void tearDownOnce() throws IOException {
        Files.deleteIfExists(mockBootstrapConf);
        Files.deleteIfExists(tempConfDir);

        spp.close();
    }

    @Test
    public void testEncryptDecrypt() { runEncryptDecryptTest(); }

    private static void runEncryptDecryptTest() {
        String protectedValue = spp.protect(SAMPLE_PLAINTEXT);
        String unprotectedValue = spp.unprotect(protectedValue);

        Assert.assertEquals(SAMPLE_PLAINTEXT, unprotectedValue);
        Assert.assertNotEquals(SAMPLE_PLAINTEXT, protectedValue);
        Assert.assertNotEquals(protectedValue, unprotectedValue);
    }
}
