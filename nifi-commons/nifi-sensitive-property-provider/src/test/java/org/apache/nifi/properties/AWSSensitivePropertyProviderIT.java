package org.apache.nifi.properties;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * To run this test, make sure to first configure sensitive credential information as in the following link
 * https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html
 *
 * Then run:
 * aws kms create-key
 *
 * Take note of the key id or arn.
 *
 * Then, set the system property -Daws.kms.auth.properties.key to the either key id value or arn value
 *
 * After you are satisfied with the test, schedule the key deletion
 * aws kms schedule-key-deletion --key-id <"KeyId from earlier create-key"> --pending-window-in-days <no. of days>
 *
 */

public class AWSSensitivePropertyProviderIT {
    private static final String SAMPLE_PLAINTEXT = "AWSSensitivePropertyProviderIT SAMPLE-PLAINTEXT";
    private AWSSensitivePropertyProvider spp;

    @Before
    public void init() {
        String key = System.getProperty("aws.kms.auth.properties.key");
        spp = new AWSSensitivePropertyProvider(key);
        Assert.assertNotNull(spp);
    }

    @Test
    public void testEncryptDecrypt() {
        this.runEncryptDecryptTest();
    }

    private void runEncryptDecryptTest() {
        String protectedValue = spp.protect(SAMPLE_PLAINTEXT);
        String unprotectedValue = spp.unprotect(protectedValue);

        Assert.assertEquals(SAMPLE_PLAINTEXT, unprotectedValue);
        Assert.assertNotEquals(SAMPLE_PLAINTEXT, protectedValue);
        Assert.assertNotEquals(protectedValue, unprotectedValue);
    }
}
