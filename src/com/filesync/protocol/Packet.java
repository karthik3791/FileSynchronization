package com.filesync.protocol;

import java.io.Serializable;

public class Packet implements Serializable{

	private static final long serialVersionUID = 620709189205664094L;

	private String flag;
	private String[] control_data;
	private byte[] file_data;

	public Packet(){

	}
	public void setflag(String f){
		flag =f;
	}

	public String getflag(){
		return flag;
	}

	public void setControlData(String[] s){

		control_data =s;

	}

	public String[] getControlData(){
		return control_data;
	}


	public void setFileData(byte[] file_chunk){
		file_data = file_chunk;		
	}

	public byte[] getFileData(){
		return file_data;
	}

}
