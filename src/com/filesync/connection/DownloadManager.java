package com.filesync.connection;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.filesync.peer.PeerClient;
import com.filesync.peer.PeerServer;

public class DownloadManager {

	// Thread-safe map of all active download connections.
	private Map<String,PeerClient> active_download_connections ;


	public DownloadManager(){

		active_download_connections = Collections.synchronizedMap(new HashMap<String, PeerClient>());
	}

	// Function will note that a new download connection has been formed
	public void add_download_connection (String remote_ip, PeerClient pclient){
		
    	active_download_connections.put(remote_ip, pclient);

	}

	// Function will close existing downloading connection with a peer
	public void close_download_connection (String remote_ip){		
		//Obtain the PeerClient object which is doing the downloading task from remote_ip
		PeerClient pclient = active_download_connections.get(remote_ip);
		pclient.keep_alive = false;		

	}

	// Function will close all the downloading connections
	public void close_all_download_connections(){

	}

	public Map<String,PeerClient> getActiveDownloadConnections(){

		return active_download_connections;

	}

	public boolean checkDownloadConn (String remote_ip){
		
		if (active_download_connections.containsKey(remote_ip))
			return true;
		else
			return false;
	}

}
