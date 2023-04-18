package com.github.theprez.codefori;

import java.util.Arrays;
import java.util.LinkedList;

import com.github.theprez.jcmdutils.AppLogger;

public class CodeForiServer {
    public static void main(String[] _args) {

        LinkedList<String> args = new LinkedList<String>();
        args.addAll(Arrays.asList(_args));
        if (args.remove("--version")) {
            System.out.println("Version: " + Version.s_version);
            System.out.println("Build time: " + Version.s_compileDateTime);
            System.exit(0);
        }
        final boolean isVerbose = args.remove("-v");
        AppLogger logger = AppLogger.getSingleton(isVerbose); // TODO: custom AppLogger to ensure no System.out
                                                              // contamination
        try {
            SystemConnection conn = new SystemConnection(logger);
            DataStreamProcessor io = new DataStreamProcessor(logger, System.in, System.out, conn);
            io.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(-1);
    }

    private static void printUsageAndExit() {
        System.out.println("Usage: java -jar CodeForiServer.jar [--help] [--port <port>]");
        System.out.println("  --help: Prints this help message.");
        System.exit(0);
    }
}