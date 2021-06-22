package org.apache.nifi.properties.gcp;

import com.google.api.gax.rpc.ApiException;
import com.google.cloud.kms.v1.CryptoKey;
import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.CryptoKeyVersion;
import com.google.cloud.kms.v1.DecryptResponse;
import com.google.cloud.kms.v1.EncryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.protobuf.ByteString;
import org.apache.nifi.properties.AbstractSensitivePropertyProvider;
import org.apache.nifi.properties.BootstrapProperties;
import org.apache.nifi.properties.PropertyProtectionScheme;
import org.apache.nifi.properties.SensitivePropertyProtectionException;
import org.apache.nifi.properties.SensitivePropertyProvider;
import org.apache.nifi.util.StringUtils;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.DecoderException;
import org.bouncycastle.util.encoders.EncoderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class GCPSensitivePropertyProvider extends AbstractSensitivePropertyProvider {
    private static final Logger logger = LoggerFactory.getLogger(GCPSensitivePropertyProvider.class);

    private static final String IMPLEMENTATION_NAME = "GCP Key Management Service Sensitive Property Provider";
    private static final String IMPLEMENTATION_KEY = "gcp/kms";

    private static final String BOOTSTRAP_GCP_FILE_PROPS_NAME = "nifi.bootstrap.sensitive.props.gcp.properties";
    private static final String PROJECT_ID_PROPS_NAME = "gcp.kms.project.id";
    private static final String LOCATION_ID_PROPS_NAME = "gcp.kms.location.id";
    private static final String KEYRING_ID_PROPS_NAME = "gcp.kms.keyring.id";
    private static final String KEY_ID_PROPS_NAME = "gcp.kms.key.id";

    private static final Charset PROPERTY_CHARSET = StandardCharsets.UTF_8;

    private KeyManagementServiceClient client;
    private BootstrapProperties gcpBootstrapProperties;
    private CryptoKeyName keyName;

    public GCPSensitivePropertyProvider(BootstrapProperties bootstrapProperties) {
        super(bootstrapProperties);
        // if either gcpBootstrapProperties or any of the required key properties is loaded as null values,
        // then isSupported will return false
        gcpBootstrapProperties = getGCPBootstrapProperties(bootstrapProperties);
        loadRequiredGCPProperties(gcpBootstrapProperties);

    }

    /**
     * Initializes the GCP KMS Client to be used for encrypt, decrypt and other interactions with GCP Cloud KMS.
     * Note: This does not verify if credentials are valid
     */
    private void initializeClient() {
        try {
            client = KeyManagementServiceClient.create();
        } catch (IOException e) {
            logger.error("Encountered an error initializing the {}: {}", IMPLEMENTATION_NAME, e.getMessage());
            throw new SensitivePropertyProtectionException("Error initializing the GCP Cloud KMS Client", e);
        }
    }

    /**
     * Validates the key details provided by the user.
     * @return the CryptoKeyName representing the key to be used by Google KMS
     */
    private void validate() throws ApiException, SensitivePropertyProtectionException {
        CryptoKey key;
        CryptoKeyVersion keyVersion;
        try {
            key = client.getCryptoKey(keyName);
            keyVersion = client.getCryptoKeyVersion(key.getPrimary().getName());
        } catch (ApiException e) {
            logger.error("Encountered an error while fetching key details");
            throw new SensitivePropertyProtectionException("Encountered an error while fetching key details", e);
        }

        // check if the key is enabled
        if (keyVersion.getState() != CryptoKeyVersion.CryptoKeyVersionState.ENABLED) {
            logger.error("The key is not enabled");
            throw new SensitivePropertyProtectionException("The key is not enabled");
        }
    }

    /**
     * Checks if we have the required key properties for GCP Cloud KMS and loads it into {@link #keyName}.
     * Will load null if key is not present
     * Note: This function does not verify if the key is correctly formatted/valid
     * @param props the properties representing bootstrap-gcp.conf
     */
    private void loadRequiredGCPProperties(BootstrapProperties props) {
        String projectId = props.getProperty(PROJECT_ID_PROPS_NAME, null);
        String locationId = props.getProperty(LOCATION_ID_PROPS_NAME, null);
        String keyRingId = props.getProperty(KEYRING_ID_PROPS_NAME, null);
        String keyId = props.getProperty(KEY_ID_PROPS_NAME, null);
        this.keyName = CryptoKeyName.of(projectId, locationId, keyRingId, keyId);
    }

    /**
     * Checks bootstrap.conf to check if {@link #BOOTSTRAP_GCP_FILE_PROPS_NAME} property is configured to the
     * bootstrap-gcp.conf file. Also will load bootstrap-gcp.conf to {@link #gcpBootstrapProperties} if possible
     * @param bootstrapProperties BootstrapProperties object corresponding to bootstrap.conf
     * @return BootstrapProperties object corresponding to bootstrap-gcp.conf, null otherwise
     */
    private BootstrapProperties getGCPBootstrapProperties(BootstrapProperties bootstrapProperties) {
        if (bootstrapProperties == null) {
            return null;
        }

        BootstrapProperties cloudBootstrapProperties;

        // get the bootstrap-gcp.conf file and process it
        String filePath = bootstrapProperties.getProperty(BOOTSTRAP_GCP_FILE_PROPS_NAME, null);
        if (filePath == null) {
            return null;
        }

        // Load the bootstrap-gcp.conf file based on path specified in
        // "nifi.bootstrap.sensitive.props.gcp.properties" property of bootstrap.conf
        Properties properties = new Properties();
        Path gcpBootstrapConf = Paths.get(filePath).toAbsolutePath();

        try (final InputStream inputStream = Files.newInputStream(gcpBootstrapConf)){
            properties.load(inputStream);
            cloudBootstrapProperties = new BootstrapProperties("gcp", properties, gcpBootstrapConf);
        } catch (IOException e) {
            return null;
        }

        return cloudBootstrapProperties;
    }

    /**
     * Checks bootstrap-gcp.conf for the required configurations for Google Cloud KMS encrypt/decrypt operations
     * @return True if bootstrap-gcp.conf contains the required properties for GCP KMS SPP, False otherwise
     */
    private boolean hasRequiredGCPProperties() {
        if (gcpBootstrapProperties == null) {
            return false;
        }

        if (gcpBootstrapProperties.getProperty(PROJECT_ID_PROPS_NAME, null) == null) {
            return false;
        }
        if (gcpBootstrapProperties.getProperty(LOCATION_ID_PROPS_NAME, null) == null) {
            return false;
        }
        if (gcpBootstrapProperties.getProperty(KEYRING_ID_PROPS_NAME, null) == null) {
            return false;
        }
        if (gcpBootstrapProperties.getProperty(KEY_ID_PROPS_NAME, null) == null) {
            return false;
        }

        return true;
    }

    @Override
    public boolean isSupported() {
        return hasRequiredGCPProperties();
    }

    @Override
    protected PropertyProtectionScheme getProtectionScheme() {
        return PropertyProtectionScheme.GCP;
    }

    @Override
    public String getName() {
        return IMPLEMENTATION_NAME;
    }

    @Override
    public String getIdentifierKey() {
        return IMPLEMENTATION_KEY;
    }

    /**
     * Returns the ciphertext blob of this value encrypted using a key stored in GCP KMS
     *
     * @return the ciphertext blob to persist in the {@code nifi.properties} file
     */
    private byte[] encrypt(byte[] input) throws IOException {
        EncryptResponse response = client.encrypt(keyName, ByteString.copyFrom(input));
        return response.getCiphertext().toByteArray();
    }

    /**
     * Returns the value corresponding to a ciphertext blob decrypted using a key stored in GCP KMS
     *
     * @return the "unprotected" byte[] of this value, which could be used by the application
     */
    private byte[] decrypt(byte[] input) throws IOException {
        DecryptResponse response = client.decrypt(keyName, ByteString.copyFrom(input));
        return response.getPlaintext().toByteArray();
    }

    @Override
    public String protect(String unprotectedValue) throws SensitivePropertyProtectionException {
        if (unprotectedValue == null || unprotectedValue.trim().length() == 0) {
            throw new IllegalArgumentException("Cannot encrypt an empty value");
        }

        if (this.client == null) {
            try {
                initializeClient();
                validate();
            } catch (SensitivePropertyProtectionException e) {
                logger.error("Encountered an error initializing the {}: {}", IMPLEMENTATION_NAME, e.getMessage());
                throw new SensitivePropertyProtectionException("Error initializing the GCP Cloud KMS Client", e);
            }
        }

        try {
            byte[] plainBytes = unprotectedValue.getBytes(PROPERTY_CHARSET);
            byte[] cipherBytes = encrypt(plainBytes);
            logger.debug(getName() + " encrypted a sensitive value successfully");
            return Base64.toBase64String(cipherBytes);
        } catch (IOException | ApiException | EncoderException e) {
            final String msg = "Error encrypting a protected value";
            logger.error(msg, e);
            throw new SensitivePropertyProtectionException(msg, e);
        }
    }

    @Override
    public String unprotect(String protectedValue) throws SensitivePropertyProtectionException {
        if (protectedValue == null) {
            throw new IllegalArgumentException("Cannot decrypt a null cipher");
        }

        if (this.client == null) {
            try {
                initializeClient();
                validate();
            } catch (SensitivePropertyProtectionException e) {
                logger.error("Encountered an error initializing the {}: {}", IMPLEMENTATION_NAME, e.getMessage());
                throw new SensitivePropertyProtectionException("Error initializing the GCP Cloud KMS Client", e);
            }
        }

        try {
            byte[] cipherBytes = Base64.decode(protectedValue);
            byte[] plainBytes = decrypt(cipherBytes);
            logger.debug(getName() + " decrypted a sensitive value successfully");
            return new String(plainBytes, PROPERTY_CHARSET);
        } catch (IOException | ApiException | DecoderException e) {
            final String msg = "Error decrypting a protected value";
            logger.error(msg, e);
            throw new SensitivePropertyProtectionException(msg, e);
        }
    }

    /**
     * Closes any clients that may have been opened by the SPP and releases
     * any resources possibly used by any SPP implementation
     * Note: If there is nothing to be done, then this function is a no-op
     */
    @Override
    public void close() {
        if (this.client != null) {
            this.client.close();
        }
    }
}
