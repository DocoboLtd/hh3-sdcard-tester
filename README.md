
# hh3-sdcard-tester
A small test application used to stress the sdcard on HH3 devices

When the test sequence is started the following steps are performed.
1. Wait for device to settle in the event of a reboot		 
	 - Allow at least 1 minute to elapse from device boot
	 - Check SD card mount state and wait if necessary until the SD card is mounted
2. Perform the test task if performing a write test (i.e. all except the reboot test)
     - Check integrity of previous generated file if one exists using MD5 validation.
     - Generate new data and calculate MD5
     - Record the MD5 has of generated data in /sdcard or /sysctl
3. Wait for the specified period of time denoted by the test interval
4. Reboot device

When the application launches after a device reboot, the test sequence is repeated. Hence the test is performed repeatedly until one of the following  error conditions are detected;
 - Data partition format - The OS has formatted the /data partition
	 - The device will reboot and display the Touch Screen Calibration UI
 - SD card write failures - Data written to the SD card is not persisted after a power cycle
	 - The test application will report a validation failure on the screen and stop running the test

#### Supported Test Types
 - Reboot Only - Reboot the device without performing write operations to /data partition.
 - Write Random - Writes a random number of bytes (10 Kb to 500 Kb)
 - Write Small - Writes a 1 Kb file
 - Write Large - Writes a 500 Kb file

#### Test Interval
After the test type operation is performed the wait period before the reboot is determined by the test interval. The following test intervals are supported;
 - 1 minute
 - 5 minutes
 - 10 minutes
 - 15 minutes
