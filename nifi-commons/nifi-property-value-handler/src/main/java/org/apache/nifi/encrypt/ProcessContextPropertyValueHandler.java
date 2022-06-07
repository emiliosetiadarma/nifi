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

/**
 * This class is used by ProcessContext to allow its encrypt and decrypt function. The decode function in particular
 * allows for values that aren't previously encapsulated by the specific PropertyValueHandler pattern to be still decoded
 * and decrypted.
 */
public class ProcessContextPropertyValueHandler implements PropertyValueHandler {
    private PropertyValueHandler handler;

    public ProcessContextPropertyValueHandler(final PropertyValueHandler handler) {
        this.handler = handler;
    }

    @Override
    public boolean isEncoded(final String value) {
        return handler.isEncoded(value);
    }

    @Override
    public String encode(final String value) {
        return handler.encode(value);
    }

    @Override
    public String decode(final String value) {
        if (!isEncoded(value)) {
            // wrapping is done on an unencoded value for the compatibility purposes
            return handler.decode(String.format(getFormat(), value));
        }
        return handler.decode(value);
    }

    @Override
    public String getFormat() {
        return handler.getFormat();
    }

    @Override
    public String getRegex() {
        return handler.getRegex();
    }
}
