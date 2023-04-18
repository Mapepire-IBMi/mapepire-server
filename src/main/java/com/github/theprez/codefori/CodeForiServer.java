package com.github.theprez.codefori;

import java.util.Arrays;
import java.util.LinkedList;

public class CodeForiServer {
    public static void main(final String[] _args) {

        final LinkedList<String> args = new LinkedList<String>();
        args.addAll(Arrays.asList(_args));
        if (args.remove("--version")) {
            System.out.println("Version: " + Version.s_version);
            System.out.println("Build time: " + Version.s_compileDateTime);
            System.exit(0);
        }
        boolean isVerbose = args.remove("-v");
        isVerbose = isVerbose || Boolean.getBoolean("codeserver.verbose");
        if(isVerbose) {
            System.setProperty("codeserver.verbose", "true");
        }
        try {
            final SystemConnection conn = new SystemConnection();
            final DataStreamProcessor io = new DataStreamProcessor(System.in, System.out, conn);
            io.run();
        } catch (final Exception e) {
            e.printStackTrace();
        }
        System.exit(-1);
    }
}