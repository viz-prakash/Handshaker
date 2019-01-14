package handshaker.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

// org.json.* is a slow library, refactor it to remove it's usage
import org.json.JSONException;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class HandshakeScanner {

    private static final String CONFIG_FILE_NAME = "/home/vfrost/Handshaker/config/config.json";
    private static final String CONFIG_PROTOCOL = "protocols";
    private static final String CONFIG_PROTOCOL_COMMENT = "_comment";
    private static final String CONFIG_INPUT_FILES_DIR = "inputIpListFilesDir";
    private static final String CONFIG_OUTPUT_FILES_DIR = "outputFilesDir"; // Where to output JSON formatted results
    private static final String CONFIG_OUTPUT_TO_JSON_ONLY = "outputJSONOnly";
    private static final String RESULT_FILE_SUFFIX = "_handshake.json";
    private static final String CONFIG_FILE_CREATION = "createFiles";
    private static final String CONFIG_ZGRAB_DIR = "zgrabInstallationDir";
    private static final Logger LOGGER = Logger.getLogger(handshaker.core.HandshakeScanner.class.getName());

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
        brin.close();
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
        JSONObject configJson = handshakeScanner.configParser();

        String dirName = configJson.getString(CONFIG_INPUT_FILES_DIR);
        LOGGER.info(MessageFormat.format(
                "Provided input directory where files consisting of list of IPs will be scanned is : {0}", dirName));

        String outputDirName = configJson.getString(CONFIG_OUTPUT_FILES_DIR);
        LOGGER.info(MessageFormat.format(
                "Provided output directory where files containing results of scanned IPs is : {0}", outputDirName));

        File dir = new File(dirName);
        File[] listOfFiles = dir.listFiles();
        if (listOfFiles.length == 0) {
            LOGGER.info("No input files to process.\nExiting.");
            System.exit(0);
        }

        ArrayList<Thread> threads = new ArrayList<>();
        for (int i = 0; i < listOfFiles.length; i++) {
            ArrayList<JSONObject> ipJsonObjects = handshakeScanner.readInputIPList(listOfFiles[i]);
            LOGGER.info(MessageFormat.format("Found file : {0} in directory : {1}", listOfFiles[i].getAbsolutePath(),
                    dirName));
            HandShakeScan handShakeScan = null;
            if (configJson.getBoolean(CONFIG_FILE_CREATION)) {
                handShakeScan = handshakeScanner.new HandShakeScan(ipJsonObjects, configJson,
                        listOfFiles[i].getAbsolutePath() + RESULT_FILE_SUFFIX, i);
                LOGGER.info(MessageFormat.format("Results are going to be written in file: {0}",
                        listOfFiles[i].getAbsolutePath() + RESULT_FILE_SUFFIX));
                LOGGER.info("Results are also going to be stored in sqlite database.");
            } else {
                LOGGER.info("Results are only going to be stored in sqlite database.");
                handShakeScan = handshakeScanner.new HandShakeScan(ipJsonObjects, configJson, i, listOfFiles[i].getAbsolutePath());
            }
            Thread thread = new Thread(handShakeScan);
            thread.start();
            LOGGER.info(MessageFormat.format("Started thread #: {0} on file {1}", i, listOfFiles[i]));
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
        public String fileName;

        public HandShakeScan(ArrayList<JSONObject> ipJsonObjectList, JSONObject config, int id) {
            result = null;
            this.ipJsonObjectList = ipJsonObjectList;
            this.config = config;
            this.outputFileName = null;
            this.id = id;
        }

        public HandShakeScan(ArrayList<JSONObject> ipJsonObjectList, JSONObject config, int id, String fileName) {
            result = null;
            this.ipJsonObjectList = ipJsonObjectList;
            this.config = config;
            this.outputFileName = null;
            this.id = id;
            this.fileName = fileName;
        }

        public HandShakeScan(ArrayList<JSONObject> ipJsonObjectList, JSONObject config, String outputFileName, int id) {
            result = null;
            this.ipJsonObjectList = ipJsonObjectList;
            this.config = config;
            this.outputFileName = outputFileName;
            this.id = id;
        }

        public void writeResultsToJSON(String fileName, ArrayList<Protocol> protocolResultList) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                String fileContents = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(protocolResultList);
                BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));
                writer.write(fileContents);
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {

            try {
                if (outputFileName != null) {
                    result = handshakeScan(ipJsonObjectList, config);
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.enable(SerializationFeature.INDENT_OUTPUT);
                    mapper.writeValue(new File(outputFileName), (Object) result);
                } else {
                    // TODO;
                    handshakeScan(ipJsonObjectList, config);
                }
                LOGGER.info(MessageFormat.format("Thread ID: {0}: completed handshakes with all IPs in {1}", id, fileName));
                System.out.println("Thread " + id + " finished scanning IPs from " + fileName);
            } catch (IOException | InterruptedException e) {
                System.out.println("Thread " + id + " stopped scanning " + fileName + "due to a memory issue.");
                e.printStackTrace();
            }
        }

        private Result handshakeScan(ArrayList<JSONObject> ipJsonObjectList, JSONObject config)
                throws IOException, InterruptedException {

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
                ArrayList<Protocol> protocolResultList = new ArrayList<Protocol>();
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine(MessageFormat.format("Thread {0}, started handshake scan for ip: {1}", id, ipaddr));
                }
                while (protocolIter.hasNext()) {
                    Protocol protocolResult = new Protocol();
                    String protocol = protocolIter.next();
                    if (protocol.equals(CONFIG_PROTOCOL_COMMENT)) {
                        continue;
                    }

                    JSONObject protocolJsonObject = protocols.getJSONObject(protocol);
                    Iterator<String> ciphersNames = protocolJsonObject.keys();
                    ArrayList<Cipher> cipherResultList = new ArrayList<>();
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.fine(MessageFormat.format(
                                "Thread ID: {0}: starting handshake for protocol: {1} for ip: {2} ", id, protocol,
                                ipaddr));
                    }
                    
                    boolean timeout = false;

                    while (ciphersNames.hasNext() && !timeout) {
                        Cipher cipherResult = new Cipher();
                        String cipherName = ciphersNames.next();
                        cipherResult.setName(cipherName);
                        String cipherInHex = protocolJsonObject.getString(cipherName);
                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.fine(MessageFormat.format(
                                    "Thread ID: {0}: going to do handshake for protocol: {1} with cipher: {2}({3}) for ip: {4} ",
                                    id, protocol, cipherInHex, cipherName, ipaddr));
                        }

                        StringBuffer outputBuffer = new StringBuffer();
                        // zgrab2 should be available in $PATH variable
                        String command = String.format("echo \"%s\" | zgrab2 %s --cipher-suite=%s 2>/dev/null", ipaddr,
                                protocol, cipherInHex);
                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.fine(MessageFormat.format(
                                    "Thread ID: {0}: going to execute handshake zgrab2 command: {1}", id, command));
                        }

                        String[] cmd = { "/bin/sh", "-c", command };
                        ProcessBuilder pb = new ProcessBuilder(cmd);
                        Map<String, String> env = pb.environment();
                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.fine(env.get("PATH"));
                        }
                        env.put("PATH", env.get("PATH") + ":" + config.getString(CONFIG_ZGRAB_DIR));

                        childProcess = pb.start();
                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.fine(MessageFormat.format("Thread ID: {0}: child process is alive: {1}", id,
                                    childProcess.isAlive()));
                        }

                        BufferedReader br = new BufferedReader(new InputStreamReader(childProcess.getInputStream()));

                        while (true) {
                            // TODO: line below seemed to take a long time... investigate?
                            outputLine = br.readLine();
                            if (outputLine == null) {
                                br.close();
                                break;
                            }
                            outputBuffer.append(outputLine);
                        }

                        timeout = outputBuffer.indexOf("connection-timeout") != -1;

                        BufferedReader brError = new BufferedReader(
                                new InputStreamReader(childProcess.getErrorStream()));
                        StringBuffer errorBuffer = new StringBuffer();
                        while (true) {
                            outputLine = brError.readLine();
                            if (outputLine == null) {
                                brError.close();
                                break;
                            }
                            errorBuffer.append(outputLine);
                        }
                        childProcess.waitFor();
                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.fine(
                                    MessageFormat.format("Thread ID: {0}: child process exited with return value: {1}",
                                            id, childProcess.exitValue()));
                        }
                        childProcess.destroy();

                        // TODO: below line took a long time; investigate
                        ObjectMapper mapper = new ObjectMapper();
                        LOGGER.info("Going to log output" + outputBuffer.toString());
                        if (outputBuffer.toString() == "") {
                            LOGGER.info("No output");
                        }
                        LOGGER.fine(outputBuffer.toString());
                        cipherResult.setHandshakeData(mapper.readValue(outputBuffer.toString(), Object.class));
                        cipherResultList.add(cipherResult);
                    }
                    protocolResult.setName(protocol);
                    protocolResult.setCipherResultList(cipherResultList);
                    protocolResultList.add(protocolResult);
                }
                if (config.getBoolean(CONFIG_OUTPUT_TO_JSON_ONLY) == true) {
                    String outputDirName = config.getString(CONFIG_OUTPUT_FILES_DIR);
                    String fileName = outputDirName + ipaddr + ".json"; // Craft the output filename (assume
                                                                        // outputDirName has a trailing slash)
                    LOGGER.info("Writting Results for IP " + ipaddr + " to file: " + fileName);
                    writeResultsToJSON(fileName, protocolResultList);
                }
                if (outputFileName != null) {
                    resultPerIp.setIp(ipaddr);
                    resultPerIp.setProtocol(protocolResultList);
                    resultPerIpsList.add(resultPerIp);
                }
            }
            if (outputFileName != null) {
                result.setScanResult(resultPerIpsList);
                return result;
            }
            return null;
        }
    }
}
