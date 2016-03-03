package com.greenowl.callisto.timer.service;

import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Calendar;
import java.util.TimerTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import com.greenowl.callisto.timer.Constants;

public class TicketCheckTask extends TimerTask{

	private Log logger = LogFactory.getLog(this.getClass());
	
	private long ticketId;
	private int ticketType;
	private String gateSimulateMode;
	private int checkCount;
	private int maxCheckTimes;
	private String token;
	
	public TicketCheckTask(long ticketId, int ticketType, String gateSimulateMode, int checkCount, int maxCheckTimes, String token){
		this.ticketId = ticketId;
		this.ticketType = ticketType;
		this.gateSimulateMode = gateSimulateMode;
		this.checkCount = checkCount;
		this.maxCheckTimes = maxCheckTimes;
		this.token = token;
	}	
	
	private String getHttpContent(String url, String token){
		try{
			HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();  	        
	        CloseableHttpClient closeableHttpClient = httpClientBuilder.build();
	        HttpPost httpPost = new HttpPost(url.toString());	
	        httpPost.setHeader("X-Auth-Token", token);
	        httpPost.setHeader("Accept","application/json");
	        httpPost.setHeader("Content-Type", "application/json");
	        HttpResponse httpResponse = closeableHttpClient.execute(httpPost); 
	        HttpEntity entity = httpResponse.getEntity();  
	        String content = EntityUtils.toString(entity,"utf-8").trim();
	        return content;
		}
		catch(Exception e){
			logger.error(e.getMessage(), e);			
		}
		return null;
	}
	
	public void run(){			
			
  			Connection con = null;
				try{
					String status = "";
					con = ConnectionFactory.getInstance().getConnection(Constants.GISH_JOB_DB);
					PreparedStatement pstmt = con.prepareStatement("select parking_status from T_PARKING_ACTIVITY where id=?");
					pstmt.setLong(1, ticketId);
					ResultSet rs = pstmt.executeQuery();
					if(rs.next()){
						status = rs.getString(1);
					}
					rs.close();
					pstmt.close();
					logger.info("ticket id:"+ticketId+",parking status:"+status);
					if(!((Constants.PARKING_STATUS_IN_FLIGHT.equalsIgnoreCase(status) && ticketType==Constants.PARKING_TICKET_TYPE_ENTER) || (Constants.PARKING_STATUS_COMPLETED.equalsIgnoreCase(status) && ticketType==Constants.PARKING_TICKET_TYPE_EXIT))){	  	
						String ticketStatus = null;
						try{
							String ip = ConfigService.getInstance().getConfig(Constants.GATE_API_IP);
							if(ip==null){
								ip = "localhost";
							}
							int port = 2222;
							String portStr = ConfigService.getInstance().getConfig(Constants.GATE_API_PORT);
							try{
								port = Integer.parseInt(portStr);
							}
							catch(Exception e){
								logger.error(e.getMessage(), e);
							}
							
							HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();  	        
					        CloseableHttpClient closeableHttpClient = httpClientBuilder.build();
					        StringBuilder url = new StringBuilder();
					        url.append("http://");
					        url.append(ip).append(":").append(port).append("/");
					        url.append("gatecmd/gate_ticket_passed?gate_id=");
					        if(ticketType==Constants.PARKING_TICKET_TYPE_EXIT){
					        	url.append("2");
					        }
					        else{
					        	url.append("1");
					        }
					        url.append("&ticket=");
					        url.append(ticketId);
					        logger.info("****************************************************");
							logger.info("check count:"+checkCount+",url:"+url.toString());
							HttpGet httpGet = new HttpGet(url.toString());							
					        HttpResponse httpResponse = closeableHttpClient.execute(httpGet); 
					        HttpEntity entity = httpResponse.getEntity();  
					        ticketStatus = EntityUtils.toString(entity,"utf-8").trim();					        						
							
							logger.info("ticket id:"+ticketId+",ticket type:"+ticketType+",gate response:"+ticketStatus);
							if("true".equalsIgnoreCase(gateSimulateMode)){
								if(ticketStatus.contains("UNKNON TICKET") || ticketStatus.contains("Process exited with an error")){
									ticketStatus = "SAFTETY-STATUS: PASSED";
									logger.info("=====new ticketStatus:"+ticketStatus);
								}
							}		
							logger.info("****************************************************");
						}
						catch(Exception e){
							logger.error(e.getMessage()+",TICKET ID:"+ticketId+",TICKET TYPE:"+ticketType, e);
						}
						StringBuilder url = new StringBuilder();
				        String uri = ConfigService.getInstance().getConfig(Constants.APP_URI);
				        if(uri==null){
				        	uri = "http://52.71.192.103:8080/callisto_dev/";
				        }
						url.append(uri);
						url.append("api/admin/task/updateParkingActivity/").append(ticketId);
						url.append("?");
	      				if(ticketStatus!=null && ticketStatus.contains("SAFTETY-STATUS: PASSED")){ 	      					
	      					if(ticketType==Constants.PARKING_TICKET_TYPE_ENTER){
	      						url.append("parkingStatus=").append(Constants.PARKING_STATUS_IN_FLIGHT);
	      						url.append("&entryDateTime=").append(Calendar.getInstance().getTimeInMillis());
	      					}
	      					else if(ticketType==Constants.PARKING_TICKET_TYPE_EXIT){
	      						url.append("parkingStatus=").append(Constants.PARKING_STATUS_COMPLETED);
	      						url.append("&exitDateTime=").append(Calendar.getInstance().getTimeInMillis());
	      					}
	      					url.append("&gateResponse=").append(URLEncoder.encode(ticketStatus,"utf-8"));
	      					String content = getHttpContent(url.toString(),token);	
	      					logger.info(url.toString()+":"+content);
	      				}
	      				else if(checkCount==maxCheckTimes){		      					
	      					if(ticketType==Constants.PARKING_TICKET_TYPE_ENTER){
	      						url.append("parkingStatus=").append(Constants.PARKING_STATUS_ENTER_EXCEPTION);
	      					}
	      					else{
	      						url.append("parkingStatus=").append(Constants.PARKING_STATUS_EXIT_EXCEPTION);
	      					}
	      					url.append("&gateResponse=").append(URLEncoder.encode(ticketStatus,"utf-8"));
	      					String content = getHttpContent(url.toString(),token);	
	      					logger.info(url.toString()+":"+content);
	      				}	      				
	      				
	      				pstmt = con.prepareStatement("update T_PARKING_VAL_TICKET_STATUS set validate_date_time=? where ticket_no=? and ticket_type=?");
	      				pstmt.setTimestamp(1, new java.sql.Timestamp(Calendar.getInstance().getTimeInMillis()));
	      				pstmt.setLong(2, ticketId);
	      				pstmt.setInt(3, ticketType);
	      				pstmt.executeUpdate();
	      				pstmt.close();
					}
					else{
						logger.info("PARKING STATUS ALREADY UPDATED TO:"+Constants.PARKING_STATUS_IN_FLIGHT+" or "+Constants.PARKING_STATUS_COMPLETED+",TICKET ID:"+ticketId);
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
