package com.greenowl.callisto.timer.job;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

import com.greenowl.callisto.timer.Constants;
import com.greenowl.callisto.timer.service.ConfigService;
import com.greenowl.callisto.timer.service.ConnectionFactory;
import com.greenowl.callisto.timer.service.TicketCheckTask;

public class ValidateParkingTicketStatusJob extends QuartzJobBean{

private Log logger = LogFactory.getLog(this.getClass());
	
	protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
	      Connection con = null;
	      PreparedStatement pstmt = null;
		  try{
			  List<Object[]> ticketList = new ArrayList<Object[]>();			  
	    	  con = ConnectionFactory.getInstance().getConnection(Constants.GISH_JOB_DB);
	    	  pstmt = con.prepareStatement("select ticket_no,ticket_type from T_PARKING_VAL_TICKET_STATUS where validated_flag is null or validated_flag=0");
	    	  ResultSet rs = pstmt.executeQuery();
	    	  while(rs.next()){
	    		  ticketList.add(new Object[]{rs.getLong(1),rs.getInt(2)});
	    	  }
	    	  rs.close();
	    	  pstmt.close();
	    	  logger.info("ticket list size:"+ticketList.size());
	    	  for(int i=0;i<ticketList.size();i++){
	    		final Long ticketId = (Long)ticketList.get(i)[0];	
	    		final Integer ticketType = (Integer)ticketList.get(i)[1];
	    		pstmt = con.prepareStatement("update T_PARKING_VAL_TICKET_STATUS set validated_flag=1 where ticket_no=? and ticket_type=?");	    		
	    		pstmt.setLong(1, ticketId);
	    		pstmt.setInt(2, ticketType);
	    		pstmt.executeUpdate();
	    		pstmt.close();
	    		String gateSimulateMode = "false";
	    		String autoCloseTimeStr = "120";
	    		pstmt = con.prepareStatement("select key_name,value from T_APPLICATION_CONFIG where key_name='GATE_SIMULATION_MODE' or key_name='GATE_AUTO_CLOSE_TIME'");
	    		rs = pstmt.executeQuery();
	    		while(rs.next()){
	    			if("GATE_SIMULATION_MODE".equals(rs.getString("key_name"))){
	    				gateSimulateMode = rs.getString("value");
	    			}
	    			if("GATE_AUTO_CLOSE_TIME".equals(rs.getString("key_name"))){
	    				autoCloseTimeStr = rs.getString("value");
	    			}
	    		}
	    		rs.close();
	    		pstmt.close();
	    		int autoCloseTime = 120;
	    		try{
	    			autoCloseTime = Integer.parseInt(autoCloseTimeStr);
	    		}
	    		catch(Exception e){
	    			logger.error(e.getMessage(), e);
	    		}
	    		String token = "";
	    		try{
	    			HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();  	        
			        CloseableHttpClient closeableHttpClient = httpClientBuilder.build();
			        StringBuilder url = new StringBuilder();
			        String uri = ConfigService.getInstance().getConfig(Constants.APP_URI);
			        if(uri==null){
			        	uri = "http://52.71.192.103:8080/callisto_dev/";
			        }
	    			url.append(uri).append("api/v1/authenticate?username=admin@greenowlmobile.com&password=adminGreenowl123");
	    			HttpGet httpGet = new HttpGet(url.toString());							
			        HttpResponse httpResponse = closeableHttpClient.execute(httpGet); 
			        HttpEntity entity = httpResponse.getEntity();  
			        String content = EntityUtils.toString(entity,"utf-8").trim();			        
	    			if(content.contains("\"token\":")){
	    				String[] items = content.split("\"");
	    				token = items[3];
	    			}	    			
	    		}
	    		catch(Exception e){
	    			logger.error(e.getMessage(), e);
	    		}
	    		logger.info("token:"+token);
	    		int timeInterval = 20*1000;
	    		Timer timer = new Timer();
	    		for(int k=0;k<Math.ceil(autoCloseTime/(timeInterval*1.0f));k++){
	    			TimerTask task = new TicketCheckTask(ticketId,ticketType,gateSimulateMode,k+1,(int)Math.ceil(autoCloseTime/(timeInterval*1.0f)),token);
	    			timer.schedule(task, (k+1)*timeInterval);
	    		}	    		
	    	  }	    	  
	      }
		  catch(Exception e){
			  logger.error(e.getMessage(), e);
		  }
	      finally{
	    	  try{
	    		  if(con!=null)
	    			  con.close();
	    	  }
	    	  catch(Exception e){
	    		  logger.error(e.getMessage(),e);
	    	  }
	      }
		
	}	
	
}
