package com.github.ibm.mapepire;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;

import com.github.ibm.mapepire.authfile.AuthFile;
import com.github.ibm.mapepire.authfile.AuthRule;
import com.github.ibm.mapepire.authfile.AuthRule.RuleType;
import com.github.theprez.jcmdutils.StringUtils;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400JDBCConnection;
import com.ibm.as400.access.AS400JDBCDriver;

public class SystemConnection {
    // System property names
    private static final String PROP_DEBUG_USER = "mapepire.debug.user";
    private static final String PROP_DEBUG_HOST = "mapepire.debug.host";
    private static final String PROP_DEBUG_PASSWORD = "mapepire.debug.pw";
    private static final String PROP_JDBC_AUTOCONNECT = "codeserver.jdbc.autoconnect";
    private static final String PROP_RESTRICTED_LOCAL_CONNECTION = "jdbc.db2.restricted.local.connection.only";
    private static final String PROP_DISABLE_BUILTINS_IN_BASIC_QUERY = "mapepire.bqo.disablebuiltins";

    // Error messages and codes (client-visible; do not change the values)
    private static final String IMPROPER_USAGE_MESSAGE = "Improper usage";
    private static final String INVALID_USERNAME_MESSAGE = "Invalid Username";
    private static final String INVALID_PASSWORD_MESSAGE = "Invalid Password";
    private static final String BASIC_QUERY_ONLY_MESSAGE = "Only basic queries are allowed";
    private static final String SQLSTATE_NOT_AUTHORIZED = "42505";
    private static final int BASIC_QUERY_ONLY_VENDOR_CODE = -99999;

    private static final String READ_ONLY_JDBC_PROPERTY = "access=read only";

    // Built-in functions from "Chapter 4. Built-in functions" of the Db2 for i SQL Reference, IBM i 7.3
    //@formatter:off
    private static final Set<String> s_builtinFunctionsAllowedInBasicQuery = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            // Aggregate functions
            "ARRAY_AGG", "AVG", "CORR", "CORRELATION", "COUNT", "COUNT_BIG", "COVARIANCE", "COVAR", "COVAR_SAMP", "COVARIANCE_SAMP",
            "GROUPING", "JSON_ARRAYAGG", "JSON_OBJECTAGG", "LISTAGG", "MAX", "MEDIAN", "MIN", "PERCENTILE_CONT", "PERCENTILE_DISC",
            "REGR_AVGX", "REGR_AVGY", "REGR_COUNT", "REGR_ICPT", "REGR_INTERCEPT", "REGR_R2", "REGR_SLOPE", "REGR_SXX", "REGR_SXY", "REGR_SYY",
            "STDDEV_POP", "STDDEV", "STDDEV_SAMP", "SUM", "VAR_POP", "VARIANCE", "VAR", "VAR_SAMP", "VARIANCE_SAMP", "XMLAGG", "XMLGROUP",
            // Scalar functions
            "ABS", "ABSVAL", "ACOS", "ADD_MONTHS", "ANTILOG", "ARRAY_MAX_CARDINALITY", "ARRAY_TRIM", "ASCII", "ASIN", "ATAN", "ATANH", "ATAN2",
            "BASE64_DECODE", "BASE64_ENCODE", "BIGINT", "BINARY", "BITAND", "BITANDNOT", "BITOR", "BITXOR", "BITNOT", "BIT_LENGTH", "BLOB",
            "BSON_TO_JSON", "CARDINALITY", "CEILING", "CEIL", "CHAR", "CHARACTER_LENGTH", "CHAR_LENGTH", "CHR", "CLOB", "COALESCE",
            "COMPARE_DECFLOAT", "CONCAT", "CONTAINS", "COS", "COSH", "COT", "CURDATE", "CURTIME", "DATABASE", "DATAPARTITIONNAME",
            "DATAPARTITIONNUM", "DATE", "DAY", "DAYNAME", "DAYOFMONTH", "DAYOFWEEK", "DAYOFWEEK_ISO", "DAYOFYEAR", "DAYS", "DBCLOB",
            "DBPARTITIONNAME", "DBPARTITIONNUM", "DECFLOAT", "DECFLOAT_FORMAT", "DECFLOAT_SORTKEY", "DECIMAL", "DEC", "DECRYPT_BIT",
            "DECRYPT_BINARY", "DECRYPT_CHAR", "DECRYPT_DB", "DEGREES", "DIFFERENCE", "DIGITS", "DLCOMMENT", "DLLINKTYPE", "DLURLCOMPLETE",
            "DLURLPATH", "DLURLPATHONLY", "DLURLSCHEME", "DLURLSERVER", "DLVALUE", "DOUBLE_PRECISION", "DOUBLE", "ENCRYPT_AES", "ENCRYPT_RC2",
            "ENCRYPT", "ENCRYPT_TDES", "EXP", "EXTRACT", "FLOAT", "FLOOR", "GENERATE_UNIQUE", "GET_BLOB_FROM_FILE", "GET_CLOB_FROM_FILE",
            "GET_DBCLOB_FROM_FILE", "GET_XML_FILE", "GETHINT", "GRAPHIC", "GREATEST", "HASH", "HASHED_VALUE", "HEX", "HEXTORAW", "HOUR",
            // Excluded: HTTP functions can send data off the system or change remote state, which read-only access does not prevent
            // "HTTP_DELETE", "HTTP_GET", "HTTP_PATCH", "HTTP_POST", "HTTP_PUT",
            "IDENTITY_VAL_LOCAL", "IFNULL", "INSERT", "INTEGER", "INT",
            "INTERPRET", "INSTR", "JSON_ARRAY", "JSON_OBJECT", "JSON_QUERY", "JSON_TO_BSON", "JSON_VALUE", "JULIAN_DAY", "LAND", "LAST_DAY",
            "LCASE", "LEAST", "LEFT", "LENGTH", "LN", "LNOT", "LOCATE", "LOCATE_IN_STRING", "LOG10", "LOR", "LOWER", "LPAD", "LTRIM",
            "MAX_CARDINALITY", "MICROSECOND", "MIDNIGHT_SECONDS", "MINUTE", "MOD", "MONTH", "MONTHNAME", "MONTHS_BETWEEN", "MQREAD",
            "MQREADCLOB",
            // Excluded: MQSEND writes a message and MQRECEIVE* removes messages from the queue, which read-only access does not prevent
            // "MQRECEIVE", "MQRECEIVECLOB", "MQSEND",
            "MULTIPLY_ALT", "NEXT_DAY", "NORMALIZE_DECFLOAT", "NOW", "NULLIF", "NVL",
            "OCTET_LENGTH", "OVERLAY", "PI", "POSITION", "POSSTR", "POWER", "POW", "QUANTIZE", "QUARTER", "RADIANS", "RAISE_ERROR", "RANDOM",
            "RAND", "REAL", "REGEXP_COUNT", "REGEXP_INSTR", "REGEXP_REPLACE", "REGEXP_SUBSTR", "REPEAT", "REPLACE", "RID", "RIGHT", "ROUND",
            "ROUND_TIMESTAMP", "ROWID", "RPAD", "RRN", "RTRIM", "SCORE", "SECOND", "SIGN", "SIN", "SINH", "SMALLINT", "SOUNDEX", "SPACE", "SQRT",
            "STRIP", "STRLEFT", "STRPOS", "STRRIGHT", "SUBSTR", "SUBSTRING", "TABLE_NAME", "TABLE_SCHEMA", "TAN", "TANH", "TIME", "TIMESTAMP",
            "TIMESTAMP_FORMAT", "TIMESTAMP_ISO", "TIMESTAMPDIFF", "TO_CHAR", "TO_CLOB", "TO_DATE", "TO_NUMBER", "TO_TIMESTAMP", "TOTALORDER",
            "TRANSLATE", "TRIM", "TRIM_ARRAY", "TRUNCATE", "TRUNC", "TRUNC_TIMESTAMP", "UCASE", "UPPER", "URL_DECODE", "URL_ENCODE", "VALUE",
            "VARBINARY", "VARBINARY_FORMAT", "VARCHAR", "VARCHAR_BIT_FORMAT", "VARCHAR_FORMAT", "VARCHAR_FORMAT_BINARY", "VARGRAPHIC",
            "VERIFY_GROUP_FOR_USER", "WEEK", "WEEK_ISO", "WRAP", "XMLATTRIBUTES", "XMLCOMMENT", "XMLCONCAT", "XMLDOCUMENT", "XMLELEMENT",
            "XMLFOREST", "XMLNAMESPACES", "XMLPARSE", "XMLPI", "XMLROW", "XMLSERIALIZE", "XMLTEXT", "XMLVALIDATE", "XOR", "XSLTRANSFORM",
            "YEAR", "ZONED",
            // Table functions
            "BASE_TABLE", "JSON_TABLE", "MQREADALL", "MQREADALLCLOB", "XMLTABLE"
            // Excluded for the same reasons as the corresponding scalar functions above
            // "HTTP_DELETE_VERBOSE", "HTTP_GET_VERBOSE", "HTTP_PATCH_VERBOSE", "HTTP_POST_VERBOSE", "HTTP_PUT_VERBOSE",
            // "MQRECEIVEALL", "MQRECEIVEALLCLOB",
        )));
    //@formatter:on

    public enum ConnectionMethod {
        TCP, CLI;

        public static ConnectionMethod getDefault() {
            return MapepireServer.isSingleMode() ? CLI : TCP;
        }
    }

    private Connection m_conn;
    private ConnectionMethod m_lastUsedConnectionMethod = ConnectionMethod.CLI;
    private String m_lastUsedJdbcProps = "";
    /**
     * The IBM i system to connect to. In daemon mode this is the host name the client asked for, and is never
     * empty. In single mode it comes from the {@code mapepire.debug.host} system property and is usually
     * {@code null}, which means the local system ({@code localhost}).
     */
    private final String m_host;
    /**
     * The IBM i user profile to connect as. In daemon mode this is the user the client authenticated as, and is
     * never empty and never contains {@code *}. In single mode it comes from the {@code mapepire.debug.user}
     * system property, falling back to the user the server process runs as. A value containing {@code @} is
     * treated as a Kerberos principal.
     */
    private final String m_userProfile;
    /**
     * The password for {@link #m_userProfile}. In daemon mode this is never {@code null} or empty. In single mode
     * it comes from the {@code mapepire.debug.pw} system property and is usually {@code null}, in which case the
     * connection is made without explicit credentials. This is a secret: it must never be logged, traced, or
     * included in an exception message.
     */
    private final char[] m_password;
    private final ClientSpecialRegisters m_clientRegs;
    private String m_applicationName;
    private final String m_clientAddress;
    private final Tracer m_tracer;
    // Raw Base64 "user:pass" from the WebSocket Authorization header.
    // Stored so BlobStore can validate HTTP /blob/{token} requests.
    private String m_rawCredentials = null;
    private boolean m_isBasicQueryOnly = true;

    private final WeakHashMap<String, Boolean> m_knownWhetherBasicQuerySql = new WeakHashMap<String, Boolean>();

    /**
     * Create a connection for single mode. The user, host, and password are taken from the
     * {@code mapepire.debug.*} system properties, falling back to the user the server process runs as on the
     * local system.
     *
     * @throws IOException
     *             if the server is not running in single mode
     */
    public SystemConnection() throws IOException {
        if (!MapepireServer.isSingleMode()) {
            throw new IOException(IMPROPER_USAGE_MESSAGE);
        }
        final ClientSpecialRegistersVSCode clientRegs = new ClientSpecialRegistersVSCode();
        m_clientRegs = clientRegs;
        m_clientAddress = clientRegs.getClientAddress();
        m_userProfile = System.getProperty(PROP_DEBUG_USER, System.getProperty("user.name"));
        m_tracer = Tracer.getNew();
        m_host = System.getProperty(PROP_DEBUG_HOST, null);
        final String debugpw = System.getProperty(PROP_DEBUG_PASSWORD);
        m_password = null == debugpw ? null : debugpw.toCharArray();

    }

    /**
     * Create a connection for daemon mode, on behalf of a remote client that has supplied credentials.
     *
     * @param _clientHost
     *            the host name of the connecting client, recorded in the client special registers
     * @param _clientAddress
     *            the IP address of the connecting client, checked against the auth rules file on connect
     * @param _host
     *            the IBM i system to connect to; must not be empty or contain {@code *}
     * @param _user
     *            the IBM i user profile to connect as; must not be empty or contain {@code *}
     * @param _pass
     *            the password for {@code _user}; must not be {@code null} or empty
     * @param _tracer
     *            the per-connection tracer
     * @throws IOException
     *             if the server is running in single mode, or the user, host, or password is invalid
     */
    public SystemConnection(final String _clientHost, final String _clientAddress, final String _host, final String _user, final char[] _pass, final Tracer _tracer) throws IOException {
        super();
        if (MapepireServer.isSingleMode()) {
            throw new IOException(IMPROPER_USAGE_MESSAGE);
        }
        m_host = _host;
        if (StringUtils.isEmpty(_user) || _user.contains("*")) {
            throw new IOException(INVALID_USERNAME_MESSAGE);
        }
        if (StringUtils.isEmpty(_host) || _host.contains("*")) {
            throw new IOException("Invalid Hostname");
        }
        if (null == _pass || 0 == _pass.length) {
            throw new IOException(INVALID_PASSWORD_MESSAGE);
        }
        m_userProfile = _user;
        m_password = _pass;
        m_clientAddress = _clientAddress;
        m_clientRegs = new ClientSpecialRegistersRemote(_clientHost, _clientAddress, _user);
        m_tracer = _tracer;
    }

    /** Returns the raw Base64 Authorization credentials, or {@code null} in single mode. */
    public String getRawCredentials() {
        return m_rawCredentials;
    }

    /** Called by {@link com.github.ibm.mapepire.ws.DbWebsocketClient} at connection time. */
    public void setRawCredentials(final String _rawCredentials) {
        m_rawCredentials = _rawCredentials;
    }

    public static boolean isRunningOnIBMi() {
        return System.getProperty("os.name", "").contains("400") || new File("/usr/bin/qsh").exists();
    }

    /**
     * Return the current JDBC connection. If there is no open connection and the
     * {@code codeserver.jdbc.autoconnect} system property is set, reconnect using the settings from the last
     * connection attempt.
     *
     * @return the open JDBC connection
     * @throws SQLException
     *             if there is no open connection and autoconnect is disabled, or reconnecting fails
     */
    public synchronized Connection getJdbcConnection() throws SQLException {
        if (null != m_conn && !m_conn.isClosed()) {
            return m_conn;
        }
        if (Boolean.getBoolean(PROP_JDBC_AUTOCONNECT)) {
            return reconnect(m_lastUsedConnectionMethod, m_lastUsedJdbcProps, m_applicationName);
        }
        throw new SQLException("Not connected");
    }

    /**
     * @return the {@link AS400} object for the current JDBC connection, or {@code null} if the connection is not
     *         a jt400 connection
     * @throws SQLException
     *             if there is no open JDBC connection
     */
    public synchronized AS400 getSystem() throws SQLException {
        final Connection connection = getJdbcConnection();
        if (connection instanceof AS400JDBCConnection) {
            return ((AS400JDBCConnection) connection).getSystem();
        }
        return null;
    }

    /**
     * Return the qualified name ({@code number/user/name}) of the database server job behind the current JDBC
     * connection. Never fails: if the name cannot be determined, the error is traced and a placeholder is
     * returned.
     *
     * @return the qualified job name, or {@code ??????/??????/??????} if it cannot be determined
     * @throws SQLException
     *             declared for callers; not currently thrown
     */
    public String getJdbcJobName() throws SQLException {
        try {
            final Connection c = getJdbcConnection();
            if (c instanceof AS400JDBCConnection) {
                return makePrettyJobNameFromJt400Name(((AS400JDBCConnection) c).getServerJobIdentifier());
            }
            return c.getClass().getMethod("getServerJobName").invoke(c).toString();
        } catch (final Exception _e) {
            // Deliberately broad: besides the SQLException and the several reflection exceptions, the reflective
            // fallback can fail with runtime exceptions (a null return, a driver without the method). Every failure
            // has the same outcome -- a placeholder name -- so there is nothing gained by listing them separately.
            m_tracer.logErr(_e);
            return "??????/??????/??????";
        }
    }

    private String makePrettyJobNameFromJt400Name(final String _jobString) {
        final String name = _jobString.substring(0, 10).trim();
        final String user = _jobString.substring(10, 20).trim();
        final String number = _jobString.substring(20).trim();
        return String.format("%s/%s/%s", number, user, name);
    }

    /**
     * Close the current JDBC connection, if there is one. For a jt400 connection, all services on the
     * underlying {@link AS400} object are disconnected. Errors are traced, not thrown.
     */
    public synchronized void close() {
        if (null != m_conn) {
            try {
                final AS400 system = getSystem();
                if (null != system) {
                    system.disconnectAllServices();
                } else {
                    m_conn.close();
                }
            } catch (final Exception _e) {
                getTracer().logErr(_e);
            }
            m_conn = null;
        }
    }

    /**
     * Close any current JDBC connection and open a new one. The connection is first checked against the auth
     * rules file; if the server, the matching rule, or the client's JDBC properties restrict it to basic queries,
     * the connection is opened read-only.
     *
     * @param _connectionMethod
     *            how to connect; {@link ConnectionMethod#CLI} is only honored in single mode on IBM i
     * @param _jdbcProps
     *            semicolon-separated JDBC properties supplied by the client, or {@code null}
     * @param _applicationName
     *            the client application name for the special registers, or empty to keep the previous one
     * @return the new JDBC connection
     * @throws SQLException
     *             if the connection is refused by an auth rule, or cannot be opened
     */
    public synchronized Connection reconnect(final ConnectionMethod _connectionMethod, final String _jdbcProps, final String _applicationName) throws SQLException {
        close();
        if (StringUtils.isNonEmpty(_applicationName)) {
            m_applicationName = _applicationName;
        }

        // Store connection settings
        m_lastUsedConnectionMethod = _connectionMethod;
        m_lastUsedJdbcProps = _jdbcProps;

        try {
            // check if this connection is allowed by our security rules file
            final AuthRule accessRule = AuthFile.getDefault().getAccessRuleAndThrowIfDeny(m_userProfile, m_clientAddress); // TODO: how to handle this for kerberos?

            m_isBasicQueryOnly = MapepireServer.isBasicQueryOnly() || (RuleType.ALLOWBASICONLY == accessRule.getRuleType()) || isReadOnlyRequestedByProperty(_jdbcProps);
            final String jdbcPropsStr;
            if (m_isBasicQueryOnly) {
                if (StringUtils.isEmpty(_jdbcProps)) {
                    jdbcPropsStr = READ_ONLY_JDBC_PROPERTY;
                } else {
                    jdbcPropsStr = _jdbcProps + ";" + READ_ONLY_JDBC_PROPERTY;
                }
            } else {
                jdbcPropsStr = _jdbcProps;
            }

            if (isUsingKerberos()) {
                // Create AS400 object
                final AS400 as400System;
                final String systemName = (null != m_host) ? m_host : "localhost";

                if (null != m_userProfile && null != m_password) {
                    as400System = new AS400(systemName, m_userProfile, m_password);
                } else {
                    as400System = new AS400(systemName);
                }

                // Parse JDBC properties into Properties object
                final Properties jdbcProps = new Properties();
                if (StringUtils.isNonEmpty(jdbcPropsStr)) {
                    final String[] propPairs = jdbcPropsStr.split(";");
                    for (final String pair : propPairs) {
                        if (StringUtils.isNonEmpty(pair)) {
                            final String[] keyValue = pair.split("=", 2);
                            if (2 == keyValue.length) {
                                jdbcProps.setProperty(keyValue[0].trim(), keyValue[1].trim());
                            }
                        }
                    }
                }

                // Create AS400JDBCConnection from AS400 object
                final AS400JDBCDriver driver = new AS400JDBCDriver();

                // Connect with null database name (database name should only be used for IASP connections)
                m_conn = verifyReadOnlyAtDriver(driver.connect(as400System, jdbcProps, null));
                m_conn.setClientInfo(m_clientRegs.getProperties(_applicationName));
                return m_conn;
            }
            DriverManager.registerDriver(new AS400JDBCDriver());
            final String connectionString = getConnectionString();
            getTracer().logInfo("Using connection string " + connectionString);
            m_conn = verifyReadOnlyAtDriver(DriverManager.getConnection(connectionString + ";" + jdbcPropsStr));
            m_conn.setClientInfo(m_clientRegs.getProperties(_applicationName));
            return m_conn;

        } catch (final Exception _e) {
            throw new SQLException(_e);
        }
    }

    /**
     * Returns whether the client-supplied JDBC properties request read-only access, that is, contain an
     * {@code access=read only} property. Property names and values are matched case-insensitively and
     * with surrounding whitespace ignored, the same way the JDBC driver interprets them.
     *
     * @param _jdbcProps
     *            the semicolon-separated JDBC properties supplied by the client, or {@code null}
     * @return {@code true} if any {@code access} property in the string has the value {@code read only}
     */
    private static boolean isReadOnlyRequestedByProperty(final String _jdbcProps) {
        if (StringUtils.isEmpty(_jdbcProps)) {
            return false;
        }
        for (final String pair : _jdbcProps.split(";")) {
            final String[] keyValue = pair.split("=", 2);
            if (2 == keyValue.length && "access".equalsIgnoreCase(keyValue[0].trim()) && "read only".equalsIgnoreCase(keyValue[1].trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * If this connection is restricted to basic queries, verify that the driver really did open the given
     * connection read-only, and mark it read-only. Does nothing if the connection is not restricted.
     *
     * @param _conn
     *            the newly opened JDBC connection
     * @return {@code _conn}
     * @throws SQLException
     *             if the connection is restricted but the driver did not honor read-only mode
     */
    private Connection verifyReadOnlyAtDriver(final Connection _conn) throws SQLException {
        if (!m_isBasicQueryOnly) {
            return _conn;
        }
        // Tracer.getGlobalTracer().logInfo("Connection type is " + _conn.getClass().getName());
        if (!_conn.isReadOnly()) {
            throw new SQLException(BASIC_QUERY_ONLY_MESSAGE, SQLSTATE_NOT_AUTHORIZED, BASIC_QUERY_ONLY_VENDOR_CODE);
        }
        try (final Statement s = _conn.createStatement()) {
            try {
                s.execute("CALL systools.lprintf('ERROR: Disregard of read only mode detected')");
                throw new SQLException(BASIC_QUERY_ONLY_MESSAGE, SQLSTATE_NOT_AUTHORIZED, BASIC_QUERY_ONLY_VENDOR_CODE);
            } catch (final SQLException _e) {
                // expected condition. If we're read-only, this should fail
            }
        }
        _conn.setReadOnly(true);
        return _conn;
    }

    public String getHost() {
        return null == m_host ? "localhost" : m_host;
    }

    /**
     * Build the host-and-credentials part of a {@code jdbc:as400:} URL. The result contains the password, so it
     * must never be logged.
     *
     * @return the host, followed by {@code ;user=...;password=...} when credentials are available
     * @throws IOException
     *             if the server is in daemon mode and the user profile or password is invalid
     */
    private String getAuthString() throws IOException {
        if (!MapepireServer.isSingleMode()) {
            if (StringUtils.isEmpty(m_userProfile) || m_userProfile.contains("*")) {
                throw new IOException(INVALID_USERNAME_MESSAGE);
            }
            if (StringUtils.isEmpty(m_password)) {
                throw new IOException(INVALID_PASSWORD_MESSAGE);
            }
        }
        if (MapepireServer.isSingleMode() && (null == m_userProfile || null == m_password)) {
            return getHost();
        } else {
            return String.format("%s;user=%s;password=%s", getHost(), m_userProfile, new String(m_password));
        }
    }

    private String getConnectionString() throws IOException {
        if (isRunningOnIBMi() && MapepireServer.isSingleMode() && (ConnectionMethod.CLI == m_lastUsedConnectionMethod)) {
            return Boolean.getBoolean(PROP_RESTRICTED_LOCAL_CONNECTION) ? "jdbc:default:connection" : "jdbc:db2:*LOCAL";
        }
        return "jdbc:as400:" + getAuthString();
    }

    private boolean isUsingKerberos() {
        if (null == m_userProfile) {
            return false;
        }
        return m_userProfile.contains("@");
    }

    public Tracer getTracer() {
        return m_tracer;
    }

    public String getConnectionId() {
        return getTracer().getConnectionId();
    }

    public ClientSpecialRegisters getCSRs() {
        return m_clientRegs;
    }

    /**
     * Verify that the given SQL is a basic query, if this connection is restricted to basic queries. A
     * basic query is a query statement that only reads schemas, tables, columns, variables, and
     * allow-listed functions. Does nothing if the connection is not restricted.
     *
     * @param _sql
     *            the SQL statement the client wants to run
     * @throws SQLException
     *             if the connection is restricted and the statement is not a basic query, or the
     *             statement cannot be parsed
     */
    public void verifyBasicQueryOnly(final String _sql) throws SQLException {
        if (!m_isBasicQueryOnly) {
            return;
        }
        final Boolean b = m_knownWhetherBasicQuerySql.get(_sql);
        if (null != b) {
            if (b.booleanValue()) {
                return;
            } else {
                throw new SQLException(BASIC_QUERY_ONLY_MESSAGE, SQLSTATE_NOT_AUTHORIZED, BASIC_QUERY_ONLY_VENDOR_CODE);
            }
        }
        final Connection conn = getJdbcConnection();
        try (final PreparedStatement s = conn.prepareStatement("SELECT NAME_TYPE,SCHEMA,NAME,USAGE_TYPE, SQL_STATEMENT_TYPE FROM TABLE(QSYS2.PARSE_STATEMENT(?))")) {
            s.setString(1, _sql);
            try (final ResultSet rs = s.executeQuery()) {
                while (rs.next()) {
                    final String nameType = rs.getString(1);
                    final String schema = rs.getString(2);
                    final String name = rs.getString(3);
                    final String usageType = rs.getString(4);
                    final String sqlStatementType = rs.getString(5);

                    final boolean isStatementQuery = "QUERY".equals(sqlStatementType);
                    final boolean isUsageQuery = "QUERY".equals(usageType);
                    final boolean isData = "SCHEMA".equals(nameType) || "TABLE".equals(nameType) || "COLUMN".equals(nameType) || "VARIABLE".equals(nameType);
                    final boolean isFunction = "FUNCTION".equals(nameType);
                    final boolean isAllowedFunction = isFunction && isFunctionAllowListed(name, schema);

                    final boolean isOk;
                    if (isFunction) {
                        isOk = isStatementQuery && isAllowedFunction;
                    } else {
                        isOk = isStatementQuery && isUsageQuery && isData;
                    }
                    m_knownWhetherBasicQuerySql.put(_sql, Boolean.valueOf(isOk));
                    if (!isOk) {
                        throw new SQLException(BASIC_QUERY_ONLY_MESSAGE, SQLSTATE_NOT_AUTHORIZED, BASIC_QUERY_ONLY_VENDOR_CODE);
                    }
                }
            }
        }
    }

    /**
     * Returns whether the given function may be used in a basic query. Only unqualified built-in functions
     * are allowed; a schema-qualified name always refers to a user-defined or catalog function, so it is
     * never allowed.
     *
     * @param _name
     *            the function name reported by QSYS2.PARSE_STATEMENT
     * @param _schema
     *            the function schema reported by QSYS2.PARSE_STATEMENT, or {@code null} if unqualified
     * @return {@code true} if the function is an unqualified built-in function
     */
    private static boolean isFunctionAllowListed(final String _name, final String _schema) {
        if (null != _schema || null == _name) {
            return false;
        }
        return Boolean.getBoolean(PROP_DISABLE_BUILTINS_IN_BASIC_QUERY) ? false : s_builtinFunctionsAllowedInBasicQuery.contains(_name.trim().toUpperCase(Locale.ROOT));
    }

    public boolean isBasicQueryOnly() {
        return m_isBasicQueryOnly;
    }
}