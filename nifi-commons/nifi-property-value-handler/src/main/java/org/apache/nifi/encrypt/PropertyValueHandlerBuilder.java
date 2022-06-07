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
import org.apache.nifi.security.util.crypto.PBECipherProvider;
import org.apache.nifi.util.NiFiProperties;

import javax.crypto.SecretKey;
import java.util.Objects;
import java.util.Properties;

public class PropertyValueHandlerBuilder {
    private static final String SENSITIVE_PROPS_KEY = "nifi.sensitive.props.key";
    private static final String SENSITIVE_PROPS_ALGORITHM = "nifi.sensitive.props.algorithm";

    private PropertyEncryptor encryptor;
    private NiFiProperties nifiProperties;
    private Properties properties;
    private String password;
    private String algorithm;

    public PropertyValueHandlerBuilder() {
        this.encryptor = null;
        this.nifiProperties = null;
        this.password = null;
        this.algorithm = null;
    }

    /**
     * Set Encryptor to be used for PropertyValueHandlerBuilder
     * @param encryptor PropertyEncryptor to setup the PropertyValueHandler
     * @return Property Value Handler Builder
     */
    public PropertyValueHandlerBuilder setEncryptor(final PropertyEncryptor encryptor) {
        this.encryptor = encryptor;
        return this;
    }

    /**
     * Set {@link NiFiProperties} to be used for PropertyValueHandlerBuilder
     * @param nifiProperties {@link NiFiProperties} with algorithm and key to be used for building PropertyValueHandler
     * @return Property Value Handler Builder
     */
    public PropertyValueHandlerBuilder setNifiProperties(final NiFiProperties nifiProperties) {
        this.nifiProperties = nifiProperties;
        return this;
    }

    /**
     * Set {@link properties} to be used for PropertyValueHandlerBuilder
     * @param properties {@link Properties} with algorithm and key to be used for building PropertyValueHandler
     * @return Property Value Handler Builder
     */
    public PropertyValueHandlerBuilder setProperties(final Properties properties) {
        this.properties = properties;
        return this;
    }

    /**
     * Set algorithm to be used for PropertyValueHandlerBuilder
     * @param algorithm algorithm to be used for building PropertyValueHandler
     * @return Property Value Handler Builder
     */
    public PropertyValueHandlerBuilder setAlgorithm(final String algorithm) {
        this.algorithm = algorithm;
        return this;
    }

    /**
     * Set password to be used for PropertyValueHandlerBuilder
     * @param password password to be used for building PropertyValueHandler
     * @return Property Value Handler Builder
     */
    public PropertyValueHandlerBuilder setPassword(final String password) {
        this.password = password;
        return this;
    }

    /**
     * Build {@link PropertyValueHandler} using given configuration
     * @return Property Value Handler
     */
    public PropertyValueHandler build() {
        if (encryptor != null) {
            return new DefaultPropertyValueHandler(encryptor);
        }

        if (nifiProperties != null) {
            algorithm = nifiProperties.getProperty(SENSITIVE_PROPS_ALGORITHM);
            password = nifiProperties.getProperty(SENSITIVE_PROPS_KEY);
        } else if (properties != null) {
            algorithm = properties.getProperty(SENSITIVE_PROPS_ALGORITHM);
            password = properties.getProperty(SENSITIVE_PROPS_KEY);
        }

        Objects.requireNonNull(algorithm);
        Objects.requireNonNull(password);

        switch (algorithm) {
            // TODO: Add more cases here as external providers get added
            default:
                encryptor =  new PropertyEncryptorBuilder(password).setAlgorithm(algorithm).build();

                final PropertyEncryptionMethod propertyEncryptionMethod = findPropertyEncryptionMethod();
                final EncryptionMethod encryptionMethod = findEncryptionMethod();
                if (propertyEncryptionMethod != null) {
                    final KeyedCipherProvider keyedCipherProvider = new AESKeyedCipherProvider();
                    final SecretKey secretKey = new StandardPropertySecretKeyProvider().getSecretKey(propertyEncryptionMethod, password);
                    encryptor = new KeyedCipherPropertyEncryptor(keyedCipherProvider, propertyEncryptionMethod.getEncryptionMethod(), secretKey);
                    return new DefaultPropertyValueHandler(encryptor);
                } else if (encryptionMethod != null) {
                    encryptor = getPasswordBasedCipherPropertyEncryptor(encryptionMethod, password);
                    return new DefaultPropertyValueHandler(encryptor);
                }

                final String message = "Given parameters not sufficient to create PropertyValueHandler";
                throw new IllegalArgumentException(message);
        }
    }

    private PropertyEncryptionMethod findPropertyEncryptionMethod() {
        for (final PropertyEncryptionMethod propertyEncryptionMethod : PropertyEncryptionMethod.values()) {
            if (propertyEncryptionMethod.toString().equals(algorithm)) {
                return propertyEncryptionMethod;
            }
        }
        return null;
    }

    private EncryptionMethod findEncryptionMethod() {
        return EncryptionMethod.forAlgorithm(algorithm);
    }

    @SuppressWarnings("deprecation")
    private PasswordBasedCipherPropertyEncryptor getPasswordBasedCipherPropertyEncryptor(final EncryptionMethod encryptionMethod,
                                                                                         final String password) {
        if (encryptionMethod.isPBECipher()) {
            final PBECipherProvider cipherProvider = new org.apache.nifi.security.util.crypto.NiFiLegacyCipherProvider();
            return new PasswordBasedCipherPropertyEncryptor(cipherProvider, encryptionMethod, password);
        } else {
            final String message = String.format("Algorithm [%s] not supported for Sensitive Properties", encryptionMethod.getAlgorithm());
            throw new UnsupportedOperationException(message);
        }
    }
}
