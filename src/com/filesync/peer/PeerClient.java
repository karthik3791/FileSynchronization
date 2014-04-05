package com.filesync.peer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Map;
import java.util.Vector;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;

import com.filesync.protocol.Packet;
import com.filesync.shareddir.SharedFolderDetails;

public class PeerClient implements Runnable {


	static Logger logger = Logger.getLogger(PeerClient.class);

	public boolean keep_alive ;
	boolean status = true;
	private Socket socket;
	private FileOutputStream fos;
	private boolean file_write = false;
	private String file_being_pulled;
	private File dwfile;
	private String remote_ip;	
	private Map<String, PeerClient> active_download_connections;

	/*
	 * Logging using Apache log4j
	 */
	public void logMessage(String s) {		

		logger.info(s);		
	}

	public PeerClient(String remoteip, Map<String,PeerClient>map){
		remote_ip =remoteip;
		active_download_connections = map;
		keep_alive = true;

		//Initializing logging file.
		SimpleLayout layout = new SimpleLayout();    
		FileAppender appender;
		try {
			appender = new FileAppender(layout,SharedFolderDetails.logdir+File.separatorChar+"Thread_downloading_from_"+remote_ip+"_log",false);
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

		// Try to connect to the remote host.
		try {
			socket = new Socket(remote_ip,5000);
			//1. Set the timeout duration for the socket.	
			this.socket.setSoTimeout(4000);
		} catch (UnknownHostException e1) {
			logMessage("Could not make a download connection to "+remote_ip);
			logMessage("Got an Unknown Host Exception. Check if node is up and running.");
			active_download_connections.remove(remote_ip);		
			status = false;

		} catch (IOException e1) {
			logMessage("Could not make a download connection to "+remote_ip);
			logMessage("Got an IOException. Check if node is up and running.");
			active_download_connections.remove(remote_ip);
			status = false;
		}

		try{					


			while(status){
				
				if(keep_alive==false){
					logMessage("Force close connection of client by user. Closing connection.");
					close_connection();
					break;
				}

				//--PeerClient Ready-------------//
				logMessage("Download Thread ready");
				//2. Send Pull Request		
				send_over_socket("PULL",null);
				logMessage("Sent Pull Request");
				//3.Get List of Files in Remote node's shared folder.
				Packet file_list_pkt = read_from_socket();		
				if(file_list_pkt.getflag().equals("FILELIST")){

					String[] remote_file_list = file_list_pkt.getControlData();
					String[] local_file_list = getSyncDirFiles(syncdir);

					//4. Compare the local file list with the remote file list. Filter the files that needs to be pulled.
					String[] pull_list = getRequiredFiles(remote_file_list,local_file_list);

					//5. If pull list is null
					if(pull_list == null){
						//5.1 Inform Server nothing to pull as of this moment.
						send_over_socket("NOPULL",null);
						logMessage("NO PULL");
					}
					else{
						//5. Send the list of files which you need to pull from the node.
						send_over_socket("PULLLIST",pull_list);
						logMessage("Required Files by client ");
						for(String fname: pull_list){
							logMessage(fname);
						}						
						//6. Check if no need for manual closing of connection.
						if(keep_alive==true){
							//6.1 Send OK to server.
							send_over_socket("PULLREADY",null);

							//7 Read Server status 
							Packet server_status = read_from_socket();
							if(server_status.getflag().equals("PUSHREADY")){
								//Both server and client are ready to exchange file.

								//8. For each file in pull_list
								for(String pull_file: pull_list){

									//8.1 Send Pull filename
									send_over_socket("PULLFILE",new String[]{pull_file});				
									logMessage("PULL : "+pull_file);
									//8.2 Open the new file to write.
									String filepath = SharedFolderDetails.directorypath+File.separatorChar+pull_file;
									this.file_being_pulled = filepath;
									dwfile = new File(filepath);
									fos = new FileOutputStream(dwfile);
									file_write = true;
									while(true){
										// 8.3 Read File Packets. (Packet with flag as FILE	)
										Packet filepkt = read_from_socket();	
										if(filepkt.getflag().equals("FILE")){
											logMessage(pull_file+": Read a chunk");
											fos.write(filepkt.getFileData());											

										}else if(filepkt.getflag().equals("FILECOMPLETE")){
											file_write = false;
											fos.close();
											logMessage(pull_file+" has been downloaded successfully");
											break;
										}
										
										if(keep_alive==false){
											logMessage("Was asked to terminate connection immediately. Terminating connection.");
											close_connection();
										}

									}		
								}

								logMessage("Downloaded all files successfully");

							}
							else if (server_status.getflag().equals("QUIT")){
								//Server wants to close connection
								logMessage("Node's server thread quit before file sync. So closing connection.");
								close_connection();
							}
						}else{
							send_over_socket("QUIT",null);
							logMessage("Was asked to close connection. File sync hasnt begun yet.");
							// Client wants to close connection.
							close_connection();
						}
					}
				}
				else{
					// If it was not FileList
				}
				logMessage("Going to sleep for 1 minute");
				Thread.sleep(60000);
			}

		}catch (SocketTimeoutException e){
			logMessage("Timeout occured while reading from the socket.");
			logMessage("Plausible reason: Node is down.");
			logMessage("Closing the socket.");
			close_connection();			

		}catch(IOException ie){
			logMessage("Connection lost to the node. Closing the socket.");
			close_connection();						

		} catch (ClassNotFoundException ce) {
			logMessage("Class Not found Exception caught. Closing the connection.");
			close_connection();			


		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			logMessage("Interrupted Exception while sleeping. Closing the connection.");
			close_connection();

		}


	}

	//Function that will close the connection.
	public void close_connection(){
		// Remove the connection tracked in active_upload_connections and close the socket.

		active_download_connections.remove(remote_ip);		
		logMessage("Removed connection from hashmap tracking active download connections. Now closing socket...");

		if(file_write == true){
			// Chance that the file was being written halfway when connection was lost.
			try {
				fos.flush();
				fos.close();
				File tfile = new File(file_being_pulled);
				if(tfile.exists()){
					logMessage("File which was being pulled when connection was lost, is now being deleted. Status: "+tfile.delete());
				}
				logMessage("Closed FileOutputStream.");
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				logMessage("FileOutputStream was already closed");
			}catch(NullPointerException ne){
				logMessage("FileOutputStream was not instantiated.");
			}
		}
		try {
			this.socket.close();
			logMessage("Socket closed.");
			logMessage("Exiting thread");
			return;
		} catch (IOException e) {
			logMessage("Socket could not be closed.");
		}

	}





	//-- Compares local_file_list against remote_file_list and filters only the required files --//
	private String[] getRequiredFiles(String[] remote_file_list,
			String[] local_file_list) {		

		Vector<String> needed_files = new Vector<String>();

		for(String remotefile: remote_file_list){
			if(Arrays.asList(local_file_list).contains(remotefile) == false){
				needed_files.add(remotefile);
			}
		}

		if(needed_files.size()<1){
			return null;
		}

		String[] filtered_files = new String[needed_files.size()];

		for(int i=0;i<needed_files.size();i++){
			filtered_files[i] = needed_files.get(i);
		}

		return filtered_files;
	}



	// Function takes in directory and returns the list of files in that directory.
	private String[] getSyncDirFiles(File folder){

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

	private void send_over_socket(String flag, String[]control_data) throws IOException{
		Packet pkt = new Packet();
		pkt.setflag(flag);
		pkt.setControlData(control_data);
		ObjectOutputStream  oos = new 
				ObjectOutputStream(this.socket.getOutputStream());
		oos.writeObject(pkt);

	}

	private Packet read_from_socket() throws IOException, ClassNotFoundException{

		ObjectInputStream ois = 
				new ObjectInputStream(this.socket.getInputStream());

		Packet pkt = (Packet)ois.readObject();
		return pkt;

	}

}
