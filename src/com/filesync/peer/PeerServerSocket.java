package com.filesync.peer;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;

import com.filesync.connection.DownloadManager;
import com.filesync.connection.UploadManager;
import com.filesync.shareddir.SharedFolderDetails;

public class PeerServerSocket implements Runnable {	

	static Logger logger = Logger.getLogger(PeerServerSocket.class);

	/*
	 * Logging using Apache log4j
	 */
	public void logMessage(String s) {		
		
		logger.info(s);		
	}


	private int portno = 7000;

	private UploadManager upmanager;
	private DownloadManager downmanager;

	// Constructor to mention port number 
	public PeerServerSocket(UploadManager up, DownloadManager down){		
		upmanager = up;
		downmanager = down;
		
		//Initializing logging file.
		SimpleLayout layout = new SimpleLayout();    
		FileAppender appender;
		try {
			appender = new FileAppender(layout,SharedFolderDetails.logdir+File.separatorChar+"Listening_Socket_Thread_log",false);
			logger.addAppender(appender);
			logger.setLevel((Level) Level.DEBUG);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}    
		
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		ServerSocket listen_socket;

		try {

			//1. Initialize the socket and bind it to port number.
			listen_socket= new ServerSocket(portno);

			//2. Keep listening to the port to accept new upload request connections from nodes in overlay network
			while (true) {
				//3. Accept connection 
				Socket connectionSocket = listen_socket.accept();

				//---4.Get IP address of remote peer and inform upload manager.---------------------------// 
				String remoteip = connectionSocket.getInetAddress().getHostAddress();
				logMessage("Got a connection from "+remoteip);
				PeerServer peerserver = new PeerServer(connectionSocket, upmanager.getActiveUploadConnections()); 				
				upmanager.add_upload_connection(remoteip, peerserver);
				//----------------------------------------------------------------------------------------//


				//---5. Check if download connection already exists with this IP address.------------------//
				if(downmanager.checkDownloadConn(remoteip) == false){
					//6. If not, means this is a fresh incoming connection. So need to instantiate a PeerClient Connection.
					PeerClient peerclient = new PeerClient(remoteip,downmanager.getActiveDownloadConnections());
					downmanager.add_download_connection(remoteip, peerclient); 
					Thread c = new Thread(peerclient);
					c.setName("Client thread to "+remoteip);
					c.start();
					logMessage("Created an outgoing connection to "+remoteip);

					//Thread.sleep(300);
				}
				//------------------------------------------------------------------------------------------//

				//---6. Start the PeerServer object in a new thread-----------------------------------------// 				
				Thread s = new Thread(peerserver);
				s.setName("Server Thread for "+remoteip);
				s.start();
				logMessage("Started server thread for the request");
				//------------------------------------------------------------------------------------------//

			}

		} catch (IOException e) {
			logMessage("IO Exception in PeerServerSocket.java");
			logMessage(e.getMessage());
			System.exit(-1);

		} //catch (InterruptedException ie){
		//	logMessage("Sleep call was interrupted in PeerServerSocket.java");
		//}

	}

}
