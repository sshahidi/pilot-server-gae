package com.unipos.pilotserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.appengine.api.utils.SystemProperty;




public class PhoneServer {

	public enum JsonKeys{
		/* aps */mac(00),ssid(01),frequency(02),capabilities(11),date_created(21),date_modified(31),visit_count(10),last_visit(41),
		/* rps */rp_id(20),rp_name(51),rp_type(61),latitude(05),longitude(15),altitude(25),accuracy(35),floor_number(03),creator_id(30),building_id(251),
		/* aprp */rss(04),
		/* events */ event_id(50),start_date(71),end_date(81),start_time(91),end_time(101),event_title(111),short_info(121),long_info(131),media_id(06),
		/* users */ user_id(60),user_name(141),passwd(151),email(161),first_name(171),last_name(181),birth_date(191),
		/* control keys */ command_type(201),local_mac(70),response_type(221),error_code(231),message(241),aps(07);
		/* data types: long(%10=0) String(%10=1) int(%10=2) short(%10=3) byte(%10=04) double (%10=5) inputStream(%10=6) JSONarray (%10=7)*/
		private final int id;
		JsonKeys(int id) {this.id=id;}
		public JsonKeysTypes getKeyType()
		{
			return JsonKeysTypes.values()[this.id %10];
		}
	}

	public enum JsonKeysTypes{
		longtype,stringtype,inttype,shorttype, bytetype, doubletype, inputstreamtype, jsonarraytype
	}
	public enum CommandType{
		add_manual_rp,add_auto_rp,remove_rp,
		localize,get_close_rps,get_floor_rps,
		add_Event, remove_event, get_rp_events,
		update_rp,update_event,disconnet;
	}


	public enum ResponseType{manual_rp_added,auto_rp_added,rp_removed,
		location, close_rps, floor_rps, 
		event_added, event_removed, rp_events,
		rp_updated,event_updated,error;
	}


	public enum ErrorCode{insufficient_arguments,rp_not_found,rp_already_exists,
		rp_protected,no_Common_ap_found,event_not_found,
		event_already_exists,event_protected,unknown_error,
		localization_error,insufficient_privilages, db_error;
	}

	///////Global variables
	private final String CRLF="\r\n";
	private Connection db_conn=null;
	HttpServletRequest req;
	HttpServletResponse resp;
	private BufferedReader reader;
	private PrintWriter writer;
	private Logger logger;
	private long user_id;
	
	
	////Constructors
	public PhoneServer(HttpServletRequest req, HttpServletResponse resp) throws Exception
	{
		logger= Logger.getLogger("com.unipos.testing.TestingServlet");
		this.req=req;
		this.resp=resp;
		reader= req.getReader();
		writer = resp.getWriter();
	}


	///////////////Public methods:

	@SuppressWarnings("unchecked")
	public void start() throws Exception
	{
		//writeline("HELLO WELOCME to Pilot Server. Service type: PHONE_SERVER");
		try
		{
			//logger.log(Level.INFO,"log level: info" );
			//logger.log(Level.SEVERE,"log level: severe" );
			//logger.log(Level.WARNING,"log level: warning" );
			//TODO: we need authetication first. otherewise anyone without loggin can work with sever.
			
			String str= readAll();//readline();
			//JSONParser jparser= new JSONParser();
			user_id=Long.parseLong(req.getParameter(JsonKeys.user_id.toString()));
			JSONObject json= new JSONObject(str); //(JSONObject) jparser.parse(str); //
			json.put(JsonKeys.creator_id.toString(), user_id+""); //creator is the user!
			logger.log(Level.INFO,"whole json object: " + json.toString());

			//Type Local MAC Address(String)		Name (String)	AP_Numbers(int)	Latitude(float)	Longitude(float)	Floor_Number(int)	MAC1(String)	RSS1(int)	SSID1 (String:32-bytes)	Frequency1 (int)	Capabilites1 (String:32-bytes)
			CommandType command_type= CommandType.valueOf( (String) json.get(JsonKeys.command_type.toString()));
			JSONObject jresponse=new JSONObject();
			switch(command_type)
			{
			//TODO: support all command types.
			case add_manual_rp:
				jresponse=addRereferencePoint(json);
				break;
			case add_auto_rp:
				jresponse=addRereferencePoint(json); //currently, this case is the same as the one above.
				break;
			case remove_rp:
				jresponse=removeRP(json);
				break;
			case localize:
				jresponse=localize(json);
				break;
				//			case Get_Close_RPs:
				//				break;
				//			case Get_Floor_RPs:
				//				break;
				//			case Add_Event:
				//				break;
				//			case Remove_Event:
				//				break;
				//			case Get_RP_events:
				//				break;
			case update_rp:
				jresponse=updateReferencePoint(json);
				break;
				//			case Update_Event:
				//				break;
			case disconnet:
				//doing nothing! just disconnecting! This might be useful for persistent connection.
				//jresponse.put("response_code", ResponseType.DISCONNECT);
				str="quit";
				break;
			default:
				//JSONObject jresponse=new JSONObject();
				jresponse.put(JsonKeys.response_type.toString(), ResponseType.error.toString());
				jresponse.put(JsonKeys.message.toString(), (String)("Unsupported request code received: "+command_type.toString()));
				writeline(jresponse.toString(2));
			}
			
			logger.log(Level.INFO, "processing done. Sending the result to client.");
			if(!str.equalsIgnoreCase("quit"))
				writeline(jresponse.toString(2));
			//else
			//	break;
		} 
		finally
		{
			//writeline("QUIT");
			//closing connection to db
			if(db_conn !=null && !db_conn.isClosed())
				disconnetDb();
		}
		
		
	}

	///////////// Private methods /////////////////////

	/* **********Handling requrests ********* */

	/**
	 * removes a reference point, with given rp_id and all of its wifi-scans
	 * @param json
	 */
	private JSONObject removeRP(JSONObject json) throws SQLException
	{
		
		logger.log(Level.INFO,"Remove RP entry...");
		JSONObject jresponse=new JSONObject();		
		//long user_mac= json.has(JsonKeys.local_mac.toString())? Long.parseLong((String)json.get(JsonKeys.local_mac.toString())):0;
		
		//connecting to db if necessary
		final int trials=3; //number of trials for connecting to db.
		for(int i=0;i<trials;i++)
		{
			if(db_conn ==null || db_conn.isClosed())
				connectDb();
			else
				break;
		}
		if(db_conn==null) //after trying it is still not connected
			throw new SQLException("Could not connect to db after "+trials+" trials");
		
		if(!json.has(JsonKeys.rp_id.toString()) )
		{
			jresponse.put(JsonKeys.response_type.toString(), ResponseType.error.toString());
			jresponse.put(JsonKeys.error_code.toString(), ErrorCode.insufficient_arguments.toString());
			jresponse.put(JsonKeys.message.toString(), "the rp_id was not given or was not valid.");
			return jresponse;
		}
		long rp_id= Long.parseLong((String)json.get(JsonKeys.rp_id.toString()));

		
		try
		{

			String query_str1= "DELETE FROM rps WHERE rp_id= ?"; 
			String query_str2= "DELETE FROM aprp WHERE rp_id= ?";

			PreparedStatement preparedStmt = db_conn.prepareStatement(query_str1);
			preparedStmt.setLong(1, rp_id);
			preparedStmt.execute();

			preparedStmt = db_conn.prepareStatement(query_str2);
			preparedStmt.setLong(1, rp_id);
			preparedStmt.execute();

			jresponse.put(JsonKeys.response_type.toString(), ResponseType.rp_removed.toString());
			jresponse.put(JsonKeys.rp_id.toString(), rp_id+"");

		}
		catch(SQLException e)
		{
			jresponse.put(JsonKeys.response_type.toString(), ResponseType.error.toString());
			jresponse.put(JsonKeys.error_code.toString(), ErrorCode.db_error.toString());
			jresponse.put(JsonKeys.rp_id.toString(), rp_id+"");
			jresponse.put(JsonKeys.message.toString(), e.getMessage());
		}
		//}
		//log.d("returning statement: "+jresponse.toJSONString());
		return jresponse;
	}


	/**
	 * Adds a reference point by first making sure no already registered RP within 1 meters of this RP exists in DB.
	 * If an RP already exists, it is updated.
	 * If an RP doesn;t exist, it will be created.
	 * @param json The json object received containing the contents 
	 */
	private JSONObject addRereferencePoint(JSONObject json) throws IOException,SQLException
	{
		logger.log(Level.INFO,"Add reference point entry...");
		JSONObject jresponse=new JSONObject();
		/*
		 * Type-1	Local MAC Address(String)		Name (String)	AP_Numbers(int)	Latitude(float)	Longitude(float)	Floor_Number(int)	MAC1(String)	RSS1(int)	SSID1 (String:32-bytes)	Frequency1 (int)	Capabilites1 (String:32-bytes)	...
		 */
		//long user_mac= json.has(JsonKeys.local_mac.toString())? Long.parseLong((String)json.get(JsonKeys.local_mac.toString())):0;

		//for now, if the location is not known, we can't add the RP.
		if(!json.has(JsonKeys.latitude.toString()) || !json.has(JsonKeys.longitude.toString()) )
		{
			logger.log(Level.WARNING,"lat/long is not contained in the json.");
			jresponse.put(JsonKeys.response_type.toString(), ResponseType.error.toString());
			jresponse.put(JsonKeys.error_code.toString(), ErrorCode.insufficient_arguments.toString());
			jresponse.put(JsonKeys.message.toString(), "The resquented RP could not be added since it does not have a (valid) lat/long.");
			return jresponse;
		}

		double lat= Double.parseDouble((String)json.get(JsonKeys.latitude.toString()));
		double lon= Double.parseDouble((String)json.get(JsonKeys.longitude.toString()));
		short floor=json.has(JsonKeys.floor_number.toString())? Short.parseShort((String) json.get(JsonKeys.floor_number.toString())):0; //default value is 0
		double accuracy= json.has(JsonKeys.accuracy.toString())?  Double.parseDouble((String) json.get(JsonKeys.accuracy.toString())):Double.NaN; //default value is NaN


		//checking with DB if we should update or we should create.

		//connecting to db if necessary
		final int trials=3; //number of trials for connecting to db.
		for(int i=0;i<trials;i++)
		{
			if(db_conn ==null || db_conn.isClosed())
				connectDb();
			else
				break;
		}
		if(db_conn==null) //after trying it is still not connected
			throw new SQLException("Could not connect to db after "+trials+" trials");

		//SELECT * FROM rps WHERE floor_number = floor AND latitude BETWEEN (lat-1meter,lat+1meter) AND longitude BETWEEN (lon-1,lon+1)
		ResultSet rs=null;
		for(int i=0;i<trials;i++)
		{
			rs=query("SELECT RP_id , latitude , longitude , floor_number FROM rps WHERE (latitude BETWEEN "+(lat-1d/111111d)+" AND "+(lat+1d/111111d)+") AND (longitude BETWEEN "+(lon-1d/111111d/Math.cos(lat*Math.PI/180d))+" AND "+(lon+1d/111111d/Math.cos(lat*Math.PI/180d))+")");
			if(rs!=null)
				break;
		}
		if(rs==null)
			throw new SQLException("Could not query db after "+trials+" trials");

		double closest_distance=10;
		long closest_rp_id=0; 
		while(rs.next())
		{
			//int id = rs.getInt("id");
			double neighbour_lat =rs.getDouble("latitude");
			double neighbour_lon = rs.getDouble("longitude");
			short closest_floor = rs.getShort("floor_number");
			if(closest_floor == floor && getDistanceFromLatLonInm(neighbour_lat, neighbour_lon, lat, lon) <closest_distance)
			{
				closest_distance=getDistanceFromLatLonInm(neighbour_lat, neighbour_lon, lat, lon);
				closest_rp_id= rs.getLong("RP_id");
			}
		}

		if (closest_rp_id!=0) //a close point existed before. its an update.
		{
			logger.log(Level.INFO,"neighbour point exists. updating the closest point instead of inserting...");
			json.put(JsonKeys.rp_id.toString(), closest_rp_id+"");
			json.put(JsonKeys.command_type.toString(), CommandType.update_rp.toString());
			return updateReferencePoint(json);

		}
		else //the point did not exist before. its a create.
		{
			//SELECT * FROM rps WHERE floor_number = floor AND latitude BETWEEN (lat-1meter,lat+1meter) AND longitude BETWEEN (lon-1,lon+1)
			JsonKeys[] args=new JsonKeys[] {JsonKeys.rp_name,JsonKeys.rp_type,JsonKeys.latitude,JsonKeys.longitude,JsonKeys.accuracy,JsonKeys.floor_number,JsonKeys.creator_id,JsonKeys.date_modified,JsonKeys.building_id};//"visit_count","no_aps"};
			String query_begin ="INSERT INTO rps (";//name, rp_type,latitude, longitude, accuracy, floor_number, creator_id,date_modified,visit_count,no_aps)"
			String query_end =  " values ( "; //?, ?, ?,?,?,?,?,?,?,?)";
			ArrayList<Object> values=new ArrayList<>();
			ArrayList<JsonKeysTypes> column_types=new ArrayList<>();
			for(int i=0;i<args.length;i++)
			{
				if(json.has(args[i].toString()))
				{
					query_begin+=args[i].toString()+", ";
					query_end+="?, ";
					column_types.add(args[i].getKeyType());
					values.add(json.get(args[i].toString()));
				}
			}

			//doing the insertion and retrieving the id of inserted rp.
			rs = manipulate(query_begin.substring(0, query_begin.length()-2)+") "+query_end.substring(0, query_end.length()-2)+")",column_types,values);
			if(rs==null || rs.next()==false)
			{
				jresponse.put(JsonKeys.response_type.toString(), ResponseType.error.toString());
				jresponse.put(JsonKeys.error_code.toString(), ErrorCode.db_error.toString());
				jresponse.put(JsonKeys.message.toString(), "The database did not return a valid RP_id after insertion. RP insertaion failed.");
				return jresponse;
			}
			long rp_id = rs.getLong(1);
			jresponse.put(JsonKeys.response_type.toString(), ResponseType.manual_rp_added.toString());
			jresponse.put(JsonKeys.rp_id.toString(), rp_id+"");

			logger.log(Level.INFO, "RP processed. processing wifi scans.");
			//creating a new point in the DB.
			//int ap_numbers = (Integer)json.get("ap_numbers");
			JSONArray aps= (JSONArray) json.get(JsonKeys.aps.toString());
			String sql_query1="INSERT INTO aps (MAC,ssid,frequency,capabilities,date_modified) VALUES(?,?,?,?, CURRENT_TIMESTAMP)ON DUPLICATE KEY UPDATE ssid=?, frequency = ?, capabilities = ?, date_modified= CURRENT_TIMESTAMP";
			String sql_query2=" INSERT INTO aprp (RP_id,MAC,rss,date_modified) VALUES(?,?,?, CURRENT_TIMESTAMP ) ON DUPLICATE KEY UPDATE rss= ?";
			PreparedStatement preparedStmt_aps = db_conn.prepareStatement(sql_query1);
			PreparedStatement preparedStmt_aprps = db_conn.prepareStatement(sql_query2);
			//fetching the aps and their info
			db_conn.setAutoCommit(false);
			for(int i=0;i<aps.length(); i++)
			{
				JSONObject obj= (JSONObject) aps.get(i);
				if(!obj.has(JsonKeys.mac.toString()) || !obj.has(JsonKeys.frequency.toString()) || ! obj.has(JsonKeys.capabilities.toString()))
				{
					logger.log(Level.WARNING,"mac address is not contained in the json.");
					jresponse.put(JsonKeys.response_type.toString(), ResponseType.error.toString());
					jresponse.put(JsonKeys.error_code.toString(), ErrorCode.insufficient_arguments.toString());
					jresponse.put(JsonKeys.message.toString(), "At least one of the resquented APs could not be added since it did not have a (valid) MAC address or frequency or capabilities field.");
					return jresponse;
				}
				long ap_mac= obj.has(JsonKeys.mac.toString())? Long.parseLong((String)obj.get(JsonKeys.mac.toString())):0;
				byte rss =  obj.has(JsonKeys.rss.toString())? 	Byte.parseByte((String) obj.get(JsonKeys.rss.toString())):-128;
				String ssid =  obj.has(JsonKeys.ssid.toString())? (String) obj.get(JsonKeys.ssid.toString()):"";
				int freq=  obj.has(JsonKeys.frequency.toString())? Integer.parseInt((String) obj.get(JsonKeys.frequency.toString())):0;
				String capabilities =  obj.has(JsonKeys.capabilities.toString())? (String) obj.get(JsonKeys.capabilities.toString()):"";

				preparedStmt_aps.setLong(1, ap_mac);
				preparedStmt_aps.setString(2, ssid);
				preparedStmt_aps.setInt(3, freq);
				preparedStmt_aps.setString(4, capabilities);
				preparedStmt_aps.setString(5, ssid);
				preparedStmt_aps.setInt(6, freq);
				preparedStmt_aps.setString(7, capabilities);
				preparedStmt_aps.addBatch();

				preparedStmt_aprps.setLong(1, rp_id);
				preparedStmt_aprps.setLong(2, ap_mac);
				preparedStmt_aprps.setByte(3, rss);
				preparedStmt_aprps.setByte(4, rss);
				preparedStmt_aprps.addBatch();
			}
			int[] res=preparedStmt_aps.executeBatch();
			db_conn.commit();
			//log.d("insertion to aps result: "+Arrays.toString(res));
			preparedStmt_aprps.executeBatch();
			db_conn.commit();
			//log.d("insertion to aprp result: "+ Arrays.toString(res));
			return jresponse;
		}
	}

	/**
	 * updates a reference point, it can be just adjusting the floor, lat, and long,
	 *  sending new rss values, providing higher accuracy, or changing rp type. 
	 *  The modifier_id will be changed during this process.
	 * @param json
	 */
	private JSONObject updateReferencePoint(JSONObject json) throws IOException,SQLException
	{
		logger.log(Level.INFO,"Update reference point entry...");
		JSONObject jresponse=new JSONObject();
		//long user_mac= json.has(JsonKeys.local_mac.toString())? Long.parseLong((String)json.get(JsonKeys.local_mac.toString())):0;

		//for now, if the location is not known, we can't add the RP.
		if(!json.has(JsonKeys.rp_id.toString()  ))
		{
			logger.log(Level.WARNING,"rp_id is not contained in the json.");
			jresponse.put(JsonKeys.response_type.toString(), ResponseType.error.toString());
			jresponse.put(JsonKeys.error_code.toString(), ErrorCode.insufficient_arguments.toString());
			jresponse.put(JsonKeys.message.toString(), "The resquented RP could not be updated since it does not have a (valid) rp_id.");
			return jresponse;
		}

		long rp_id=Long.parseLong((String) json.get(JsonKeys.rp_id.toString()));
		double lat= json.has(JsonKeys.latitude.toString())? Double.parseDouble((String)json.get(JsonKeys.latitude.toString())):Double.NaN;
		double lon= json.has(JsonKeys.longitude.toString())? Double.parseDouble((String)json.get(JsonKeys.longitude.toString())):Double.NaN;
		short floor=json.has(JsonKeys.floor_number.toString())? Short.parseShort((String) json.get(JsonKeys.floor_number.toString())):0; //default value is 0
		double accuracy= json.has(JsonKeys.accuracy.toString())?  Double.parseDouble((String) json.get(JsonKeys.accuracy.toString())):Double.NaN; //default value is NaN

		
		// UPDATE rps  SET name='test2',rp_type=1,latitude=11,longitude=12,accuracy=10,floor_number=null,creator_id=2,date_modified=CURRENT_TIMESTAMP,floor_id=2 WHERE rp_id=1;
		JsonKeys[] args=new JsonKeys[] {JsonKeys.rp_name,JsonKeys.rp_type,JsonKeys.latitude,JsonKeys.longitude,JsonKeys.accuracy,JsonKeys.floor_number,JsonKeys.creator_id,JsonKeys.date_modified,JsonKeys.building_id};//"visit_count","no_aps"};
		String query_begin ="UPDATE rps SET ";//name, rp_type,latitude, longitude, accuracy, floor_number, creator_id,date_modified,visit_count,no_aps)"
		String query_end =  "date_modified=CURRENT_TIMESTAMP WHERE rp_id= "+rp_id;
		ArrayList<Object> values=new ArrayList<>();
		ArrayList<JsonKeysTypes> column_types=new ArrayList<>();
		for(int i=0;i<args.length;i++)
		{
			if(json.has(args[i].toString()))
			{
				query_begin+=args[i].toString()+"= ?, ";
				//query_end+="?, ";
				column_types.add(args[i].getKeyType());
				values.add(json.get(args[i].toString()));
			}
		}

		//connecting to db if necessary
		final int trials=3; //number of trials for connecting to db.
		for(int i=0;i<trials;i++)
		{
			if(db_conn ==null || db_conn.isClosed())
				connectDb();
			else
				break;
		}
		if(db_conn==null) //after trying it is still not connected
			throw new SQLException("Could not connect to db after "+trials+" trials");

		//doing the insertion and retrieving the id of inserted rp.
		ResultSet rs = manipulate(query_begin+query_end,column_types,values);
		if(rs==null)
		{
			jresponse.put(JsonKeys.response_type.toString(), ResponseType.error.toString());
			jresponse.put(JsonKeys.error_code.toString(), ErrorCode.db_error.toString());
			jresponse.put(JsonKeys.message.toString(), "The database did not return a valid RP_id after insertion. RP insertaion failed.");
			return jresponse;
		}
		jresponse.put(JsonKeys.response_type.toString(), ResponseType.rp_updated.toString());
		jresponse.put(JsonKeys.rp_id.toString(), rp_id+"");


		logger.log(Level.INFO, " updated RP. processing wifi scans.");
		//creating a new point in the DB.
		//int ap_numbers = (Integer)json.get("ap_numbers");
		JSONArray aps= (JSONArray) json.get(JsonKeys.aps.toString());
		String sql_query1="INSERT INTO aps (MAC,ssid,frequency,capabilities,date_modified) VALUES(?,?,?,?, CURRENT_TIMESTAMP)ON DUPLICATE KEY UPDATE ssid=?, frequency = ?, capabilities = ?, date_modified= CURRENT_TIMESTAMP";
		String sql_query2=" INSERT INTO aprp (RP_id,MAC,rss,date_modified) VALUES(?,?,?, CURRENT_TIMESTAMP ) ON DUPLICATE KEY UPDATE rss= ?";
		PreparedStatement preparedStmt_aps = db_conn.prepareStatement(sql_query1);
		PreparedStatement preparedStmt_aprps = db_conn.prepareStatement(sql_query2);
		//fetching the aps and their info
		db_conn.setAutoCommit(false);
		for(int i=0;i<aps.length(); i++)
		{
			JSONObject obj= (JSONObject) aps.get(i);
			if(!obj.has(JsonKeys.mac.toString()) || !obj.has(JsonKeys.frequency.toString()) || ! obj.has(JsonKeys.capabilities.toString()))
			{
				logger.log(Level.INFO,"mac address is not contained in the json.");
				jresponse.put(JsonKeys.response_type.toString(), ResponseType.error.toString());
				jresponse.put(JsonKeys.error_code.toString(), ErrorCode.insufficient_arguments.toString());
				jresponse.put(JsonKeys.message.toString(), "At least one of the resquented APs could not be added since it did not have a (valid) MAC address or frequency or capabilities field.");
				return jresponse;
			}
			long ap_mac= obj.has(JsonKeys.mac.toString())? Long.parseLong((String)obj.get(JsonKeys.mac.toString())):0;
			byte rss =  obj.has(JsonKeys.rss.toString())? 	Byte.parseByte((String) obj.get(JsonKeys.rss.toString())):-128;
			String ssid =  obj.has(JsonKeys.ssid.toString())? (String) obj.get(JsonKeys.ssid.toString()):"";
			int freq=  obj.has(JsonKeys.frequency.toString())? Integer.parseInt((String) obj.get(JsonKeys.frequency.toString())):0;
			String capabilities =  obj.has(JsonKeys.capabilities.toString())? (String) obj.get(JsonKeys.capabilities.toString()):"";

			preparedStmt_aps.setLong(1, ap_mac);
			preparedStmt_aps.setString(2, ssid);
			preparedStmt_aps.setInt(3, freq);
			preparedStmt_aps.setString(4, capabilities);
			preparedStmt_aps.setString(5, ssid);
			preparedStmt_aps.setInt(6, freq);
			preparedStmt_aps.setString(7, capabilities);
			preparedStmt_aps.addBatch();

			preparedStmt_aprps.setLong(1, rp_id);
			preparedStmt_aprps.setLong(2, ap_mac);
			preparedStmt_aprps.setByte(3, rss);
			preparedStmt_aprps.setByte(4, rss);
			preparedStmt_aprps.addBatch();
		}
		int[] res=preparedStmt_aps.executeBatch();
		db_conn.commit();
		//log.d("insertion to aps result: "+Arrays.toString(res));
		preparedStmt_aprps.executeBatch();
		db_conn.commit();
		//log.d("insertion to aprp result: "+ Arrays.toString(res));
		return jresponse;

	}

	@SuppressWarnings("unchecked")
	private JSONObject localize(JSONObject json) throws SQLException
	{
		logger.log(Level.INFO,"Localize entry...");
		JSONObject jresponse=new JSONObject();		
		//long user_mac= json.has(JsonKeys.local_mac.toString())? Long.parseLong((String)json.get(JsonKeys.local_mac.toString())):0;
		
		//connecting to db if necessary
		final int trials=3; //number of trials for connecting to db.
		for(int i=0;i<trials;i++)
		{
			if(db_conn ==null || db_conn.isClosed())
				connectDb();
			else
				break;
		}
		if(db_conn==null) //after trying it is still not connected
			throw new SQLException("Could not connect to db after "+trials+" trials");
		
		JSONArray aps= (JSONArray) json.get(JsonKeys.aps.toString());
		String macs="";
		for(int i=0;i<aps.length();i++)
		{
			JSONObject obj=(JSONObject) aps.get(i);
			if(!obj.has(JsonKeys.mac.toString()) )
			{
				jresponse.put(JsonKeys.response_type.toString(), ResponseType.error.toString());
				jresponse.put(JsonKeys.error_code.toString(), ErrorCode.insufficient_arguments.toString());
				jresponse.put(JsonKeys.message.toString(), "At least one of the resquented APs could not be added since it did not have a (valid) MAC address.");
				return jresponse;
			}
			long mac= Long.parseLong((String)obj.get(JsonKeys.mac.toString()));
			//for now we don't care about rss!
			//byte rss= ((Number) obj.get(JsonKeys.rss.toString())).byteValue();
			macs+= mac+(i < aps.length()-1? ", ":"");
		}
		
		//Old method without tie breaker.
		/*String query_str="SELECT rps.latitude, rps.longitude, rps.floor_number, rps.building_id, aprp.rp_id, COUNT(MAC) AS NumberOfAPs"+ 
                     " FROM (aprp INNER JOIN rps ON aprp.rp_id = rps.rp_id)"+
                     " WHERE MAC IN ("+ macs+" )"+
                     " GROUP BY rp_id"+
                     " ORDER BY NumberOfAPs DESC";
        */
		//new method with tie breaker
		String query_str="SELECT rps.latitude, rps.longitude, rps.floor_number, rps.building_id, aprp.rp_id, "+
	                     " SUM(IF(MAC IN ("+macs+"), 1, 0)) AS NumberOfAPs, "+
	                     "  count(MAC) AS TotalAPs "+
	                     " FROM (aprp INNER JOIN rps ON aprp.rp_id = rps.rp_id) "+
	                     " GROUP BY rp_id"+
	                     " ORDER BY NumberOfAPs DESC, TotalAPs ASC"+
	                     " LIMIT 5";
		
		
		ResultSet rs=query(query_str);
		if(rs==null)
		{
			logger.log(Level.WARNING,"DB query failed. couldn't find location");
			jresponse.put(JsonKeys.response_type.toString(), ResponseType.error.toString());
			jresponse.put(JsonKeys.error_code.toString(), ErrorCode.db_error.toString());
			jresponse.put(JsonKeys.message.toString(), "Could not locate the user. DB query failed");
			return jresponse;
		}
		
		//checking if any result exists!
		
		//Doing 1NN now! just returning the point with the most number of common APs.
		if(rs.next()==false) //checking if any result exists! ALSO loading the first result.
		{
			jresponse.put(JsonKeys.response_type.toString(), ResponseType.error.toString());
			jresponse.put(JsonKeys.error_code.toString(), ErrorCode.no_Common_ap_found);
			logger.log(Level.INFO,"no common aps found for localization.");
			return jresponse;
		}
		double lat =rs.getDouble("latitude");
		double lon = rs.getDouble("longitude");
		short floor = rs.getShort("floor_number");
		long rp_id= rs.getLong("RP_id");
		String building_id= rs.getString("building_id");
		
		
		long best_score=rs.getLong("TotalAPs"); //Tie breaker: minimum total aps is the winnger.
		long max_ap=rs.getLong("NumberOfAPs"); //it should be among the points that have max or (max-1) common aps.
		
		if(max_ap==0) //checking if any result exists!
		{
			jresponse.put(JsonKeys.response_type.toString(), ResponseType.error.toString());
			jresponse.put(JsonKeys.error_code.toString(), ErrorCode.no_Common_ap_found);
			logger.log(Level.INFO,"no common aps found for localization.");
			return jresponse;
		}
		
		while(rs.next())
		{
			long num_aps=rs.getLong("NumberOfAPs");
			long score_aps=rs.getLong("TotalAPs");
			if(num_aps>=max_ap-1 && score_aps<best_score)
			{
				best_score=score_aps;
				lat =rs.getDouble("latitude");
				lon = rs.getDouble("longitude");
				floor = rs.getShort("floor_number");
				rp_id= rs.getLong("RP_id");
				building_id= rs.getString("building_id");
			}
		}
		
		
		jresponse.put(JsonKeys.response_type.toString(), ResponseType.location.toString());
		jresponse.put(JsonKeys.latitude.toString(), lat+"");
		jresponse.put(JsonKeys.longitude.toString(), lon+"");
		jresponse.put(JsonKeys.floor_number.toString(), floor+"");
		jresponse.put(JsonKeys.rp_id.toString(), rp_id+"");
		jresponse.put(JsonKeys.building_id.toString(), building_id);
		//jresponse.put("common_ap_count", max_ap);
		
		//log.d("returning statement: "+jresponse.toJSONString());
		return jresponse;
	}


	////// IO functions.

	private Connection connectDb()
	{
		logger.log(Level.INFO, "connecting to db.");
		String url = null;
		try
		{
			if (SystemProperty.environment.value() ==
					SystemProperty.Environment.Value.Production) {
				// Connecting from App Engine.
				// Load the class that provides the "jdbc:google:mysql://"
				// prefix.
				//log("Connectin to DB from Appe engine network.");
				Class.forName("com.mysql.jdbc.GoogleDriver");
				url = "jdbc:google:mysql://pilot-server:locdb";//?user=root";
			} else {
				// Connecting from an external network.
				logger.log(Level.WARNING,"Connectin to DB from External network.");
				Class.forName("com.mysql.jdbc.Driver");
				url = "jdbc:mysql://173.194.247.241:3306";//?user=root";
			}
			
			db_conn = DriverManager.getConnection(url,"shervin","123456");
			ResultSet rs = db_conn.createStatement().executeQuery("use loc_db");
			logger.log(Level.INFO, "connected to db.");
			return db_conn;
		}
		catch(ClassNotFoundException | SQLException e)
		{
			e.printStackTrace();
			return null;
		}
		
		/*
		// Connecting from an external network. 
		try {
			//TODO: this shouldn't be hardcoded!
			Class.forName("com.mysql.jdbc.Driver");
			String url = "jdbc:mysql://173.194.247.241:3306";//?user=root"; 
			db_conn = DriverManager.getConnection(url,"shervin","123456");
			//by default using the location db when stablishing connection
			ResultSet rs = db_conn.createStatement().executeQuery("use loc_db"); 
			return db_conn;
		} catch (ClassNotFoundException | SQLException e) {
			//log.e("Serving client "+CLIENT_ID+": could not connect to db.");
			//log.printStackTrace(e,Slog.Mode.DEBUG);
			e.printStackTrace();
			return null;
		} 
		*/
	}
	private boolean disconnetDb()
	{
		try {
			db_conn.close();
			return true;
		} catch (SQLException e) {
			//log.w("Serving client "+CLIENT_ID+": Error in closing database connection.");
			//log.printStackTrace(e, Slog.Mode.DEBUG);
			e.printStackTrace();
			return false;
		}
	}
	private ResultSet query(String query_str)
	{
		//log.d("calling a select statement. qurey_str: "+query_str);
		try {
			return db_conn.createStatement().executeQuery(query_str);
		} catch (SQLException e) {
			//log.w("Serving client "+CLIENT_ID+": Error in querying database.");
			//log.printStackTrace(e, Slog.Mode.DEBUG);
			e.printStackTrace();
			return null;
		}

	}
	/**
	 * can call for a insert,update, or delete sql statement via {@link com.mysql.jdbc.PreparedStatement},
	 * @param query_str the query string with parameters set as '?'
	 * @param columns the parameters data types
	 * @param values the parameter values
	 * @return a {@link com.mysql.jdbc.ResultSet} containing the auto_generated_keys. If no auto generated keys are returned, result set will be empty. 
	 * If a problem happens in the call, <code>null</code> is returned.
	 */
	private ResultSet manipulate(String query_str,ArrayList<JsonKeysTypes> column_types, ArrayList<Object> values)
	{
		//logger.log(Level.INFO,"calling a prepared statement. qurey_str: "+query_str);
		try
		{
			//" INSERT INTO log (echo_time, text,id)" + " values (?, ?, ?)";
			String query = query_str;



			// create the mysql insert preparedstatement
			PreparedStatement preparedStmt = db_conn.prepareStatement(query,PreparedStatement.RETURN_GENERATED_KEYS);
			for(int i=0;i<values.size();i++)
			{
				switch (column_types.get(i))
				{
				case stringtype:
					preparedStmt.setString (i+1, (String)values.get(i));
					break;
				case longtype:
					preparedStmt.setLong(i+1, Long.parseLong((String)values.get(i)));
					break;
				case doubletype:
					preparedStmt.setDouble(i+1, Double.parseDouble((String)values.get(i)));
					break;
				case bytetype:
					preparedStmt.setByte(i+1, Byte.parseByte((String)values.get(i)));
					break;
				case inttype:
					preparedStmt.setInt(i+1, Integer.parseInt((String)(values.get(i))));
					break;
				case shorttype:
					preparedStmt.setShort(i+1, Short.parseShort((String)(values.get(i))));
					break;
					//not implelmented for now.	
					//case inputStreamType:
					//	preparedStmt.setblob(i+1, ...
					//	break;
				default:
					throw new Exception("Unkonwn column type, when preparing the sql statement.");
				}
			}
			// execute the preparedstatement
			//log.d("final prepared statement: "+ preparedStmt.toString());
			preparedStmt.executeUpdate();
			
			return preparedStmt.getGeneratedKeys();
		}
		catch(Exception e)
		{
			logger.log(Level.WARNING,"Writing to DB was not successful");
			//log.printStackTrace(e, Slog.Mode.DEBUG);
			e.printStackTrace();
			return null;
		}
	}
	
	private void writeline(String msg) throws Exception
	{
		writer.println(msg);
		writer.flush();
		//log.v("to "+CLIENT_ID+": "+msg);	
	}

	private String readline() throws Exception
	{
		String str= reader.readLine();
		//log.v("from "+CLIENT_ID+": "+str);
		return str;
	}

	private String readAll() throws IOException
	{
		StringBuilder sb = new StringBuilder();

		BufferedReader reader = req.getReader();
		//reader.mark(10000);

		String line="";
		while (true)
		{
			line = reader.readLine();
			if(line==null)
				break;
			sb.append(line).append("\n");
		} 
		//reader.reset();
		return sb.toString();

	}
	
	
	///Other methods that come handy 
	double getDistanceFromLatLonInm(double lat1,double lon1,double lat2,double lon2) 
	{
		double R = 6378137; // Avg Radius of the earth in m
		double dLat = (lat2-lat1)*(Math.PI/180);  // degree2radian
		double dLon = (lon2-lon1)*(Math.PI/180);  // degree2radian
		double a = 
				Math.sin(dLat/2) * Math.sin(dLat/2) +
				Math.cos(lat1*(Math.PI/180)) * Math.cos(lat2*(Math.PI/180)) * 
				Math.sin(dLon/2) * Math.sin(dLon/2);

		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a)); 
		double d = R * c; // Distance in m
		return d;
	}



	////////Getters and Setters	
	public HttpServletRequest getReq() {
		return req;
	}
	
	public void setReq(HttpServletRequest req) {
		this.req = req;
	}
	
	public HttpServletResponse getResp() {
		return resp;
	}
	
	public void setResp(HttpServletResponse resp) {
		this.resp = resp;
	}
	


}