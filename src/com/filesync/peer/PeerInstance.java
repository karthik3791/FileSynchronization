package com.filesync.peer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

import com.filesync.connection.DownloadManager;
import com.filesync.connection.UploadManager;
import com.filesync.shareddir.SharedFolderDetails;

public class PeerInstance {

	public static void safePrintln(String s) {
		synchronized (System.out) {
			System.out.println("Main Thread: " + s);
		}
	}

	/**
	 * @param args
	 * @throws IOException
	 * @throws NumberFormatException
	 */
	public static void main(String[] args) throws NumberFormatException,
			IOException {

		// Making some changes
		safePrintln("Initializing Peer Node instance....");

		// 1. Initialize instance of Download Connections Manager and Upload
		// Connections Manager
		DownloadManager dm = new DownloadManager();
		UploadManager um = new UploadManager();

		// 2. Get the listening thread running in well known port.
		PeerServerSocket ps = new PeerServerSocket(um, dm);
		Thread listening_thread = new Thread(ps);
		listening_thread.setName("Peer Listening Socket Thread");
		listening_thread.start();

		safePrintln("Listening socket thread is up and running!");

		// 2.1 If the synchronizing folder does not exist, then create it.
		File syncdir = new File(SharedFolderDetails.directorypath);
		if (!syncdir.exists()) {
			boolean result = syncdir.mkdir();
			if (result) {
				safePrintln("Synchronizing Directory : "
						+ SharedFolderDetails.directorypath + " created.");
			}
		}

		// 2.2 If the logging folder does not exist, then create it.
		File logdir = new File(SharedFolderDetails.logdir);
		if (!logdir.exists()) {
			boolean result = logdir.mkdir();
			if (result) {
				safePrintln("Log Directory : " + SharedFolderDetails.logdir
						+ " created.");
			}
		}

		// 3. Main Menu display
		int choice;
		do {

			safePrintln("********** NODE CONTROL *************");
			safePrintln("1. Connect to a remote node ");
			safePrintln("2. View all active connections");
			safePrintln("3. Close an existing connection");
			safePrintln("*************************************");
			safePrintln("Please enter your option (1-3): ");

			// 4. Get user input
			BufferedReader br = new BufferedReader(new InputStreamReader(
					System.in));
			choice = Integer.parseInt(br.readLine());

			if (choice < 1 || choice > 3) {
				safePrintln("Please enter valid input between 1- 3");
				continue;
			}

			// 5. Process the choice which was chosen by the user.
			if (choice == 1) {
				// 5.1 Connect to a node option.

				InetAddress ip_address;
				String remoteip;

				do {
					safePrintln("********* Connect to a node ************");
					safePrintln("Enter the IP Address of the node in string format separated by '.' For eg: 192.168.5.3");

					try {
						remoteip = br.readLine();
						ip_address = InetAddress.getByName(remoteip);
						safePrintln("Checking if a node with that IP address is alive in the network ");
						if (ip_address.isReachable(2000)) {
							safePrintln("Node found!");
							// Check if either an outgoing or incoming
							// connection already exists with this ip address.
							if (dm.checkDownloadConn(remoteip) == true
									|| um.checkUploadConn(remoteip) == true) {
								safePrintln("Existing connection already exists with that node. Please enter a different node.");
								continue;
							}
							break;
						} else {
							safePrintln("Could not confirm presence of Host in network. Please ensure you have entered correct IP address of remote host.");
						}
					} catch (UnknownHostException e) {
						safePrintln("Please enter valid input for IP. Caught unknownhostexception.");
					} catch (IOException ie) {
						safePrintln("Caught IO Exception while trying to reach the node.");
					}

				} while (true);

				connectToNode(remoteip, dm);
			} else if (choice == 2) {

				print_all_download_connections(dm);
				print_all_upload_connections(um);

			} else if (choice == 3) {

				boolean conn_check = false;
				do {

					safePrintln("********* Close an existing connection ************");
					safePrintln("Enter the IP Address of the remote node whose connection needs to be closed in string format separated by '.' For eg: 192.168.5.3");
					String remoteip;
					remoteip = br.readLine();
					safePrintln("Checking if a download/upload connection exists with this node at "
							+ remoteip);

					if (dm.checkDownloadConn(remoteip) == false
							&& um.checkUploadConn(remoteip) == false) {

						safePrintln("\n No download/upload connection exists currently with this node. Please enter a different Node.");
						continue;
					} else {
						if (dm.checkDownloadConn(remoteip) == true) {

							safePrintln("\n A download connection exists with the node. Closing the download connection now.");
							dm.close_download_connection(remoteip);
							conn_check = true;
						}
						if (um.checkUploadConn(remoteip) == true) {
							safePrintln("\n An upload connection exists with the node. Closing the uploading connection now.");
							um.close_upload_connection(remoteip);
							conn_check = true;
						}
					}
				} while (conn_check == false);
			}
			System.out.println("\n\n\n\n\n");
		} while (true);

	}

	// Function to print all active Download Connections.
	public static void print_all_download_connections(DownloadManager dm) {

		Map<String, PeerClient> active_download_connections = dm
				.getActiveDownloadConnections();

		safePrintln("********** DOWNLOAD CONNECTIONS    *****************");
		int no_active_connections = active_download_connections.keySet().size();
		safePrintln("Totally " + no_active_connections
				+ " download connections are active currently.");
		if (no_active_connections > 0) {
			for (String remoteip : active_download_connections.keySet()) {
				safePrintln("Connection downloading from " + remoteip);
			}
		}
		safePrintln("*****************************************************");

	}

	// Function to print all active Upload Connections.
	public static void print_all_upload_connections(UploadManager um) {

		Map<String, PeerServer> active_upload_connections = um
				.getActiveUploadConnections();

		safePrintln("********** UPLOAD CONNECTIONS    *****************");
		int no_active_connections = active_upload_connections.keySet().size();
		safePrintln("Totally " + no_active_connections
				+ " upload connections are active currently.");
		if (no_active_connections > 0) {
			for (String remoteip : active_upload_connections.keySet()) {
				safePrintln("Connection uploading to " + remoteip);
			}
		}
		safePrintln("*****************************************************");

	}

	// Function to make a connection to a new node.
	public static void connectToNode(String remoteip, DownloadManager dm) {

		safePrintln("Connecting to the node at " + remoteip);
		PeerClient peerclient = new PeerClient(remoteip,
				dm.getActiveDownloadConnections());
		dm.add_download_connection(remoteip, peerclient);
		Thread c = new Thread(peerclient);
		c.setName("Client Thread to " + remoteip);
		c.start();

	}

}
