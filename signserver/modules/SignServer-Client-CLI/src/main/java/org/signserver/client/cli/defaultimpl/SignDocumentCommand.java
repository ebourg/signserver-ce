/*************************************************************************
 *                                                                       *
 *  SignServer: The OpenSource Automated Signing Server                  *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.signserver.client.cli.defaultimpl;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;
import javax.xml.ws.soap.SOAPFaultException;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.ejbca.ui.cli.util.ConsolePasswordReader;
import org.signserver.cli.spi.AbstractCommand;
import org.signserver.cli.spi.CommandFailureException;
import org.signserver.cli.spi.IllegalCommandArgumentsException;
import org.signserver.common.AccessDeniedException;
import org.signserver.common.AuthorizationRequiredException;
import org.signserver.common.CryptoTokenOfflineException;
import org.signserver.common.IllegalRequestException;
import org.signserver.common.SignServerException;
import org.signserver.protocol.ws.client.SignServerWSClientFactory;

/**
 * Command Line Interface (CLI) for signing documents.
 *
 * @author Markus Kilås
 * @version $Id$
 */
public class SignDocumentCommand extends AbstractCommand {

    /** Logger for this class. */
    private static final Logger LOG = Logger.getLogger(SignDocumentCommand.class);

    /** ResourceBundle with internationalized StringS. */
    private static final ResourceBundle TEXTS = ResourceBundle.getBundle(
            "org/signserver/client/cli/defaultimpl/ResourceBundle");

    private static final String DEFAULT_CLIENTWS_WSDL_URL = "/signserver/ClientWSService/ClientWS?wsdl";
    
    /** System-specific new line characters. **/
    private static final String NL = System.getProperty("line.separator");

    /** The name of this command. */
    private static final String COMMAND = "signdocument";

    /** Option WORKERID. */
    public static final String WORKERID = "workerid";

    /** Option WORKERNAME. */
    public static final String WORKERNAME = "workername";

    /** Option DATA. */
    public static final String DATA = "data";

    /** Option HOST. */
    public static final String HOST = "host";

    /** Option INFILE. */
    public static final String INFILE = "infile";
    
    /** Option OUTFILE. */
    public static final String OUTFILE = "outfile";
    
    /** Option INDIR. */
    public static final String INDIR = "indir";

    /** Option OUTDIR. */
    public static final String OUTDIR = "outdir";
    
    /** Option THREADS. */
    public static final String THREADS = "threads";
    
    /** Option REMOVEFROMINDIR. */
    public static final String REMOVEFROMINDIR = "removefromindir";
    
    /** Option ONEFIRST. */
    public static final String ONEFIRST = "onefirst";

    /** Option PORT. */
    public static final String PORT = "port";

    public static final String SERVLET = "servlet";

    /** Option PROTOCOL. */
    public static final String PROTOCOL = "protocol";

    /** Option USERNAME. */
    public static final String USERNAME = "username";

    /** Option PASSWORD. */
    public static final String PASSWORD = "password";

    /** Option PDFPASSWORD. */
    public static final String PDFPASSWORD = "pdfpassword";

    /** Option METADATA. */
    public static final String METADATA = "metadata";

    /** The command line options. */
    private static final Options OPTIONS;

    private static final int DEFAULT_THREADS = 1;

    /**
     * Protocols that can be used for accessing SignServer.
     */
    public static enum Protocol {
        /** The SignServerWS interface. */
        WEBSERVICES,
        
        /** The ClientWS interface. */
        CLIENTWS,

        /** The HTTP interface. */
        HTTP
    }

    static {
        OPTIONS = new Options();
        OPTIONS.addOption(WORKERID, true,
                TEXTS.getString("WORKERID_DESCRIPTION"));
        OPTIONS.addOption(WORKERNAME, true,
                TEXTS.getString("WORKERNAME_DESCRIPTION"));
        OPTIONS.addOption(DATA, true,
                TEXTS.getString("DATA_DESCRIPTION"));
        OPTIONS.addOption(INFILE, true,
                TEXTS.getString("INFILE_DESCRIPTION"));
        OPTIONS.addOption(OUTFILE, true,
                TEXTS.getString("OUTFILE_DESCRIPTION"));
        OPTIONS.addOption(HOST, true,
                TEXTS.getString("HOST_DESCRIPTION"));
        OPTIONS.addOption(PORT, true,
                TEXTS.getString("PORT_DESCRIPTION"));
        OPTIONS.addOption(SERVLET, true,
                TEXTS.getString("SERVLET_DESCRIPTION"));
        OPTIONS.addOption(PROTOCOL, true,
                TEXTS.getString("PROTOCOL_DESCRIPTION"));
        OPTIONS.addOption(USERNAME, true,
                TEXTS.getString("USERNAME_DESCRIPTION"));
        OPTIONS.addOption(PASSWORD, true,
                TEXTS.getString("PASSWORD_DESCRIPTION"));
        OPTIONS.addOption(PDFPASSWORD, true,
                TEXTS.getString("PDFPASSWORD_DESCRIPTION"));
        OPTIONS.addOption(METADATA, true,
                TEXTS.getString("METADATA_DESCRIPTION"));
        OPTIONS.addOption(INDIR, true,
                "TODO");
        OPTIONS.addOption(OUTDIR, true,
                "TODO");
        OPTIONS.addOption(THREADS, true,
                "TODO");
        OPTIONS.addOption(REMOVEFROMINDIR, false,
                "TODO");
        OPTIONS.addOption(ONEFIRST, false,
                "TODO");
        for (Option option : KeyStoreOptions.getKeyStoreOptions()) {
            OPTIONS.addOption(option);
        }
    }

    /** ID of worker who should perform the operation. */
    private int workerId;

    /** Name of worker who should perform the operation. */
    private String workerName;

    /** Data to sign. */
    private String data;

    /** Hostname or IP address of the SignServer host. */
    private String host;

    /** TCP port number of the SignServer host. */
    private Integer port;

    private String servlet = "/signserver/process";

    /** File to read the data from. */
    private File inFile;

    /** File to read the signed data to. */
    private File outFile;
    
    /** Directory to read files from. */
    private File inDir;
    
    /** Directory to write files to. */
    private File outDir;
    
    /** Number of threads to use when running in batch mode. */
    private Integer threads;
    
    /** If the successfully processed files should be removed from indir. */
    private boolean removeFromIndir;
    
    /** If one request should be set first before starting the remaining threads. */
    private boolean oneFirst;

    /** Protocol to use for contacting SignServer. */
    private Protocol protocol = Protocol.HTTP;

    private String username;
    private String password;

    private String pdfPassword;

    private KeyStoreOptions keyStoreOptions = new KeyStoreOptions();

    /** Meta data parameters passed in */
    private Map<String, String> metadata;
    
    @Override
    public String getDescription() {
        return "Request a document to be signed by SignServer";
    }

    @Override
    public String getUsages() {
        StringBuilder footer = new StringBuilder();
        footer.append(NL)
            .append("Sample usages:").append(NL)
            .append("a) ").append(COMMAND).append(" -workername XMLSigner -data \"<root/>\"").append(NL)
            .append("b) ").append(COMMAND).append(" -workername XMLSigner -infile /tmp/document.xml").append(NL)
            .append("c) ").append(COMMAND).append(" -workerid 2 -data \"<root/>\" -truststore truststore.jks -truststorepwd changeit").append(NL)
            .append("d) ").append(COMMAND).append(" -workerid 2 -data \"<root/>\" -keystore superadmin.jks -keystorepwd foo123").append(NL)
            .append("e) ").append(COMMAND).append(" -workerid 2 -data \"<root/>\" -metadata param1=value1 -metadata param2=value2").append(NL)
            .append("f) ").append(COMMAND).append(" -workerid 3 -indir ./input/ -removefromindir -outdir ./output/ -threads 5 -onefirst").append(NL);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final HelpFormatter formatter = new HelpFormatter();
        
        PrintWriter pw = new PrintWriter(bout);
        formatter.printHelp(pw, HelpFormatter.DEFAULT_WIDTH, "signdocument <-workername WORKERNAME | -workerid WORKERID> [options]",  getDescription(), OPTIONS, HelpFormatter.DEFAULT_LEFT_PAD, HelpFormatter.DEFAULT_DESC_PAD, footer.toString());
        pw.close();
        
        return bout.toString();
    }

    /**
     * Reads all the options from the command line.
     *
     * @param line The command line to read from
     */
    private void parseCommandLine(final CommandLine line)
        throws IllegalCommandArgumentsException {
        if (line.hasOption(WORKERNAME)) {
            workerName = line.getOptionValue(WORKERNAME, null);
        }
        if (line.hasOption(WORKERID)) {
            workerId = Integer.parseInt(line.getOptionValue(WORKERID, null));
        }
        host = line.getOptionValue(HOST, KeyStoreOptions.DEFAULT_HOST);
        if (line.hasOption(PORT)) {
            port = Integer.parseInt(line.getOptionValue(PORT));
        }
        if (line.hasOption(SERVLET)) {
            servlet = line.getOptionValue(SERVLET, null);
        }
        if (line.hasOption(DATA)) {
            data = line.getOptionValue(DATA, null);
        }
        if (line.hasOption(INFILE)) {
            inFile = new File(line.getOptionValue(INFILE, null));
        }
        if (line.hasOption(OUTFILE)) {
            outFile = new File(line.getOptionValue(OUTFILE, null));
        }
        if (line.hasOption(INDIR)) {
            inDir = new File(line.getOptionValue(INDIR, null));
        }
        if (line.hasOption(OUTDIR)) {
            outDir = new File(line.getOptionValue(OUTDIR, null));
        }
        if (line.hasOption(THREADS)) {
            threads = Integer.parseInt(line.getOptionValue(THREADS, null));
        }
        if (line.hasOption(REMOVEFROMINDIR)) {
            removeFromIndir = true;
        }
        if (line.hasOption(ONEFIRST)) {
            oneFirst = true;
        }
        if (line.hasOption(PROTOCOL)) {
            protocol = Protocol.valueOf(line.getOptionValue(
                    PROTOCOL, null));
            
            // if the protocol is WS and -servlet is not set, override the servlet URL
            // with the default one for the WS servlet
            if (Protocol.WEBSERVICES.equals(protocol) &&
            	!line.hasOption(SERVLET)) {
            	servlet = SignServerWSClientFactory.DEFAULT_WSDL_URL;
            }
            if ((Protocol.CLIENTWS.equals(protocol)) &&
            	!line.hasOption(SERVLET)) {
            	servlet = DEFAULT_CLIENTWS_WSDL_URL;
            }
        }
        if (line.hasOption(USERNAME)) {
            username = line.getOptionValue(USERNAME, null);
        }
        if (line.hasOption(PASSWORD)) {
            password = line.getOptionValue(PASSWORD, null);
        }
        if (line.hasOption(PDFPASSWORD)) {
            pdfPassword = line.getOptionValue(PDFPASSWORD, null);
        }
        
        if (line.hasOption(METADATA)) {
            metadata = MetadataParser.parseMetadata(line.getOptionValues(METADATA));
        }
        
        try {
            final ConsolePasswordReader passwordReader = createConsolePasswordReader();
            keyStoreOptions.parseCommandLine(line, passwordReader, out);

            // Prompt for user password if not given
            if (username != null && password == null) {
                out.print("Password for user '" + username + "': ");
                out.flush();
                password = new String(passwordReader.readPassword());
            }
        } catch (IOException ex) {
            throw new IllegalCommandArgumentsException("Failed to read password: " + ex.getLocalizedMessage());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalCommandArgumentsException("Failure setting up keystores: " + ex.getMessage());
        } catch (CertificateException ex) {
            throw new IllegalCommandArgumentsException("Failure setting up keystores: " + ex.getMessage());
        } catch (KeyStoreException ex) {
            throw new IllegalCommandArgumentsException("Failure setting up keystores: " + ex.getMessage());
        }
    }
    
    /**
     * @return a ConsolePasswordReader that can be used to read passwords
     */
    protected ConsolePasswordReader createConsolePasswordReader() {
        return new ConsolePasswordReader();
    }

    /**
     * Checks that all mandatory options are given.
     */
    private void validateOptions() throws IllegalCommandArgumentsException {
        if (workerName == null && workerId == 0) {
            throw new IllegalCommandArgumentsException(
                    "Missing -workername or -workerid");
        } else if (data == null && inFile == null && inDir == null && outDir == null) {
            throw new IllegalCommandArgumentsException("Missing -data, -infile or -indir");
        } 
        
        if (inDir != null && outDir == null) {
            throw new IllegalCommandArgumentsException("Missing -outdir");
        }
        if (data != null && inFile != null) {
            throw new IllegalCommandArgumentsException("Can not specify both -data and -infile");
        }
        if (data != null && inDir != null) {
            throw new IllegalCommandArgumentsException("Can not specify both -data and -indir");
        }
        if (inFile != null && inDir != null) {
            throw new IllegalCommandArgumentsException("Can not specify both -infile and -indir");
        }

        if (inDir != null && inDir.equals(outDir)) {
            throw new IllegalCommandArgumentsException("Can not specify the same directory as -indir and -outdir");
        }
        
        if (inDir == null & threads != null) {
            throw new IllegalCommandArgumentsException("Can not specify -threads unless -indir");
        }

        if (threads != null && threads < 1) {
            throw new IllegalCommandArgumentsException("Number of threads must be > 0");
        }

        keyStoreOptions.validateOptions();
    }

    /**
     * Creates a DocumentSigner using the choosen protocol.
     *
     * @return a DocumentSigner using the choosen protocol
     * @throws MalformedURLException in case an URL can not be constructed
     * using the given host and port
     */
    private DocumentSigner createSigner(final String currentPassword) throws MalformedURLException {
        final DocumentSigner signer;

        keyStoreOptions.setupHTTPS(); // TODO: Should be done earlier and only once (not for each signer)

        if (port == null) {
            if (keyStoreOptions.isUsePrivateHTTPS()) {
                port = KeyStoreOptions.DEFAULT_PRIVATE_HTTPS_PORT;
            } else if (keyStoreOptions.isUseHTTPS()) {
                port = KeyStoreOptions.DEFAULT_PUBLIC_HTTPS_PORT;
            } else {
                port = KeyStoreOptions.DEFAULT_HTTP_PORT;
            }
        }

        switch (protocol) {
            case WEBSERVICES: {
                LOG.debug("Using SignServerWS as procotol");
            
                final String workerIdOrName;
                if (workerId == 0) {
                    workerIdOrName = workerName;
                } else {
                    workerIdOrName = String.valueOf(workerId);
                }

                signer = new WebServicesDocumentSigner(
                    host,
                    port,
                    servlet,
                    workerIdOrName,
                    keyStoreOptions.isUseHTTPS(),
                    username, currentPassword,
                    pdfPassword, metadata);
                break;
            }
            case CLIENTWS: {
                LOG.debug("Using ClientWS as procotol");
            
                final String workerIdOrName;
                if (workerId == 0) {
                    workerIdOrName = workerName;
                } else {
                    workerIdOrName = String.valueOf(workerId);
                }

                signer = new ClientWSDocumentSigner(
                    host,
                    port,
                    servlet,
                    workerIdOrName,
                    keyStoreOptions.isUseHTTPS(),
                    username, currentPassword,
                    pdfPassword, metadata);
                break;
            }
            case HTTP:
            default: {
                LOG.debug("Using HTTP as procotol");
                final URL url = new URL(keyStoreOptions.isUseHTTPS() ? "https" : "http", host, port, servlet);
                if (workerId == 0) {
                    signer = new HTTPDocumentSigner(url, workerName, username, currentPassword, pdfPassword, metadata);
                } else {
                    signer = new HTTPDocumentSigner(url, workerId, username, currentPassword, pdfPassword, metadata);
                }
            }
        }
        return signer;
    }

    /**
     * Execute the signing operation.
     */
    protected void run1(TransferManager producer, final File inFile, final File outFile) {
        FileInputStream fin = null;
        try {
            final byte[] bytes;

            Map<String, Object> requestContext = new HashMap<String, Object>();
            if (inFile == null) {
                bytes = data.getBytes();
            } else {
                requestContext.put("FILENAME", inFile.getName());
                fin = new FileInputStream(inFile);
                bytes = new byte[(int) inFile.length()];
                fin.read(bytes);
            }
            run(producer, requestContext, inFile, bytes, outFile);
        } catch (FileNotFoundException ex) {
            LOG.error(MessageFormat.format(TEXTS.getString("FILE_NOT_FOUND:"),
                    ex.getLocalizedMessage()));
        } catch (IOException ex) {
            LOG.error(ex);
        } finally {
            if (fin != null) {
                try {
                    fin.close();
                } catch (IOException ex) {
                    LOG.error("Error closing file", ex);
                }
            }
        }
    }

    private void run(TransferManager producer, Map<String, Object> requestContext, final File inFile, final byte[] bytes, final File outFile) {  // TODO: merge with run1 ?, inFile here is only used when removing the file
        try {
            OutputStream out = null;
            try {
                if (outFile == null) {
                    out = this.out;
                } else {
                    out = new FileOutputStream(outFile);
                }
                final DocumentSigner signer = createSigner(producer == null ? password : producer.getPassword());
                
                // Take start time
                final long startTime = System.nanoTime();
        
                // Get the data signed
                signer.sign(bytes, out, requestContext);
                
                // Take stop time
                final long estimatedTime = System.nanoTime() - startTime;
                
                if (LOG.isInfoEnabled()) {
                    LOG.info("Wrote " + outFile + ".");
                    LOG.info("Processing " + (inFile == null ? "" : inFile.getName()) + " took "
                        + TimeUnit.NANOSECONDS.toMillis(estimatedTime) + " ms.");
                }
            } finally {
                if (out != null && out != System.out) {
                    out.close();
                }
            }
            
            if (removeFromIndir && inFile != null && inFile.exists()) {
                if (inFile.delete()) {
                    LOG.info("Removed " + inFile);
                } else {
                    LOG.error("Could not remove " + inFile);
                    if (producer != null) {
                        producer.registerFailure();
                    }
                }
            }
            if (producer != null) {
                producer.registerSuccess(); // Login must have worked
            }
        } catch (FileNotFoundException ex) {
            LOG.error(MessageFormat.format(TEXTS.getString("FILE_NOT_FOUND:"),
                    ex.getLocalizedMessage())); // TODO
            if (producer != null) {
                producer.registerFailure();
            }
        } catch (IllegalRequestException ex) {
            LOG.error(ex.getLocalizedMessage()); // TOOD
            if (producer != null) {
                producer.registerFailure();
            }
        } catch (CryptoTokenOfflineException ex) {
            LOG.error(ex.getLocalizedMessage()); // TODO
            if (producer != null) {
                producer.registerFailure();
            }
        } catch (SignServerException ex) {
            LOG.error(ex.getLocalizedMessage()); // TODO
            if (producer != null) {
                producer.registerFailure();
            }
        } catch (SOAPFaultException ex) {
            if (ex.getCause() instanceof AuthorizationRequiredException) {
                final AuthorizationRequiredException authEx =
                        (AuthorizationRequiredException) ex.getCause();
                LOG.error("Authorization required: " + authEx.getMessage()); // TODO
            } else if (ex.getCause() instanceof AccessDeniedException) {
                final AccessDeniedException authEx =
                        (AccessDeniedException) ex.getCause();
                LOG.error("Access denied: " + authEx.getMessage()); // TODO
            }
            LOG.error(ex);
        } catch (HTTPException ex) {
            LOG.error("Failure for " + (inFile == null ? "" : inFile.getName()) + ": HTTP Error " + ex.getResponseCode() + ": " + ex.getResponseMessage());
            
            if (producer != null) {
                if (ex.getResponseCode() == 401) { // Only abort for authentication failure
                    producer.tryAgainWithNewPassword(inFile);
                } else {
                    producer.registerFailure();
                }
            }
        } catch (IOException ex) {
            LOG.error("Failure for " + (inFile == null ? "" : inFile.getName()) + ": " + ex.getMessage());
            if (producer != null) {
                producer.registerFailure();
            }
        }
    }

    @Override
    public int execute(String[] args) throws IllegalCommandArgumentsException, CommandFailureException {
        try {
            // Parse the command line
            parseCommandLine(new GnuParser().parse(OPTIONS, args));
            validateOptions();

            if (inFile != null) {
                LOG.debug("Will request for single file " + inFile);
                run1(null, inFile, outFile);
            } else if(inDir != null) {
                LOG.debug("Will request for each file in directory " + inDir);
                File[] inFiles = inDir.listFiles();
                if (inFiles == null || inFiles.length == 0) {
                    LOG.error("No input files");
                    return 1;
                }
                TransferManager producer = new TransferManager(inFiles, username, password, oneFirst);
                
                if (threads == null) {
                    threads = DEFAULT_THREADS;
                }
                final int threadCount = threads > inFiles.length ? inFiles.length : threads;
                final ArrayList<TransferThread> consumers = new ArrayList<TransferThread>();
                
                for (int i = 0; i < threadCount; i++) {
                    consumers.add(new TransferThread(i, producer));
                }
                
                // Start the threads
                for (TransferThread consumer : consumers) {
                    consumer.start();
                }
                
                // Wait for the threads to finish
                try {
                    for (TransferThread w : consumers) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Waiting for thread " + w.getName());
                        }
                        w.join();
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Thread " + w.getName() + " stopped");
                        }
                    }
                } catch (InterruptedException ex) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Interupted when waiting for thread: " + ex.getMessage());
                    }
                }
                
                if (producer.isAborted()) {
                    throw new CommandFailureException("Aborted due to failure.");
                }
                
                if (producer.hasFailures()) {
                    throw new CommandFailureException("At least one file failed.");
                }
                
            } else {
                LOG.debug("Will requst for the specified data");
                run1(null, null, outFile);
            }
                
            return 0;
        } catch (ParseException ex) {
            throw new IllegalCommandArgumentsException(ex.getMessage());
        }
    }
    
    private static class TransferManager {

        private final LinkedList<File> files = new LinkedList<File>();
        private final String username;
        private volatile String password;
        
        private boolean oneFirst;
        private boolean firstAlreadyServed;
        private File first;
        private int retryCount;
        
        private boolean aborted;
        private boolean failed;
        private int success;
        
        private TransferManager(File[] inFiles, String username, String password, boolean oneFirst) {
            files.addAll(Arrays.asList(inFiles));
            this.username = username;
            this.password = password;
            this.oneFirst = oneFirst;
        }

        public synchronized File nextFile() {
            if (aborted) {
                return null;
            }
            
            if (oneFirst && firstAlreadyServed) {
                while (oneFirst && !aborted) {
                    try {
                        wait();
                    } catch (InterruptedException ignored) {}
                }
                if (aborted) {
                    return null;
                }
            } else if (oneFirst) {
                firstAlreadyServed = true;
                first = files.isEmpty() ? null : files.remove();
                return first;
            }
            
            return files.isEmpty() ? null : files.remove();
        }
        
        public synchronized void abort() {
            aborted = true;
            notifyAll();
        }
        
        public synchronized boolean isAborted() {
            return aborted;
        }
        
        public synchronized void registerFailure() {
            failed = true;
            if (oneFirst) { // If the first one did not succeed in onefirst mode we will abort
                abort();
            }
        }

        public synchronized boolean hasFailures() {
            return failed;
        }
        
        public synchronized void registerSuccess() {
            success++;
            if (oneFirst && firstAlreadyServed) {
                oneFirst = false;
                notifyAll();
            }
        }
        
        private boolean hasSuccess() {
            return success > 0;
        }

        private synchronized void tryAgainWithNewPassword(File inFile) {
            // Note more than one thread might be standing in line for this method
            if (aborted) {
                return;
            }
            
            // If the password has not worked before, ask for a new password unless we are already waiting for the oneFirst
            // Or if this is the next attempt for the same file that we just asked password for
            if ((!hasSuccess() && !oneFirst) || (oneFirst && inFile.equals(first))) {
                if (++retryCount > 3) {
                    abort();
                    return;
                } else {
                    char[] pass = System.console().readPassword("Enter correct password for user '%s': ", username);
                    password = new String(pass);

                    // We will now only accept one new request until it succeeds
                    oneFirst = true;
                    firstAlreadyServed = false;
                }
            }
            
            // Put back the file to be tested again
            files.addFirst(inFile);
            notifyAll();
        }
        
        public String getPassword() {
            return password; // TODO.......
        }
        
    }
    
    private class TransferThread extends Thread {
        private final int id;
        private final TransferManager producer;

        public TransferThread(int id, TransferManager producer) {
            super("transfer-" + id);
            this.id = id;
            this.producer = producer;
        }
        
        @Override
        public void run() {
            LOG.trace("Starting " + getName() + "...");
            File file;
            while ((file = producer.nextFile()) != null) {
                LOG.info("Sending " + file + "...");

                run1(producer, file, new File(outDir, file.getName())); // TODO: error handling
            }
            LOG.trace(id + ": No more work.");
        }
    }
    
}
