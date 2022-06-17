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

import org.apache.nifi.util.NiFiProperties;

import java.util.HashMap;
import java.util.Map;

public class PropertyValueHandlerFactory {
    private static final String SENSITIVE_PROPS_KEY = "nifi.sensitive.props.key";
    private static final String SENSITIVE_PROPS_ALGORITHM = "nifi.sensitive.props.algorithm";

    /**
     * Return the appropriate PropertyValueHandler to use with the given {@link NiFiProperties}
     * @param properties the {@link NiFiProperties} with reference to the algorithm and key to be used for encryption/decryption
     * @return a PropertyHandler
     */
    public static PropertyValueHandler getPropertyValueHandler(final NiFiProperties properties) {
        final PropertyValueHandlerConfigurationContext context = getContext(properties);
        return new PropertyValueHandlerBuilder().setContext(context).build();
    }

    /**
     * Creates a context to can be used to configure the appropriate {@link PropertyValueHandler}
     * @param nifiProperties NiFi properties
     * @return a {@link PropertyValueHandlerConfigurationContext} that can be used to configure a PropertyValueHandler
     */
    private static PropertyValueHandlerConfigurationContext getContext(final NiFiProperties nifiProperties) {
        final Map<String, String> properties = new HashMap<>();
        properties.put(SENSITIVE_PROPS_KEY, nifiProperties.getProperty(SENSITIVE_PROPS_KEY, null));
        properties.put(SENSITIVE_PROPS_ALGORITHM, nifiProperties.getProperty(SENSITIVE_PROPS_ALGORITHM, null));
        return new StandardPropertyValueHandlerConfigurationContext(properties);
    }
}
