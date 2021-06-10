package org.apache.nifi.properties;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.DecoderException;
import org.bouncycastle.util.encoders.EncoderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptResponse;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KmsException;

import java.nio.charset.StandardCharsets;

public class AWSSensitivePropertyProvider implements SensitivePropertyProvider{
    private static final Logger logger = LoggerFactory.getLogger(AWSSensitivePropertyProvider.class);

    private static final String IMPLEMENTATION_NAME = "AWS KMS Sensitive Property Provider";
    private static final String IMPLEMENTATION_KEY = "aws/kms/";
    private static final String PROVIDER = "AWS";

    private static String keyId;


    public AWSSensitivePropertyProvider(String keyId) throws SensitivePropertyProtectionException {
        try {
            this.keyId = validateKey(keyId);
        } catch (KmsException e) {
            logger.error("Encountered an error initializing the AWS KMS Client: {}", e.getMessage());
            throw new SensitivePropertyProtectionException("Error initializing the AWS KMS Client", e);
        } catch (SensitivePropertyProtectionException e) {
            logger.error("Encountered an error initializing the {}: {}", IMPLEMENTATION_NAME, e.getMessage());
            throw new SensitivePropertyProtectionException("Error initializing the AWS KMS Client", e);
        }
    }

    /**
     * Validates the key ARN provided by the user.
     *
     * @param keyId the ARN to be sent to the AWS KMS service
     * @return the ARN to be sent to the AWS KMS service
     */
    private String validateKey(String keyId) throws KmsException, SensitivePropertyProtectionException {
        if (keyId == null || StringUtils.isBlank(keyId)) {
            throw new SensitivePropertyProtectionException("The key cannot be empty");
        }

        // asking for a Key Description is the best way to check whether a key is valid
        // because AWS KMS accepts various formats for its keys.
        KmsClient kmsClient = KmsClient.builder()
                .build();

        DescribeKeyRequest request = DescribeKeyRequest.builder()
                .keyId(keyId)
                .build();

        DescribeKeyResponse response = kmsClient.describeKey(request);
        KeyMetadata metadata = response.keyMetadata();


        // TODO: (maybe) add more checks on the key
        if (!metadata.enabled()) {
            throw new SensitivePropertyProtectionException("The key is not enabled");
        }

        kmsClient.close();

        return keyId;
    }

    /**
     * Returns the name of the underlying implementation.
     *
     * @return the name of this sensitive property provider
     */
    @Override
    public String getName() {
        return IMPLEMENTATION_NAME;
    }

    /**
     * Returns the key used to identify the provider implementation in {@code nifi.properties}.
     *
     * @return the key to persist in the sibling property
     */
    @Override
    public String getIdentifierKey() {
        return IMPLEMENTATION_KEY;
    }


    /**
     * Returns the ciphertext blob of this value encrypted using an AWS KMS CMK.
     *
     * @return the ciphertext blob to persist in the {@code nifi.properties} file
     */
    private byte[] encrypt(byte[] input) {
        SdkBytes plainBytes = SdkBytes.fromByteArray(input);

        // TODO: Verify region (??)
        KmsClient kmsClient = KmsClient.builder().
                build();

        // builds an encryption request to be sent to the kmsClient
        EncryptRequest encryptRequest = EncryptRequest.builder()
                .keyId(this.keyId)
                .plaintext(plainBytes)
                .build();

        // sends request, records response
        EncryptResponse response = kmsClient.encrypt(encryptRequest);

        // get encrypted data
        SdkBytes encryptedData = response.ciphertextBlob();

        kmsClient.close();

        return encryptedData.asByteArray();
    }

    /**
     * Returns the value corresponding to a ciphertext blob decrypted using an AWS KMS CMK
     *
     * @return the "unprotected" byte[] of this value, which could be used by the application
     */
    private byte[] decrypt(byte[] input) {
        SdkBytes cipherBytes = SdkBytes.fromByteArray(input);

        // TODO: Verify region (??)
        KmsClient kmsClient = KmsClient.builder()
                .build();

        // builds a decryption request to be sent to the kmsClient
        DecryptRequest decryptRequest = DecryptRequest.builder()
                .ciphertextBlob(cipherBytes)
                .keyId(this.keyId)
                .build();

        // sends request, records response
        DecryptResponse response = kmsClient.decrypt(decryptRequest);

        // get decrypted data
        SdkBytes decryptedData = response.plaintext();

        return decryptedData.asByteArray();
    }

    /**
     * Returns the "protected" form of this value. This is a form which can safely be persisted in the {@code nifi.properties} file without compromising the value.
     * An encryption-based provider would return a cipher text, while a remote-lookup provider could return a unique ID to retrieve the secured value.
     *
     * @param unprotectedValue the sensitive value
     * @return the value to persist in the {@code nifi.properties} file
     */
    @Override
    public String protect(String unprotectedValue) throws SensitivePropertyProtectionException {
        if (unprotectedValue == null || unprotectedValue.trim().length() == 0) {
            throw new IllegalArgumentException ("Cannot encrypt an empty value");
        }

        try {
            byte[] plainBytes = unprotectedValue.getBytes(StandardCharsets.UTF_8);
            byte[] cipherBytes = encrypt(plainBytes);
            logger.debug(getName() + " encrypted a sensitive value successfully");
            return base64Encode(cipherBytes);
        } catch (KmsException | EncoderException e) {
            final String msg = "Error encrypting a protected value";
            logger.error(msg, e);
            throw new SensitivePropertyProtectionException(msg, e);
        }
    }

    private String base64Encode(byte[] input) {
        return Base64.toBase64String(input).replaceAll("=", "");
    }

    /**
     * Returns the "unprotected" form of this value. This is the raw sensitive value which is used by the application logic.
     * An encryption-based provider would decrypt a cipher text and return the plaintext, while a remote-lookup provider could retrieve the secured value.
     *
     * @param protectedValue the protected value read from the {@code nifi.properties} file
     * @return the raw value to be used by the application
     */
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
        } catch (KmsException | DecoderException e) {
            final String msg = "Error decrypting a protected value";
            logger.error(msg, e);
            throw new SensitivePropertyProtectionException(msg, e);
        }
    }
}
