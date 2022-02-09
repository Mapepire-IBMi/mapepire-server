package com.github.theprez.codefori;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedList;

import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.ConsoleQuestionAsker;
import com.google.gson.stream.JsonWriter;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400SecurityException;

public class CodeForiServer {
    public static void main(String[] _args) {

        LinkedList<String> args = new LinkedList<String>();
        args.addAll(Arrays.asList(_args));
        if(args.remove("--version")) {
            System.out.println("Version: "+Version.s_version);
            System.out.println("Build time: "+Version.s_compileDateTime);
            System.exit(0);
        }
        final boolean isVerbose = args.remove("-v");
        AppLogger logger = AppLogger.getSingleton(isVerbose); //TODO: custom AppLogger to ensure no System.out contamination

        JsonWriterWriter results = new JsonWriterWriter(logger, new JsonWriter(new PrintWriter(System.out, true)));
        try {
            // results.beginObject();
            if (args.contains("--help") || args.isEmpty()) {
                printUsageAndExit();
            }
            String operation = args.removeFirst().toLowerCase();
            AS400 as400 = getAS400(logger);
            boolean isValid = as400.validateSignon();

            HardWorker worker = new HardWorker(results, logger, as400);
            switch (operation) {
                case "sysval":
                    worker.doSysVal(args);
                    break;
                case "sql":
                    worker.doSql(args);
                    break;
                case "gencmdxml":
                    worker.doGenCmdXml(args);
                    break;
                case "filebytes":
                    worker.doFileBytes(args);
                    break;
                default:
                    logger.printfln_err("ERROR: Unknown command '%s'", operation);
                    printUsageAndExit();
            }
        } catch (Exception e) {
            results.err(e);
            logger.println_err("ERROR: " + e.getLocalizedMessage());
        } finally {
            results.completeflush();
            System.out.println();
        }
    }

    static void addExceptionToResults(AppLogger _logger, Throwable _e, String _key, JsonWriter _results) throws IOException {
        _logger.printExceptionStack_verbose(_e);
        _results.name("error");
        _results.beginObject();
        try {
            _results.name("message").value(_e.getLocalizedMessage());
            _results.name("type").value(_e.getClass().getSimpleName());
        } finally {
            _results.endObject();
        }
    }

    private static AS400 getAS400(AppLogger _logger) throws IOException, AS400SecurityException {
        if (System.getProperty("os.name", "").equalsIgnoreCase("OS/400")) {
            return new AS400("localhost", "*CURRENT", "*CURRENT");
        }
        ConsoleQuestionAsker asker = new ConsoleQuestionAsker();
        String host = asker.askNonEmptyStringQuestion(_logger, null, "Enter system name: ");
        String uid = asker.askNonEmptyStringQuestion(_logger, null, "Enter user id: ");
        String pw = asker.askUserForPwd("Password: ");
        return new AS400(host, uid, pw);
    }

    private static void printUsageAndExit() {
        System.out.println("Usage: java -jar CodeForiServer.jar [--help] [--port <port>]");
        System.out.println("  --help: Prints this help message.");
        System.exit(0);
    }
}