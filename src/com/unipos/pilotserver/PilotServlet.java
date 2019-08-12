package com.unipos.pilotserver;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;

import com.google.appengine.api.ThreadManager;
import com.google.appengine.api.utils.SystemProperty;
import com.unipos.pilotserver.PhoneServer.JsonKeys;
import com.unipos.pilotserver.PhoneServer.ResponseType;


import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDriver;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;


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

		///
		//test(null);
		if(API_KEY!=null)
			return;
		////
		
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
				url = "jdbc:google:mysql://pilot-server:locdb";//?user=root";
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
			Connection conn = DriverManager.getConnection(url+"/loc_db","shervin","123456");
			log("connected to DB");
			try {
				String user_name = req.getParameter("user_name");
				if (user_name == null || user_name == "") {
					/*out.println(
							"<html><head></head><body>You are missing either a message or a name! Try again! " +
							"Redirecting in 3 seconds...</body></html>");*/
					out.println("You are missing the user_name!");	
				} else {
					//ResultSet rs = conn.createStatement().executeQuery("use loc_db"); 
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
			if(API_KEY==null || !API_KEY.equalsIgnoreCase("5ab9535ff010efcabb636b5dd6103fadb2bed9eb") || !autheticateUesr(req))
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
	
	
	boolean autheticateUesr(HttpServletRequest req)
	{
		String user_name=req.getParameter(JsonKeys.user_name.toString());
		String user_id=req.getParameter(JsonKeys.user_id.toString());
		
		if(user_name==null || user_id==null)
		{
			return false;
		}
		//TODO: check if user_name and id match with db.
		return true;
	}
	
	
	
	
	/////////////////////***********Testing conneciton pools
	/* public void test(String[] args) {
	        //
	        // First we load the underlying JDBC driver.
	        // You need this if you don't use the jdbc.drivers
	        // system property.
	        //
	        System.out.println("Loading underlying JDBC driver.");
	        try {
	            Class.forName("com.mysql.jdbc.Driver");
	        } catch (ClassNotFoundException e) {
	            e.printStackTrace();
	        }
	        System.out.println("Done.");

	        //
	        // Then we set up and register the PoolingDriver.
	        // Normally this would be handled auto-magically by
	        // an external configuration, but in this example we'll
	        // do it manually.
	        //
	        System.out.println("Setting up driver.");
	        HikariDataSource ds=null;
	        try {
	            //setupDriver("jdbc:mysql://173.194.247.241:3306/loc_db");
	        	ds= setupDriver2();
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	        System.out.println("Done.");

	        //
	        // Now, we can use JDBC as we normally would.
	        // Using the connect string
	        //  jdbc:apache:commons:dbcp:example
	        // The general form being:
	        //  jdbc:apache:commons:dbcp:<name-of-pool>
	        //

	        Connection conn = null;
	        Statement stmt = null;
	        ResultSet rset = null;

	        try {
	            System.out.println("Creating connection.");
	            conn = ds.getConnection(); //DriverManager.getConnection("jdbc:apache:commons:dbcp:example");
	            System.out.println("Creating statement.");
	            stmt = conn.createStatement();
	            System.out.println("Executing statement.");
	            rset = stmt.executeQuery("SELECT * FROM USERS WHERE user_id < 10");
	            System.out.println("Results:");
	            int numcols = rset.getMetaData().getColumnCount();
	            while(rset.next()) {
	                for(int i=1;i<=numcols;i++) {
	                    System.out.print("\t" + rset.getString(i));
	                }
	                System.out.println("");
	            }
	        } catch(SQLException e) {
	            e.printStackTrace();
	        } finally {
	            try { if (rset != null) rset.close(); } catch(Exception e) { }
	            try { if (stmt != null) stmt.close(); } catch(Exception e) { }
	            try { if (conn != null) conn.close(); } catch(Exception e) { }
	        }

	        // Display some pool statistics
	        try {
	            printDriverStats();
	        } catch (Exception e) {
	            e.printStackTrace();
	        }

	        // closes the pool
	        try {
	            shutdownDriver();
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }

	 
	 	public HikariDataSource setupDriver2() throws Exception
	 	{
	 		HikariConfig config = new HikariConfig();
			config.setThreadFactory(ThreadManager.backgroundThreadFactory());
			config.setMaximumPoolSize(100);

		    config.setDataSourceClassName("com.mysql.jdbc.Driver");
		    config.addDataSourceProperty("serverName", "jdbc:mysql://173.194.247.241");
		    config.addDataSourceProperty("port", "3306");
		    config.addDataSourceProperty("databaseName", "loc_db");
		    config.addDataSourceProperty("user", "shervin");
		    config.addDataSourceProperty("password", "123456");

		    System.out.println("config done.");

		    HikariDataSource ds = new HikariDataSource(config);
		    //ds.setConnectionTimeout(800);
		    return ds;
	 	}
	 
	    public static void setupDriver(String connectURI) throws Exception {
	        //
	        // First, we'll create a ConnectionFactory that the
	        // pool will use to create Connections.
	        // We'll use the DriverManagerConnectionFactory,
	        // using the connect string passed in the command line
	        // arguments.
	        //
	        ConnectionFactory connectionFactory =
	            new DriverManagerConnectionFactory(connectURI,"shervin","123456");

	        //
	        // Next, we'll create the PoolableConnectionFactory, which wraps
	        // the "real" Connections created by the ConnectionFactory with
	        // the classes that implement the pooling functionality.
	        //
	        PoolableConnectionFactory poolableConnectionFactory =
	            new PoolableConnectionFactory(connectionFactory, null);
	        
	        //
	        // Now we'll need a ObjectPool that serves as the
	        // actual pool of connections.
	        //
	        // We'll use a GenericObjectPool instance, although
	        // any ObjectPool implementation will suffice.
	        //
	        ObjectPool<PoolableConnection> connectionPool =
	            new GenericObjectPool<>(poolableConnectionFactory);
	        
	        // Set the factory's pool property to the owning pool
	        poolableConnectionFactory.setPool(connectionPool);

	        //
	        // Finally, we create the PoolingDriver itself...
	        //
	        Class.forName("org.apache.commons.dbcp2.PoolingDriver");
	        PoolingDriver driver = (PoolingDriver) DriverManager.getDriver("jdbc:apache:commons:dbcp:");

	        //
	        // ...and register our pool with it.
	        //
	        driver.registerPool("example",connectionPool);

	        //
	        // Now we can just use the connect string "jdbc:apache:commons:dbcp:example"
	        // to access our pool of Connections.
	        //
	    }

	    public static void printDriverStats() throws Exception {
	        PoolingDriver driver = (PoolingDriver) DriverManager.getDriver("jdbc:apache:commons:dbcp:");
	        ObjectPool<? extends Connection> connectionPool = driver.getConnectionPool("example");

	        System.out.println("NumActive: " + connectionPool.getNumActive());
	        System.out.println("NumIdle: " + connectionPool.getNumIdle());
	    }

	    public static void shutdownDriver() throws Exception {
	        PoolingDriver driver = (PoolingDriver) DriverManager.getDriver("jdbc:apache:commons:dbcp:");
	        driver.closePool("example");
	    }*/
	
}

	
