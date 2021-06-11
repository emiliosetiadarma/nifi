package org.apache.nifi.properties;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * To run this test, make sure to first configure sensitive credential information as in the following link
 * https://cloud.google.com/kms/docs/reference/libraries#cloud-console
 *
 * Create a project, keyring and key in the web console.
 *
 * Take note of the project name, location, keyring name and key name.
 *
 * Then, set the system properties as follows:
 * -Dgcp.kms.auth.properties.project=<project>
 * -Dgcp.kms.auth.properties.location=<location>
 * -Dgcp.kms.auth.properties.keyring=<key ring name>
 * -Dgcp.kms.auth.properties.key=<key name>
 * when running the integration tests
 */

public class GCPSensitivePropertyProviderIT {
    private static final String SAMPLE_PLAINTEXT = "GCPSensitivePropertyProviderIT SAMPLE-PLAINTEXT";
    private GCPSensitivePropertyProvider spp;

    @Before
    public void init() {
        String project = System.getProperty("gcp.kms.auth.properties.project");
        String location = System.getProperty("gcp.kms.auth.properties.location");
        String keyRing = System.getProperty("gcp.kms.auth.properties.keyring");
        String key = System.getProperty("gcp.kms.auth.properties.key");
        spp = new GCPSensitivePropertyProvider(project, location, keyRing, key);
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
