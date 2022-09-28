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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;

import comp90015.idxsrv.filemgr.FileMgr;
import comp90015.idxsrv.message.BlockReply;
import comp90015.idxsrv.message.BlockRequest;
import comp90015.idxsrv.message.Goodbye;
import comp90015.idxsrv.message.JsonSerializationException;
import comp90015.idxsrv.message.Message;
import comp90015.idxsrv.message.MessageFactory;
import comp90015.idxsrv.textgui.ISharerGUI;

public class BlockHandler extends Thread{
    private InetAddress peerAddress;
    private int port;
    private ArrayList<Integer> blockNumberList;
    private FileMgr fileMgr;
    private ISharerGUI tgui;
    private String fileName;
    private String fileMd5;


    public BlockHandler(InetAddress peerAddress,
            ISharerGUI tgui,
            int port, 
            FileMgr fileMgr,
            String fileName,
            String fileMd5,
            ArrayList<Integer> blockNumberList) throws IOException {
        this.tgui = tgui;
        this.blockNumberList = blockNumberList;
        this.port = port;
        this.fileMgr = fileMgr;
        this.peerAddress = peerAddress;
        this.fileName = fileName;
        this.fileMd5 = fileMd5;
    }

    @Override
    public void run() {
        Socket socket = null;
        InputStream inputStream = null;
        OutputStream outputStream = null;
        BufferedReader bufferedReader = null;
        BufferedWriter bufferedWriter = null;
        tgui.logDebug("download thread started");
        try{
            socket = new Socket(peerAddress, port);
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        }catch(IOException e){
            tgui.logError("peer:"+peerAddress.getHostAddress()+"not available");
            try {
                socket.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return;
        }
        
        Message msg = null;
        Path path = Paths.get(fileName);

        for(Integer blockIdx : blockNumberList){
            try{
                tgui.logInfo(String.valueOf(path));
                BlockRequest blockRequest = new BlockRequest(fileName, fileMd5, blockIdx);
                writeMsg(bufferedWriter, blockRequest);
                msg = readMsg(bufferedReader);
				tgui.logInfo("Byte data downloaded");
                boolean b = false;
				if(msg.getClass().getName() == BlockReply.class.getName()){
					BlockReply blockReply = (BlockReply) msg;
					byte[] bytes =  Base64.getDecoder().decode(blockReply.bytes);
                    while (Peer.flag != false) {
                        Thread.sleep(10);
                    }
                    Peer.flag = true;
                    b = fileMgr.writeBlock(blockIdx, bytes);
                    Peer.flag = false;
				}
            }catch(IOException e){
                tgui.logInfo("Download fail, please retry, IO exception");
            }catch(JsonSerializationException e){
                tgui.logInfo("Download fail, please retry, Json Exception");
            } catch (InterruptedException e) {
                tgui.logInfo("Interrupt error");
            } 

            boolean fileComplete = fileMgr.isComplete();
            if(fileComplete){
                try {
                    tgui.logDebug("close file");
                    fileMgr.closeFile();
                } catch (IOException e) {
                    tgui.logError("fail to close file");
                    e.printStackTrace();
                }
            }
        }

        try {
            writeMsg(bufferedWriter, new Goodbye());
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        try {
            socket.close();
        } catch (IOException e) {
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
