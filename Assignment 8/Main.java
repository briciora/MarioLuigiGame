import java.net.*;
import java.io.*;
import java.util.Date;
import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;


class Main
{
	static long clientcount = 0;
	static double mx = 100;
	static double my = 400;
	static double lx = 200;
	static double ly = 400;


	static String getServerTime()
	{
		Calendar calendar = Calendar.getInstance();
		SimpleDateFormat dateFormat = new SimpleDateFormat(
			"EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		return dateFormat.format(calendar.getTime());
	}

	static void sendLine(PrintWriter out, String line)
	{
		out.print(line); // Send over the socket
		out.print("\r\n");
		//System.out.println(line); // Print it to the console too, just to make debugging easier
	}

	static void onGet(OutputStream os, String url) throws Exception
	{
		PrintWriter out = new PrintWriter(os, true);
		String filename = url.substring(1); // cut off the initial "/"
		File f = new File(filename);
		Path path = Paths.get(filename);
		String dateString = getServerTime();
		//System.out.println("----------The server replied: ----------");
		if(f.exists() && !f.isDirectory())
		{
			// Read the file from disk
			byte[] fileContents = Files.readAllBytes(path);

			// Send the headers
			sendLine(out, "HTTP/1.1 200 OK");
			sendLine(out, "Content-Type: " + Files.probeContentType(path));
			sendLine(out, "Content-Length: " + Integer.toString(fileContents.length));
			sendLine(out, "Date: " + dateString);
			sendLine(out, "Last-Modified: " + dateString);
			sendLine(out, "Connection: close");
			sendLine(out, "");
			out.flush();

			// Send the payload
			os.write(fileContents);
			String blobHead = fileContents.length < 60 ? new String(fileContents) : new String(fileContents, 0, 60) + "...";
			//System.out.println(blobHead);
		}
		else
		{
			// Make an error message
			String payload = "404 - File not found: " + filename;

			// Send HTTP headers
			sendLine(out, "HTTP/1.1 200 OK");
			sendLine(out, "Content-Type: text/html");
			sendLine(out, "Content-Length: " + Integer.toString(payload.length()));
			sendLine(out, "Date: " + dateString);
			sendLine(out, "Last-Modified: " + dateString);
			sendLine(out, "Connection: close");
			sendLine(out, "");

			// Send the payload
			sendLine(out, payload);
		}
	}

	//recieves things from the client 
	static void onPost(OutputStream os, String url, char[] incomingPayload)
	{
		// Parse the incoming payload
 		//System.out.println("----------------------------------------");
		String payload = String.valueOf(incomingPayload);
		//System.out.println("Received the following payload: " + payload);
		Json incoming = Json.parse(payload);

		long clientid = incoming.getLong("id");

		Json outgoing = Json.newObject();

		if(clientid < 0)
		{
			outgoing.add("id", clientcount);
		   	outgoing.add("mx", mx);
            outgoing.add("my", my);
			outgoing.add("lx", lx);
            outgoing.add("ly", ly);
			clientcount++;
		}
		else if(clientid == 0)
		{
			mx = incoming.getDouble("mx");
            my = incoming.getDouble("my");
			
			outgoing.add("id", clientid);
            outgoing.add("lx", lx);
            outgoing.add("ly", ly);
		}
		else if(clientid == 1)
		{
			lx = incoming.getDouble("lx");
            ly = incoming.getDouble("ly");

			outgoing.add("id", clientid);
            outgoing.add("mx", mx);
            outgoing.add("my", my);
		}

		// Make a response
		String response = outgoing.toString();

		// Send HTTP headers
		//System.out.println("----------The server replied: ----------");
		String dateString = getServerTime();
		PrintWriter out = new PrintWriter(os, true);
		sendLine(out, "HTTP/1.1 200 OK");
		sendLine(out, "Content-Type: application/json");
		sendLine(out, "Content-Length: " + Integer.toString(response.length()));
		sendLine(out, "Date: " + dateString);
		sendLine(out, "Last-Modified: " + dateString);
		sendLine(out, "Connection: close");
		sendLine(out, "");
		
		// Send the response
		sendLine(out, response);
		out.flush();
	}

	//client = browser
	//sever = java program

	public static void main(String[] args) throws Exception
	{
		// Make a socket to listen for clients
		int port = 1234; //number that facilitates communication, can be any number
		String ServerURL = "http://localhost:" + Integer.toString(port) + "/MarioLuigi.html"; //local host = url of the computer you are on
		ServerSocket serverSocket = new ServerSocket(port); //can send stuff over a socket, like a telephone

		// Start the (2) web browsers
		if(Desktop.isDesktopSupported())
		{
			//opens 2 tabs instead of one when you duplicate this line
			Desktop.getDesktop().browse(new URI(ServerURL));
			Desktop.getDesktop().browse(new URI(ServerURL));
		}
		else
			System.out.println("Please direct your browser to " + ServerURL);

		// Handle requests from clients
		while(true)
		{
			Socket clientSocket = serverSocket.accept(); // This call blocks until a client connects
			BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); //processes messages that arrive
			OutputStream os = clientSocket.getOutputStream(); 

			// Read the HTTP headers
			String headerLine;
			int requestType = 0;
			int contentLength = 0;
			String url = "";
			//System.out.println("----------A client said: ----------");
			while ((headerLine = in.readLine()) != null)
			{
				//System.out.println(headerLine);
				if(headerLine.length() > 3 && headerLine.substring(0, 4).equals("GET "))
				{
					requestType = 1;
					url = headerLine.substring(4, headerLine.indexOf(" ", 4));
				}
				else if(headerLine.length() > 4 && headerLine.substring(0, 5).equals("POST "))
				{
					requestType = 2;
					url = headerLine.substring(5, headerLine.indexOf(" ", 5));
				}
				else if(headerLine.length() > 15 && headerLine.substring(0, 16).equals("Content-Length: "))
					contentLength = Integer.parseInt(headerLine.substring(16));
				if(headerLine.length() < 2) // Headers are terminated by a "\r\n" line
					break;
			}

			// Send a response
			if(requestType == 1)
			{
				onGet(os, url);
			}
			else if(requestType == 2)
			{
				// Read the incoming payload
				char[] incomingPayload = new char[contentLength];
				in.read(incomingPayload, 0, contentLength);
				String blobHead = incomingPayload.length < 60 ? new String(incomingPayload) : new String(incomingPayload, 0, 60) + "...";
				//System.out.println(blobHead);
				onPost(os, url, incomingPayload);
			}
			else
				System.out.println("Received bad headers. Ignoring.");

			// Hang up
			os.flush();
			clientSocket.close();
		}
	}
}
