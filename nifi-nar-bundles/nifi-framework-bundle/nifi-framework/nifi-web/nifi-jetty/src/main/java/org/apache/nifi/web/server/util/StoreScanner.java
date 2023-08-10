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

import org.apache.nifi.security.util.TlsConfiguration;
import org.apache.nifi.security.util.TlsException;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedOperation;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.apache.nifi.security.util.SslContextFactory.createSslContext;

/**
 * File Scanner for Keystore or Truststore reloading using provided TLS Configuration
 */
public class StoreScanner extends ContainerLifeCycle implements ScannerListener {
    private static final Logger LOG = Log.getLogger(StoreScanner.class);

    private final SslContextFactory sslContextFactory;
    private final TlsConfiguration tlsConfiguration;
    private final File file;
    private final TestScanner scanner;
    private final String resourceName;

    public StoreScanner(final SslContextFactory sslContextFactory,
                        final TlsConfiguration tlsConfiguration,
                        final Resource resource) {
        this.sslContextFactory = sslContextFactory;
        this.tlsConfiguration = tlsConfiguration;
        this.resourceName = resource.getName();
        try {
            File monitoredFile = resource.getFile();
            if (monitoredFile == null || !monitoredFile.exists()) {
                throw new IllegalArgumentException(String.format("%s file does not exist", resourceName));
            }
            if (monitoredFile.isDirectory()) {
                throw new IllegalArgumentException(String.format("expected %s file not directory", resourceName));
            }

            file = monitoredFile;
            if (LOG.isDebugEnabled()) {
                LOG.debug("File monitoring started {} [{}]", resourceName, monitoredFile);
            }
        } catch (final IOException e) {
            throw new IllegalArgumentException(String.format("could not obtain %s file", resourceName), e);
        }

        File parentFile = file.getParentFile();
        if (!parentFile.exists() || !parentFile.isDirectory()) {
            throw new IllegalArgumentException(String.format("error obtaining %s dir", resourceName));
        }

        scanner = new TestScanner(resourceName + " scanner");
        scanner.setScanDirs(Collections.singletonList(parentFile));
        scanner.setScanInterval(1);
        scanner.addListener(this);
        addBean(scanner);
    }

    @Override
    public void added(final Path path) {
        LOG.debug("Resource [{}] File [{}] added", resourceName, path);
        reloadMatched(path.toString());
    }

    @Override
    public void changed(final Path path) {
        LOG.debug("Resource [{}] File [{}] changed", resourceName, path);
        reloadMatched(path.toString());
    }

    @Override
    public void removed(final Path path) {
        LOG.debug("Resource [{}] File [{}] removed", resourceName, path);
        reloadMatched(path.toString());
    }

    @ManagedOperation(
            value = "Scan for changes in the SSL Keystore/Truststore",
            impact = "ACTION"
    )
    public void scan() {
        LOG.debug("Resource [{}] scanning started", resourceName);

        this.scanner.scan();
        this.scanner.scan();
    }

    @ManagedOperation(value = "Scan for changes in the SSL Keystore", impact = "ACTION")
    public boolean scan(long waitMillis)
    {
        if (LOG.isDebugEnabled()) {
            LOG.debug("scanning");
        }

        CompletableFuture<Boolean> cf = new CompletableFuture<>();
        try {
            // Perform 2 scans to be sure that the scan is stable.
            scanner.scan(Callback.from(() ->
                    scanner.scan(Callback.from(() -> cf.complete(true), cf::completeExceptionally)), cf::completeExceptionally));
            return cf.get(waitMillis, TimeUnit.MILLISECONDS);
        }
        catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @ManagedOperation(
            value = "Reload the SSL Keystore/Truststore",
            impact = "ACTION"
    )
    public void reload() {
        LOG.debug("File [{}] reload started", file);

        try {
            this.sslContextFactory.reload(contextFactory -> contextFactory.setSslContext(createContext()));
            LOG.info("File [{}] reload completed", file);
        } catch (final Throwable t) {
            LOG.warn("File [{}] reload failed", file, t);
        }
    }

    @ManagedAttribute("scanning interval to detect changes which need reloaded")
    public int getScanInterval() {
        return this.scanner.getScanInterval();
    }

    public void setScanInterval(final int scanInterval) {
        this.scanner.setScanInterval(scanInterval);
    }

    private void reloadMatched(final String filename) {
        if (file.toPath().toString().equals(filename)) {
            reload();
        }
    }

    private SSLContext createContext() {
        try {
            return createSslContext(tlsConfiguration);
        } catch (final TlsException e) {
            throw new IllegalArgumentException("Failed to create SSL context with the TLS configuration", e);
        }
    }
}
