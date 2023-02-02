/* PopClientRun class
 * this thread listens for messages and uses the callback of
 * MessageListener interface
 */
import java.net.*;
import java.io.*;

public class PopClientRun implements Runnable {
	private Socket client;		// socket to read/write
	private boolean running;	// thread control
	private boolean suspended;	// thread control
	private String host;		// host name of server
	private BufferedReader in;	// input reader on socket
	private PrintWriter out;	// output writer on socket
	private MessageListener listener;	// listener with callback

	public PopClientRun(String h,MessageListener l) {
		host=h;
		running=true;
		suspended=true;
		listener=l;

		// connect client socket
		try {
			client=new Socket(host,31415);
			in=new BufferedReader(new InputStreamReader(
				client.getInputStream()));
			out=new PrintWriter(client.getOutputStream());
		} catch (Exception e) {
			// return error to callback if no connection
			listener.messageHandler("No server");
		}
	}

	public void run() {
		String message;
		try {
			// while running and can read
			while (running && (message=in.readLine())!=null) {
				try {
					// thread control
					while (suspended && running) {
						synchronized (this) {
							wait();
						}
					} 
					if (!running) break;

					// send message back to listener
					if (listener!=null)
						listener.messageHandler(message);

				} catch (Exception e) {
				}
			}
			// close out everything on exit of thread
			in.close();
			out.close();
			client.close();
			// send error if we dropped out even while still running
			if (running) listener.messageHandler("No server");
		} catch (Exception e) {
		}
	}

	public void stop() {
		running=false;
		synchronized (this) {
			notify();
		}
	}

	public void suspend() {
		suspended=true;
	}

	public void resume() {
		suspended=false;
		synchronized (this) {
			notify();
		}
	}

	public void start() {
		resume();
	}

	// send message out the socket
	public void say(String m) {
		try {
			out.println(m);
			out.flush();
		} catch (Exception e) {
			listener.messageHandler("No server");
		}
	}

	// register the message listener
	public void addListener(MessageListener m) {
		listener=m;
	}
}
