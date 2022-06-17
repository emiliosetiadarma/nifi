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

package org.apache.nifi.registry.flow.mapping;

import org.apache.nifi.flow.properties.PropertyValueHandler;

import static java.util.Objects.requireNonNull;

public class FlowMappingOptions {
    private final PropertyValueHandler handler;
    private final VersionedComponentStateLookup stateLookup;
    private final ComponentIdLookup componentIdLookup;
    private final boolean mapPropertyDescriptors;
    private final boolean mapSensitiveConfiguration;
    private final boolean mapInstanceIds;
    private final boolean mapControllerServiceReferencesToVersionedId;

    private FlowMappingOptions(final Builder builder) {
        handler = builder.handler;
        stateLookup = builder.stateLookup;
        componentIdLookup = builder.componentIdLookup;
        mapPropertyDescriptors = builder.mapPropertyDescriptors;
        mapSensitiveConfiguration = builder.mapSensitiveConfiguration;
        mapInstanceIds = builder.mapInstanceId;
        mapControllerServiceReferencesToVersionedId = builder.mapControllerServiceReferencesToVersionedId;
    }

    public PropertyValueHandler getHandler() {
        return handler;
    }

    public VersionedComponentStateLookup getStateLookup() {
        return stateLookup;
    }

    public ComponentIdLookup getComponentIdLookup() {
        return componentIdLookup;
    }

    public boolean isMapPropertyDescriptors() {
        return mapPropertyDescriptors;
    }

    public boolean isMapSensitiveConfiguration() {
        return mapSensitiveConfiguration;
    }

    public boolean isMapInstanceIdentifiers() {
        return mapInstanceIds;
    }

    public boolean isMapControllerServiceReferencesToVersionedId() {
        return mapControllerServiceReferencesToVersionedId;
    }

    public static class Builder {
        private PropertyValueHandler handler;
        private VersionedComponentStateLookup stateLookup;
        private ComponentIdLookup componentIdLookup;
        private boolean mapPropertyDescriptors;
        private boolean mapSensitiveConfiguration;
        private boolean mapInstanceId = false;
        private boolean mapControllerServiceReferencesToVersionedId = true;

        /**
         * Sets the PropertyValueHandler to use for encrypting/decrypting sensitive values. This value must be set
         * if {@link #mapSensitiveConfiguration(boolean) mapSensitiveConfiguration} is set to <code>true</code>.
         *
         * @param handler the PropertyValueHandler to use
         * @return the builder
         */
        public Builder propertyValueHandler(final PropertyValueHandler handler) {
            this.handler = handler;
            return this;
        }

        /**
         * Sets the State Lookup to use. When a component is mapped to a Versioned Component, this is used to determine
         * which ScheduledState should be assigned to the VersionedComponent
         *
         * @param stateLookup the State Lookup to use
         * @return the builder
         */
        public Builder stateLookup(final VersionedComponentStateLookup stateLookup) {
            this.stateLookup = stateLookup;
            return this;
        }

        /**
         * Sets the ComponentIdLookup to use. Given an existing component, the Component ID Lookup can be used to determine
         * how the component's identifier and its (optional) versioned component identifier should be used to derive an identifier
         * for the Versioned Component
         *
         * @param componentIdLookup the Component ID Lookup to use
         * @return the builder
         */
        public Builder componentIdLookup(final ComponentIdLookup componentIdLookup) {
            this.componentIdLookup = componentIdLookup;
            return this;
        }

        /**
         * Sets whether or not to map the component's Property Descriptors to the Versioned Component. If <code>false</code>, the Property Descriptors
         * will not be set for components such as Processor, Controller Services, and Reporting Tasks.
         *
         * @param mapPropertyDescriptors whether or not to map property descriptors
         * @return the builder
         */
        public Builder mapPropertyDescriptors(final boolean mapPropertyDescriptors) {
            this.mapPropertyDescriptors = mapPropertyDescriptors;
            return this;
        }

        /**
         * Sets whether or not to map sensitive values. If <code>true</code>, the {@link #propertyValueHandler(PropertyValueHandler)} must be set
         *
         * @param mapSensitiveConfiguration whether or not sensitive values should be mapped
         * @return the builder
         */
        public Builder mapSensitiveConfiguration(final boolean mapSensitiveConfiguration) {
            this.mapSensitiveConfiguration = mapSensitiveConfiguration;
            return this;
        }

        /**
         * Sets whether or not the Versioned Components' Instance Identifiers should be populated
         *
         * @param mapInstanceIdentifiers whether or not to map a component's identifier to the VersionedComponent's instanceId
         * @return the builder
         */
        public Builder mapInstanceIdentifiers(final boolean mapInstanceIdentifiers) {
            this.mapInstanceId = mapInstanceIdentifiers;
            return this;
        }

        /**
         * Specifies how Controller Service references should be mapped. If Processor A references Controller Service B, and this value is
         * set to <code>true</code>, the VersionedProcessor will have a property that references the Versioned Component ID for the Controller Service.
         * If set to <code>false</code>, the VersionedProcessor's property value will match that of the processor itself, mapping to the ID of the
         * instantiated Controller Service.
         *
         * @param mapControllerServiceReferencesToVersionedId whether or not to map Controller Service References to hte Versioned Component ID
         * @return the builder
         */
        public Builder mapControllerServiceReferencesToVersionedId(final boolean mapControllerServiceReferencesToVersionedId) {
            this.mapControllerServiceReferencesToVersionedId = mapControllerServiceReferencesToVersionedId;
            return this;
        }

        /**
         * Creates a FlowMappingOptions object, or throws an Exception if not all required configuration has been provided
         *
         * @return the FlowMappingOptions
         * @throws NullPointerException if the {@link #stateLookup(VersionedComponentStateLookup) StateLookup} is not set, the
         * {@link #componentIdLookup(ComponentIdLookup) ComponentIdLookup} is not set, or if {@link #mapSensitiveConfiguration(boolean) mapSensitiveConfiguration}
         * is set to true but the {@link #propertyValueHandler(PropertyValueHandler) propertyValueHandler} has not been set
         */
        public FlowMappingOptions build() {
            requireNonNull(stateLookup, "State Lookup must be set");
            requireNonNull(componentIdLookup, "Component ID Lookup must be set");

            if (mapSensitiveConfiguration) {
                requireNonNull(handler, "Property Value Handler must be set when sensitive configuration is to be mapped");
            }

            return new FlowMappingOptions(this);
        }
    }

    /**
     * The Default Options are acceptable for sharing a dataflow externally (outside of a given nifi instance or cluster), such as sharing
     * a dataflow to a NiFi Registry.
     */
    public static final FlowMappingOptions DEFAULT_OPTIONS = new Builder()
        .propertyValueHandler(null)
        .stateLookup(VersionedComponentStateLookup.ENABLED_OR_DISABLED)
        .componentIdLookup(ComponentIdLookup.VERSIONED_OR_GENERATE)
        .mapPropertyDescriptors(true)
        .mapSensitiveConfiguration(false)
        .mapInstanceIdentifiers(false)
        .mapControllerServiceReferencesToVersionedId(true)
        .build();

}
