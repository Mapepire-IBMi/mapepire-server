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
    private static final Set<String> BUILTIN_FUNCTIONS_ALLOWED_IN_BASIC_QUERY = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
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
    // TODO: document the expectations around the host, username, and password fields
    private final String host;
    private final String userProfile;
    private final char[] password;
    private final ClientSpecialRegisters m_clientRegs;
    private String m_applicationName;
    private final String clientAddress;
    private final Tracer m_tracer;
    // Raw Base64 "user:pass" from the WebSocket Authorization header.
    // Stored so BlobStore can validate HTTP /blob/{token} requests.
    private String m_rawCredentials = null;
    private boolean m_isBasicQueryOnly = true;

    private final WeakHashMap<String, Boolean> m_knownWhetherBasicQuerySQL = new WeakHashMap<String, Boolean>();

    /**
     * Constructor that is only to be used when not in single mode
     * 
     * @throws IOException
     */
    public SystemConnection() throws IOException {
        if (!MapepireServer.isSingleMode()) {
            throw new IOException(IMPROPER_USAGE_MESSAGE);
        }
        ClientSpecialRegistersVSCode clientRegs = new ClientSpecialRegistersVSCode();
        this.m_clientRegs = clientRegs;
        this.clientAddress = clientRegs.getClientAddress();
        this.userProfile = System.getProperty(PROP_DEBUG_USER, System.getProperty("user.name"));
        this.m_tracer = Tracer.getNew();
        this.host = System.getProperty(PROP_DEBUG_HOST, null);
        String debugpw = System.getProperty(PROP_DEBUG_PASSWORD);
        this.password = null == debugpw ? null : debugpw.toCharArray();

    }

    public SystemConnection(String clientHost, String clientAddress, String host, String user, char[] pass, Tracer tracer) throws IOException {
        super();
        if (MapepireServer.isSingleMode()) {
            throw new IOException(IMPROPER_USAGE_MESSAGE);
        }
        this.host = host;
        if (StringUtils.isEmpty(user) || user.contains("*")) {
            throw new IOException(INVALID_USERNAME_MESSAGE);
        }
        if (StringUtils.isEmpty(host) || host.contains("*")) {
            throw new IOException("Invalid Hostname");
        }
        if (pass == null || pass.length == 0) {
            throw new IOException(INVALID_PASSWORD_MESSAGE);
        }
        this.userProfile = user;
        this.password = pass;
        this.clientAddress = clientAddress;
        this.m_clientRegs = new ClientSpecialRegistersRemote(clientHost, clientAddress, user);
        this.m_tracer = tracer;
    }

    /** Returns the raw Base64 Authorization credentials, or {@code null} in single mode. */
    public String getRawCredentials() {
        return m_rawCredentials;
    }

    /** Called by {@link com.github.ibm.mapepire.ws.DbWebsocketClient} at connection time. */
    public void setRawCredentials(String rawCredentials) {
        this.m_rawCredentials = rawCredentials;
    }

    public static boolean isRunningOnIBMi() {
        return System.getProperty("os.name", "").contains("400") || new File("/usr/bin/qsh").exists();
    }

    public synchronized Connection getJdbcConnection() throws SQLException {
        if (null != m_conn && !m_conn.isClosed()) {
            return m_conn;
        }
        if (Boolean.getBoolean(PROP_JDBC_AUTOCONNECT)) {
            return reconnect(m_lastUsedConnectionMethod, m_lastUsedJdbcProps, m_applicationName);
        }
        throw new SQLException("Not connected");
    }

    public String getJdbcJobName() throws SQLException {
        try {
            Connection c = getJdbcConnection();
            if (c instanceof AS400JDBCConnection) {
                return makePrettyJobNameFromJt400Name(((AS400JDBCConnection) c).getServerJobIdentifier());
            }
            return c.getClass().getMethod("getServerJobName").invoke(c).toString();
        } catch (Exception e) {
            this.m_tracer.logErr(e);
            return "??????/??????/??????";
        }
    }

    private String makePrettyJobNameFromJt400Name(final String _jobString) {
        final String name = _jobString.substring(0, 10).trim();
        final String user = _jobString.substring(10, 20).trim();
        final String number = _jobString.substring(20).trim();
        return String.format("%s/%s/%s", number, user, name);
    }

    public synchronized void close() {
        if (null != m_conn) {
            try {
                m_conn.close();
            } catch (SQLException e) {
                getTracer().logErr(e);
            }
            m_conn = null;
        }
    }

    public synchronized Connection reconnect(final ConnectionMethod _connectionMethod, final String _jdbcProps, final String _applicationName) throws SQLException {
        if (null != m_conn) {
            final Connection cpy = m_conn;
            m_conn = null;
            cpy.close();
        }
        if (StringUtils.isNonEmpty(_applicationName)) {
            m_applicationName = _applicationName;
        }

        // Store connection settings
        m_lastUsedConnectionMethod = _connectionMethod;
        m_lastUsedJdbcProps = _jdbcProps;

        try {
            // check if this connection is allowed by our security rules file
            AuthRule accessRule = AuthFile.getDefault().getAccessRuleAndThrowIfDeny(this.userProfile, this.clientAddress); // TODO: how to handle this for kerberos?

            m_isBasicQueryOnly = MapepireServer.isReadOnly() || (RuleType.ALLOWBASICONLY == accessRule.getRuleType());
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
                AS400 as400System;
                String systemName = (this.host != null) ? this.host : "localhost";

                if (this.userProfile != null && this.password != null) {
                    as400System = new AS400(systemName, this.userProfile, this.password);
                } else {
                    as400System = new AS400(systemName);
                }

                // Parse JDBC properties into Properties object
                Properties jdbcProps = new Properties();
                if (StringUtils.isNonEmpty(jdbcPropsStr)) {
                    String[] propPairs = jdbcPropsStr.split(";");
                    for (String pair : propPairs) {
                        if (StringUtils.isNonEmpty(pair)) {
                            String[] keyValue = pair.split("=", 2);
                            if (keyValue.length == 2) {
                                jdbcProps.setProperty(keyValue[0].trim(), keyValue[1].trim());
                            }
                        }
                    }
                }

                // Create AS400JDBCConnection from AS400 object
                AS400JDBCDriver driver = new AS400JDBCDriver();

                // Connect with null database name (database name should only be used for IASP connections)
                m_conn = verifyReadOnlyAtDriver(driver.connect(as400System, jdbcProps, null));
                m_conn.setClientInfo(this.m_clientRegs.getProperties(_applicationName));
                return m_conn;
            }
            DriverManager.registerDriver(new AS400JDBCDriver());
            final String connectionString = getConnectionString();
            getTracer().logInfo("Using connection string " + connectionString);
            m_conn = verifyReadOnlyAtDriver(DriverManager.getConnection(connectionString + ";" + jdbcPropsStr));
            m_conn.setClientInfo(this.m_clientRegs.getProperties(_applicationName));
            return m_conn;

        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

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
            } catch (SQLException e) {
                // expected condition. If we're read-only, this should fail
            }
        }
        _conn.setReadOnly(true);
        return _conn;
    }

    public String getHost() {
        return null == this.host ? "localhost" : this.host;
    }

    private String getAuthString() throws IOException {
        if (!MapepireServer.isSingleMode()) {
            if (StringUtils.isEmpty(userProfile) || userProfile.contains("*")) {
                throw new IOException(INVALID_USERNAME_MESSAGE);
            }
            if (StringUtils.isEmpty(password)) {
                throw new IOException(INVALID_PASSWORD_MESSAGE);
            }
        }
        if (MapepireServer.isSingleMode() && (userProfile == null || password == null)) {
            return getHost();
        } else {
            return String.format("%s;user=%s;password=%s", getHost(), userProfile, new String(password));
        }
    }

    private String getConnectionString() throws IOException {
        if (isRunningOnIBMi() && MapepireServer.isSingleMode() && (ConnectionMethod.CLI == this.m_lastUsedConnectionMethod)) {
            return Boolean.getBoolean(PROP_RESTRICTED_LOCAL_CONNECTION) ? "jdbc:default:connection" : "jdbc:db2:*LOCAL";
        }
        return "jdbc:as400:" + this.getAuthString();
    }

    private boolean isUsingKerberos() {
        if (null == this.userProfile) {
            return false;
        }
        return this.userProfile.contains("@");
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
        Boolean b = m_knownWhetherBasicQuerySQL.get(_sql);
        if (null != b) {
            if (b.booleanValue()) {
                return;
            } else {
                throw new SQLException (BASIC_QUERY_ONLY_MESSAGE, SQLSTATE_NOT_AUTHORIZED, BASIC_QUERY_ONLY_VENDOR_CODE);
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
                    m_knownWhetherBasicQuerySQL.put(_sql, Boolean.valueOf(isOk));
                    if (!isOk) {
                        throw new SQLException (BASIC_QUERY_ONLY_MESSAGE, SQLSTATE_NOT_AUTHORIZED, BASIC_QUERY_ONLY_VENDOR_CODE);
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
        return Boolean.getBoolean(PROP_DISABLE_BUILTINS_IN_BASIC_QUERY) ? false : BUILTIN_FUNCTIONS_ALLOWED_IN_BASIC_QUERY.contains(_name.trim().toUpperCase(Locale.ROOT));
    }

    public boolean isBasicQueryOnly() {
        return m_isBasicQueryOnly;
    }
}