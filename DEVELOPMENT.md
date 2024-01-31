
# Steps for doing local development separate from any client library

### 1. Add your remote IBM i connection credentials

Locate the `SystemConnection.ConnectionOptions.getConnectionString()` method and put your
hostname, username, and password in the non-IBMi leg of code.
**For the love of all things good, do not commit your changes**

### Change the scope of jt400 from `provided` to default in pom.xml

In pom.xml, look for the `jt400` dependency declaration, which will look something like this:

```xml
<dependency>
    <groupId>net.sf.jt400</groupId>
    <artifactId>jt400</artifactId>
    <version>11.2</version>
    <!-- comment the <scope> tag for local development -->
    <scope>provided</scope>
</dependency>
```

As the comment suggests, comment out the scope tag.

### 3. Create file with test inputs

The test input file is simple a set of JSON requests as you would expect to receive from a client, for instance:
```json
{"id":"boop","type":"connect","technique":"cli", "props":"libraries=QIWS;naming=system;full open=true"}
{"id": "dovetail", "type":"dove", "sql": "select * from sample.employee", "run": true}
```

### 4. Start the server to use the test input file

Either by:
  a. Setting the `test.file` system property in your debug configuration
  b. Changing the code in CodeForiServer.java to point at your test file (search for `System.getProperty("test.file"`). Remember to not commit this change. 








# Steps for bumping the version number

1. Edit the `<version>` tag value in `pom.xml` and commit reasonably
