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

import org.apache.nifi.encrypt.PropertyEncryptor;
import org.apache.nifi.nar.ExtensionManager;

public class PropertyValueHandlerBuilder {
    private PropertyEncryptor encryptor;
    private PropertyValueHandlerConfigurationContext context;
    private ExtensionManager extensionManager;

    public PropertyValueHandlerBuilder() {
        this.encryptor = null;
        this.context = null;
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
     * Set context to be used for PropertyValueHandlerBuilder
     * @param context a {@link PropertyValueHandlerConfigurationContext} that can be used to configure the {@link PropertyValueHandler}
     * @return Property Value Handler Builder
     */
    public PropertyValueHandlerBuilder setContext(final PropertyValueHandlerConfigurationContext context) {
        this.context = context;
        return this;
    }


    /**
     * Set the extension manager to be used for PropertyValueHandlerBuilder. The extension manager will be used for
     * class loading different property value handlers if defined and configured
     * @param extensionManager a {@link ExtensionManager} that can be used to configure the {@link PropertyValueHandler}
     * @return Property Value Handler Builder
     */
    public PropertyValueHandlerBuilder setExtensionManager(final ExtensionManager extensionManager) {
        this.extensionManager = extensionManager;
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

        if (context != null) {
            PropertyValueHandler propertyHandler;
            if (context instanceof StandardPropertyValueHandlerConfigurationContext) {
                if (!context.isValidContext()) {
                    throw new IllegalArgumentException("Given PropertyValueHandler context is not valid");
                }
                propertyHandler = new DefaultPropertyValueHandler();
            } else {
                throw new IllegalArgumentException("Context unrecognized");
            }
            propertyHandler.onConfigured(context);
            return propertyHandler;
        }

        throw new IllegalArgumentException("Given parameters are not a valid set of arguments to create a PropertyValueHandler");
    }
}
