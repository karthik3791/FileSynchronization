package com.filesync.connection;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.filesync.peer.PeerClient;
import com.filesync.peer.PeerServer;

public class UploadManager {
	
	// Thread-safe map of all active upload connections.
	private Map<String,PeerServer> active_upload_connections ;
	
	
	public UploadManager(){
		
		active_upload_connections = Collections.synchronizedMap(new HashMap<String, PeerServer>());
	}
	
	
	//1. Function adds the list of active upload connections to the HashMap
    public void add_upload_connection (String remote_ip, PeerServer pserver){
    	
    	active_upload_connections.put(remote_ip, pserver);
    	
    }
    
    // Function will close existing downloading connection with a peer
    public void close_upload_connection (String remote_ip){		
    PeerServer pserver = active_upload_connections.get(remote_ip);
	pserver.keep_alive = false;		

    	
    }
    
    // Function will close all the downloading connections
    public void close_all_upload_connections(){
    	
    }
    
    public Map<String,PeerServer> getActiveUploadConnections(){
    	
    	return active_upload_connections;
    	
    }
    
	public boolean checkUploadConn (String remote_ip){
		
		if (active_upload_connections.containsKey(remote_ip))
			return true;
		else
			return false;
	}

    

}
