package comp90015.idxsrv.peer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingDeque;

import comp90015.idxsrv.filemgr.BlockUnavailableException;
import comp90015.idxsrv.filemgr.FileMgr;
import comp90015.idxsrv.message.BlockReply;
import comp90015.idxsrv.message.BlockRequest;
import comp90015.idxsrv.message.ErrorMsg;
import comp90015.idxsrv.message.Goodbye;
import comp90015.idxsrv.message.JsonSerializationException;
import comp90015.idxsrv.message.Message;
import comp90015.idxsrv.message.MessageFactory;
import comp90015.idxsrv.server.IOThread;
import comp90015.idxsrv.textgui.ISharerGUI;

public class Process extends Thread{
    private LinkedBlockingDeque<Socket> incomingConnections;
	private IOThread ioThread;
	private ISharerGUI tgui;
	private HashMap<String, FileMgr> fileMgrMap;
    private int timeout;

    public Process(int port,
            HashMap<String, FileMgr> fileMgrMap,
			LinkedBlockingDeque<Socket> incomingConnections,
			int timeout,
            IOThread ioThread,
			ISharerGUI tgui) throws IOException {
		this.timeout = timeout;
		this.tgui = tgui;
		this.incomingConnections=incomingConnections;
		this.fileMgrMap = fileMgrMap;
		this.ioThread = ioThread;
	}

    @Override
	public void run() {
		tgui.logInfo("Waiting for download request");
		while (!ioThread.isInterrupted()) {
			try {
				Socket socket = incomingConnections.take();
				socket.setSoTimeout(this.timeout);
				processRequest(socket);
				socket.close();
			} catch (InterruptedException e) {
				tgui.logWarn("Client interrupted.");
				break;
			} catch (IOException e) {
				tgui.logWarn("Client received io exception on socket.");
			} catch (BlockUnavailableException e){
				tgui.logWarn("Block UnavailableException");
			}
		}
		tgui.logInfo("Client thread waiting for IO thread to stop...");
		ioThread.interrupt();
		try {
			ioThread.join();
		} catch (InterruptedException e) {
			tgui.logWarn("Interrupted while joining with IO thread.");
		}
		tgui.logInfo("Client thread completed.");
	}

    private void processRequest(Socket socket) throws IOException, BlockUnavailableException {
		String ip = socket.getInetAddress().getHostAddress();
		tgui.logInfo("Server processing request on connection " + ip);
		InputStream inputStream = socket.getInputStream();
		OutputStream outputStream = socket.getOutputStream();
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
		BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
		/*
		 * Follow the synchronous handshake protocol.
		 */
		while(true){
			Message msg;
			try {
				msg = readMsg(bufferedReader);
			} catch (JsonSerializationException e1) {
				writeMsg(bufferedWriter, new ErrorMsg("Invalid message"));
				return;
			}
	
			// check it is an BlockRequest request
			if (msg.getClass().getName() == BlockRequest.class.getName()) {
				BlockRequest blockRequest = (BlockRequest) msg;
				String filename = blockRequest.filename;
				String fileMd5 = blockRequest.fileMd5;
				Integer blockIdx = blockRequest.blockIdx;
				FileMgr  fileMgr= fileMgrMap.get(filename);
				byte[] b =  fileMgr.readBlock(blockIdx);
				String send = Base64.getEncoder().encodeToString(b);
				// Send back block bytes
				BlockReply blockReply = new BlockReply(filename, fileMd5, blockIdx, send);
				writeMsg(bufferedWriter, blockReply);
	
			} else if(msg.getClass().getName() == Goodbye.class.getName()){
				tgui.logInfo("Goodbye");
				break;
			}
			else {
				writeMsg(bufferedWriter, new ErrorMsg("Expecting BlockRequest"));
				break;
			}
		}

		// close the streams
		bufferedReader.close();
		bufferedWriter.close();
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
