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
import com.google.gson.annotations.SerializedName;
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
        // The serialized names are part of the client-facing JSON reply and must not change
        @SerializedName("id")
        private final String m_id;
        @SerializedName("text")
        private final String m_text;
        @SerializedName("help")
        private final String m_help;
        @SerializedName("type")
        private final String m_type;
        @SerializedName("severity")
        private final int m_severity;
        @SerializedName("time")
        private final long m_time;
        @SerializedName("messageLibrary")
        private final String m_messageLibrary;
        @SerializedName("messageFile")
        private final String m_messageFile;
        @SerializedName("sendingProgram")
        private final String m_sendingProgram;
        @SerializedName("sendingInstruction")
        private final String m_sendingInstruction;
        @SerializedName("receivingProgram")
        private final String m_receivingProgram;
        @SerializedName("receivingInstruction")
        private final String m_receivingInstruction;

        /**
         * Build a message from one returned by a toolbox {@link CommandCall}.
         *
         * @param _message
         *            the message returned by the command call
         */
        public IBMiMessage(final AS400Message _message) {
            m_id = _message.getID();
            m_text = _message.getText();
            switch (_message.getType()) {
                case AS400Message.COMPLETION:
                    m_type = "COMPLETION";
                    break;
                case AS400Message.DIAGNOSTIC:
                    m_type = "DIAGNOSTIC";
                    break;
                case AS400Message.ESCAPE:
                case AS400Message.ESCAPE_NOT_HANDLED:
                    m_type = "ESCAPE";
                    break;
                case AS400Message.INFORMATIONAL:
                    m_type = "INFORMATIONAL";
                    break;
                case AS400Message.INQUIRY:
                    m_type = "INQUIRY";
                    break;
                case AS400Message.NOTIFY:
                case AS400Message.NOTIFY_NOT_HANDLED:
                    m_type = "NOTIFY";
                    break;
                case AS400Message.REPLY_FROM_SYSTEM_REPLY_LIST:
                case AS400Message.REPLY_MESSAGE_DEFAULT_USED:
                case AS400Message.REPLY_NOT_VALIDITY_CHECKED:
                case AS400Message.REPLY_SYSTEM_DEFAULT_USED:
                case AS400Message.REPLY_VALIDITY_CHECKED:
                    m_type = "REPLY";
                    break;
                case AS400Message.REQUEST:
                case AS400Message.REQUEST_WITH_PROMPTING:
                    m_type = "REQUEST";
                    break;
                case AS400Message.SENDERS_COPY:
                    m_type = "SENDER";
                    break;
                default:
                    m_type = "COMMAND";
            }
            m_help = formatHelpText(_message.getHelp());
            m_severity = _message.getSeverity();
            m_time = _message.getDate().getTime().getTime();
            m_messageLibrary = _message.getLibraryName();
            m_messageFile = _message.getFileName();
            m_sendingProgram = _message.getSendingProgramName();
            m_sendingInstruction = _message.getSendingProgramInstructionNumber();
            m_receivingProgram = _message.getReceivingProgramName();
            m_receivingInstruction = _message.getReceivingProgramInstructionNumber();
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
            m_id = _joblogRs.getString("MESSAGE_ID");
            m_type = _joblogRs.getString("MESSAGE_TYPE");
            m_text = _joblogRs.getString("MESSAGE_TEXT");
            m_help = formatHelpText(_joblogRs.getString("MESSAGE_SECOND_LEVEL_TEXT"));
            m_severity = _joblogRs.getInt("SEVERITY");
            m_time = _joblogRs.getTimestamp("MESSAGE_TIMESTAMP").getTime();
            m_messageLibrary = _joblogRs.getString("MESSAGE_LIBRARY");
            m_messageFile = _joblogRs.getString("MESSAGE_FILE");
            m_sendingProgram = _joblogRs.getString("FROM_PROGRAM");
            m_sendingInstruction = _joblogRs.getString("FROM_INSTRUCTION");
            m_receivingProgram = _joblogRs.getString("TO_PROGRAM");
            m_receivingInstruction = _joblogRs.getString("TO_INSTRUCTION");
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
            return m_help;
        }

        public String getId() {
            return m_id;
        }

        public String getMessageFile() {
            return m_messageFile;
        }

        public String getMessageLibrary() {
            return m_messageLibrary;
        }

        public String getReceivingInstruction() {
            return m_receivingInstruction;
        }

        public String getReceivingProgram() {
            return m_receivingProgram;
        }

        public String getSendingInstruction() {
            return m_sendingInstruction;
        }

        public String getSendingProgram() {
            return m_sendingProgram;
        }

        public int getSeverity() {
            return m_severity;
        }

        public String getText() {
            return m_text;
        }

        public long getTime() {
            return m_time;
        }

        public String getType() {
            return m_type;
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
