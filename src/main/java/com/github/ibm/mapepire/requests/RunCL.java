package com.github.ibm.mapepire.requests;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.ibm.mapepire.ClientRequest;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.google.gson.JsonObject;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400Message;
import com.ibm.as400.access.CommandCall;
import com.ibm.as400.access.MessageFile;

public class RunCL extends ClientRequest {

    public RunCL(final DataStreamProcessor _io, final SystemConnection _conn, final JsonObject _reqObj) {
        super(_io, _conn, _reqObj);
    }

    /**
     * Run the requested CL command through a toolbox {@link CommandCall} and reply with every message it
     * produced.
     *
     * @throws SQLException
     *             if the connection is restricted to basic queries
     * @throws IllegalStateException
     *             if there is no IBM i system connection
     * @throws CLExecutionException
     *             if the command does not complete successfully
     */
    @Override
    public void go() throws Exception {
        final SystemConnection systemConnection = getSystemConnection();
        if (systemConnection.isBasicQueryOnly()) {
            throw new SQLException("Only basic queries are allowed");
        }

        final AS400 system = Optional.ofNullable(systemConnection.getSystem())
                .orElseThrow(() -> new IllegalStateException("Not connected to an IBM i system."));

        final String cmd = getRequestField("cmd").getAsString();
        final CommandCall commandCall = new CommandCall(system, cmd);
        commandCall.setMessageOption(AS400Message.MESSAGE_OPTION_ALL);
        final boolean success = commandCall.run();
        final List<IBMiMessage> messages = Stream.of(commandCall.getMessageList()).map(IBMiMessage::new).collect(Collectors.toList());
        addReplyData("data", messages);

        if (!success) {
            throw new CLExecutionException(messages);
        }
    }

    @Override
    public boolean isForcedSynchronous() {
        return true;
    }

    static class IBMiMessage {
        // Gson serializes these field names as the client-facing JSON keys, so they deliberately omit the m_ prefix
        private final String id;
        private final String text;
        private final String help;
        private final String type;
        private final int severity;
        private final long time;
        private final String messageLibrary;
        private final String messageFile;
        private final String sendingProgram;
        private final String sendingInstruction;
        private final String receivingProgram;
        private final String receivingInstruction;

        /**
         * Build a message from one returned by a toolbox {@link CommandCall}.
         *
         * @param _message
         *            the message returned by the command call
         */
        public IBMiMessage(final AS400Message _message) {
            id = _message.getID();
            text = _message.getText();
            switch (_message.getType()) {
                case AS400Message.COMPLETION:
                    type = "COMPLETION";
                    break;
                case AS400Message.DIAGNOSTIC:
                    type = "DIAGNOSTIC";
                    break;
                case AS400Message.ESCAPE:
                case AS400Message.ESCAPE_NOT_HANDLED:
                    type = "ESCAPE";
                    break;
                case AS400Message.INFORMATIONAL:
                    type = "INFORMATIONAL";
                    break;
                case AS400Message.INQUIRY:
                    type = "INQUIRY";
                    break;
                case AS400Message.NOTIFY:
                case AS400Message.NOTIFY_NOT_HANDLED:
                    type = "NOTIFY";
                    break;
                case AS400Message.REPLY_FROM_SYSTEM_REPLY_LIST:
                case AS400Message.REPLY_MESSAGE_DEFAULT_USED:
                case AS400Message.REPLY_NOT_VALIDITY_CHECKED:
                case AS400Message.REPLY_SYSTEM_DEFAULT_USED:
                case AS400Message.REPLY_VALIDITY_CHECKED:
                    type = "REPLY";
                    break;
                case AS400Message.REQUEST:
                case AS400Message.REQUEST_WITH_PROMPTING:
                    type = "REQUEST";
                    break;
                case AS400Message.SENDERS_COPY:
                    type = "SENDER";
                    break;
                default:
                    type = "COMMAND";
            }
            help = formatHelpText(_message.getHelp());
            severity = _message.getSeverity();
            time = _message.getDate().getTime().getTime();
            messageLibrary = _message.getLibraryName();
            messageFile = _message.getFileName();
            sendingProgram = _message.getSendingProgramName();
            sendingInstruction = _message.getSendingProgramInstructionNumber();
            receivingProgram = _message.getReceivingProgramName();
            receivingInstruction = _message.getReceivingProgramInstructionNumber();
        }

        /**
         * Build a message from the current row of a {@code QSYS2.JOBLOG_INFO} query.
         *
         * @param _joblogRs
         *            a result set positioned on a job log row; it is read but not advanced or closed
         * @throws Exception
         *             if a column cannot be read from the result set
         */
        public IBMiMessage(final ResultSet _joblogRs) throws Exception {
            id = _joblogRs.getString("MESSAGE_ID");
            type = _joblogRs.getString("MESSAGE_TYPE");
            text = _joblogRs.getString("MESSAGE_TEXT");
            help = formatHelpText(_joblogRs.getString("MESSAGE_SECOND_LEVEL_TEXT"));
            severity = _joblogRs.getInt("SEVERITY");
            time = _joblogRs.getTimestamp("MESSAGE_TIMESTAMP").getTime();
            messageLibrary = _joblogRs.getString("MESSAGE_LIBRARY");
            messageFile = _joblogRs.getString("MESSAGE_FILE");
            sendingProgram = _joblogRs.getString("FROM_PROGRAM");
            sendingInstruction = _joblogRs.getString("FROM_INSTRUCTION");
            receivingProgram = _joblogRs.getString("TO_PROGRAM");
            receivingInstruction = _joblogRs.getString("TO_INSTRUCTION");
        }

        /**
         * Normalize second-level message text for display.
         *
         * @param _helpText
         *            the raw help text, possibly containing message formatting characters
         * @return the help text with formatting characters substituted and whitespace trimmed, or
         *         {@code null} if there is no non-blank help text
         */
        private static String formatHelpText(final String _helpText) {
            return Optional.ofNullable(_helpText)
                    .map(MessageFile::substituteFormattingCharacters)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .orElse(null);
        }

        public String getHelp() {
            return help;
        }

        public String getId() {
            return id;
        }

        public String getMessageFile() {
            return messageFile;
        }

        public String getMessageLibrary() {
            return messageLibrary;
        }

        public String getReceivingInstruction() {
            return receivingInstruction;
        }

        public String getReceivingProgram() {
            return receivingProgram;
        }

        public String getSendingInstruction() {
            return sendingInstruction;
        }

        public String getSendingProgram() {
            return sendingProgram;
        }

        public int getSeverity() {
            return severity;
        }

        public String getText() {
            return text;
        }

        public long getTime() {
            return time;
        }

        public String getType() {
            return type;
        }
    }

    static class CLExecutionException extends Exception {
        /**
         * Create an exception whose message describes the most recent escape message, ignoring the generic
         * CPF0006 ("Errors occurred in command") that wraps the real failure.
         *
         * @param _messages
         *            the messages produced by the failed command
         */
        public CLExecutionException(final List<IBMiMessage> _messages) {
            super("CL command execution failed: " + _messages.stream()
                    .sorted((m1, m2) -> Long.compare(m2.getTime(), m1.getTime()))
                    .filter(m -> "ESCAPE".equals(m.getType()) && !"CPF0006".equals(m.getId()))
                    .findFirst()
                    .map(m -> String.format("[%s] %s", m.getId(), m.getText()))
                    .orElse("Unknown error"));
        }
    }
}
