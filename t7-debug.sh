set -x
mvnDebug tomcat7:run -pl :archiva-webapp -am  $@
