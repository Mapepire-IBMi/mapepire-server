package com.github.ibm.mapepire;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.github.ibm.mapepire.SystemConnection.ConnectionMethod;
import com.github.ibm.mapepire.Tracer.TraceLevel;
import com.ibm.as400.access.AS400JDBCDriver;

/** Tests credential handling at the JDBC boundary without connecting to an IBM i. */
@RunWith(Parameterized.class)
public class SystemConnectionTest {
    private static final String JDBC_OPTIONS = "naming=system;secure=true;extended metadata=true";

    private final String m_password;
    private final RecordingDriver m_driver = new RecordingDriver();
    private final List<Driver> m_originalDrivers = new ArrayList<Driver>();
    private SystemConnection m_connection;

    public SystemConnectionTest(final String _label, final String _password) {
        m_password = _password;
    }

    /**
     * Supplies synthetic credentials, using labels so test output never includes passwords.
     *
     * @return cases covering JDBC delimiters and the punctuation reported in issue 3505
     */
    @Parameters(name = "{0}")
    public static Object[][] passwords() {
        return new Object[][] {
            { "alphanumeric", "Synthetic123" },
            { "double quote", "Synthetic\"123" },
            { "paired quotes", "Synthetic\"12\"3" },
            { "single quote", "Synthetic'123" },
            { "percent", "Synthetic%123" },
            { "semicolon", "Synthetic;123" },
            { "colon and equals", "Synthetic:=123" },
            { "backslash", "Synthetic\\123" },
            { "mixed punctuation", "Synthetic?'@$\"%123" }
        };
    }

    /**
     * Installs a recording JDBC driver before the real driver to prevent network access.
     *
     * @throws Exception if the JDBC drivers cannot be registered
     */
    @Before
    public void registerDriver() throws Exception {
        Class.forName(AS400JDBCDriver.class.getName());
        m_originalDrivers.addAll(Collections.list(DriverManager.getDrivers()));
        for (final Driver driver : m_originalDrivers) {
            DriverManager.deregisterDriver(driver);
        }
        DriverManager.registerDriver(m_driver);
    }

    /**
     * Restores the JVM's driver registrations, including after a failing assertion.
     *
     * @throws SQLException if the JDBC drivers cannot be restored
     */
    @After
    public void restoreDrivers() throws SQLException {
        if (null != m_connection) {
            m_connection.close();
        }
        for (final Driver driver : Collections.list(DriverManager.getDrivers())) {
            DriverManager.deregisterDriver(driver);
        }
        for (final Driver driver : m_originalDrivers) {
            DriverManager.registerDriver(driver);
        }
    }

    /**
     * Exercises the production reconnect path and JTOpen's URL parser using synthetic credentials.
     *
     * @throws Exception if connection setup or tracing fails
     */
    @Test
    public void credentialsAreSeparateFromJdbcUrlAndTrace() throws Exception {
        final Tracer tracer = Tracer.getNew().setTraceLevel(TraceLevel.ON);
        m_connection = new SystemConnection("test-client", "127.0.0.1", "localhost", "TESTUSER", m_password.toCharArray(), tracer);
        for (int attempt = 0; attempt < 2; attempt++) {
            assertNotNull(m_connection.reconnect(ConnectionMethod.TCP, JDBC_OPTIONS, "credential regression"));
            assertTrue("JDBC URL should contain only the host and options", ("jdbc:as400:localhost;" + JDBC_OPTIONS).equals(m_driver.m_url));
            assertEquals("TESTUSER", m_driver.m_credentials.getProperty("user"));
            assertTrue("JDBC must receive the original password", m_password.equals(m_driver.m_credentials.getProperty("password")));
            assertEquals("system", m_driver.m_parsedOptions.getProperty("naming"));
            assertEquals("true", m_driver.m_parsedOptions.getProperty("secure"));
            assertEquals("true", m_driver.m_parsedOptions.getProperty("extended metadata"));
        }
        final String trace = tracer.getRawData().toString();
        assertTrue("Connection diagnostics should still be available", trace.contains("Using connection string"));
        assertFalse("Trace must not include credentials", trace.contains("password="));
        assertFalse("Trace must not include the synthetic password", trace.contains("Synthetic"));
    }

    private static final class RecordingDriver implements Driver {
        private String m_url;
        private Properties m_credentials;
        private final Properties m_parsedOptions = new Properties();

        /**
         * Parses the URL with the real JDBC driver, then returns a connection stub. The stub permits
         * client-register setup and closing, but throws if the test unexpectedly executes SQL.
         *
         * @param _url the production JDBC URL
         * @param _info the production connection properties
         * @return a local connection stub
         * @throws SQLException if the JDBC URL cannot be parsed
         */
        @Override
        public Connection connect(final String _url, final Properties _info) throws SQLException {
            m_url = _url;
            m_credentials = new Properties();
            m_credentials.putAll(_info);
            for (final DriverPropertyInfo property : new AS400JDBCDriver().getPropertyInfo(_url, _info)) {
                if (null != property.value) {
                    m_parsedOptions.setProperty(property.name, property.value);
                }
            }
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
                    (final Object _proxy, final Method _method, final Object[] _args) -> {
                        switch (_method.getName()) {
                            case "isClosed":
                            case "isReadOnly":
                                return Boolean.FALSE;
                            case "setClientInfo":
                            case "close":
                                return null;
                            default:
                                throw new UnsupportedOperationException(_method.getName());
                        }
                    });
        }

        @Override
        public boolean acceptsURL(final String _url) {
            return true;
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(final String _url, final Properties _info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }
    }
}
