# CodeForIBMiServer
Server-side support for Code for IBM i.
Intended for programmatic interaction.

# Usage
```bash
/QOpenSys/QIBM/ProdData/JavaVM/jdk80/64bit/bin/java -jar codeforibmiserver.jar <operation> [[item]..]
```
For instance, 
```bash
/QOpenSys/QIBM/ProdData/JavaVM/jdk80/64bit/bin/java -jar codeforibmiserver.jar sysval qccsid QINACTITV
```
Supported operations are currently:
- `sysval`: fetch system values
- `sql`: run SQL
- `gencmdxml`: Generate XML for a CL command

# Options for customizing behavior
Some operations support behavior customizations. This is done by setting Java system properties. 

Operation `sql` supports the following properties:
- `codefori.sql.initialschema`: Initial schema
- `codefori.sql.connprops`: Connection properties (semicolon-delimited)

So, for instance:

```bash
/QOpenSys/QIBM/ProdData/JavaVM/jdk80/64bit/bin/java -Dcodefori.sql.initialschema=qiws -jar codeforibmiserver.jar sql "select * from qcustcdt"
```

# Output
It's JSON. Schema/format not documented, but you'll figure it out. Pipe the output through `jq` for pretty printing
