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
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

import com.greenowl.callisto.timer.Constants;
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
	    		pstmt = con.prepareStatement("select value from T_APPLICATION_CONFIG where key_name='gate_simulate_mode'");
	    		rs = pstmt.executeQuery();
	    		if(rs.next()){
	    			gateSimulateMode = rs.getString(1);
	    		}
	    		rs.close();
	    		pstmt.close();
	    		Timer timer = new Timer();	    		
	    		TimerTask task1 = new TicketCheckTask(ticketId,ticketType,gateSimulateMode);	     
	    		TimerTask task2 = new TicketCheckTask(ticketId,ticketType,gateSimulateMode);
	    		TimerTask task3 = new TicketCheckTask(ticketId,ticketType,gateSimulateMode);
          		timer.schedule(task1, 10*1000);
          		timer.schedule(task2, 25*1000);
          		timer.schedule(task3, 35*1000);
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
