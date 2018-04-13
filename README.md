# Handshaker
**Steps:**
1. create directory named "my_data_dir", and copy files with list of ip address, in this directory. Modify the config.json file to use your "my_data_dir" as a "inputIpListFilesDir"
	
2. sudo apt-get install sqlite to install the sqlite. Then modify the config.json file accordingly. Make sure sqliteDataBaseLocation exists.
```
"sqliteDataBaseLocation": "C://sqlite/db/"
"sqliteDataBaseName": "handshake.db"
"sqliteTableName": "handshakes"
```
3. Only few cipher suites are added in protocls, add more if needed.
4. To build the project there are two ways either use maven or manual build

Manual Build:
  1. Download jackson-core-2.9.5.jar, jackson-databind-2.9.5.jar, jackson-annotations-2.9.5.jar, json-20180130.jar (for org.json)       sqlite-jdbc-3.21.0.jar, download the specified versoin or the latest one.
	2. To comple from source: javac -cp path_to_dir_where_all_jars_are_stored src/handshaker/*.java
	3. To run: java -cp path_to_dir_where_all_jars_are_stored handshaker.core.HandshakeScanner
    
Maven:
  1. Install maven on the system
	2. got to directory where .pom file exists
	3. run command: maven build to build the project, then maven install to create an executeable jar. It will create a jar name *-jar-with-dependencies.jar in target directory.
	4. run the jar generated in previous step
