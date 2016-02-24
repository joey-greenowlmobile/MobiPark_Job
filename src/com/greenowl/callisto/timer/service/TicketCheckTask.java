package com.greenowl.callisto.timer.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Calendar;
import java.util.TimerTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.greenowl.callisto.timer.Constants;
import com.greenowlmobile.parkgateclient.parkgateCmdClient;

public class TicketCheckTask extends TimerTask{

	private Log logger = LogFactory.getLog(this.getClass());
	
	private long ticketId;
	private int ticketType;
	private String gateSimulateMode;
	
	public TicketCheckTask(long ticketId, int ticketType, String gateSimulateMode){
		this.ticketId = ticketId;
		this.ticketType = ticketType;
		this.gateSimulateMode = gateSimulateMode;
	}
	
	public void run(){
			String ticketStatus = null;
			try{
				parkgateCmdClient parkClient2 = new parkgateCmdClient("localhost",2222);
				if(ticketType==Constants.PARKING_TICKET_TYPE_EXIT){
					ticketStatus = parkClient2.getTicketStatus(2, Long.toString(ticketId));	
				}
				else{
					ticketStatus = parkClient2.getTicketStatus(Constants.ENTER_GATE, Long.toString(ticketId));	
				}
				logger.info("ticket id:"+ticketId+",ticket type:"+ticketType+",gate response:"+ticketStatus);
				if("true".equalsIgnoreCase(gateSimulateMode)){
					if(ticketStatus.contains("UNKNON TICKET")){
						ticketStatus = "SAFTETY-STATUS: PASSED";
						logger.info("=====new ticketStatus:"+ticketStatus);
					}
				}
				
			}
			catch(Exception e){
				logger.error(e.getMessage()+",TICKET ID:"+ticketId+",TICKET TYPE:"+ticketType, e);
			}
			if(ticketStatus!=null){
  			Connection con = null;
				try{
					String status = "";
					con = ConnectionFactory.getInstance().getConnection(Constants.GISH_JOB_DB);
					PreparedStatement pstmt = con.prepareStatement("select parking_status from T_PARKING_SALES_ACTIVITY where id=?");
					pstmt.setLong(1, ticketId);
					ResultSet rs = pstmt.executeQuery();
					if(rs.next()){
						status = rs.getString(1);
					}
					rs.close();
					pstmt.close();
					if(!Constants.PARKING_STATUS_IN_FLIGHT.equalsIgnoreCase(status)){	  					
	      				if(ticketStatus!=null && ticketStatus.contains("SAFTETY-STATUS: PASSED")){ 
	      					if(ticketType==Constants.PARKING_TICKET_TYPE_ENTER){
	      						pstmt = con.prepareStatement("update T_PARKING_SALES_ACTIVITY set parking_status=?,gate_response=?,entry_datetime=? where id=?");
	      						pstmt.setString(1, Constants.PARKING_STATUS_IN_FLIGHT);	
	      					}
	      					else if(ticketType==Constants.PARKING_TICKET_TYPE_EXIT){
	      						pstmt = con.prepareStatement("update T_PARKING_SALES_ACTIVITY set parking_status=?,gate_response=?,exit_datetime=? where id=?");
	      						pstmt.setString(1, Constants.PARKING_STATUS_COMPLETED);	  
	      					}
	      					pstmt.setString(2, ticketStatus);
	      					pstmt.setTimestamp(3, new java.sql.Timestamp(Calendar.getInstance().getTimeInMillis()));
		      				pstmt.setLong(4, ticketId);
	      				}
	      				else{	   
	      					pstmt = con.prepareStatement("update T_PARKING_SALES_ACTIVITY set parking_status=?,gate_response=? where id=?");
	      					pstmt.setString(1, Constants.PARKING_STATUS_EXCEPTION);
	      					pstmt.setString(2, ticketStatus);
		      				pstmt.setLong(3, ticketId);
	      				}	      				
	      				pstmt.executeUpdate();
	      				pstmt.close();
	      				pstmt = con.prepareStatement("update T_PARKING_VAL_TICKET_STATUS set validate_date_time=? where ticket_no=? and ticket_type=?");
	      				pstmt.setTimestamp(1, new java.sql.Timestamp(Calendar.getInstance().getTimeInMillis()));
	      				pstmt.setLong(2, ticketId);
	      				pstmt.setInt(3, ticketType);
	      				pstmt.executeUpdate();
	      				pstmt.close();
					}
					else{
						logger.info("PARKING STATUS ALREADY UPDATED TO:"+Constants.PARKING_STATUS_IN_FLIGHT+",TICKET ID:"+ticketId);
					}
				}
				catch(Exception e){
					logger.error(e.getMessage()+",TICKET ID:"+ticketId+",TICKET TYPE:"+ticketType, e);
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
		}	                
	
	
}
