package com.github.ibm.mapepire.requests;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.ibm.mapepire.ClientRequest;
import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.google.gson.JsonObject;
import com.ibm.as400.access.*;

public class RunCL extends ClientRequest {

    public RunCL(final DataStreamProcessor _io, final SystemConnection m_conn, final JsonObject _reqObj) {
        super(_io, m_conn, _reqObj);
    }

    @Override
    public boolean isForcedSynchronous() {
        return true;
    }
;
    @Override
    public void go() throws Exception {
        final AS400 system = Optional.ofNullable(getSystemConnection().getSystem())
                .orElseThrow(() -> new IllegalStateException("Not connected to an IBM i system."));

        final String cmd = getRequestField("cmd").getAsString();
        final CommandCall commandCall = new CommandCall(system, cmd);
        commandCall.setMessageOption(AS400Message.MESSAGE_OPTION_ALL);
        addReplyData("successful", commandCall.run());
        addReplyData("messages", Stream.of(commandCall.getMessageList()).map(IBMiMessage::new).collect(Collectors.toList()));
    }

    static class IBMiMessage {
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

        private static String formatHelpText(String helpText) {
           return Optional.ofNullable(helpText)
                    .map(MessageFile::substituteFormattingCharacters)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .orElse(null);
        }

        public IBMiMessage(AS400Message message) {
            id = message.getID();
            text = message.getText();
            switch (message.getType()) {
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
            help = formatHelpText(message.getHelp());
            severity = message.getSeverity();
            time = message.getDate().getTime().getTime();
            messageLibrary = message.getLibraryName();
            messageFile = message.getFileName();
            sendingProgram = message.getSendingProgramName();
            sendingInstruction = message.getSendingProgramInstructionNumber();
            receivingProgram = message.getReceivingProgramName();
            receivingInstruction = message.getReceivingProgramInstructionNumber();
        }

        public IBMiMessage(ResultSet joblogRs) throws Exception {
            id = joblogRs.getString("MESSAGE_ID");
            type = joblogRs.getString("MESSAGE_TYPE");
            text = joblogRs.getString("MESSAGE_TEXT");
            help = formatHelpText(joblogRs.getString("MESSAGE_SECOND_LEVEL_TEXT"));
            severity = joblogRs.getInt("SEVERITY");
            time = joblogRs.getTimestamp("MESSAGE_TIMESTAMP").getTime();
            messageLibrary = joblogRs.getString("MESSAGE_LIBRARY");
            messageFile = joblogRs.getString("MESSAGE_FILE");
            sendingProgram = joblogRs.getString("FROM_PROGRAM");
            sendingInstruction = joblogRs.getString("FROM_INSTRUCTION");
            receivingProgram = joblogRs.getString("TO_PROGRAM");
            receivingInstruction = joblogRs.getString("TO_INSTRUCTION");
        }

        public String getId() {
            return id;
        }

        public String getText() {
            return text;
        }

        public String getHelp() {
            return help;
        }

        public int getSeverity() {
            return severity;
        }

        public long getTime() {
            return time;
        }

        public String getMessageLibrary() {
            return messageLibrary;
        }

        public String getMessageFile() {
            return messageFile;
        }

        public String getSendingProgram() {
            return sendingProgram;
        }

        public String getSendingInstruction() {
            return sendingInstruction;
        }

        public String getReceivingProgram() {
            return receivingProgram;
        }

        public String getReceivingInstruction() {
            return receivingInstruction;
        }

        public String getType() {
            return type;
        }
    }
}
