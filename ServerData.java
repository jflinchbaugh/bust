/* ServerData
 * shared data between server connections
 */
import java.util.*;
import java.io.*;

public class ServerData {
	private Vector logins;			// login names
	private Vector connections;		// PopServerRun connections
	private Vector ballQueue;		// the queue of balls sent out
	private Random gen;				// random number generator

	public ServerData() {
		gen=new Random();
		logins=new Vector();
		connections=new Vector();
		restartQueue();
	}

	// initialize the ball queue
	public void restartQueue() {
		ballQueue=new Vector();
		fillQueue();
	}

	// register a login
	public synchronized boolean registerLogin(String login) {
		boolean ret=false;
		if (login!=null) {
			// check if login already in list
			if (!login.equals("") && !logins.contains(login) &&
				login.indexOf(' ')==-1) {
				logins.addElement(login);
				ret=true;
				System.out.println("registering "+login);
			}
		}
		return ret;
	}

	// remove a login 
	public synchronized void unregisterLogin(String login) {
		logins.removeElement(login);
		System.out.println("unregistering "+login);
	}

	// get an enum of logins for iteration
	public Enumeration getLoginsEnum() {
		return logins.elements();
	}

	// add a connection (PopServerRun) to the connections vector
	public void addConnection(PopServerRun c) {
		connections.addElement(c);
	}

	// remove a connection from the vector
	public void removeConnection(PopServerRun c) {
		connections.removeElement(c);
	}

	// return an enum of connections for iteration
	public Enumeration getConnectionsEnum() {
		return connections.elements();
	}

	// fill the ball queue or expand the ball queue by 200 random balls
	public void fillQueue() {
		for (int x=0;x<200;x++) {
			ballQueue.addElement(
				new Integer(getRandomBall()));
		}
	}

	// return ball type at a point in the queue
	public int getBallAt(int b) {
		if (b>=ballQueue.size()) {
			fillQueue();
		}
		return ((Integer)ballQueue.elementAt(b)).intValue();
	}

	// return a random ball type
	public int getRandomBall() {
		return (int)(gen.nextFloat()*7.0);
	}

	// return enum of sorted scores from all connections
	public Enumeration getScoresEnum() {
		return getSortedConnections().elements();
	}

	public Vector getSortedConnections() {
		Vector scores=(Vector)connections.clone();
		for (int x=0;x<scores.size();x++) {
			for (int y=x+1;y<scores.size();y++) {
				if (((PopServerRun)scores.elementAt(x)).getScore() <
					((PopServerRun)scores.elementAt(y)).getScore()) {
					PopServerRun temp=(PopServerRun)
						scores.elementAt(x);
					scores.setElementAt(scores.elementAt(y),x);
					scores.setElementAt(temp,y);
				}
			}
		}
		return scores;
	}

	// save scores to a file
	public synchronized void saveScores() {
		try {
			PrintWriter out=new PrintWriter(
				new FileOutputStream("scores"));
			Enumeration scores=getSortedConnections().elements();
			while (scores.hasMoreElements()) {
				PopServerRun conn=(PopServerRun)scores.nextElement();
				out.println(conn.getLogin()+" "+conn.getScore());
			}
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
