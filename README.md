# CodeForIBMiServer
Server-side support for Code for IBM i, more specifically database support.
Intended for programmatic interaction.

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


The following request types are currently supported

| Type          | Description   | Additional input fields  | Additional output fields  |
| ------------- | ------------- | ------------- | -------------  |
| `connect`     | Connect to the database (implicitly disconnects any existing connection) | `props`: a semicolon-delimited list of connection properties | `job`: the server job | 
| `sql`         | Run SQL  | `sql`: the SQL statement <br/> `rows`: the maximum number of rows to return on the first request | `metadata`: metadata about the result set <br/> `data`: the data <br/> `is_done`: whether all rows were fetched | 
| `prepare_sql`         | Prepare SQL statement  | `sql`: the SQL statement | 
| `execute`         | Execute prepared SQL statement  | `cont_id`: the request ID of the previously-run `sql` or `prepare_sql` <br /> `batch`: when `true`, add SQL operations to batch only <br /> `parameters`: array parameter values corresponding to any parameter markers used (can be an array of arrays when `batch` is true). If `batch` is `true` and no parameters are specified, the batch of SQL operations is executed. | 
| `sqlmore`     | fetch more rows from a previous `sql` request  | `cont_id`: the request ID of the previously-run `sql` or `prepare_sql` request <br/> `rows`: the maximum number of rows to return | `data`: the data <br/> `is_done`: whether all rows were fetched | 
| `getdbjob`     | Get server job for database tasks  |  | `job`: the server job | 
| `getversion`   | Get version info  |  | `build_date`: build date <br/> `version`: version | 
| `ping`         | Liveness check |  | `alive`: this program is still responsive <br/> `db_alive`: there is an active connection to the database | 
| `exit      `   | Exit  |  |  | 


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
