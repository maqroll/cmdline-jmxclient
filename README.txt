Use [cubism js](https://square.github.io/cubism/) to visualize JMX variables (mainly for [kafka](https://kafka.apache.org/)).
# Poll jmx server (see org.archive.jmx.Client.SLEEP_TIME) and exposes a rest interface with the values (http://localhost:9090/jmx/jmx_property@attribute)
# Port is hardcoded (see org.archive.jmx.Rest.PORT)
java -classpath "lib/*:test-1.0-SNAPSHOT.jar" org.archive.jmx.Client - ${SERVER}:${JMX_PORT} jmx_property@attribute
