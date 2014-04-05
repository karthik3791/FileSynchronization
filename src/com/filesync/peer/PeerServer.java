package com.filesync.peer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.Vector;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;

import com.filesync.protocol.Packet;
import com.filesync.shareddir.SharedFolderDetails;

public class PeerServer implements Runnable {
	static Logger logger = Logger.getLogger(PeerServer.class);
	public boolean keep_alive;
	private boolean file_read = false;
	private Socket socket;
	private String remote_ip;	
	private Map<String, PeerServer> active_upload_connections;
	private FileInputStream fis ;

	/*
	 * Logging using Apache log4j
	 */
	public void logMessage(String s) {		

		logger.info(s);		
	}


	public PeerServer(Socket s, Map<String, PeerServer> map){

		socket =s;
		remote_ip = s.getInetAddress().getHostAddress();
		active_upload_connections = map;
		keep_alive = true;

		//Initializing logging file.
		SimpleLayout layout = new SimpleLayout();    
		FileAppender appender;
		try {
			appender = new FileAppender(layout,SharedFolderDetails.logdir+File.separatorChar+"Thread_uploading_to_"+remote_ip+"_log",false);
			logger.addAppender(appender);
			logger.setLevel((Level) Level.DEBUG);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}    



	}


	@Override
	public void run() {


		File syncdir = new File(SharedFolderDetails.directorypath);

		while(true){
			// PeerServer ready.	
			if(keep_alive==false){
				logMessage("Force close of connection by user. Terminating connection.");
				close_connection();
				break;
			}
			try{	

				//1. Set the timeout duration for the socket. Maximum time wait for reading from socket is 4 seconds.	
				this.socket.setSoTimeout(4000);

				logMessage("Upload Thread ready");
				//2. Wait for Pull Request
				Packet pull_req_pkt = read_from_socket();

				//Check if it is pull request. It must be.
				if(pull_req_pkt.getflag().equals("PULL")){

					logMessage("Got PULL Request");
					//3. Send the list of files in the shared directory.
					String[] file_list = getSyncDirFiles(syncdir);  

					logMessage("Files in shared folder :");
					for(String fname: file_list){
						logMessage(fname);
					}
					send_over_socket("FILELIST",file_list);

					//5. Receive pull list from the client.
					Packet pull_list = read_from_socket();

					//5.1 If no file is needed
					if(pull_list.getflag().equals("NOPULL")){
						logMessage("NO PULL");
						//5.2 Means the downloading thread has all files in our local folder. Go to sleeping portion.
					}else if(pull_list.getflag().equals("PULLLIST")){
						//5. Get the list of files which the client needs to pull.
						String[] required_files = pull_list.getControlData();
						logMessage("Required Files by client ");
						for(String fname: required_files){
							logMessage(fname);
						}
						//6. Wait for pullready from client
						Packet client_status = read_from_socket();
						if(client_status.getflag().equals("PULLREADY")){
							//7. Check manual shutdown
							if(keep_alive==true){
								send_over_socket("PUSHREADY",null);
								// Both server and client are ready to exchange file.

								//8. For each file needed by the client.
								for(int i=0;i<required_files.length;i++){

									//8.1 Get the file needed currently
									Packet pullfilepkt = read_from_socket();
									String current_file = pullfilepkt.getControlData()[0];  

									logMessage("Uploading file: "+current_file);

									File uploadfile = getSyncDirfile(syncdir,current_file);


									if(uploadfile!=null){

										//8.2 Read portion of file and send it.
										fis = new FileInputStream(uploadfile);
										file_read= true;
										byte[] buffer = new byte[1024];
										int bytes =0;

										//8.3 Read a chunk of data from file and send it with flag as FILE
										while ((bytes = fis.read(buffer)) != -1) {                    					
											send_file_chunk(buffer);     
											logMessage(current_file+" a chunk sent");
											if(keep_alive == false){
												logMessage("Was asked to terminate connection immediately. Terminating connection.");
												close_connection();
											}
										}

										// If control comes here, means that the file has been sent fully.
										logMessage(current_file+" uploaded successfully");
										file_read = false;
										send_over_socket("FILECOMPLETE",null);
										fis.close();
									}

									else{
										//File could not be found.
									}



								}

								logMessage("All files uploaded successfully");

							}
							else{
								send_over_socket("QUIT",null);
								//Server wants to close connection.
								logMessage("Asked to terminate connection. Will terminate now.");
								close_connection();
							}                    		
						}
						else if (client_status.getflag().equals("QUIT")){
							//Client wants to close the connection.
							logMessage("Client server of node wanted to close connection even before file sync. Closing connection.");
							close_connection();
						}

					}
					else{
						//if it was not PULLLIST
					}

				}
				else{
					// if is was not PULL
				}
				logMessage("Going to sleep for one minute");
				Thread.sleep(60000);
				
			}catch (SocketTimeoutException e){
				logMessage("Timeout occured while reading from the socket.");
				logMessage("Plausible reason: Node is down.");
				logMessage("Closing the socket.");
				close_connection();
				break;

			}catch(IOException ie){
				logMessage("Connection lost to the node. Closing the socket.");
				close_connection();			
				break;

			} catch (ClassNotFoundException ce) {
				logMessage("Class Not found Exception caught. Closing the connection.");
				close_connection();			
				break;

			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				logMessage("Interrupted Exception while sleeping. Closing the connection.");
				close_connection();			
				break;

			}
		}


	}


	//Function that will close the connection.
	public void close_connection(){
		// Remove the connection tracked in active_upload_connections and close the socket.

		active_upload_connections.remove(remote_ip);

		if(file_read==true){
			try {
				fis.close();
				logMessage("FileInputStream has been closed.");
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				logMessage("FileInputStream was already closed");
			}catch(NullPointerException ne){
				logMessage("FileInputStream was not instantiated.");
			}
		}

		logMessage("Removed connection from hashmap tracking active upload connections. Now closing socket...");
		try {
			this.socket.close();
			logMessage("Socket closed.");
			logMessage("Exiting thread");
			return;
		} catch (IOException e) {
			logMessage("Socket could not be closed.");
		}

	}


	// Function takes in directory and returns the list of files in that directory.
	public String[] getSyncDirFiles(File folder){

		File[] listOfFiles = folder.listFiles(); 
		Vector<String> files = new Vector<String>() ;
		String [] listedfiles;
		for (int i = 0; i < listOfFiles.length; i++){

			if (listOfFiles[i].isFile()){
				files.add(listOfFiles[i].getName());
			}
		}

		listedfiles = new String[files.size()];
		for(int i=0;i<files.size();i++){
			listedfiles[i]=files.get(i);
		}


		return listedfiles;
	}


	public File getSyncDirfile(File folder,String filename){

		File[] listOfFiles = folder.listFiles();
		boolean found = false;
		File returnfile = null;

		for (int i = 0; i < listOfFiles.length; i++){

			if (listOfFiles[i].isFile()&&listOfFiles[i].getName().equals(filename)){
				found=true;
				returnfile = listOfFiles[i];
			}
		}

		return returnfile;

	}




	public void send_over_socket(String flag, String[]control_data) throws IOException{
		Packet pkt = new Packet();
		pkt.setflag(flag);
		pkt.setControlData(control_data);
		ObjectOutputStream  oos = new 
				ObjectOutputStream(this.socket.getOutputStream());
		oos.writeObject(pkt);

	}

	public void send_file_chunk(byte[] file_chunk)throws IOException{
		Packet pkt = new Packet();
		pkt.setflag("FILE");
		pkt.setFileData(file_chunk);
		ObjectOutputStream  oos = new 
				ObjectOutputStream(this.socket.getOutputStream());
		oos.writeObject(pkt);
	}


	public Packet read_from_socket() throws IOException, ClassNotFoundException{

		ObjectInputStream ois = 
				new ObjectInputStream(this.socket.getInputStream());

		Packet pkt = (Packet)ois.readObject();
		return pkt;

	}

}
