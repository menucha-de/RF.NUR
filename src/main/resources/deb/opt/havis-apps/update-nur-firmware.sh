#!/bin/sh

. /etc/profile

VERSION="@Bundle-Version@"
VERSION_FILE_PATH="../conf/havis/device/rf/firmware.version"
APP_PATH=$(dirname $(realpath $0))
LOG_PATH="/tmp/update-nur-firmware.log"
FIRMWARE=NUR-L2-application.bin
BOOTLOADER=NUR-05WL2-loader.bin
JAR=havis.device.rf.nur-tools.jar
ARGS="-jar $JAR"

# change to bin/ path where the Updater JAR file resides
cd $APP_PATH/bin

if [ "$1" != "manual" ]; then
	LAST_RUN_VERSION=$(cat $VERSION_FILE_PATH 2> /dev/null)
	if [ "$VERSION" = "$LAST_RUN_VERSION" ]; then
		# don't check, bail
		exit 0
	fi
fi

# find dependencies
SO_PATH=$(find ../felix-cache/ -name libNativeSerialTransport.so 2> /dev/null)
if [ -z "$SO_PATH" ]; then
	# remove old link and unzip from JAR
	rm libNativeSerialTransport.so
	unzip $(find ../bundle/ -name ??_com.nordicid.nativeserial*.jar) -q -o -x META-INF/*
else
	ln -sf $SO_PATH
fi
ln -sf $(find ../bundle/ -name ??_NurApi.jar) NurApi.jar
ln -sf $(find ../bundle/ -name ??_NativeSerialTransport.jar) NativeSerialTransport.jar

RUN_UPDATE=0
HTTPD_PID=0
if [ "$1" = "manual" ]; then
	echo "$(date '+%F %T.%N'): Running manually started firmware update" >> $LOG_PATH
	RUN_UPDATE=1
else
	echo "$(date '+%F %T.%N'): Running automatically started firmware update" >> $LOG_PATH
	# check if update necessary
	java $ARGS -u $FIRMWARE >> $LOG_PATH 2>&1
	EXIT_CODE=$?
	if [ $EXIT_CODE -eq 0 ]; then
		httpd -f -h ../www/havis.device.rf-nur/firmware &
		HTTPD_PID=$!
		RUN_UPDATE=1
	elif [ $EXIT_CODE -eq 255 ]; then
		echo "$(date '+%F %T.%N'): Nothing to update" >> $LOG_PATH
	else
		echo "$(date '+%F %T.%N'): Failed to check, exiting" >> $LOG_PATH
		exit 0
	fi
fi

if [ $RUN_UPDATE -eq 1 ]; then
	# switch to boot loader mode
	java $ARGS -s b >> $LOG_PATH 2>&1
	
	# Install bootloader file
	java $ARGS -b $BOOTLOADER >> $LOG_PATH 2>&1
	
	# Install firmware file
	java $ARGS -f $FIRMWARE >> $LOG_PATH 2>&1
	
	# switch back to application mode
	java $ARGS -s a >> $LOG_PATH 2>&1
	
	# resets GPIO config and antenna mask
	java $ARGS -r >> $LOG_PATH 2>&1
fi

if [ $HTTPD_PID -gt 0 ]; then
	kill -9 $HTTPD_PID
fi

# write version file
mkdir -p $(dirname $VERSION_FILE_PATH)
echo $VERSION > $VERSION_FILE_PATH