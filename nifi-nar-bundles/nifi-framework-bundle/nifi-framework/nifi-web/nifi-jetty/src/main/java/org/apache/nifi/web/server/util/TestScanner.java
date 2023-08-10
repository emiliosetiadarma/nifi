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

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestScanner extends ContainerLifeCycle {
    public static final int DEFAULT_SCAN_DEPTH = 1;
    private static final Logger LOG = LoggerFactory.getLogger(TestScanner.class);

    private final List<ScannerListener> listeners = new CopyOnWriteArrayList<>();

    private final Set<Path> scannables = ConcurrentHashMap.newKeySet();
    private final LinkOption[] linkOptions = new LinkOption[] {LinkOption.NOFOLLOW_LINKS};
    private final Scheduler scheduler;
    private Scheduler.Task task;

    private int scanDepth = DEFAULT_SCAN_DEPTH;
    private int scanInterval;
    private Map<Path, FileMetaData> prevScan;


    public TestScanner(final String name) {
        scheduler = new ScheduledExecutorScheduler(name, true, 1);
        addBean(scheduler);
    }

    @Override
    public void doStart() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Scanner start:, depth={}, interval={}, scannables={}",
                    scanDepth, scanInterval, scannables);
        }

        // register list of existing files and report changes only after this
        prevScan = scanFiles();

        super.doStart();

        schedule();
    }

    private void schedule() {
        if (isRunning() && getScanInterval() > 0) {
            task = scheduler.schedule(new ScanTask(), 1010L * getScanInterval(), TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void doStop() throws Exception {
        Scheduler.Task task = this.task;
        this.task = null;
        if (task != null) {
            task.cancel();
        }
    }

    public void addListener(final ScannerListener listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
    }

    /**
     * Get the scan interval
     *
     * @return interval between scans in seconds
     */
    public int getScanInterval() {
        return scanInterval;
    }

    /**
     * Set the scan interval
     *
     * @param scanInterval pause between scans in seconds, or 0 for no scan after the initial scan.
     */
    public void setScanInterval(final int scanInterval) {
        if (isRunning()) {
            throw new IllegalStateException("Scanner started");
        }

        this.scanInterval = scanInterval;
    }

    public void setScanDirs(final List<File> dirs) {
        if (isRunning()) {
            throw new IllegalStateException("Scanner started");
        }

        scannables.clear();
        if (dirs == null) {
            return;
        }
        for (final File f :dirs) {
            if (f.isDirectory()) {
                addDirectory(f.toPath());
            } else {
                throw new IllegalArgumentException("Given file is not a directory");
            }
        }
    }

    /**
     * Add a directory to be scanned. The directory must not be null and must exist.
     *
     * @param path the directory to scan.
     */
    public void addDirectory(final Path path) {
        if (isRunning()) {
            throw new IllegalStateException("Scanner started");
        }

        if (path == null) {
            throw new IllegalArgumentException("Given path is null");
        }

        try {
            final Path realPath = path.toRealPath(linkOptions);
            if (!Files.exists(realPath) || !Files.isDirectory(realPath)) {
                throw new IllegalStateException("Not directory or doesn't exist: " + path);
            }

            scannables.add(realPath);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Get the scanner to perform a scan cycle as soon as possible
     * and call the Callback when the scan is finished or failed.
     *
     * @param complete called when the scan cycle finishes or fails.
     */
    void scan(final Callback complete) {
        if (!isRunning() || scheduler == null) {
            complete.failed(new IllegalStateException("Scanner not running"));
            return;
        }

        scheduler.schedule(() -> {
            try {
                scan();
                complete.succeeded();
            }
            catch (final Throwable t) {
                complete.failed(t);
            }
        }, 0, TimeUnit.MILLISECONDS);
    }

    public void scan() {
        final Map<Path, FileMetaData> currentScan = scanFiles();
        reportDifferences(currentScan, prevScan == null ? Collections.emptyMap() : Collections.unmodifiableMap(prevScan));
        prevScan = currentScan;
    }

    /**
     * Scans all the paths in the given scannables
     * @return a Map of file paths and metadata regarding those files
     */
    private Map<Path, FileMetaData> scanFiles() {
        final Map<Path, FileMetaData> scanResults = new HashMap<>();
        for (final Path path : scannables) {
            try {
                Files.walkFileTree(
                        path,
                        EnumSet.allOf(FileVisitOption.class),
                        scanDepth,
                        new ScannerFileVisitor(path, scanResults, linkOptions)
                );
            } catch (final IOException e) {
                LOG.warn("Error scanning files.", e);
            }
        }
        return scanResults;
    }

    /**
     * Reports the adds/changes/removes to the Listeners
     * @param currentScan the info from the most recent pass
     * @param oldScan the info from the previous pass
     */
    private void reportDifferences(final Map<Path, FileMetaData> currentScan, final Map<Path, FileMetaData> oldScan) {
        final Map<Path, Notification> changes = new HashMap<>();

        // handle deleted files
        final Set<Path> oldScanKeys = new HashSet<>(oldScan.keySet());
        oldScanKeys.removeAll(currentScan.keySet());
        for (final Path path : oldScanKeys) {
            changes.put(path, Notification.REMOVED);
        }

        // handle new and changed files
        for (final Map.Entry<Path, FileMetaData> entry : currentScan.entrySet()) {
            final FileMetaData current = entry.getValue();
            final FileMetaData previous = oldScan.get(entry.getKey());

            if (previous == null) {
                current.setStatus(Status.ADDED);
            } else if (current.isModified(previous)) {
                if (Status.ADDED.equals(previous.getStatus())) {
                    current.setStatus(Status.ADDED);
                } else {
                    current.setStatus(Status.CHANGED);
                }
            } else {
                if (Status.ADDED.equals(previous.getStatus())) {
                    changes.put(entry.getKey(), Notification.ADDED);
                } else if (Status.CHANGED.equals(previous.getStatus())) {
                    changes.put(entry.getKey(), Notification.CHANGED);
                }
                current.setStatus(Status.STABLE);
            }
        }

        for (final Map.Entry<Path, Notification> entry : changes.entrySet()) {
            switch(entry.getValue()) {
                case ADDED:
                    reportAddition(entry.getKey());
                    break;
                case CHANGED:
                    reportChange(entry.getKey());
                    break;
                case REMOVED:
                    reportRemoval(entry.getKey());
                    break;
                default:
                    LOG.warn("Unknown file change: {}", entry.getValue());
                    break;
            }
        }
    }

    /**
     * Report a file change to the registered listeners
     * @param path the path of the changed file
     */
    private void reportChange(final Path path) {
        if (path == null) {
            return;
        }

        for (final ScannerListener l : listeners) {
            try {
                l.changed(path);
            }
            catch (final Throwable e) {
                LOG.warn("Encountered error reporting removal", e);
            }
        }
    }

    /**
     * Report a file addition to the registered listeners
     * @param path the path
     */
    private void reportAddition(final Path path) {
        for (final ScannerListener l : listeners) {
            try {
                l.added(path);
            }
            catch (final Throwable e) {
                LOG.warn("Encountered error reporting addition", e);
            }
        }
    }

    /**
     * Report a file removal to the registered listeners
     * @param path the path of the removed filename
     */
    private void reportRemoval(final Path path) {
        for (final ScannerListener l : listeners) {
            try {
                l.removed(path);
            }
            catch (final Throwable e) {
                LOG.warn("Encountered error reporting removal", e);
            }
        }
    }

    private class ScanTask implements Runnable {
        @Override
        public void run() {
            scan();
            schedule();
        }
    }
}
