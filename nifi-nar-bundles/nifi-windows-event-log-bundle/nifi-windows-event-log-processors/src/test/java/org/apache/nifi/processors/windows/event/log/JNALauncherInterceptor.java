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
package org.apache.nifi.processors.windows.event.log;

import com.sun.jna.platform.win32.Kernel32Util;

import java.util.HashMap;
import java.util.Map;

public class JNALauncherInterceptor extends JNAOverridingLauncherInterceptor {
    public static final String TEST_COMPUTER_NAME = "testComputerName";
    public static final String KERNEL_32_UTIL_CANONICAL_NAME = Kernel32Util.class.getCanonicalName();

    public JNALauncherInterceptor() {
        super();
    }

    @Override
    protected Map<String, Map<String, String>> getClassOverrideMap() {
        final Map<String, Map<String, String>> classOverrideMap = new HashMap<>();

        final Map<String, String> nativeOverrideMap = new HashMap<>();
        nativeOverrideMap.put(LOAD_LIBRARY, "return null;");
        classOverrideMap.put(NATIVE_CANONICAL_NAME, nativeOverrideMap);

        final Map<String, String> kernel32UtilMap = new HashMap<>();
        kernel32UtilMap.put("getComputerName", "return \"" + TEST_COMPUTER_NAME + "\";");
        classOverrideMap.put(KERNEL_32_UTIL_CANONICAL_NAME, kernel32UtilMap);

        return classOverrideMap;
    }
}
