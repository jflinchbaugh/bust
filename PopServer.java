/* PopServer
 * main server application.  accepts connections and spawns threads
 */
import java.net.*;
import java.io.*;
import java.util.*;

public class PopServer {
	// time limit per round in milliseconds
	private static final int TIMELIMIT=300000;

	public static void main(String args[]) {
		ServerSocket server;
		Socket client;
		BufferedReader in;
		PrintWriter out;

		// shared data between threads
		ServerData serverData=new ServerData();

		try {
			// start timer
			PopTimer timer=new PopTimer(serverData,TIMELIMIT);
			Thread t=new Thread(timer);
			t.start();

			// bind the socket
			server=new ServerSocket(31415);

			// loop waiting for connections
			while (true) {
				client=server.accept();
				PopServerRun runner=new PopServerRun(client,
					serverData);
				t=new Thread(runner);
				t.start();
			}
		} catch (Exception e) {
			System.out.println(e);
		}
	}
}
