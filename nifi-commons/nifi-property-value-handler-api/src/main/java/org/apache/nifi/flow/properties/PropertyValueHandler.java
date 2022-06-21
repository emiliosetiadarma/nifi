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
package org.apache.nifi.flow.properties;

public interface PropertyValueHandler {
    /**
     * Configures the {@link PropertyValueHandler} using the given {@link PropertyValueHandlerConfigurationContext}. In
     * some implementations, this may be a no-op. In implementations where it is not a no-op, the context is required to
     * not be null
     * @param context the {@link PropertyValueHandlerConfigurationContext} to configure the {@link PropertyValueHandler}
     */
    public void onConfigured(final PropertyValueHandlerConfigurationContext context);

    /**
     * Evaluates the property and checks whether the property is encoded
     * @param value the property to check
     * @return true if property is encoded, false otherwise
     */
    public boolean isEncoded(final String value);

    /**
     * Returns a encrypted value that has been wrapped according to the scheme of the specific {@link PropertyValueHandler}
     * @param value a value to be encrypted, wrapped and persisted
     * @return an encoded string value that is ready to be persisted
     */
    public String encode(final String value);

    /**
     * Reads and returns the encrypted value that have been written out using the corresponding encode function
     * @param value A value that has been persisted, wrapped and encrypted by the specific {@link PropertyValueHandler}
     * @return the plaintext property value
     */
    public String decode(final String value);

    /**
     * Returns the format that is used to wrap the encrypted values
     * @return a String representing the format
     */
    public String getFormat();

    /**
     * Returns a Regex String that can be used to check strings for an occurrence of an encoded value
     * @return a Regex String
     */
    public String getRegex();
}
