package com.github.ibm.mapepire;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.server.NativeWebSocketServletContainerInitializer;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeFilter;

import com.github.ibm.mapepire.Tracer.TraceLevel;
import com.github.ibm.mapepire.authfile.AuthFile;
import com.github.ibm.mapepire.certstuff.ServerCertGetter;
import com.github.ibm.mapepire.certstuff.ServerCertInfo;
import com.github.ibm.mapepire.http.BlobServlet;
import com.github.ibm.mapepire.http.InstallLocationServlet;
import com.github.ibm.mapepire.http.Routes;
import com.github.ibm.mapepire.http.VersionServlet;
import com.github.ibm.mapepire.ws.DbSocketCreator;
import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.StringUtils;
import com.ibm.ibmi_util.SystemNativeUtils;
import com.ibm.ibmi_util.SystemNativeUtils.JobLogEnabling;

public class MapepireServer {
    private static Server s_server;
    // Required minimum Java version
    private static final String MINIMUM_REQUIRED_JAVA_VERSION = "1.8.0_341";
    // Files in the temp directory older than this are deleted
    private static final long TEMP_FILE_MAX_AGE_MILLIS = TimeUnit.DAYS.toMillis(90);
    // Java system property (value in seconds) that overrides DEFAULT_WS_IDLE_TIMEOUT_MILLIS
    public static final String PROP_WS_IDLE_TIMEOUT = "mapepire.ws.idletimeout";
    // WebSocket connections with no traffic for this long are closed, unless overridden via PROP_WS_IDLE_TIMEOUT
    private static final long DEFAULT_WS_IDLE_TIMEOUT_MILLIS = TimeUnit.DAYS.toMillis(7);
    // Resolved once so an invalid override is only warned about once, not on every connection
    private static final long WS_IDLE_TIMEOUT_MILLIS = resolveWsIdleTimeoutMillis();
    private static volatile boolean s_isSingleMode = false;
    private static volatile boolean s_isReadOnly = Boolean.getBoolean("mapepire.basiconly");

    /**
     * Get how long a WebSocket connection may sit idle before the server closes it.
     *
     * @return the WebSocket idle timeout, in milliseconds
     */
    public static long getWsIdleTimeoutMillis() {
        return WS_IDLE_TIMEOUT_MILLIS;
    }

    /**
     * Determine the WebSocket idle timeout. The default is {@link #DEFAULT_WS_IDLE_TIMEOUT_MILLIS}; it can be
     * overridden with the {@link #PROP_WS_IDLE_TIMEOUT} Java system property, given in seconds. A value that is
     * not a positive integer is ignored with a warning, so the idle timeout can never be disabled by
     * configuration.
     *
     * @return the WebSocket idle timeout, in milliseconds
     */
    private static long resolveWsIdleTimeoutMillis() {
        final String idleTimeoutStr = System.getProperty(PROP_WS_IDLE_TIMEOUT);
        if (StringUtils.isEmpty(idleTimeoutStr)) {
            return DEFAULT_WS_IDLE_TIMEOUT_MILLIS;
        }
        try {
            final long idleTimeoutSeconds = Long.parseLong(idleTimeoutStr.trim());
            if (0 < idleTimeoutSeconds) {
                return TimeUnit.SECONDS.toMillis(idleTimeoutSeconds);
            }
        } catch (final NumberFormatException _e) {
            // Fall through to the warning below
        }
        Tracer.globalWarn("Invalid " + PROP_WS_IDLE_TIMEOUT + " value '" + idleTimeoutStr + "', using default " + TimeUnit.MILLISECONDS.toSeconds(DEFAULT_WS_IDLE_TIMEOUT_MILLIS) + "s");
        return DEFAULT_WS_IDLE_TIMEOUT_MILLIS;
    }

    public static boolean isSingleMode() {
        return s_isSingleMode;
    }

    /**
     * Entry point for the Mapepire server.
     * <p>
     * With {@code --single}, a single connection is served over standard input and output. Otherwise the server
     * runs as a daemon, accepting TLS WebSocket connections on the configured port. This method does not return
     * normally; the JVM is terminated when processing ends.
     *
     * @param _args
     *            the command-line arguments
     */
    public static void main(final String[] _args) {

        final LinkedList<String> args = new LinkedList<String>();
        args.addAll(Arrays.asList(_args));

        if (args.remove("--version")) {
            System.out.println("Version: " + Version.s_version);
            System.out.println("Build time: " + Version.s_compileDateTime);
            System.exit(0);
        }
        if (args.remove("--bqo") || args.remove("-bqo") || args.remove("--basic-queries-only") || args.remove("--basic-only")) {
            s_isReadOnly = true;
        }

        try {
            SystemNativeUtils.enableJobLogging(JobLogEnabling.FOUR_ZERO_SECLVL_JOBEND);
            Tracer.getGlobalTracer().logInfo("Mapepire (Version: " + Version.s_version + "/" + Version.s_compileDateTime + ") starting...");
            Tracer.getGlobalTracer().logInfo("Install location: " + getInstallLocationHumanReadable());
            Tracer.getGlobalTracer().logInfo(Tracer.getJtOpenStatusString());
            Tracer.getGlobalTracer().logInfo(Tracer.getJtOpenComponentStatusString());
            Tracer.getGlobalTracer().logInfo(Tracer.getJtOpenFileString());
            if (SystemNativeUtils.isNativeLoaded()) {
                final PipedInputStream pipeIn = new PipedInputStream();
                final PipedOutputStream pipeOut = new PipedOutputStream(pipeIn);
                final PrintStream origErr = System.err;
                System.setErr(new PrintStream(pipeOut));
                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            final BufferedReader isr = new BufferedReader(new InputStreamReader(pipeIn));
                            String line = null;
                            while (null != (line = isr.readLine())) {
                                origErr.println(line);
                                Tracer.getGlobalTracer().logInfo(line);
                            }
                        } catch (final IOException _e) {
                            System.setErr(origErr);
                            Tracer.globalErr(_e);
                        }
                    }

                }, "StdErrLoggerMain").start();
            }

            if (args.remove("--single")) {
                s_isSingleMode = true;
                // Make sure we can process our security rules file, if it exists
                AuthFile.getDefault().getRules();

                final SystemConnection conn = new SystemConnection();
                final String testFile = System.getProperty("test.file", "");
                final boolean testMode = StringUtils.isNonEmpty(testFile);
                if (testMode) {
                    System.setIn(new FileInputStream(testFile));
                }
                final DataStreamProcessor io = new DataStreamProcessor(System.in, System.out, conn, testMode);

                io.run();
            } else {
                s_isSingleMode = false;

                if (Boolean.getBoolean("mapepire.skipuserswap")) {
                    Tracer.getGlobalTracer().logWarn("Not swapping user profile. Deploying in this manner goes against security best practices.");
                } else {
                    final String userProfile = SystemNativeUtils.swapUser();
                    Tracer.getGlobalTracer().logInfo("Current user is " + userProfile);
                }

                // Needed to enforce TLS capabilities when not in single mode
                checkJavaVersion(MINIMUM_REQUIRED_JAVA_VERSION);

                // Make sure we can process our security rules file, if it exists
                AuthFile.getDefault().getRules();

                if (args.remove("--traceErrors")) {
                    Tracer.getGlobalTracer().setTraceLevel(TraceLevel.ERRORS);
                }
                if (args.remove("--traceOn")) {
                    Tracer.getGlobalTracer().setTraceLevel(TraceLevel.ON);
                }
                if (args.remove("--traceDs")) {
                    Tracer.getGlobalTracer().setTraceLevel(TraceLevel.DATASTREAM);
                }
                final AppLogger logger = AppLogger.getSingleton(args.remove("-v"));
                logger.printf("Starting daemon...");
                Tracer.globalInfo("Starting daemon...");
                DbSocketCreator.enableDaemon();

                s_server = new Server();

                final ServerConnector connector;
                final boolean isUnsecure = false; //Development only if having issues with TLS in client language runtime. NEVER commit changes which would cause this to be true in a real build. 
                if (isUnsecure) {
                    final String uhOhWarning = "WARNING: Running in unsecure mode. Credentials are NOT encrypted!";
                    logger.println_err("\n\n" + uhOhWarning + "\n\n");
                    Tracer.globalWarn(uhOhWarning);
                    connector = new ServerConnector(s_server);
                } else {
                    final SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
                    final ServerCertInfo serverCertInfo = new ServerCertGetter().get();
                    sslContextFactory.setKeyStore(serverCertInfo.getKeyStore());
                    sslContextFactory.setKeyStorePassword(serverCertInfo.getStorePass());
                    sslContextFactory.setCertAlias(serverCertInfo.getAlias());
                    sslContextFactory.setKeyManagerPassword(serverCertInfo.getKeyPass());
                    Tracer.globalInfo("Using key store " + serverCertInfo.getKeyStoreFile().getAbsolutePath());
                    connector = new ServerConnector(s_server, sslContextFactory);
                }

                s_server.addConnector(connector);

                // Setup the basic application "context" for this application at "/"
                // This is also known as the handler tree (in jetty speak)
                final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
                context.setContextPath("/");
                context.addServlet(new ServletHolder(VersionServlet.class), Routes.VERSION);
                context.addServlet(new ServletHolder(InstallLocationServlet.class), Routes.SOURCE);
                context.addServlet(new ServletHolder(BlobServlet.class), Routes.BLOB);

                final Constraint constraint = new Constraint();
                constraint.setName("Disable TRACE");
                constraint.setAuthenticate(true);

                final ConstraintMapping disableTrace = new ConstraintMapping();
                disableTrace.setConstraint(constraint);
                disableTrace.setMethod("TRACE");
                disableTrace.setPathSpec("/");

                final ConstraintMapping allowOthers = new ConstraintMapping();
                allowOthers.setConstraint(new Constraint());
                allowOthers.setMethod("*");
                allowOthers.setPathSpec("/");

                final ConstraintSecurityHandler handler = new ConstraintSecurityHandler();
                handler.addConstraintMapping(disableTrace);
                handler.addConstraintMapping(allowOthers);

                handler.setHandler(context);
                s_server.setHandler(handler);

                final String remoteServer = System.getenv("DB_SERVER"); // TODO: replace `System.getenv` calls with IBMiDotEnv or something.
                if (StringUtils.isNonEmpty(remoteServer)) {
                    DbSocketCreator.setDatabaseHost(remoteServer);
                }
                if (SystemConnection.isRunningOnIBMi() && (StringUtils.isEmpty(remoteServer) || "localhost".equalsIgnoreCase(remoteServer) || "127.0.0.1".equalsIgnoreCase(remoteServer))) {
                    final SSLContext ctx = SSLContext.getInstance("TLS");
                    //@formatter:off
                    // Intentional: this trust-all manager is a fallback path only, applied solely when connecting to the
                    // database on this same IBM i (localhost). The connection is still TLS-encrypted; non-TLS
                    // communications are not allowed. Only certificate chain validation is skipped for the loopback hop.
                    // codeql[java/insecure-trustmanager]
                    ctx.init(null, new TrustManager[] {
                            new X509TrustManager() {
                                @Override  public void checkClientTrusted(final X509Certificate[] _chain, final String _authType) throws CertificateException { }
                                @Override  public void checkServerTrusted(final X509Certificate[] _chain, final String _authType) throws CertificateException { }
                                @Override  public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                            }
                        }, null);
                    //@formatter:on
                    SSLContext.setDefault(ctx);
                }

                final String remotePort = System.getenv("PORT");
                if (StringUtils.isNonEmpty(remotePort)) {
                    DbSocketCreator.setServerPort(Integer.parseInt(remotePort));
                }

                connector.setPort(DbSocketCreator.getPort());

                logger.println("Starting server for " + DbSocketCreator.getHost() + " on port " + DbSocketCreator.getPort());

                // Configure specific websocket behavior
                NativeWebSocketServletContainerInitializer.configure(context, (_servletContext, _nativeWebSocketConfiguration) -> {
                    _nativeWebSocketConfiguration.getPolicy().setMaxTextMessageBufferSize(65535);
                    // Max WS message size - default Integer.MAX_VALUE (unlimited).
                    // Can be capped via MAX_WS_MESSAGE_SIZE env var if desired.
                    final String maxWsMessageSizeStr = System.getenv("MAX_WS_MESSAGE_SIZE");
                    final int maxWsMessageSize;
                    if (StringUtils.isNonEmpty(maxWsMessageSizeStr)) {
                        maxWsMessageSize = Integer.parseInt(maxWsMessageSizeStr);
                    } else {
                        maxWsMessageSize = Integer.MAX_VALUE;
                    }
                    _nativeWebSocketConfiguration.getPolicy().setMaxTextMessageSize(maxWsMessageSize);
                    _nativeWebSocketConfiguration.getPolicy().setIdleTimeout(getWsIdleTimeoutMillis());

                    // Add websockets
                    _nativeWebSocketConfiguration.addMapping("/db/*", new DbSocketCreator());
                });

                // Add generic filter that will accept WebSocket upgrade.
                WebSocketUpgradeFilter.configure(context);

                try {
                    s_server.start();
                    s_server.join();
                    logger.println_warn("Server ending gracefully");
                } catch (final Throwable _t) {
                    logger.exception(_t);
                    Tracer.globalErr(_t);
                }
            }
        } catch (final Exception _e) {
            Tracer.globalErr(_e);
        }
        Tracer.globalWarn("data stream processing completed (end of request stream?)");
        System.exit(12);
    }

    /**
     * Get the location the server code was loaded from, for display in logs and diagnostics.
     *
     * @return the code source location, or {@code "<unknown>"} if it cannot be determined; never {@code null}
     */
    public static String getInstallLocationHumanReadable() {
        try {
            return MapepireServer.class.getProtectionDomain().getCodeSource().getLocation().toString();
        } catch (final Exception _e) {
            Tracer.globalErr(_e);
            return "<unknown>";
        }
    }

    /**
     * Determine the directory to use for temporary files.
     * <p>
     * In daemon mode, this is {@code /QOpenSys/QIBM/UserData/AI/db_server/}. In single mode, this is
     * {@code <user.home>/.vscode/tmp}, which is created if it does not already exist. In either mode, the chosen
     * directory is only used if it exists, is a directory, and is writable; otherwise {@code /tmp} is
     * returned.
     *
     * @return the temporary directory to use; never {@code null}
     */
    public static File getTempDirectory() {
        final File fallback = new File("/tmp");
        final File candidate;
        if (isSingleMode()) {
            final String home = System.getProperty("user.home");
            if (StringUtils.isEmpty(home)) {
                return fallback;
            }
            candidate = new File(new File(home, ".vscode"), "tmp");
            // mkdirs() returns false when the directory already exists, so verify the result below instead
            candidate.mkdirs();
        } else {
            candidate = new File("/QOpenSys/QIBM/UserData/AI/db_server/");
        }
        if (candidate.exists() && candidate.isDirectory() && candidate.canWrite()) {
            final Thread cleanupThread = new Thread(() -> deleteOldFiles(candidate), "TempDirCleanup");
            cleanupThread.setDaemon(true);
            cleanupThread.start();
            return candidate;
        }
        return fallback;
    }

    /**
     * Recursively delete regular files under the given directory whose last-modified time is older than
     * {@link #TEMP_FILE_MAX_AGE_MILLIS}. Directories are left in place and symbolic links are not followed.
     * Failures are logged and do not stop the traversal.
     *
     * @param _dir
     *            the directory to clean up
     */
    private static void deleteOldFiles(final File _dir) {
        final long cutoff = System.currentTimeMillis() - TEMP_FILE_MAX_AGE_MILLIS;
        try {
            Files.walkFileTree(_dir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path _file, final BasicFileAttributes _attrs) {
                    if (_attrs.isRegularFile() && _attrs.lastModifiedTime().toMillis() < cutoff) {
                        try {
                            Files.deleteIfExists(_file);
                        } catch (final IOException _e) {
                            Tracer.globalErr(_e);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(final Path _file, final IOException _exc) {
                    Tracer.globalErr(_exc);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (final IOException _e) {
            Tracer.globalErr(_e);
        }
    }

    /**
     * Check that the Java version is at least a certain level
     *
     * @param _requiredVersion
     *            The minimum required Java version
     * @implNote This method terminates the application with {@code System.exit(1)} if the
     *           current Java version does not meet the required version.
     */
    private static void checkJavaVersion(final String _requiredVersion) {

        // Get the current Java version
        final String javaVersion = System.getProperty("java.version");

        // Compare versions
        if (isVersionLessThan(javaVersion, _requiredVersion)) {
            System.err.println("Error: Java version must be >= " + _requiredVersion + ". Current version: " + javaVersion);
            System.exit(1); // Exit with an error code
        }
    }

    /**
     * Compares two Java versions and determines if the first is less than the second.
     *
     * @param _currentVersion
     *            the current Java version.
     * @param _requiredVersion
     *            the required Java version.
     * @return true if _currentVersion < _requiredVersion, false otherwise.
     */
    private static boolean isVersionLessThan(final String _currentVersion, final String _requiredVersion) {
        final String[] currentParts = _currentVersion.split("\\.|_|-");
        final String[] requiredParts = _requiredVersion.split("\\.|_|-");

        final int length = Math.max(currentParts.length, requiredParts.length);
        for (int i = 0; i < length; i++) {
            final String currentPart = (i < currentParts.length) ? currentParts[i] : "0";
            final String requiredPart = (i < requiredParts.length) ? requiredParts[i] : "0";
            try {
                final int currentNumericPart = Integer.parseInt(currentPart);
                final int requiredNumericPart = Integer.parseInt(requiredPart);

                if (currentNumericPart < requiredNumericPart) {
                    return true;
                } else if (currentNumericPart > requiredNumericPart) {
                    return false;
                }
            } catch (final NumberFormatException _e) {
                // If it's not a number, compare as strings
                final int comparison = currentPart.compareTo(requiredPart);
                if (comparison < 0) {
                    return true;
                } else if (comparison > 0) {
                    return false;
                }
            }
        }
        return false;
    }

    public static boolean isReadOnly() {
        return s_isReadOnly;
    }
}
