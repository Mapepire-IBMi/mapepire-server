package com.github.theprez.codefori;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Properties;

import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.StringUtils;
import com.google.gson.stream.JsonWriter;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400FileRecordDescription;
import com.ibm.as400.access.AS400JDBCDriver;
import com.ibm.as400.access.Command;
import com.ibm.as400.access.FieldDescription;
import com.ibm.as400.access.IFSFile;
import com.ibm.as400.access.IFSFileInputStream;
import com.ibm.as400.access.ObjectDescription;
import com.ibm.as400.access.ObjectList;
import com.ibm.as400.access.RecordFormat;
import com.ibm.as400.access.SequentialFile;
import com.ibm.as400.access.SystemValue;

class HardWorker {

    private JsonWriterWriter m_results;
    private AS400 m_as400;
    private AppLogger m_logger;

    HardWorker(JsonWriterWriter _results, AppLogger _logger, AS400 _as400) {
        m_results = _results;
        m_logger = _logger;
        m_as400 = _as400;
    }

    void doSysVal(LinkedList<String> _args) throws IOException {
        m_results.startObject("sysvals");
        m_results.beginArray(null);
        for (String sysval : _args) {
            m_logger.printf_verbose("Retrieving system value '%s'\n", sysval);
            m_results.startObject(null);
            m_results.addValue("name", sysval);
            try {
                SystemValue sv = new SystemValue(m_as400, sysval);
                Object value = sv.getValue();
                m_logger.printf_verbose("System value '%s' type is '%s'. Value='%s'\n", sysval, value.getClass().getSimpleName(), "" + value);
                m_results.addValue("value", value.toString());
            } catch (Exception e) {
                m_results.err(e);
            } finally {
                m_results.endObject();
            }
        }
    }

    void doRecFmt(LinkedList<String> _args) throws IOException {
        m_results.startObject("record_formats");
        m_results.beginArray(null);
        for (String fileIn : _args) {
            String file = new IBMiObjectPathNormalizer(fileIn, "file").getIfsPath();
            m_logger.printf_verbose("Retrieving record format for '%s'\n", file);
            m_results.startObject(null);
            m_results.addValue("name", file);
            try {
                AS400FileRecordDescription f = new AS400FileRecordDescription(m_as400, file);
                RecordFormat[] fmts = f.retrieveRecordFormat();
                for (RecordFormat fmt : fmts) {
                    m_results.addValue("name", fmt.getName());
                    m_results.beginArray("fields");
//                    m_results.addValue("name", fmt.getName());
//                    m_results.beginArray(fmt.getName());
                    for (FieldDescription fd : fmt.getFieldDescriptions()) {
                        m_results.addBeanObject(null, fd);
                    }
                    m_results.endArray();
                }
                m_results.endArray();
            } catch (Exception e) {
                m_results.err(e);
            } finally {
                m_results.endObject();
            }
        }
    }

    void doFileBytes(LinkedList<String> _args) throws IOException {
        m_results.startObject("file_bytes");
        m_results.beginArray(null);
        for (String file : _args) {
            m_results.startObject(null);
            m_results.addValue("name", file);
            try (IFSFileInputStream f = new IFSFileInputStream(m_as400, file)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[1024 * 1024];
                int bytesRead = -1;
                while (-1 < (bytesRead = f.read(buf))) {
                    baos.write(buf, 0, bytesRead);
                }
                String encoded = Base64.getEncoder().encodeToString(baos.toByteArray());
                baos.reset();
                m_results.addValue("bytes_base64", encoded);
            } catch (Exception e) {
                m_results.err(e);
            } finally {
                m_results.endObject();
            }
        }
    }

    void doGenCmdXml(LinkedList<String> _args) throws IOException {
        m_results.startObject("cmd_xml");
        m_results.beginArray(null);
        for (String cmd : _args) {
            try {
                m_results.startObject(null);
                m_results.addValue("name", cmd);
                String[] split = cmd.trim().split("\\s*/\\s*", 2);
                final String lib;
                final String obj;
                final String ifsPath;
                if (1 == split.length) {
                    obj = split[0].trim();
                    ObjectList lister = new ObjectList(m_as400, ObjectList.LIBRARY_LIST, obj, "*CMD");
                    ObjectDescription[] matches = lister.getObjects(0, 1);
                    if (1 > matches.length) {
                        throw new RuntimeException("Cannot locate command in library list: " + obj);
                    }
                    lib = matches[0].getLibrary();
                } else if (2 == split.length) {
                    lib = split[0].trim();
                    obj = split[1].trim();
                } else {
                    throw new IllegalArgumentException("Improper object name " + cmd);
                }
                ifsPath = (lib.trim().equalsIgnoreCase("qsys") ? "/" : "/qsys.lib/") + lib + ".lib/" + obj + ".cmd";

                Command c = new Command(m_as400, ifsPath);

                m_results.addValue("library", lib);
                m_results.addValue("xml", c.getXMLExtended());
            } catch (Exception e) {
                m_results.err(e);
            } finally {
                m_results.endObject();
            }
        }
    }

    public void doSql(LinkedList<String> _args) throws IOException {
        m_results.startObject("sql");
        m_results.beginArray(null);
        AS400JDBCDriver driver = new AS400JDBCDriver();
        Properties connectionProperties = new Properties();
        String initialSchema = System.getProperty("codefori.sql.initialschema", null);
        String connectionPropertiesString = System.getProperty("codefori.sql.connprops", null);
        if (StringUtils.isNonEmpty(connectionPropertiesString)) {
            connectionProperties.load(new StringReader(connectionPropertiesString.replace(';', '\n')));
        }
        for (String sql : _args) {
            m_results.startObject(null);
            m_results.addValue("query", sql);
            try (Connection connection = driver.connect(m_as400, connectionProperties, initialSchema)) {
                m_logger.printf_verbose("Running SQL '%s'\n", sql);
                Statement stmt = connection.createStatement();
                boolean isRs = stmt.execute(sql);
                m_results.addValue("resultset_available", Boolean.valueOf(isRs));
                if (isRs) {
                    ResultSet rs = stmt.getResultSet();
                    ResultSetMetaData metadata = rs.getMetaData();
                    int columnCount = metadata.getColumnCount();
                    m_results.beginArray("column_info");
                    try {
                        for (int i = 1; i <= columnCount; ++i) {
                            LinkedHashMap<String, Object> colInfo = new LinkedHashMap<String, Object>();
                            colInfo.put("column_name", metadata.getColumnName(i));
                            colInfo.put("column_label", metadata.getColumnLabel(i));
                            colInfo.put("column_type", metadata.getColumnTypeName(i));
                            m_results.addObject(null, colInfo);
                        }
                    } finally {
                        m_results.endArray();
                    }
                    m_results.beginArray("data");
                    try {
                        while (rs.next()) {
                            m_results.beginArray(null);
                            try {
                                for (int i = 1; i <= columnCount; ++i) {
                                    m_results.addArrayElement(rs.getObject(i));
                                }
                            } catch (SQLException e) {
                                m_results.err(e);
                            } finally {
                                m_results.endArray();
                            }
                        }
                    } catch (SQLException e) {
                        m_results.err(e);
                    } finally {
                        m_results.endArray();
                    }
                }
            } catch (SQLException e) {
                m_results.err(e);
            } finally {
                m_results.endObject();
            }
        }
    }
}
