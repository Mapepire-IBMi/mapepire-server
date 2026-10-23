
# Steps for doing local development separate from any client library

### 1. Add your remote IBM i connection credentials

Assuming VSCode usage, locate your `.vscode/launch.json` file and add these Java properties to the "vmArgs":
- `mapepire.debug.host`
- `mapepire.debug.user`
- `mapepire.debug.pw`

```json
    {
      "type": "java",
      "name": "CLI mode",
      "request": "launch",
      "vmArgs": "-Dmapepire.debug.host=myhostname.mycompany.com -Dmapepire.debug.user=MYUSRPRF -Dmapepire.debug.pw=mypw",
      "mainClass": "com.github.ibm.mapepire.MapepireServer",
      "projectName": "codeforibmiserver",
      "args": "--single --bqo",
      "envFile": "${workspaceFolder}/.env"
    }
```


**For the love of all things good, do not commit this file**

### 2. Create file with test inputs

The test input file is simple a set of JSON requests as you would expect to receive from a client, for instance:
```json
{"id":"boop","type":"connect","technique":"cli", "props":"libraries=QIWS;naming=system;full open=true"}
{"id": "dovetail", "type":"dove", "sql": "select * from sample.employee", "run": true}
```

### 3. Start the server to use the test input file

Either by:
  a. Setting the `test.file` system property in your debug configuration
  b. Copy/pasting commands for manual inspection






# Steps for bumping the version number

1. Edit the `<version>` tag value in `pom.xml` and commit reasonably
