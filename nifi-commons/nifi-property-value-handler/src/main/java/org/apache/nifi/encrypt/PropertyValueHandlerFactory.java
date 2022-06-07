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

public class PropertyValueHandlerFactory {
    /**
     * Return the appropriate PropertyValueHandler to use with the given {@link NiFiProperties}
     * @param properties the {@link NiFiProperties} with reference to the algorithm and key to be used for encryption/decryption
     * @return a PropertyHandler
     */
    public static PropertyValueHandler getPropertyValueHandler(final NiFiProperties properties) {
        return new PropertyValueHandlerBuilder().setNifiProperties(properties).build();
    }
}
