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
package org.apache.nifi.web.server.util;

import java.nio.file.Path;

public interface ScannerListener {
    /**
     * Called when a file is changed.
     * @param path the Path of the changed file
     */
    public void changed(final Path path);

    /**
     * Called when a file is added.
     * @param path the Path of the added file
     * @throws Exception May be thrown for handling errors
     */
    public void added(final Path path);

    /**
     * Called when a file is removed.
     * @param path the Path of the removed file
     * @throws Exception May be thrown for handling errors
     */
    public void removed(final Path path);
}
