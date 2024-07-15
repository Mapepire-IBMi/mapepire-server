
<img src="mapepire-logo.png" alt="logo" width="200"/>

# Mapepire Server

Server side component for the Mapepire project, which provides a new, convenient way to access Db2 on IBM i.

This provides server-side support for Code for IBM i, more specifically database support.
Intended for programmatic interaction.

Client SDKs for Java, JavaScript, and Python are in the works! 

# Usage
```bash
/QOpenSys/QIBM/ProdData/JavaVM/jdk80/64bit/bin/java -jar codeforibmiserver.jar 
```
For instance, 
```bash
/QOpenSys/QIBM/ProdData/JavaVM/jdk80/64bit/bin/java -jar codeforibmiserver.jar
```

This server-side job processes requests asynchronously. Requests are sent and received
through simple use of stdin/stdout streams. The intent is that the client program
is able to launch this process through SSH and interact with it through pipes. 

The data stream is relatively simple. Requests and responses are newline-delimited and
are formatted in JSON. 

All requests require these two fields to be specified:
- `id` (string): Since the server can process requests asynchronously, responses are not
   guaranteed back in the same order as requests were sent. The `id` field passed into
   the request will be included in the response so that the client can match it up
   to the request. This can be any string, but should be unique for obvious reasons
- `type` (string): this specifies the type of request

All responses will include these fields:
- `id` (string): corresponding to the request ID
- `success` (boolean): whether or not the request was successful

If an error occurs, all responses will include these fields:
- `error`: a description of the error

Under certain error conditions, responses may contain one or more of these fields:
- `sql_rc`: the SQL error code
- `sql_state`: the SQL state

The following request types are currently supported

| Type          | Description   | Additional input fields  | Additional output fields  |
| ------------- | ------------- | ------------- | -------------  |
| `connect`     | Connect to the database (implicitly disconnects any existing connection) | `props`: a semicolon-delimited list of connection properties <br/> `application`: the application name (for use in Client Special Registers) <br/> `technique`: database connection technique (`cli` or `tcp`) | `job`: the server job | 
| `cl`          | Run CL command  | `cmd`: the CL command | `data`: the resulting job log entries | 
| `sql`         | Run SQL  | `sql`: the SQL statement <br/> `rows`: the maximum number of rows to return on the first request <br/> `terse`: return data in terse format | `metadata`: metadata about the result set <br/> `data`: the data <br/> `is_done`: whether all rows were fetched | 
| `prepare_sql`         | Prepare SQL statement  | `sql`: the SQL statement <br/> `terse`: return data in terse format | 
| `execute`         | Execute prepared SQL statement  | `cont_id`: the request ID of the previously-run `sql` or `prepare_sql` <br /> `batch`: when `true`, add SQL operations to batch only <br /> `parameters`: array parameter values corresponding to any parameter markers used (can be an array of arrays when `batch` is true). <br /><br />**NOTE: **If `batch` is `true` and no parameters are specified, or if `batch` is `false`, the batch of SQL operations is executed. |   `data`: the data |
| `prepare_sql_execute`         | Prepare and execute SQL statement  | `parameters`: array parameter values <br/> `terse`: return data in terse format |  `data`: the data |
| `sqlmore`     | fetch more rows from a previous `sql`/`prepare_sql`/`prepare_sql_execute` request  | `cont_id`: the request ID of the previously-run `sql`/`prepare_sql`/`prepare_sql_execute` request <br/> `rows`: the maximum number of rows to return | `data`: the data <br/> `is_done`: whether all rows were fetched | 
| `sqlclose`     | close cursor from a previous `sql`/`prepare_sql`/`prepare_sql_execute` request  | `cont_id`: the request ID of the previously-run `sql`/`prepare_sql`/`prepare_sql_execute` request |  | 
| `getdbjob`     | Get server job for database tasks  |  | `job`: the server job | 
| `getversion`   | Get version info  |  | `build_date`: build date <br/> `version`: version | 
| `ping`         | Liveness check |  | `alive`: this program is still responsive <br/> `db_alive`: there is an active connection to the database |
| `setconfig`    | Set configuration options | `tracelevel`: see valid trace levels, below <br/> `tracedest`: one of (`file`, `in_mem`) <br/> `jtopentracelevel`: see valid trace levels, below <br/> `jtopentracedest`: one of (`file`, `in_mem`) | `tracedest`, `tracelevel`,`jtopentracedest`, `jtopentracelevel`, | 
| `gettracedata` | Get trace data |  | `tracedata`: the trace data (as a singular HTML string) <br/> `jtopentracedata`: the JtOpen trace data (plain text) |
| `exit      `   | Exit  |  |  | 

Valid trace levels:
- `OFF`: off
- `ON`: all except datastream
- `ERRORS`: errors only
- `DATASTREAM`: all including data stream
- `INPUT_AND_ERRORS`: errors and data stream inputs

# Examples

Example request to exit gracefully:
```json
{"id": "bye", "type": "exit"}
```

Example to connect to the database with an initial library list
```json
{"id": "conn14", "type": "connect", "props":"naming=system;libraries=jesseg,qiws"}
```

Example SQL query:
```json
{"id": "1l", "type": "sql", "rows":4, "sql":"select * from qiws.qcustcddt"}
```

Example to fetch more data (4 more rows) from previous query
```json
{"id": "2l", "type": "sqlmore", "cont_id":"1l", "rows":4}
```


# Options for customizing behavior

Operation `sql` supports the following Java system properties:
- `codeserver.jdbc.autoconnect`: Enable SQL to be run without first issuing a `connect` request (uses default values)
- `codeserver.verbose`: verbose mode

So, for instance:

```bash
/QOpenSys/QIBM/ProdData/JavaVM/jdk80/64bit/bin/java -Dcodeserver.jdbc.autoconnect=true -jar codeforibmiserver.jar
```
