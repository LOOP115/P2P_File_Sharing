package comp90015.idxsrv.peer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

import comp90015.idxsrv.message.AuthenticateReply;
import comp90015.idxsrv.message.AuthenticateRequest;
import comp90015.idxsrv.message.JsonSerializationException;
import comp90015.idxsrv.message.Message;
import comp90015.idxsrv.message.MessageFactory;
import comp90015.idxsrv.message.SearchRequest;
import comp90015.idxsrv.textgui.ITerminalLogger;

public class StressFunc extends Thread {

    private AuthenticateRequest auth = null;
    private Socket socket;
    private InputStream inputStream = null;
    private OutputStream outputStream = null;
    private BufferedReader bufferedReader = null;
    private BufferedWriter bufferedWriter = null;
    private ITerminalLogger logger;

    public StressFunc(AuthenticateRequest auth, String ipAddr, int port, ITerminalLogger logger) {
        this.auth = auth;
        this.logger = logger;
        try {
            InetAddress a = InetAddress.getByName(ipAddr);
            socket = new Socket(a, port);
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.logError(e.getMessage());
            return;
        }
        try {
            this.socket.setSoTimeout(1000);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try {
            // Send Auth request
            writeMsg(bufferedWriter, auth);
            Message msg;
            // get hello from server
            msg = readMsg(bufferedReader);
            // read auth reply from server
            msg = readMsg(bufferedReader);
            AuthenticateReply ar = (AuthenticateReply) msg;
            if (ar.success) {
                String[] s = { "r" };
                SearchRequest searchRequest = new SearchRequest(1000, s);
                writeMsg(bufferedWriter, searchRequest);
                msg = readMsg(bufferedReader);

                // count success
                StressTest.numSuccess++;
//                logger.logInfo(StressTest.numSuccess + "");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JsonSerializationException e) {
            e.printStackTrace();
        }
    }

    private void writeMsg(BufferedWriter bufferedWriter, Message msg) throws IOException {
        // tgui.logDebug("sending: "+msg.toString());
        bufferedWriter.write(msg.toString());
        bufferedWriter.newLine();
        bufferedWriter.flush();
    }

    private Message readMsg(BufferedReader bufferedReader) throws IOException, JsonSerializationException {
        String jsonStr = bufferedReader.readLine();
        if (jsonStr != null) {
            Message msg = (Message) MessageFactory.deserialize(jsonStr);
            // tgui.logDebug("received: "+msg.toString());
            return msg;
        } else {
            throw new IOException();
        }
    }
}
