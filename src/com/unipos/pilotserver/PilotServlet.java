package com.unipos.pilotserver;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;

import com.google.appengine.api.utils.SystemProperty;
import com.unipos.pilotserver.PhoneServer.ResponseType;

@SuppressWarnings("serial")
public class PilotServlet extends HttpServlet {
	
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException 
	{
		resp.setContentType("application/json");
		if (SystemProperty.environment.value() ==
				SystemProperty.Environment.Value.Production) {
			//if running on server, must use https.
			if(!req.isSecure())
			{
				resp.getWriter().println((new JSONObject()).put("ERROR","Unsecure connections are not accepted. Try using Https").toString());
				return;
			}
		}
		
		String API_KEY= req.getParameter("api_key");
		//Makhafanim
		if(API_KEY==null || !API_KEY.equalsIgnoreCase("5ab9535ff010efcabb636b5dd6103fadb2bed9eb"))
		{
			resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
			resp.getWriter().println("Access forbiden.");
			//throw new Exception("API KEY not correct.");
			return;
		}	
		
		
		
		resp.getWriter().println("This is the get response!");

		String url = null;
		try
		{
			if (SystemProperty.environment.value() ==
					SystemProperty.Environment.Value.Production) {
				// Connecting from App Engine.
				// Load the class that provides the "jdbc:google:mysql://"
				// prefix.
				log("Connectin to DB from Appe engine network.");
				Class.forName("com.mysql.jdbc.GoogleDriver");
				url =
						"jdbc:google:mysql://pilot-server:locdb";//?user=root";
			} else {
				// Connecting from an external network.
				log("Connectin to DB from External network.");
				Class.forName("com.mysql.jdbc.Driver");
				url = "jdbc:mysql://173.194.247.241:3306";//?user=root";
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return;
		}


		PrintWriter out = resp.getWriter();
		try {
			Connection conn = DriverManager.getConnection(url,"shervin","123456");
			log("connected to DB");
			try {
				String user_name = req.getParameter("user_name");
				if (user_name == null || user_name == "") {
					/*out.println(
							"<html><head></head><body>You are missing either a message or a name! Try again! " +
							"Redirecting in 3 seconds...</body></html>");*/
					out.println("You are missing the user_name!");	
				} else {
					ResultSet rs = conn.createStatement().executeQuery("use loc_db"); 
					String statement = "INSERT INTO users (user_name,email,first_name,last_name,birth_date) VALUES (?,'sh.shervin@gmail.com','Shervin','Shahidi','1987-04-08')";
					PreparedStatement stmt = conn.prepareStatement(statement);
					stmt.setString(1, user_name);
					int success = 2;
					success = stmt.executeUpdate();
					if (success == 1) {
						/*out.println(
								"<html><head></head><body>Success! Redirecting in 3 seconds...</body></html>");*/
						out.println("Success!");	
					} else if (success == 0) {
						/*out.println(
								"<html><head></head><body>Failure! Please try again! " +
								"Redirecting in 3 seconds...</body></html>");*/
						out.println("Failed!");	
					}
				}
				
			} catch(Exception e){
				out.println("Failed! reason: "+ e.getMessage());	
				e.printStackTrace();
			} finally {

				conn.close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		//resp.setHeader("Refresh", "3; url=/testing");
		//super.doPost(req, resp);
		
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException 
	{
		Logger logger= Logger.getLogger("com.unipos.testing.TestingServlet");
		resp.setContentType("application/json");
		try {
			if (SystemProperty.environment.value() ==
					SystemProperty.Environment.Value.Production) {
				//if running on server, must use https.
				if(!req.isSecure())
					throw new Exception("Unsecure connections are not accepted. Try using Https");
			}
			
			
			String API_KEY= req.getParameter("api_key");
			//Makhafanim
			if(API_KEY==null || !API_KEY.equalsIgnoreCase("5ab9535ff010efcabb636b5dd6103fadb2bed9eb"))
			{
				resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
				resp.getWriter().println("Access forbiden.");
				return;
				//throw new Exception("API KEY not correct.");
			}	
			PhoneServer ps=new PhoneServer(req, resp);
			ps.start();
		} catch (Exception e) {
			JSONObject jresponse=new JSONObject();
			jresponse.put(PhoneServer.JsonKeys.response_type.toString(), ResponseType.error.toString());
			jresponse.put(PhoneServer.JsonKeys.error_code.toString(), PhoneServer.ErrorCode.unknown_error.toString());
			jresponse.put(PhoneServer.JsonKeys.message.toString(), "reason: "+(String)(e.getMessage()));
			resp.getWriter().println(jresponse.toString(2));
			logger.log(  Level.WARNING, "Exception occurred. message: "+ e.getMessage());
			e.printStackTrace();
		}
		
	}
}
