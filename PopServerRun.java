/* PopServerRun
 * runnable to service individual connections to the server
 */
import java.net.*;
import java.io.*;
import java.util.*;

class PopServerRun implements Runnable {
	// values for scoring
	private final int DROP_VALUE=10;
	private final int GAMEOVER_VALUE=-100;
	private final int REQUEST_VALUE=1;

	private Socket client;
	private ServerData data;
	private String login;
	private BufferedReader in;
	private PrintWriter out;
	private int score;

	public PopServerRun(Socket s, ServerData d) {
		data=d;
		client=s;
		login=null;
		score=0;
	}

	public void run() {
		in=null;
		out=null;

		try {
			// initialize io on client socket
			in=new BufferedReader(new InputStreamReader(
				client.getInputStream()));
			out=new PrintWriter(client.getOutputStream());
			
			String s;

			// while we can read
			while ((s=in.readLine())!=null) {
				System.out.println(login+": "+s);

				// take and verify a login, sending back a response
				if (login==null && s.startsWith("Login ")) {
					login=s.substring(6,s.length());
					// check for successful login
					if (data.registerLogin(login)) {
						data.addConnection(this);
						data.saveScores();
						out.println("Welcome");
						StringBuffer players=new
							StringBuffer("Players");
						Enumeration logins=data.getLoginsEnum();
						while (logins.hasMoreElements())
							players.append(" "+logins.nextElement());
						out.println(players.toString());

						// broadcast login to all players
						sayToAll(s);
					} else {	// failed to login
						out.println("Login Taken");
						login=null;
					}
					out.flush();

				// client sent gameover
				} else if (s.equals("Game over")) {
					// broadcast to all players
					sayToAll("Game over "+login);
					adjustScore(GAMEOVER_VALUE);
					data.saveScores();

				// client dropped balls
				} else if (s.startsWith("Dropped ")) {
					int number=0;

					// read number dropped
					try {
						number=Integer.parseInt(
							s.substring(8,s.length()));
					} catch (Exception e) {
						e.printStackTrace();
					}

					// build message with random balls
					StringBuffer message=new StringBuffer("Add");
					message.append(" "+login);
					for (int x=0;x<number;x++) {
						message.append(" "+data.getRandomBall());
					}
					
					// broadcast message to all players
					sayToAll(message.toString());

					adjustScore(DROP_VALUE*number);
					data.saveScores();

				// client requests a ball
				} else if (s.startsWith("Request ")) {
					int n=0;
					try {
						n=Integer.parseInt(s.substring(8,s.length()));
					} catch (Exception e) {
						e.printStackTrace();
					}
					say("Ball "+data.getBallAt(n));
					adjustScore(REQUEST_VALUE);
					data.saveScores();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		// when the client falls out of the above loop, unregister
		if (login!=null) {
			data.unregisterLogin(login);
			data.removeConnection(this);
			// broadcast client logout to all players
			sayToAll("Logout "+login);
			data.saveScores();
		}

		// close connections
		try {
			in.close();
			out.close();
			client.close();
		} catch (Exception e) {
			System.out.println(e);
		}
	}

	// send message to this client
	public void say(String m) {
		out.println(m);
		out.flush();
	}

	// broadcast messages to all clients
	public void sayToAll(String m) {
		Enumeration conns=data.getConnectionsEnum();

		// loop through the enum of connections
		while (conns.hasMoreElements()) {
			PopServerRun nextConnection=
				(PopServerRun)conns.nextElement();
			if (nextConnection!=this) {
				nextConnection.say(m);
			}
		}
	}

	// restart the round, called by the timer thread
	public void restart() {
		
		// build message with logins and scores
		StringBuffer message=new StringBuffer("Restart");
		Enumeration conns=data.getScoresEnum();
		while (conns.hasMoreElements()) {
			PopServerRun nextConnection=
				(PopServerRun)conns.nextElement();
			message.append(" "+nextConnection.getLogin()+
				" "+nextConnection.getScore());;
		}
		say(message.toString());
	}

	// return score for this connection
	public int getScore() {
		return score;
	}

	// adjust the score for this connection
	public void adjustScore(int x) {
		score+=x;
	}

	// set score for this connection
	public void setScore(int x) {
		score=x;
	}
	
	// get login name for this connection
	public String getLogin() {
		return login;
	}

	// report time left to the client
	public void reportTime(long t) {
		say("Time "+t/1000);
	}
}
