package ssd8.socket.http;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Class <em>HttpClient</em> is a class representing a simple HTTP client.
 *
 * @author wben
 */

public class HttpClient {
	/**
	 * Allow a maximum buffer size of 8192 bytes
	 */
	private static int buffer_size = 8192;

	/**
	 * My socket to the world.
	 */
	Socket socket = null;

	/**
	 * Default port is 80.
	 */
	private static final int PORT = 80;

	/**
	 * Output stream to the socket.
	 */
	BufferedOutputStream ostream = null;

	/**
	 * Input stream from the socket.
	 */
	BufferedInputStream istream = null;

	/**
	 * StringBuffer storing the header
	 */
	private StringBuffer header = null;

	/**
	 * StringBuffer storing the response.
	 */
	private StringBuffer response = null;

	/**
	 * host storing the server host
	 */
	private String host = null;

	/**
	 * String to represent the Carriage Return and Line Feed character sequence.
	 */
	static private String CRLF = "\r\n";

	/**
	 * HttpClient constructor;
	 */
	public HttpClient() {
		header = new StringBuffer();
		response = new StringBuffer();
	}

	/**
	 * <em>connect</em> connects to the input host on the default http port --
	 * port 80. This function opens the socket and creates the input and output
	 * streams used for communication.
	 */
	public void connect(String host) throws Exception {

		this.host = host;

		/**
		 * Open my socket to the specified host at the default port.
		 */
		socket = new Socket(host, PORT); // www.51mx.com:80

		/**
		 * Create the output stream.
		 */
		ostream = new BufferedOutputStream(socket.getOutputStream());

		/**
		 * Create the input stream.
		 */
		istream = new BufferedInputStream(socket.getInputStream());
	}

	/**
	 * <em>processGetRequest</em> process the input GET request.
	 */
	public void processGetRequest(String request) throws Exception {
		/**
		 * Send the request to the server.
		 */
		request += CRLF + "Host: " + host + CRLF;
		request += "Connection: Close" + CRLF + CRLF;// two CRLF means the end of the header
		byte[] buffer = new byte[buffer_size];
		buffer = request.getBytes();
		ostream.write(buffer, 0, request.length());
		ostream.flush();
		/**
		 * waiting for the response.
		 */
		processResponse();
	}

	/**
	 * <em>processPutRequest</em> process the input PUT request.
	 */
	public void processPutRequest(String request) throws Exception {
		//=======start your job here============//
		/**
		 * Judge if the file is existed and is a file
		 * and append some information to the request
		 */
		String[] parameters = request.split(" ");
		String fileName = parameters[1];
		File putFile = new File(fileName);
		if (! (putFile.exists() && putFile.isFile())){
			System.out.println("file not exist or file " + fileName + " isn't a file type");
			return;
		}
		request += CRLF + "Host: " + host + CRLF;
		request += "Content-Length: " + putFile.length() + CRLF + CRLF; // two CRLF indicate the end of the header

		/**
		 * Send request to the server
		 */
		byte[] buffer;
		buffer = request.getBytes();
		ostream.write(buffer,0, request.length());

		/**
		 * Write putFile to server by ostream
		 */
		BufferedInputStream bis = new BufferedInputStream(new FileInputStream(putFile));
		buffer = new byte[buffer_size];
        while (bis.read(buffer) > 0){
        	ostream.write(buffer,0,buffer.length);
		}
		ostream.flush();
        bis.close();

		/**
		 * waiting for the response.
		 */
        processResponse();
		//=======end of your job============//
	}

	/**
	 * <em>processResponse</em> process the server response.
	 *
	 */
	public void processResponse() throws Exception {
		int last = 0, c = 0;
		/**
		 * Process the header and add it to the header StringBuffer.
		 */
		boolean inHeader = true; // loop control
		while (inHeader && ((c = istream.read()) != -1)) {
			switch (c) {
			case '\r':
				break;
			case '\n':
				if (c == last) {
					inHeader = false;
					break;
				}
				last = c;
				header.append("\n");
				break;
			default:
				last = c;
				header.append((char) c);
			}
		}

		/**
		 * Read the contents and add it to the response StringBuffer.
		 */
		byte[] buffer = new byte[buffer_size];
		while (istream.read(buffer) != -1) {
			response.append(new String(buffer, StandardCharsets.ISO_8859_1));
		}
	}

	/**
	 * Get the response header.
	 */
	public String getHeader() {
		return header.toString();
	}

	/**
	 * Get the server's response.
	 */
	public String getResponse() {
		return response.toString();
	}

	/**
	 * Close all open connections -- sockets and streams.
	 */
	public void close() throws Exception {
		socket.close();
		istream.close();
		ostream.close();
	}
}
