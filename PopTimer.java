/* PopTimer
 * timer thread used by server to signal ends of rounds
 */
import java.net.*;
import java.io.*;
import java.util.*;

public class PopTimer implements Runnable {
	private boolean running=true;
	private boolean suspended=false;
	private ServerData data;	// shared server data
	private int timelimit;

	public PopTimer(ServerData s, int t) {
		timelimit=t;
		data=s;
	}

	// timer
	public void run() {
		while (running) {
			while (suspended) {
				synchronized (this) {
					try {
						wait();
					} catch (Exception e) {
					}
				}
			}
			if (!running) break;

			long startTime=System.currentTimeMillis();
			long stopTime=startTime+timelimit;

			while (System.currentTimeMillis()<stopTime) {
				// sleep while game runs
				try {
					Thread.sleep(5000);
				} catch (Exception e) {
				}

				Enumeration conns=data.getConnectionsEnum();
				while (conns.hasMoreElements()) {
					((PopServerRun)conns.nextElement()).reportTime(
						stopTime-System.currentTimeMillis());
				}
			}
			
			// restart the ball queue
			data.restartQueue();

			// save scores to file
			data.saveScores();

			// send restart to everyone
			Enumeration conns=data.getConnectionsEnum();
			while (conns.hasMoreElements()) {
				((PopServerRun)conns.nextElement()).restart();
			}
			conns=data.getConnectionsEnum();
			while (conns.hasMoreElements()) {
				((PopServerRun)conns.nextElement()).setScore(0);
			}
		}
	}
}
