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
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400JDBCConnection;
import com.ibm.as400.access.AS400Message;
import com.ibm.as400.access.CommandCall;

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

    protected static class IBMiMessage {
        private final String id;
        private final String text;
        private final String help;
        private final int severity;
        private final long time;
        private final String messageLibrary;
        private final String messageFile;
        private final String sendingProgram;
        private final String sendingInstruction;
        private final String sendingType;
        private final String receivingProgram;
        private final String receivingInstruction;
        private final String receivingType;
        private final String defaultReply;

        public IBMiMessage(AS400Message message) {
            id = message.getID();
            text = message.getText();
            help = message.getHelp();
            severity = message.getSeverity();
            time = message.getDate().getTime().getTime();
            messageLibrary = message.getLibraryName();
            messageFile = message.getFileName();
            sendingProgram = message.getSendingProgramName();
            sendingInstruction = message.getSendingProgramInstructionNumber();
            sendingType = message.getSendingType();
            receivingProgram = message.getReceivingProgramName();
            receivingInstruction = message.getReceivingProgramInstructionNumber();
            receivingType = message.getReceivingType();
            defaultReply = message.getDefaultReply();
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

        public String getSendingType() {
            return sendingType;
        }

        public String getReceivingProgram() {
            return receivingProgram;
        }

        public String getReceivingInstruction() {
            return receivingInstruction;
        }

        public String getReceivingType() {
            return receivingType;
        }

        public String getDefaultReply() {
            return defaultReply;
        }
    }
}
