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
package org.apache.nifi.properties.aws;

import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.properties.AbstractSensitivePropertyProvider;
import org.apache.nifi.properties.BootstrapProperties;
import org.apache.nifi.properties.PropertyProtectionScheme;
import org.apache.nifi.properties.SensitivePropertyProtectionException;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.DecoderException;
import org.bouncycastle.util.encoders.EncoderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptResponse;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KmsException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class AWSSensitivePropertyProvider extends AbstractSensitivePropertyProvider {
    private static final Logger logger = LoggerFactory.getLogger(AWSSensitivePropertyProvider.class);

    private static final String IMPLEMENTATION_NAME = "AWS KMS Sensitive Property Provider";
    private static final String IMPLEMENTATION_KEY = "aws/kms/";

    private static final String BOOTSTRAP_AWS_FILE_PROPS_NAME = "nifi.bootstrap.sensitive.props.aws.properties";
    private static final String ACCESS_KEY_PROPS_NAME = "aws.access.key.id";
    private static final String SECRET_KEY_PROPS_NAME = "aws.secret.key.id";
    private static final String REGION_KEY_PROPS_NAME = "aws.region";
    private static final String KMS_KEY_PROPS_NAME = "aws.kms.key.id";

    private static final Charset PROPERTY_CHARSET = StandardCharsets.UTF_8;

    private String keyId;
    private KmsClient client;
    private BootstrapProperties awsBootstrapProperties;

    private boolean initialized = false;
    private boolean valid = false;

    public AWSSensitivePropertyProvider(BootstrapProperties bootstrapProperties) throws SensitivePropertyProtectionException {
        super(bootstrapProperties);
        try {
            if (!isSupported(bootstrapProperties)) {
                initialized = true;
                return;
            }
            initializeClient();
            validate();
        } catch (KmsException e) {
            logger.error("Encountered an error initializing the AWS KMS Client: {}", e.getMessage());
            throw new SensitivePropertyProtectionException("Error initializing the AWS KMS Client", e);
        } catch (SensitivePropertyProtectionException e) {
            logger.error("Encountered an error initializing the {}: {}", IMPLEMENTATION_NAME, e.getMessage());
            throw new SensitivePropertyProtectionException("Error initializing the AWS KMS Client", e);
        }
    }

    /**
     * Initializes the KMS Client to be used for encrypt, decrypt and other interactions with AWS KMS
     * First attempts to use default AWS Credentials Provider Chain
     * If that attempt fails, attempt to initialize credentials using bootstrap-aws.conf
     * Note: This does not verify if credentials are valid
     */
    private void initializeClient() {
        initialized = true;

        // attempts to initialize client with credentials provider chain
        try {
            DefaultCredentialsProvider credentialsProvider = DefaultCredentialsProvider.builder()
                    .build();
            credentialsProvider.resolveCredentials();
            this.client = KmsClient.builder()
                    .credentialsProvider(credentialsProvider)
                    .build();
            return;
        } catch (SdkClientException e) {
            // this exception occurs if default credentials are not provided
            logger.debug("Default credentials/configuration for AWS not provided, attempting to fetch from bootstrap-aws.conf");
        }

        // if the credentials provider chain does not work, then bootstrap.aws.conf is used
        String accessKeyId = this.awsBootstrapProperties.getProperty(ACCESS_KEY_PROPS_NAME);
        String secretKeyId = this.awsBootstrapProperties.getProperty(SECRET_KEY_PROPS_NAME);
        String region = this.awsBootstrapProperties.getProperty(REGION_KEY_PROPS_NAME);

        try {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretKeyId);
            this.client = KmsClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .build();
        } catch (KmsException | NullPointerException | IllegalArgumentException e) {
            logger.debug("Credentials/Configuration provided in bootstrap-aws.conf are invalid");
            throw new SensitivePropertyProtectionException("Require valid credentials/configuration to initialize KMS client");
        }
    }

    /**
     * Validates the key ARN, credentials and configuration provided by the user.
     * Note: This function performs checks on the key and indirectly also validates the credentials and
     * configurations provided during the initialization of the client
     */
    private void validate() throws KmsException, SensitivePropertyProtectionException {
        if (keyId == null || StringUtils.isBlank(keyId)) {
            throw new SensitivePropertyProtectionException("The key cannot be empty");
        }

        // asking for a Key Description is the best way to check whether a key is valid
        // because AWS KMS accepts various formats for its keys.

        DescribeKeyRequest request = DescribeKeyRequest.builder()
                .keyId(keyId)
                .build();

        // using the KmsClient in a DescribeKey request indirectly also verifies if the credentials provided
        // during the initialization of the key is valid
        DescribeKeyResponse response = this.client.describeKey(request);
        KeyMetadata metadata = response.keyMetadata();

        if (!metadata.enabled()) {
            throw new SensitivePropertyProtectionException("The key is not enabled");
        }
        valid = true;
    }

    @Override
    protected PropertyProtectionScheme getProtectionScheme() {
        return PropertyProtectionScheme.AWS_KMS;
    }

    /**
     * Checks if we have credentials and configuration for AWS
     * Note: This only checks if there is a access-key-id, secret-key-id and region
     *       It does not check if they are valid
     * @param props the properties representing bootstrap-aws.conf
     * @return True if there is a form of credentials
     */
    private boolean credentialsAndConfigurationPresent(BootstrapProperties props) {
        // checks default setting of credentials first by making a mock kmsClient with default credential configuration
        try {
            // the following will return an error if credentials are not provided
            DefaultCredentialsProvider credentialsProvider = DefaultCredentialsProvider.builder()
                    .build();
            credentialsProvider.resolveCredentials();

            // client builder will return an error if region configuration is not provided
            KmsClient mockClient = KmsClient.builder()
                    .credentialsProvider(credentialsProvider)
                    .build();

            mockClient.close();

            // client can be built with default credentials/configuration
            return true;
        } catch (SdkClientException e) {
            // this exception occurs if default configuration is not provided
            logger.debug("Default credentials/configuration for AWS not provided, attempting to fetch from bootstrap-aws.conf");
        }

        // checks to see if there are properties in bootstrap-aws.conf that specify credentials and region configurations
        if (props.getProperty(ACCESS_KEY_PROPS_NAME, null) == null) {
            return false;
        }
        if (props.getProperty(SECRET_KEY_PROPS_NAME, null) == null) {
            return false;
        }
        if (props.getProperty(REGION_KEY_PROPS_NAME, null) == null) {
            return false;
        }

        // client can be built with credentials/configuration given in bootstrap-aws.conf
        return true;
    }


    /**
     * Checks if we have a key ID from AWS KMS.
     * Note: This only checks if there is a key specified. This does not verify if the key is correctly specified or not
     *       Also if key is present, stores it in this.keyId
     * @param props the properties representing bootstrap-aws.conf
     * @return True if there is a key ID
     */
    private boolean keyPresent(BootstrapProperties props) {
        if (props.getProperty(KMS_KEY_PROPS_NAME, null) == null) {
            return false;
        }

        this.keyId = props.getProperty(KMS_KEY_PROPS_NAME);
        return true;
    }

    @Override
    protected boolean isSupported(BootstrapProperties bootstrapProperties) {
        // Performance improvement, removes the need to do checks if the SPP has already been initialized
        // If the SPP is already constructed, then checks if the SPP is valid
        if (initialized) {
            return valid;
        }

        if (bootstrapProperties == null) {
            return false;
        }

        // get the bootstrap-aws.conf file and process it
        String filePath = bootstrapProperties.getProperty(BOOTSTRAP_AWS_FILE_PROPS_NAME, null);
        if (filePath == null) {
            return false;
        }

        // Load the bootstrap-aws.conf file based on path specified in
        // "nifi.bootstrap.sensitive.props.aws.properties" property of bootstrap.conf
        Properties properties = new Properties();
        Path awsBootstrapConf = Paths.get(filePath).toAbsolutePath();

        try (final InputStream inputStream = Files.newInputStream(awsBootstrapConf)){
            properties.load(inputStream);
            awsBootstrapProperties = new BootstrapProperties("aws", properties, awsBootstrapConf);
        } catch (IOException e) {
            return false;
        }

        if (awsBootstrapProperties == null) {
            return false;
        }

        // AWS KMS SPP is supported if:
        // - there is a key id (arn)
        // - there is the required credentials (access key id and secret key id)
        // - there is the required configuration (region settings)
        return keyPresent(awsBootstrapProperties) && credentialsAndConfigurationPresent(awsBootstrapProperties);
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

        // builds an encryption request to be sent to the kmsClient
        EncryptRequest encryptRequest = EncryptRequest.builder()
                .keyId(this.keyId)
                .plaintext(plainBytes)
                .build();

        // sends request, records response
        EncryptResponse response = this.client.encrypt(encryptRequest);

        // get encrypted data
        SdkBytes encryptedData = response.ciphertextBlob();

        return encryptedData.asByteArray();
    }

    /**
     * Returns the value corresponding to a ciphertext blob decrypted using an AWS KMS CMK
     *
     * @return the "unprotected" byte[] of this value, which could be used by the application
     */
    private byte[] decrypt(byte[] input) {
        SdkBytes cipherBytes = SdkBytes.fromByteArray(input);

        // builds a decryption request to be sent to the kmsClient
        DecryptRequest decryptRequest = DecryptRequest.builder()
                .ciphertextBlob(cipherBytes)
                .keyId(this.keyId)
                .build();

        // sends request, records response
        DecryptResponse response = this.client.decrypt(decryptRequest);

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

        // if SPP is invalid
        if (!valid) {
            throw new SensitivePropertyProtectionException("AWS SPP is invalid, cannot be used to protect a value");
        }

        try {
            byte[] plainBytes = unprotectedValue.getBytes(PROPERTY_CHARSET);
            byte[] cipherBytes = encrypt(plainBytes);
            logger.debug(getName() + " encrypted a sensitive value successfully");
            return Base64.toBase64String(cipherBytes);
        } catch (KmsException | EncoderException e) {
            final String msg = "Error encrypting a protected value";
            logger.error(msg, e);
            throw new SensitivePropertyProtectionException(msg, e);
        }
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

        // if SPP is invalid
        if (!valid) {
            throw new SensitivePropertyProtectionException("AWS SPP is invalid, cannot be used to unprotect a value");
        }

        try {
            byte[] cipherBytes = Base64.decode(protectedValue);
            byte[] plainBytes = decrypt(cipherBytes);
            logger.debug(getName() + " decrypted a sensitive value successfully");
            return new String(plainBytes, PROPERTY_CHARSET);
        } catch (KmsException | DecoderException e) {
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
