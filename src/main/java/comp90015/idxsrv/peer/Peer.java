package comp90015.idxsrv.peer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;

import comp90015.idxsrv.filemgr.FileDescr;
import comp90015.idxsrv.filemgr.FileMgr;
import comp90015.idxsrv.message.AuthenticateReply;
import comp90015.idxsrv.message.SearchReply;
import comp90015.idxsrv.message.SearchRequest;
import comp90015.idxsrv.message.ShareReply;
import comp90015.idxsrv.message.ShareRequest;
import comp90015.idxsrv.message.AuthenticateRequest;
import comp90015.idxsrv.message.DropShareReply;
import comp90015.idxsrv.message.DropShareRequest;
import comp90015.idxsrv.server.IOThread;
import comp90015.idxsrv.server.IndexElement;
import comp90015.idxsrv.textgui.ISharerGUI;
import comp90015.idxsrv.message.Message;
import comp90015.idxsrv.message.MessageFactory;
import comp90015.idxsrv.message.JsonSerializationException;
import comp90015.idxsrv.message.LookupReply;
import comp90015.idxsrv.message.LookupRequest;

/**
 * Skeleton Peer class to be completed for Project 1.
 * 
 * @author aaron
 *
 */
public class Peer extends Thread implements IPeer{

	private IOThread ioThread;

	private Process process;

	private LinkedBlockingDeque<Socket> incomingConnections;

	private ISharerGUI tgui;

	private String basedir;

	private int timeout;

	private int port;

	private HashMap<String, FileMgr> fileMgrMap;

	public static boolean flag = false;

	public Peer(int port, String basedir, int socketTimeout, ISharerGUI tgui) throws IOException {
		this.tgui = tgui;
		this.port = port;
		this.timeout = socketTimeout;
		this.basedir = new File(basedir).getCanonicalPath();
		fileMgrMap = new HashMap<>();
		incomingConnections=new LinkedBlockingDeque<Socket>();
		ioThread = new IOThread(port, incomingConnections, socketTimeout, tgui);
		ioThread.start();
		process = new Process(port,fileMgrMap,incomingConnections,timeout,ioThread,tgui);
		process.start();
	}

	public void shutdown() throws InterruptedException, IOException {
		ioThread.shutdown();
		ioThread.interrupt();
		ioThread.join();
		process.interrupt();
		process.join();
	}
	/*
	 * Students are to implement the interface below.
	 */
	@Override
	public void shareFileWithIdxServer(File file, InetAddress idxAddress, int idxPort, String idxSecret,
			String shareSecret) {
		// Create Auth Request
		AuthenticateRequest auth = new AuthenticateRequest(idxSecret);

		// Open socket
		Socket socket = null;
		InputStream inputStream = null;
		OutputStream outputStream = null;
		BufferedReader bufferedReader = null;
		BufferedWriter bufferedWriter = null;

		try {
			socket = new Socket(idxAddress, idxPort);
			inputStream = socket.getInputStream();
			outputStream = socket.getOutputStream();
			bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

			// Send Auth request
			writeMsg(bufferedWriter, auth);
		} catch (IOException e) {
			tgui.logError(e.getMessage());
			return;
		}

		Message msg;
		try {
			// Create FileMgr
			FileMgr fileMgr = new FileMgr(file.getCanonicalPath());
			tgui.logDebug("block length=" + String.valueOf(fileMgr.getFileDescr().getNumBlocks()));
			// get hello from server
			msg = readMsg(bufferedReader);
			// read auth reply from server
			msg = readMsg(bufferedReader);
			AuthenticateReply ar = (AuthenticateReply) msg;
			if (ar.success) {
				// create share request
				ShareRequest shareRequest = new ShareRequest(fileMgr.getFileDescr(), file.getName(), shareSecret, port);
				writeMsg(bufferedWriter, shareRequest);

				// Get server reply
				msg = readMsg(bufferedReader);
				if (msg.getClass().getName() == ShareReply.class.getName()) {
					ShareReply shareReply = (ShareReply) msg;
					int numSharers = shareReply.numSharers;
					// Create shareRecord
					String status = "sharing";
					InetAddress idxSrvAddress = socket.getInetAddress();
					ShareRecord shareRecord = new ShareRecord(fileMgr, numSharers, status, idxSrvAddress, idxPort,
							idxSecret, shareSecret);
					tgui.addShareRecord(file.getPath(), shareRecord);

					// Add to share map that contains all file where we ready to share
					fileMgrMap.put(file.getName(), fileMgr);
				} else {
					tgui.logError("Failed sharing secret");
				}
			} else {
				tgui.logError("Invalid Auth");
			}

		} catch (JsonSerializationException e1) {
			tgui.logError(e1.getMessage());
		} catch (IOException e) {
			tgui.logError(e.getMessage());
		} catch (NoSuchAlgorithmException n) {
			tgui.logError("no MD5 algo");
		}

		// close socket
		try {
			// close the streams
			bufferedReader.close();
			bufferedWriter.close();
			socket.close();
		} catch (IOException e) {
			tgui.logError(e.getMessage());
			return;
		}

	}
	@Override
	public void searchIdxServer(String[] keywords,
			int maxhits,
			InetAddress idxAddress,
			int idxPort,
			String idxSecret) {
		// Create Auth Request
		AuthenticateRequest auth = new AuthenticateRequest(idxSecret);

		// Open socket
		Socket socket = null;
		InputStream inputStream = null;
		OutputStream outputStream = null;
		BufferedReader bufferedReader = null;
		BufferedWriter bufferedWriter = null;

		try {
			socket = new Socket(idxAddress, idxPort);
			inputStream = socket.getInputStream();
			outputStream = socket.getOutputStream();
			bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

			// Send Auth request
			writeMsg(bufferedWriter, auth);

		} catch (IOException e) {
			tgui.logError(e.getMessage());
			return;
		}

		// get a message
		Message msg;
		try {
			// get hello from server
			msg = readMsg(bufferedReader);

			// read auth reply from server
			msg = readMsg(bufferedReader);
			AuthenticateReply ar = (AuthenticateReply) msg;
			if (ar.success) {
				// Search server with keywords
				SearchRequest searchRequest = new SearchRequest(maxhits, keywords);
				writeMsg(bufferedWriter, searchRequest);

				// Get search reply
				msg = readMsg(bufferedReader);
				if (msg.getClass().getName() == SearchReply.class.getName()) {
					SearchReply reply = (SearchReply) msg;
					IndexElement[] hits = reply.hits;
					Integer[] seedCounts = reply.seedCounts;
					// add reply to gui search table
					for (int i = 0; i < hits.length; i++) {
						IndexElement hit = hits[i];
						FileDescr fileDescr = hit.fileDescr;
						Long numSharers = seedCounts[i].longValue();
						InetAddress idxSrvAddress = idxAddress;
						int idxSrvPort = idxPort;
						String sharerSecret = hits[i].secret;
						String idxSrvSecret = idxSecret;
						SearchRecord shareRecord = new SearchRecord(fileDescr, numSharers, idxSrvAddress, idxSrvPort,
								idxSrvSecret, sharerSecret);
						tgui.addSearchHit(hits[i].filename, shareRecord);
					}
				} else {
					tgui.logError("Invalid Search Reply");
				}

			} else {
				tgui.logError("Invalid Auth");
			}

		} catch (JsonSerializationException e1) {
			tgui.logError(e1.getMessage());
		} catch (IOException e) {
			tgui.logError(e.getMessage());
		}

		// close socket
		try {
			// close the streams
			bufferedReader.close();
			bufferedWriter.close();
			socket.close();
		} catch (IOException e) {
			tgui.logError(e.getMessage());
			return;
		}

	}
	@Override
	public boolean dropShareWithIdxServer(String relativePathname, ShareRecord shareRecord) {
		// Create Auth Request
		AuthenticateRequest auth = new AuthenticateRequest(shareRecord.idxSrvSecret);

		// Open socket
		Socket socket = null;
		InputStream inputStream = null;
		OutputStream outputStream = null;
		BufferedReader bufferedReader = null;
		BufferedWriter bufferedWriter = null;

		try {
			socket = new Socket(shareRecord.idxSrvAddress, shareRecord.idxSrvPort);
			inputStream = socket.getInputStream();
			outputStream = socket.getOutputStream();
			bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

			// Send Auth request
			writeMsg(bufferedWriter, auth);
		} catch (IOException e) {
			tgui.logError(e.getMessage());
			return false;
		}

		Message msg;
		try {
			// get hello from server
			msg = readMsg(bufferedReader);
			// read auth reply from server
			msg = readMsg(bufferedReader);
			AuthenticateReply ar = (AuthenticateReply) msg;
			if (ar.success) {
				// send DropShareRequest
				tgui.logDebug("relative Path ="+relativePathname);
				tgui.logDebug("filename = "+getFileNameByPath(relativePathname));
				String fileName = getFileNameByPath(relativePathname);
				String fileMd5 = shareRecord.fileMgr.getFileDescr().getFileMd5();
				String sharingSecret = shareRecord.sharerSecret;
				DropShareRequest dropShareRequest = new DropShareRequest(fileName, fileMd5, sharingSecret, port);
				writeMsg(bufferedWriter, dropShareRequest);

				// handle DropShareReply
				msg = readMsg(bufferedReader);
				if (msg.getClass().getName() == DropShareReply.class.getName()) {
					DropShareReply dropShareReply = (DropShareReply) msg;
					if (!dropShareReply.success) {
						tgui.logError("drop share request fail to drop");
					}
				} else {
					tgui.logError("Invalid DropShareRequest");
				}
			} else {
				tgui.logError("Invalid Auth");
			}
		} catch (JsonSerializationException e1) {
			tgui.logError(e1.getMessage());
		} catch (IOException e) {
			tgui.logError(e.getMessage());
		}

		// close socket
		try {
			// close the streams
			bufferedReader.close();
			bufferedWriter.close();
			socket.close();
		} catch (IOException e) {
			tgui.logError(e.getMessage());
			return false;
		}
		tgui.logInfo("Drop success");
		fileMgrMap.remove(getFileNameByPath(relativePathname));
		return true;
	}

	@Override
	public void downloadFromPeers(String relativePathname, SearchRecord searchRecord){
		// Save Lookup hits
		IndexElement[] hits = null;
		// Create Auth Request
		AuthenticateRequest auth = new AuthenticateRequest(searchRecord.idxSrvSecret);

		// Open socket
		Socket socket = null;
		InputStream inputStream = null;
		OutputStream outputStream = null;
		BufferedReader bufferedReader = null;
		BufferedWriter bufferedWriter = null;

		try {
			socket = new Socket(searchRecord.idxSrvAddress, searchRecord.idxSrvPort);
			inputStream = socket.getInputStream();
			outputStream = socket.getOutputStream();
			bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

			// Send Auth request
			writeMsg(bufferedWriter, auth);

		} catch (IOException e) {
			tgui.logError(e.getMessage());
			return;
		}

		Message msg;
		try {
			// get hello from server
			msg = readMsg(bufferedReader);
			// read auth reply from server
			msg = readMsg(bufferedReader);
			AuthenticateReply ar = (AuthenticateReply) msg;
			if (ar.success) {
				// send Lookup request
				String filename = relativePathname;
				String fileMd5 = searchRecord.fileDescr.getFileMd5();
				LookupRequest lookupRequest = new LookupRequest(filename, fileMd5);
				writeMsg(bufferedWriter, lookupRequest);

				// Get lookup reply
				msg = readMsg(bufferedReader);
				if(msg.getClass().getName() == LookupReply.class.getName()){
					LookupReply lookupReply = (LookupReply) msg;
					hits = lookupReply.hits;
				}else{
					tgui.logError("Invalid Lookup Reply from idxserver");
				}
			} else {
				tgui.logError("Invalid Auth");
			}
		} catch (JsonSerializationException e1) {
			tgui.logError(e1.getMessage());
		} catch (IOException e) {
			tgui.logError(e.getMessage());
		}
		// close socket
		try {
			// close the streams
			bufferedReader.close();
			bufferedWriter.close();
			socket.close();
		} catch (IOException e) {
			tgui.logError(e.getMessage());
		}

		if (hits.length == 0){
			tgui.logInfo("Peer droped file, cannot download");
			return;
		}

		// Download from peers
		// Open socket
		
		String fileName = relativePathname;
		String fileMd5 = searchRecord.fileDescr.getFileMd5();
		FileDescr fileDescr = hits[0].fileDescr;
		FileMgr fileMgr = null;
		// Create empty file Manager
		try {
			fileMgr = new FileMgr(fileName, fileDescr);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		int numBlocks = fileDescr.getNumBlocks();
		int totalNumPeers = hits.length;

		// Distribute download tasks to Thread
		int i=0;
		int averageBlock = numBlocks / totalNumPeers + 1;
		for(IndexElement hit:hits){
			try {
				if(averageBlock <= numBlocks){
					InetAddress peerAddress = InetAddress.getByName(hit.ip);
					ArrayList<Integer> blockIdxList = createBlockIdxList(i, averageBlock);
					BlockHandler blockHandler = null;
					try {
						blockHandler = new BlockHandler(peerAddress, tgui,hit.port, fileMgr, fileName, fileMd5, blockIdxList);
					} catch (IOException e) {
						e.printStackTrace();
						tgui.logError("BlockHandler create error");
						return;
					}finally{
						blockHandler.start();
					}
					i = i+averageBlock;
					averageBlock += averageBlock;
				}else{
					averageBlock -= 1;
					InetAddress peerAddress = InetAddress.getByName(hit.ip);
					ArrayList<Integer> blockIdxList = createBlockIdxList(i, averageBlock);
					BlockHandler blockHandler = null;
					try {
						blockHandler = new BlockHandler(peerAddress, tgui,hit.port, fileMgr,fileName, fileMd5, blockIdxList);
					} catch (IOException e) {
						e.printStackTrace();
						tgui.logError("BlockHandler create error");
						return;
					}finally{
						blockHandler.start();
					}
				}
			} catch (UnknownHostException e) {
				e.printStackTrace();
				tgui.logError("Fail to start download threads");
			}
		}
	}

	private ArrayList<Integer> createBlockIdxList(Integer lower, Integer upper){
		ArrayList<Integer> list = new ArrayList<Integer>();
		for(int i=lower; i<upper; i++){
			list.add(i);
		}
		return list;
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

	private String getFileNameByPath(String path) {
		int i = path.length() - 1;
		for (; i > 0; i--) {
			if (path.charAt(i) == '/' || path.charAt(i) == '\\')
				break;
		}
		return path.substring(i + 1);
	}

}
