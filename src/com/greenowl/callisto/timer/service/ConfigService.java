package com.greenowl.callisto.timer.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.greenowl.callisto.timer.Constants;

public class ConfigService {

	private static ConfigService cs = new ConfigService();
	private HashMap<String,String> map = new HashMap<String,String>(); 
	private Log logger = LogFactory.getLog(this.getClass());
	
	private ConfigService(){
		init();
	}
	
	public static ConfigService getInstance(){
		return cs;
	}
	
	private void init(){
		Connection con = null;
		try{
			con = ConnectionFactory.getInstance().getConnection(Constants.GISH_JOB_DB);
			PreparedStatement pstmt = con.prepareStatement("select key_name,value from T_APPLICATION_CONFIG");
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()){
				map.put(rs.getString(1),rs.getString(2));
			}
			rs.close();
			pstmt.close();
		}
		catch(Exception e){
			logger.error(e.getMessage(), e);
		}
		finally{
			if(con!=null){
				try{
					con.close();
				}
				catch(Exception e){
					logger.error(e.getMessage(), e);
				}
			}
		}
	}
	
	
	public String getConfig(String key){
		return map.get(key);
	}	
	
}
