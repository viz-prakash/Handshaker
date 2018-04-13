package handshaker.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.json.*; // slow library, refactor it to remove it's usage

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import handshaker.Util;
import handshaker.db.SqliteDataBaseOps;

public class HandshakeScanner {

	private static final String CONFIG_FILE_NAME = "./config/config.json";
	private static final String CONFIG_PROTOCOL = "protocols";
	private static final String CONFIG_PROTOCOL_COMMENT = "_comment";
	private static final String CONFIG_PROTOCOL_TLS = "tls";
	private static final String CONFIG_PROTOCOL_SSH = "ssh";
	private static final String CONFIG_INPUT_FILES_DIR = "inputIpListFilesDir";
	private static final String RESULT_FILE_SUFFIX = "_handshake.json";
	private static final String CONFIG_SQLITE_DB_LOCATION = "sqliteDataBaseLocation";
	private static final String CONFIG_SQLITE_DB_NAME = "sqliteDataBaseName";
	private static final String CONFIG_SQLITE_TABLE_NAME = "sqliteTableName";
	private static final Logger LOGGER = Logger.getLogger(handshaker.core.HandshakeScanner.class.getName());
	private static SqliteDataBaseOps sqliteDb = null;
	private static String tableName = null;

	private ArrayList<JSONObject> readInputIPList(File file) throws JSONException, IOException {
		BufferedReader brin = new BufferedReader(new FileReader(file));
		ArrayList<JSONObject> ipJsonObjectList = new ArrayList<>();
		while (true) {
			String line = brin.readLine();
			if (line == null || line == "") {
				break;
			}
			ipJsonObjectList.add(new JSONObject(line));
		}
		return ipJsonObjectList;
	}

	private JSONObject configParser() throws IOException {
		BufferedReader brin = new BufferedReader(new FileReader(CONFIG_FILE_NAME));
		String line = null;
		StringBuffer configBuffer = new StringBuffer();
		while ((line = brin.readLine()) != null) {
			configBuffer.append(line);
		}
		brin.close();
		return (new JSONObject(configBuffer.toString()));
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		try {
			LOGGER.setUseParentHandlers(false);
			FileHandler fh = new FileHandler("./handshaker.log", 1024 * 1024 * 5, 1, false);
			fh.setFormatter(new SimpleFormatter() {
				private static final String format = "[%1$tF %1$tT] [%2$-7s] %3$s %n";

				@Override
				public synchronized String format(LogRecord lr) {
					return String.format(format, new Date(lr.getMillis()), lr.getLevel().getLocalizedName(),
							lr.getMessage());
				}
			});
			fh.setLevel(Level.FINE);
			LOGGER.addHandler(fh);
			LOGGER.setLevel(Level.FINE);
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		HandshakeScanner handshakeScanner = new HandshakeScanner();
		String cur_dir = System.getProperty("user.dir");
		JSONObject configJson = handshakeScanner.configParser();
		String dirName = configJson.getString(CONFIG_INPUT_FILES_DIR);
		LOGGER.info(MessageFormat.format(
				"Provided input directory where files consisting of list of IPs will be scanned is : {0}", dirName));
		File dir = new File(dirName);
		File[] listOfFiles = dir.listFiles();
		if (listOfFiles.length == 0) {
			LOGGER.info("No input files to process.\nExiting.");
			System.exit(0);
		}
		else {
			try {
				sqliteDb = new SqliteDataBaseOps(configJson.getString(CONFIG_SQLITE_DB_LOCATION),
						configJson.getString(CONFIG_SQLITE_DB_NAME));
				tableName = configJson.getString(CONFIG_SQLITE_TABLE_NAME);
				sqliteDb.createTable(tableName);
			} catch (JSONException | SQLException e) {
				LOGGER.severe(e.getMessage());
				LOGGER.severe(Util.exceptionToStackTraceString(e));
			}
		}
		
		// ArrayList<HandShakeScan> handShakeScans = new ArrayList<>();
		ArrayList<Thread> threads = new ArrayList<>();
		for (int i = 0; i < listOfFiles.length; i++) {
			ArrayList<JSONObject> ipJsonObjects = handshakeScanner.readInputIPList(listOfFiles[i]);
			LOGGER.info(MessageFormat.format("Found file : {0} in directory : {1}", listOfFiles[i].getAbsolutePath(),
					dirName));
			HandShakeScan handShakeScan = handshakeScanner.new HandShakeScan(ipJsonObjects, configJson,
					listOfFiles[i].getAbsolutePath() + RESULT_FILE_SUFFIX, i);
			LOGGER.info(MessageFormat.format("Results are going to be written in file: {0}",
					listOfFiles[i].getAbsolutePath() + RESULT_FILE_SUFFIX));
			// handShakeScans.add(handShakeScan);
			Thread thread = new Thread(handShakeScan);
			thread.start();
			LOGGER.info(MessageFormat.format("Started thread #: {0}", i));
			threads.add(thread);
		}
		Iterator<Thread> iter = threads.iterator();
		while (iter.hasNext()) {
			iter.next().join();
		}
		LOGGER.info("All the threads exited and handshake scanning is done.");
	}

	public class HandShakeScan implements Runnable {

		public Result result;
		public ArrayList<JSONObject> ipJsonObjectList;
		public JSONObject config;
		public String outputFileName;
		public int id;

		public HandShakeScan(ArrayList<JSONObject> ipJsonObjectList, JSONObject config, String outputFileName, int id) {
			result = null;
			this.ipJsonObjectList = ipJsonObjectList;
			this.config = config;
			this.outputFileName = outputFileName;
			this.id = id;
		}

		@Override
		public void run() {
			try {
				result = handshakeScan(ipJsonObjectList, config);
				// BufferedWriter brout = new BufferedWriter(new
				// FileWriter(outputFileName));
				ObjectMapper mapper = new ObjectMapper();
				mapper.enable(SerializationFeature.INDENT_OUTPUT);
				mapper.writeValue(new File(outputFileName), (Object) result);
				LOGGER.info(MessageFormat.format("Thread ID: {0}: completed handshakes with all the IPs", id));
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		}

		private Result handshakeScan(ArrayList<JSONObject> ipJsonObjectList, JSONObject config)
				throws IOException, InterruptedException {
			ArrayList<JSONObject> handshakeList = new ArrayList<>();
			Iterator<JSONObject> ipListIter = ipJsonObjectList.iterator();
			Result result = new Result();
			ArrayList<ResultPerIp> resultPerIpsList = new ArrayList<>();
			while (ipListIter.hasNext()) {
				ResultPerIp resultPerIp = new ResultPerIp();
				String ipaddr = ipListIter.next().getString("ip");
				Process childProcess;
				String outputLine;
				JSONObject protocols = config.getJSONObject(CONFIG_PROTOCOL);
				Iterator<String> protocolIter = protocols.keys();
				Set<String> protocols_keys = protocols.keySet();
				ArrayList<Protocol> protocolResultList = new ArrayList<Protocol>();
				LOGGER.fine(MessageFormat.format("Thread {0}, started handshake scan for ip: {1}", id, ipaddr));
				while (protocolIter.hasNext()) {
					Protocol protocolResult = new Protocol();
					String protocol = protocolIter.next();
					if (protocol.equals(CONFIG_PROTOCOL_COMMENT)) {
						continue;
					}
					JSONObject protocolJsonObject = protocols.getJSONObject(protocol);
					Iterator<String> ciphersNames = protocolJsonObject.keys();
					ArrayList<Cipher> cipherResultList = new ArrayList<>();
					LOGGER.fine(
							MessageFormat.format("Thread ID: {0}: going to do handsahke for protocol: {1} for ip: {2} ",
									id, protocol, ipaddr));
					while (ciphersNames.hasNext()) {
						Cipher cipherResult = new Cipher();
						String cipherName = ciphersNames.next();
						cipherResult.setName(cipherName);
						String cipherInHex = protocolJsonObject.getString(cipherName);
						LOGGER.fine(MessageFormat.format(
								"Thread ID: {0}: going to do handsahke for protocol: {1} with cipher: {2}({3}) for ip: {4} ",
								id, protocol, cipherInHex, cipherName, ipaddr));
						StringBuffer outputBuffer = new StringBuffer();
						// TODO: zgrab2 should be available in $PATH
						String command = String.format("echo \"%s\" | zgrab2 %s --cipher-suite=%s 2>/dev/null", ipaddr,
								protocol, cipherInHex);
						LOGGER.fine(MessageFormat
								.format("Thread ID: {0}: going to execute handshake zgrab2 command: {1}", id, command));
						/*
						 * childProcess = Runtime.getRuntime().exec(command);
						 * LOGGER.fine(MessageFormat.
						 * format("Thread ID: {0}: child process is alive: {1}",
						 * id, childProcess.isAlive())); BufferedReader br = new
						 * BufferedReader(new
						 * InputStreamReader(childProcess.getInputStream()));
						 */
						BufferedReader br = new BufferedReader(new FileReader("result.json"));
						while ((outputLine = br.readLine()) != null)
							outputBuffer.append(outputLine);
						/*
						 * childProcess.waitFor(); LOGGER.fine(MessageFormat.
						 * format("Thread ID: {0}: child process exited with return value: {1}"
						 * , id, childProcess.exitValue()));
						 */
						ObjectMapper mapper = new ObjectMapper();
						// Object json =
						// mapper.readValue(outputBuffer.toString(),
						// Object.class);
						// cipherResult.setHandshakeData(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
						cipherResult.setHandshakeData(mapper.readValue(outputBuffer.toString(), Object.class));
						cipherResultList.add(cipherResult);
					}
					protocolResult.setName(protocol);
					protocolResult.setCipherResultList(cipherResultList);
					protocolResultList.add(protocolResult);
				}
				resultPerIp.setIp(ipaddr);
				resultPerIp.setProtocol(protocolResultList);
				try {
					ObjectMapper mapper = new ObjectMapper();
					sqliteDb.insertData(tableName, ipaddr, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(protocolResultList));
				} catch (SQLException e) {
					LOGGER.severe(Util.exceptionToStackTraceString(e));
				}
				resultPerIpsList.add(resultPerIp);
			}
			result.setScanResult(resultPerIpsList);
			return result;
		}
	}
}
