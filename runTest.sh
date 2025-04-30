SRV_LOC=../../_test_server/srv
PKG_NAME=potionarmor

mvn && \
	rm -rf ${SRV_LOC}/plugins/${PKG_NAME}*.jar && \
	cp -f target/${PKG_NAME}*.jar ${SRV_LOC}/plugins/ && \
	cd $SRV_LOC && \
	java -jar purpur-1.21.4-2394.jar