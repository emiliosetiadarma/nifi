package org.apache.nifi.properties.gcp;

import org.apache.nifi.properties.BootstrapProperties;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
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
 * https://cloud.google.com/kms/docs/reference/libraries#cloud-console
 *
 * Create a project, keyring and key in the web console.
 *
 * Take note of the project name, location, keyring name and key name.
 *
 * Then, set the system properties as follows:
 * -Dgcp.kms.project.id=<project>
 * -Dgcp.kms.location.id=<location>
 * -Dgcp.kms.keyring.id=<key ring name>
 * -Dgcp.kms.key.id=<key name>
 * when running the integration tests
 */

public class GCPSensitivePropertyProviderIT {
    private static final String SAMPLE_PLAINTEXT = "GCPSensitivePropertyProviderIT SAMPLE-PLAINTEXT";
    private static final String PROJECT_ID_PROPS_NAME = "gcp.kms.project.id";
    private static final String LOCATION_ID_PROPS_NAME = "gcp.kms.location.id";
    private static final String KEYRING_ID_PROPS_NAME = "gcp.kms.keyring.id";
    private static final String KEY_ID_PROPS_NAME = "gcp.kms.key.id";
    private static final String BOOTSTRAP_GCP_FILE_PROPS_NAME = "nifi.bootstrap.sensitive.props.gcp.properties";

    private static GCPSensitivePropertyProvider spp;

    private static BootstrapProperties props;

    private static Path mockBootstrapConf, mockGCPBootstrapConf;

    private static void initializeBootstrapProperties() throws IOException{
        mockBootstrapConf = Files.createTempFile("bootstrap", ".conf").toAbsolutePath();
        mockGCPBootstrapConf = Files.createTempFile("bootstrap-gcp", ".conf").toAbsolutePath();
        IOUtil.writeText(BOOTSTRAP_GCP_FILE_PROPS_NAME + "=" + mockGCPBootstrapConf.toAbsolutePath(), mockBootstrapConf.toFile());

        final Properties bootstrapProperties = new Properties();
        try (final InputStream inputStream = Files.newInputStream(mockBootstrapConf)) {
            bootstrapProperties.load(inputStream);
            props = new BootstrapProperties("nifi", bootstrapProperties, mockBootstrapConf);
        }

        String projectId = System.getProperty(PROJECT_ID_PROPS_NAME);
        String locationId = System.getProperty(LOCATION_ID_PROPS_NAME);
        String keyringId = System.getProperty(KEYRING_ID_PROPS_NAME);
        String keyId = System.getProperty(KEY_ID_PROPS_NAME);

        StringBuilder bootstrapConfText = new StringBuilder();
        bootstrapConfText.append(PROJECT_ID_PROPS_NAME + "=" + projectId);
        bootstrapConfText.append("\n" + LOCATION_ID_PROPS_NAME + "=" + locationId);
        bootstrapConfText.append("\n" + KEYRING_ID_PROPS_NAME + "=" + keyringId);
        bootstrapConfText.append("\n" + KEY_ID_PROPS_NAME + "=" + keyId);
        IOUtil.writeText(bootstrapConfText.toString(), mockGCPBootstrapConf.toFile());
    }

    @BeforeClass
    public static void initOnce() throws IOException {
        initializeBootstrapProperties();
        Assert.assertNotNull(props);
        spp = new GCPSensitivePropertyProvider(props);
        Assert.assertNotNull(spp);
    }

    @AfterClass
    public static void tearDownOnce() throws IOException {
        Files.deleteIfExists(mockBootstrapConf);
        Files.deleteIfExists(mockGCPBootstrapConf);

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
