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
package org.apache.nifi.flow.encryptor;

import org.apache.nifi.encrypt.PassthroughPropertyValueHandler;
import org.apache.nifi.encrypt.PropertyValueHandler;
import org.apache.nifi.encrypt.PropertyValueHandlerBuilder;
import org.apache.nifi.encrypt.PropertyValueHandlerConfigurationContext;
import org.apache.nifi.encrypt.StandardPropertyValueHandlerConfigurationContext;
import org.apache.nifi.security.util.EncryptionMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StandardFlowEncryptorTest {
    private static final String INPUT_KEY = UUID.randomUUID().toString();

    private static final String OUTPUT_KEY = UUID.randomUUID().toString();

    private static final String INPUT_ALGORITHM = EncryptionMethod.MD5_256AES.getAlgorithm();

    private static final String OUTPUT_ALGORITHM = EncryptionMethod.SHA256_256AES.getAlgorithm();

    private StandardFlowEncryptor flowEncryptor;

    private PropertyValueHandler inputHandler;

    private PropertyValueHandler outputHandler;


    @BeforeEach
    public void setHandlers() {
        inputHandler = getPropertyValueHandler(INPUT_KEY, INPUT_ALGORITHM);
        outputHandler = getPropertyValueHandler(OUTPUT_KEY, OUTPUT_ALGORITHM);
        flowEncryptor = new StandardFlowEncryptor();
    }

    @Test
    public void testProcessEncrypted() {
        final String property = StandardFlowEncryptorTest.class.getSimpleName();

        final String encryptedProperty = inputHandler.encode(property);
        final String encryptedRow = String.format("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>%n" +
                "<test>%s</test>", encryptedProperty);

        final InputStream inputStream = new ByteArrayInputStream(encryptedRow.getBytes(StandardCharsets.UTF_8));
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        flowEncryptor.processFlow(inputStream, outputStream, inputHandler, outputHandler);

        final String outputEncrypted = new String(outputStream.toByteArray());

        final Matcher matcher = Pattern.compile(outputHandler.getRegex()).matcher(outputEncrypted);
        assertTrue(matcher.find(), String.format("Encrypted Pattern not found [%s]", outputEncrypted));

        final String outputDecrypted = outputHandler.decode(matcher.group());
        assertEquals(property, outputDecrypted);
    }

    @Test
    public void testProcessNoEncrypted() {
        final String property = String.format("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>%n" +
                "<test>%s</test>", StandardFlowEncryptorTest.class.getSimpleName());

        final InputStream inputStream = new ByteArrayInputStream(property.getBytes(StandardCharsets.UTF_8));
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        flowEncryptor.processFlow(inputStream, outputStream, inputHandler, outputHandler);

        final String outputProperty = outputStream.toString();
        assertEquals(removeXmlDeclaration(property).trim(), removeXmlDeclaration(outputProperty).trim());
    }

    @Test
    public void testProcessJson() throws IOException {
        final String password = StandardFlowEncryptorTest.class.getSimpleName();
        final String encryptedPassword = inputHandler.encode(password);

        final String sampleFlowJson = getSampleFlowJson(encryptedPassword);

        try (final InputStream inputStream = new ByteArrayInputStream(sampleFlowJson.getBytes(StandardCharsets.UTF_8));) {
            try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();) {
                flowEncryptor.processFlow(inputStream, outputStream, inputHandler, outputHandler);

                final String outputFlowJson = outputStream.toString();

                compareFlow(sampleFlowJson.trim(), outputFlowJson.trim());
            }
        }
    }

    @Test
    public void testProcessXml() throws IOException {
        final String password = StandardFlowEncryptorTest.class.getSimpleName();
        final String encryptedPassword = inputHandler.encode(password);
        final String sampleFlowXml = getSampleFlowXml(encryptedPassword);
        try (final InputStream inputStream = new ByteArrayInputStream(sampleFlowXml.getBytes(StandardCharsets.UTF_8))) {
            try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                flowEncryptor.processFlow(inputStream, outputStream, inputHandler, outputHandler);
                final String outputXml = new String(outputStream.toByteArray());

                compareFlow(removeXmlDeclaration(sampleFlowXml).trim(), removeXmlDeclaration(outputXml).trim());
            }
        }
    }

    private PropertyValueHandler getPropertyValueHandler(final String propertiesKey, final String propertiesAlgorithm) {
        final PropertyValueHandlerConfigurationContext context = new StandardPropertyValueHandlerConfigurationContext(propertiesKey, propertiesAlgorithm);
        return new PropertyValueHandlerBuilder().setContext(context).build();
    }

    private void compareFlow(final String sampleFlow, final String outputFlow) {
        final Matcher inputMatcher = Pattern.compile(inputHandler.getRegex()).matcher(sampleFlow);
        final Matcher outputMatcher = Pattern.compile(outputHandler.getRegex()).matcher(outputFlow);
        assertTrue(inputMatcher.find() && outputMatcher.find());
        assertEquals(inputHandler.decode(inputMatcher.group()), outputHandler.decode(outputMatcher.group()));

        assertEquals(sampleFlow.replaceAll(inputHandler.getRegex(), ""),
                outputFlow.replaceAll(outputHandler.getRegex(), ""));
    }

    private String getSampleFlowJson(final String password) {
        Objects.requireNonNull(password);
        return String.format("{\"properties\":{\"username\":\"sample_username\",\"password\":\"%s\"}}", password);
    }

    private String getSampleFlowXml(final String password) {
        Objects.requireNonNull(password);
        final String flowXml = String.format("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>%n" +
                "<processor>%n" +
                "\t<property>%n" +
                "\t\t<name>Username</name>%n" +
                "\t\t<value>SAMPLE_USERNAME</value>%n" +
                "\t</property>%n" +
                "\t<property>%n" +
                "\t\t<name>Password</name>%n" +
                "\t\t<value>%s</value>%n" +
                "\t</property>%n" +
                "</processor>", password);

        return getProcessedFlowXml(flowXml);
    }

    private String getProcessedFlowXml(final String flowXml) {
        final PassthroughPropertyValueHandler handler = new PassthroughPropertyValueHandler() {
            @Override
            public String encode(final String property) {
                return property;
            }

            @Override
            public String decode(final String encryptedProperty) {
                return encryptedProperty;
            }
        };
        final InputStream inputStream = new ByteArrayInputStream(flowXml.getBytes(StandardCharsets.UTF_8));
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        flowEncryptor.processFlow(inputStream, outputStream, handler, handler);
        return outputStream.toString();
    }

    private String removeXmlDeclaration(final String xmlFlow) {
        return xmlFlow.replaceAll("<\\?xml.+\\?>", "");
    }
}
