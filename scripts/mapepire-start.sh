#!/QOpenSys/usr/bin/sh
QIBM_JAVA_STDIO_CONVERT=N QIBM_PASE_DESCRIPTOR_STDIO=B QIBM_USE_DESCRIPTOR_STDIO=Y QIBM_MULTI_THREADED=Y exec /QOpenSys/QIBM/ProdData/JavaVM/jdk80/64bit/bin/java -Xmx16g -Xms256M -jar $(/QOpenSys/usr/bin/dirname $0)/../lib/mapepire/mapepire-server.jar $*
