package com.greenowl.callisto.timer.service;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Properties;

import javax.sql.DataSource;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.ClassPathResource;

public class ConnectionFactory {

	
	private static ConnectionFactory factory = new ConnectionFactory();
	private static Log logger = LogFactory.getLog(ConnectionFactory.class);
	private HashMap<String,DataSource> sourceMap= new HashMap<String,DataSource>();
	private static final Object _lock = new Object();
	
	private ConnectionFactory(){}
	
	public static ConnectionFactory getInstance(){
		return factory;
	}
	
	public Connection getConnection(String databaseName){
		try{
			if(databaseName==null || databaseName.trim().length()==0){
				return null;
			}
			DataSource ds = sourceMap.get(databaseName);
			if(ds==null){
				synchronized(_lock){
					ds = getDataSource(databaseName);
					if(ds!=null){
						sourceMap.put(databaseName, ds);
					}					
				}				
			}
			return ds.getConnection();			
		}
		catch(Exception e){
			logger.error("", e);
			return null;
		}		
	}
	
	
	private DataSource getDataSource(String databaseName){
		try{
			ClassPathResource resource = new ClassPathResource("database.properties");
			Properties properties = new Properties() ;
			properties.load(resource.getInputStream());
			String dbInfo = properties.getProperty(databaseName);
			logger.info("get database: "+databaseName+":"+dbInfo);
			String[] dbInfos = dbInfo.split("[|]");
			if(dbInfos.length==3){
				BasicDataSource ds = new BasicDataSource();
				ds.setDriverClassName("com.mysql.jdbc.Driver");				
				ds.setUrl(dbInfos[0]);
				ds.setUsername(dbInfos[1]);
				ds.setPassword(dbInfos[2]);
				logger.info("get "+dbInfos[0]+dbInfos[1]+dbInfos[2]);
				ds.setMaxActive(500);
			    ds.setMaxOpenPreparedStatements(250);
			    ds.setMaxWait(100000);
			    ds.setMaxIdle(50);	
			    
			    ds.setTestOnBorrow(true); 
			    ds.setTestOnReturn(true); 
			    ds.setTestWhileIdle(true);	
			    ds.setValidationQuery("select 1");
				return ds;
			}			
			return null;
		}
		catch(Exception e){
			logger.error("", e);
			return null;
		}
	}
	
}
