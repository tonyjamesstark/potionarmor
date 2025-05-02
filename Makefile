SHELL=/bin/zsh
SRV_LOC=../../_test_server/srv
PKG_NAME=potionarmor
JAR=target/${PKG_NAME}*.jar
SRV_JAR=purpur-1.21.4-2394.jar

all: ${JAR}

jar: ${JAR}

${JAR}:
	mvn clean package
test: ${JAR}
	rm -rf ${SRV_LOC}/plugins/${PKG_NAME}*.jar && \
	cp -f target/${PKG_NAME}*.jar ${SRV_LOC}/plugins/ && \
	cd ${SRV_LOC} && \
	java -jar ${SRV_JAR}
clean:
	mvn clean