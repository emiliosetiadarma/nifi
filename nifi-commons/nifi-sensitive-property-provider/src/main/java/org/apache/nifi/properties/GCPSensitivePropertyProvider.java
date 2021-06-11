package org.apache.nifi.properties;

import com.google.api.gax.rpc.ApiException;
import com.google.cloud.kms.v1.CryptoKey;
import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.CryptoKeyVersion;
import com.google.cloud.kms.v1.DecryptResponse;
import com.google.cloud.kms.v1.EncryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.protobuf.ByteString;
import org.apache.nifi.util.StringUtils;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.DecoderException;
import org.bouncycastle.util.encoders.EncoderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class GCPSensitivePropertyProvider implements SensitivePropertyProvider {
    private static final Logger logger = LoggerFactory.getLogger(GCPSensitivePropertyProvider.class);

    private static final String IMPLEMENTATION_NAME = "GCP Key Management Service Sensitive Property Provider";
    private static final String IMPLEMENTATION_KEY = "gcp/kms";
    private static final String PROVIDER = "GCP";

    private static CryptoKeyName keyName;

    public GCPSensitivePropertyProvider(String projectId, String locationId, String keyRingId, String keyId) throws SensitivePropertyProtectionException {
        try {
            this.keyName = validateKey(projectId, locationId, keyRingId, keyId);
        } catch(IOException | ApiException e) {
            logger.error("Encountered an error initializing the GCP KMS Client: {}", e.getMessage());
            throw new SensitivePropertyProtectionException("error initializing the GCP KMS Client", e);
        } catch (SensitivePropertyProtectionException e) {
            logger.error("Encountered an error initializing the {}: {}", IMPLEMENTATION_NAME, e.getMessage());
            throw new SensitivePropertyProtectionException("Error initializing the GCP KMS Client", e);
        }
    }

    /**
     * Validates the key details provided by the user.
     *
     * @param projectId the project ID the key is under
     * @param locationId the location of the project
     * @param keyRingId the key ring id that the key is part of
     * @param keyId the key ID within the key ring
     * @return the CryptoKeyName representing the key to be used by Google KMS
     */
    private CryptoKeyName validateKey(String projectId, String locationId, String keyRingId, String keyId) throws IOException, ApiException, SensitivePropertyProtectionException {
        // TODO: Check if its better to move these checks into a different function
        // Check params
        if (projectId == null || StringUtils.isBlank(projectId)) {
            throw new SensitivePropertyProtectionException("The project ID cannot be empty");
        }
        if (locationId == null || StringUtils.isBlank(locationId)) {
            throw new SensitivePropertyProtectionException("The location ID cannot be empty");
        }
        if (keyRingId == null || StringUtils.isBlank(keyRingId)) {
            throw new SensitivePropertyProtectionException("The Key Ring ID cannot be empty");
        }
        if (keyId == null || StringUtils.isBlank(keyId)) {
            throw new SensitivePropertyProtectionException("The key ID cannot be empty");
        }

        // get the key
        KeyManagementServiceClient client = KeyManagementServiceClient.create();
        CryptoKeyName keyName = CryptoKeyName.of(projectId, locationId, keyRingId, keyId);

        CryptoKey key = client.getCryptoKey(keyName);
        CryptoKeyVersion keyVersion = client.getCryptoKeyVersion(key.getPrimary().getName());

        client.close();

        // TODO: (maybe) add more checks on the key
        if (keyVersion.getState() != CryptoKeyVersion.CryptoKeyVersionState.ENABLED) {
            throw new SensitivePropertyProtectionException("The key is not enabled");
        }

        client.close();

        return keyName;
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
        KeyManagementServiceClient client = KeyManagementServiceClient.create();
        EncryptResponse response = client.encrypt(keyName, ByteString.copyFrom(input));
        client.close();
        return response.getCiphertext().toByteArray();
    }

    /**
     * Returns the value corresponding to a ciphertext blob decrypted using a key stored in GCP KMS
     *
     * @return the "unprotected" byte[] of this value, which could be used by the application
     */
    private byte[] decrypt(byte[] input) throws IOException {
        KeyManagementServiceClient client = KeyManagementServiceClient.create();
        DecryptResponse response = client.decrypt(keyName, ByteString.copyFrom(input));
        client.close();
        return response.getPlaintext().toByteArray();
    }

    @Override
    public String protect(String unprotectedValue) throws SensitivePropertyProtectionException {
        if (unprotectedValue == null || unprotectedValue.trim().length() == 0) {
            throw new IllegalArgumentException("Cannot encrypt an empty value");
        }

        try {
            byte[] plainBytes = unprotectedValue.getBytes(StandardCharsets.UTF_8);
            byte[] cipherBytes = encrypt(plainBytes);
            logger.debug(getName() + " encrypted a sensitive value successfully");
            return base64Encode(cipherBytes);
        } catch (IOException | ApiException | EncoderException e) {
            final String msg = "Error encrypting a protected value";
            logger.error(msg, e);
            throw new SensitivePropertyProtectionException(msg, e);
        }
    }

    private String base64Encode(byte[] input) {
        return Base64.toBase64String(input).replaceAll("=", "");
    }

    @Override
    public String unprotect(String protectedValue) throws SensitivePropertyProtectionException {
        if (protectedValue == null) {
            throw new IllegalArgumentException("Cannot decrypt a null cipher");
        }

        try {
            byte[] cipherBytes = Base64.decode(protectedValue);
            byte[] plainBytes = decrypt(cipherBytes);
            logger.debug(getName() + " decrypted a sensitive value successfully");
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (IOException | ApiException | DecoderException e) {
            final String msg = "Error decrypting a protected value";
            logger.error(msg, e);
            throw new SensitivePropertyProtectionException(msg, e);
        }
    }
}
