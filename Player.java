/* Player class
 * tracks information about each player in the applet
 */
public class Player {
	private String name;		// player name
	private int score;			// player score
	private String message;		// message to be sent to clients

	public Player() {
		this("");
	}
	
	public Player(String n) {
		this(n,0,"");
	}

	public Player(String n,int s,String m) {
		name=n;
		score=s;
		message=m;
	}

	public synchronized void setName(String n) {
		name=n;
	}
	public synchronized void setScore(int s) {
		score=s;
	}
	public synchronized void setMessage(String m) {
		message=m;
	}

	public synchronized String getName() {
		return new String(name);
	}
	public synchronized int getScore() {
		return score;
	}
	public synchronized String getMessage() {
		return new String(message);
	}

	public boolean equals(Object p) {
		return name.equals(((Player)p).getName());
	}
}
