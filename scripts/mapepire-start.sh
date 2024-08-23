#!/QOpenSys/usr/bin/sh
exec /QOpenSys/QIBM/ProdData/JavaVM/jdk80/64bit/bin/java -Xsoftmx1G -Xmx16g -Xms256M -jar $(/QOpenSys/usr/bin/dirname $0)/../lib/mapepire/mapepire-server.jar
