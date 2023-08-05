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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

public class ScannerFileVisitor implements FileVisitor<Path> {
    final Path root;
    final Map<Path, FileMetaData> scanInfoMap;
    final LinkOption[] linkOptions;

    private static final Logger LOG = LoggerFactory.getLogger(ScannerFileVisitor.class);

    public ScannerFileVisitor(final Path root,
                              final Map<Path, FileMetaData> scanInfoMap,
                              final LinkOption[] linkOptions) {
        this.scanInfoMap = scanInfoMap;
        this.root = root;
        this.linkOptions = linkOptions;
    }

    @Override
    public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
        if (!Files.exists(dir)) {
            return FileVisitResult.SKIP_SUBTREE;
        }

        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
        final Path fileRealPath = file.toRealPath(linkOptions);

        if (!Files.exists(fileRealPath)) {
            return FileVisitResult.CONTINUE;
        }

        final File f = fileRealPath.toFile();

        if (f.isFile()) {
            scanInfoMap.put(fileRealPath, new FileMetaData(f.lastModified(), f.isDirectory() ? 0 : f.length()));
        }

        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(final Path file, final IOException exc) throws IOException {
        LOG.warn("FileVisit failed: {}", file, exc);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
        return FileVisitResult.CONTINUE;
    }
}
