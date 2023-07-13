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

import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.nifi.security.util.KeyStoreUtils;
import org.apache.nifi.security.util.KeystoreType;
import org.apache.nifi.security.util.StandardTlsConfiguration;
import org.apache.nifi.security.util.TemporaryKeyStoreBuilder;
import org.apache.nifi.security.util.TlsConfiguration;
import org.apache.nifi.security.util.TlsException;
import org.apache.nifi.security.util.TlsPlatform;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.toolchain.test.FS;
import org.eclipse.jetty.toolchain.test.MavenTestingUtils;
import org.eclipse.jetty.toolchain.test.jupiter.WorkDir;
import org.eclipse.jetty.toolchain.test.jupiter.WorkDirExtension;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.apache.nifi.security.util.SslContextFactory.createSslContext;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@ExtendWith(WorkDirExtension.class)
public class StoreScannerSymlinkTest
{
    public WorkDir testdir;
    private Path keystoreDir;
    private StoreScanner keyStoreScanner;
    private TlsConfiguration serverConfiguration;
    private TlsConfiguration oldKeystoreConfiguration;
    private TlsConfiguration newKeystoreConfiguration;
    private SslContextFactory.Server sslContextFactory;

    private static final String HOSTNAME = "localhost";
    private static final String KEYSTORE_PASSWORD = "5a66d958870305c6c39447e1f22fff8f";
    private static final String TRUSTSTORE_PASSWORD = "43b4500097ea70b8d0765347913df4ad";

    @BeforeEach
    public void setupKeystoresAndTruststores() throws Exception {
        keystoreDir = testdir.getEmptyPathDir();

        final Path oldKeystorePath = MavenTestingUtils.getTestResourcePath("oldKeystore.p12");
        final Path truststorePath = MavenTestingUtils.getTestResourcePath("truststore.p12");
        oldKeystoreConfiguration = new StandardTlsConfiguration(
                oldKeystorePath.toString(),
                KEYSTORE_PASSWORD,
                KEYSTORE_PASSWORD,
                KeystoreType.PKCS12,
                truststorePath.toString(),
                TRUSTSTORE_PASSWORD,
                KeystoreType.PKCS12,
                TlsPlatform.getLatestProtocol()
        );

        final Path keystorePath = useKeystore(oldKeystoreConfiguration.getKeystorePath().toString(), "keystore.p12");
        serverConfiguration = new StandardTlsConfiguration(
                keystorePath.toString(),
                KEYSTORE_PASSWORD,
                KEYSTORE_PASSWORD,
                KeystoreType.PKCS12,
                truststorePath.toString(),
                TRUSTSTORE_PASSWORD,
                KeystoreType.PKCS12,
                TlsPlatform.getLatestProtocol()
        );

        final Path newKeystorePath = MavenTestingUtils.getTestResourcePath("newKeystore.p12");
        newKeystoreConfiguration = new StandardTlsConfiguration(
                newKeystorePath.toString(),
                KEYSTORE_PASSWORD,
                KEYSTORE_PASSWORD,
                KeystoreType.PKCS12,
                truststorePath.toString(),
                TRUSTSTORE_PASSWORD,
                KeystoreType.PKCS12,
                TlsPlatform.getLatestProtocol()
        );
    }

    @FunctionalInterface
    public interface Configuration
    {
        void configure(SslContextFactory sslContextFactory) throws Exception;
    }

    public void start() throws Exception
    {
        start(sslContextFactory ->
        {
            sslContextFactory.setKeyStorePath(serverConfiguration.getKeystorePath());
            sslContextFactory.setKeyStorePassword(serverConfiguration.getKeystorePassword());
            sslContextFactory.setKeyManagerPassword(serverConfiguration.getKeyPassword());
            sslContextFactory.setKeyStoreResource(new PathResource(Path.of(serverConfiguration.getKeystorePath())));
        });
    }

    public void start(final Configuration configuration) throws Exception
    {
        sslContextFactory = new SslContextFactory.Server();
        configuration.configure(sslContextFactory);
        final SSLContext sslContext = createContext(serverConfiguration);

        sslContextFactory.setSslContext(sslContext);

        keyStoreScanner = new StoreScanner(sslContextFactory, serverConfiguration, sslContextFactory.getKeyStoreResource());
        keyStoreScanner.start();
    }

    @Test
    public void testKeystoreHotReload() throws Exception
    {
        start();
        final SSLContext prevSSLContext = sslContextFactory.getSslContext();
        useKeystore(newKeystoreConfiguration.getKeystorePath().toString(), "keystore.p12");
        keyStoreScanner.scan(10000);
        final SSLContext nextSSLContext = sslContextFactory.getSslContext();

        assertTrue(prevSSLContext != nextSSLContext);
    }

    public Path useKeystore(String keystoreToUse, String keystorePath) throws Exception
    {
        return useKeystore(MavenTestingUtils.getTestResourcePath(keystoreToUse), keystoreDir.resolve(keystorePath));
    }

    public Path useKeystore(Path keystoreToUse, Path keystorePath) throws Exception
    {
        if (Files.exists(keystorePath))
            Files.delete(keystorePath);

        Files.copy(keystoreToUse, keystorePath);

        if (!Files.exists(keystorePath))
            throw new IllegalStateException("keystore file was not created");

        return keystorePath.toAbsolutePath();
    }

    public Path useKeystore(String keystore) throws Exception
    {
        Path keystorePath = keystoreDir.resolve("keystore");
        if (Files.exists(keystorePath))
            Files.delete(keystorePath);

        Files.copy(MavenTestingUtils.getTestResourcePath(keystore), keystorePath);

        if (!Files.exists(keystorePath))
            throw new IllegalStateException("keystore file was not created");

        return keystorePath.toAbsolutePath();
    }

    private void assumeFileSystemSupportsSymlink() throws IOException
    {
        // Make symlink
        Path dir = MavenTestingUtils.getTargetTestingPath("symlink-test");
        FS.ensureEmpty(dir);

        Path foo = dir.resolve("foo");
        Path bar = dir.resolve("bar");

        try
        {
            Files.createFile(foo);
            Files.createSymbolicLink(bar, foo);
        }
        catch (UnsupportedOperationException | FileSystemException e)
        {
            // if unable to create symlink, no point testing the rest
            // this is the path that Microsoft Windows takes.
            assumeFalse(true, "Not supported");
        }
    }

    private static class DefaultTrustManager implements X509TrustManager
    {
        @Override
        public void checkClientTrusted(X509Certificate[] arg0, String arg1)
        {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] arg0, String arg1)
        {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers()
        {
            return null;
        }
    }

    private SSLContext createContext(final TlsConfiguration tlsConfiguration) {
        try {
            return createSslContext(tlsConfiguration, new TrustManager[] {new DefaultTrustManager()});
        } catch (final TlsException e) {
            throw new IllegalArgumentException("Failed to create SSL context with the TLS configuration", e);
        }
    }
}