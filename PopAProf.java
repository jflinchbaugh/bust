/* PopAProf class
 * main game applet class
 */
import java.applet.*;
import java.awt.List;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.Enumeration;
import java.util.Random;

public class PopAProf extends Applet
	implements Runnable,KeyListener,MouseListener,MessageListener,
	WindowListener {
	// constant game parameters
	private final int MAX_ANGLE=30;			// steps for pointer
	private final int MIN_ANGLE=-30;
	private final int STICKYNESS=8;			// radius for collision
	private final double SHOOTSPEED=15;		// increment for shot ball
	private final double DROPSPEED=20;		// inc for dropping ball
	private final boolean REDIRECT=false;	// mid-air redirection

	private boolean gameOver;		// game over flag
	private Vector dropping;		// vector of dropping balls
	private Vector rising;			// vector of rising balls
	private Ball current;			// current ball being shot
	private int swingSpeed;			// speed of pointer movement
	private Image arrows[];			// array of pointer images
	private Ball grid[][];			// grid of planted balls
	private Image ball[];			// array of ball types
	private Image offImg;			// image for double buffering
	private Image gameOverImg;		// game over graphic
	private int angle=0;			// angle of the pointer
	private Rotater rot;			// rotater for generating pointers
	private boolean running=true;	// thread control
	private boolean suspended=true;	// thread control
	private Painter painter;		// painter thread
	private PopClientRun client;	// communications client
	private String login;			// login name
	private Boolean loginOk;		// login accepted flag
	private int nextRise;			// position to rise next ball
	private int ballOffset;			// offset into the list of balls
	private Ball nextBall;			// next ball to shoot
	private Vector players;			// other players in game
	private List status;			// jfc list for displaying players
	private Frame info;				// info window for status
	private LoginWindow loginWindow;// login dialog at beginning
	private AudioClip addSnd;		// sound: add balls
	private AudioClip dropSnd;		// sound: drop balls
	private AudioClip gameOverSnd;	// sound: game over
	private AudioClip readySnd;		// sound: ready....
	private AudioClip goSnd;		// sound: go!
	private AudioClip plantSnd;		// sound: plant a ball
	private AudioClip timeUpSnd;	// sound: end round, time up
	private AudioClip promptSnd;	// sound: login prompt
	private AudioClip popSnd;		// sound: pop balls
	private AudioClip otherOverSnd;	// sound: other players' gameover
	private TextField timeLeft;
	
	public void init() {
		// init vectors
		dropping=new Vector();
		rising=new Vector();
		players=new Vector();

		// load theme from parameters, default to "mucs"
		String theme=getParameter("theme");
		if (theme==null) theme=new String("mucs");
			
		nextRise=0;
		status=new List(15,true);
		ball=new Image[7];

		// load initial arrow image
		Image arrow;
		arrows=new Image[MAX_ANGLE-MIN_ANGLE+1];

		// MediaTracker for all images
		MediaTracker tracker=new MediaTracker(this);

		// request and add ball images to tracker
		for (int x=0; x<ball.length; x++) {
			ball[x]=getImage(getDocumentBase(),theme+"/"+x+".gif");
			tracker.addImage(ball[x],x);
		}

		// request arrow image
		arrow=getImage(getDocumentBase(),theme+"/arrow.gif");
		tracker.addImage(arrow,7);

		// request gameover image
		gameOverImg=getImage(getDocumentBase(),theme+"/gameover.gif");
		tracker.addImage(gameOverImg,8);

		// load sounds while images wait
		addSnd=getAudioClip(getDocumentBase(),theme+"/add.au");
		dropSnd=getAudioClip(getDocumentBase(),theme+"/drop.au");
		gameOverSnd=getAudioClip(getDocumentBase(),theme+
			"/gameover.au");
		goSnd=getAudioClip(getDocumentBase(),theme+"/go.au");
		plantSnd=getAudioClip(getDocumentBase(),theme+"/plant.au");
		readySnd=getAudioClip(getDocumentBase(),theme+"/ready.au");
		timeUpSnd=getAudioClip(getDocumentBase(),theme+"/timeup.au");
		promptSnd=getAudioClip(getDocumentBase(),theme+"/prompt.au");
		popSnd=getAudioClip(getDocumentBase(),theme+"/pop.au");
		otherOverSnd=getAudioClip(getDocumentBase(),
			theme+"/otherover.au");

		try {
			tracker.waitForAll();		// wait for images
		} catch (InterruptedException e) {
			System.out.println(e);
		}
		
		// set applet background to black
		setBackground(Color.black);

		// create an offscreen image for double buffering
		offImg=createImage(getSize().width,getSize().height);

		// create my rotated pointers
		rot=new Rotater(arrow,28,65,true);
		for (int x=MIN_ANGLE;x<=MAX_ANGLE;x++) {
			arrows[x-MIN_ANGLE]=rot.rotate(Math.PI*x/
				(MAX_ANGLE-MIN_ANGLE));
		}

		// create and start communications client
		client=new PopClientRun(getCodeBase().getHost(),this);
		Thread ct=new Thread(client);
		ct.start();
		client.start();

		// call LoginWindow to get login from player
		Frame f=new Frame();	// dummy frame just for dialog
		do {
			promptSnd.play();
			loginWindow=new LoginWindow(f);
			login=loginWindow.getLogin();
		} while (!verifyLogin());	// loop until login is good

		// initialize a new game
		newGame();

		// start painter thread
		painter=new Painter(this,100);
		Thread pt=new Thread(painter);
		pt.start();

		// start updater thread from this class
		Thread updater=new Thread(this);
		updater.start();

		requestFocus();

		// add listeners
		addKeyListener(this);
		addMouseListener(this);

		// set time left before server tells us
		timeLeft=new TextField("0",5);
		timeLeft.setEditable(false);

		// time left label
		Label timeLeftLabel=new Label("Time Left:");

		// top panel for info
		Panel topInfo=new Panel();
		topInfo.add(timeLeftLabel);
		topInfo.add(timeLeft);

		// pop up the info frame with player status
		info=new Frame("Info");
		info.add(BorderLayout.NORTH,topInfo);
		info.add(BorderLayout.CENTER,status);
		info.addWindowListener(this);
		info.pack();
		info.setVisible(true);

		requestFocus();
	}

	public void destroy() {
		running=false;
		synchronized (this) {
			notify();
		}
		info.setVisible(false);
		info.dispose();
		loginWindow.setVisible(false);
		loginWindow.dispose();
		painter.stop();
		client.stop();
	}

	public void stop() {
		suspended=true;
		painter.suspend();
		client.suspend();
	}

	public void start() {
		suspended=false;
		synchronized(this) {
			notify();
		}
		painter.start();
		client.start();
	}

	// initialize a new game
	public void newGame() {
		nextBall=null;
		ballOffset=0;	// start rising balls on left
		dropping.removeAllElements();	// clear dropping balls
		rising.removeAllElements();		// clear rising balls
		// init grid
		grid=new Ball[getSize().width/50]
			[(int)(getSize().height/(25*Math.sqrt(3)))];
		swingSpeed=1;		// set initial pointer swing to 1
		gameOver=false;		// game not over

		// get 2 balls from server, current and next
		requestNextBall();
		requestNextBall();

		// play start sounds
		readySnd.play();
		try {
			Thread.sleep(2000);
		} catch (Exception e) {
		}
		goSnd.play();

		requestFocus();
	}

	// translate real coords to grid coords
	public int getGridY(double x,double y) {
		int gridy=(int)(y/(25*Math.sqrt(3)));
		if (gridy<0) gridy=0;
		if (gridy>grid[0].length) gridy=grid[0].length-1;
		return gridy;
	}
		
	// translate real coords to grid coords
	public int getGridX(double x,double y) {
		int gridx;
		int gridy=getGridY(x,y);
		if (gridy%2==1) {
			gridx=(int)((x-25)/50);
			if (gridx<0) gridx=0;
			if (gridx>grid.length-2) gridx=grid.length-2;
		} else {
			gridx=(int)((x)/50);
			if (gridx<0) gridx=0;
			if (gridx>grid.length-1) gridx=grid.length-1;
		}
		return gridx;
	}

	// translate grid coords to real coords
	public double getRealY(int x,int y) {
		return y*25.0*Math.sqrt(3);
	}

	// translate grid coords to real coords
	public double getRealX(int x,int y) {
		double ret;
		
		if (y%2==1) {
			ret=x*50.0+25;
		} else {
			ret=x*50.0;
		}
		return ret;
	}

	// translate real coords to real coords, but snapped to grid
	public double getRealY(double x,double y) {
		return getGridY(x,y)*25.0*Math.sqrt(3);
	}

	// translate real coords to real coords, but snapped to grid
	public double getRealX(double x,double y) {
		double ret;
		
		if (getGridY(x,y)%2==1) {
			ret=getGridX(x,y)*50.0+25;
		} else {
			ret=getGridX(x,y)*50.0;
		}
		return ret;
	}

	// run for updater thread.  this moves all the balls in motion
	public void run() {
		while (running) {
			// thread control
			synchronized (this) {
				while (suspended && running) {
					try {
						wait();
					} catch (InterruptedException e) {
					}
				}
			}
			if (!running) break;	// stopped while suspended

			// move dropping balls
			Enumeration enum=dropping.elements();
			while (enum.hasMoreElements()) {
				Ball b=(Ball)enum.nextElement();
				b.x+=b.dx;
				b.y+=b.dy;

				if (b.y>getSize().height) dropping.removeElement(b);
			}

			// move rising balls
			enum=rising.elements();
			while (enum.hasMoreElements()) {
				Ball b=(Ball)enum.nextElement();
				b.x+=b.dx;
				b.y+=b.dy;

				if (hitSomething(b)) {
					rising.removeElement(b);
					plantBall(b);
					if (belowLine()) setGameOver();
				}
			}

			try {
				if (current.dx!=0 || current.dy!=0) {
					// bounce
					if (current.x<=0 ||
						current.x>=getSize().width-50) {
						current.dx=-current.dx;
					}

					try {
						if (hitSomething(current)) {
							plantBall(current);
							if (pop()) gravity();
							if (belowLine()) setGameOver();
							current=null;
							requestNextBall();
						}
					} catch (Exception e) {
					}
				}

				current.x+=current.dx;
				current.y+=current.dy;
			} catch (Exception e) {
			}

			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
			}
		}
	}

	// count connected balls of the same color
	public int countColors(int x,int y,Vector v) {
		int count=0;
		Ball here=grid[x][y];

		// init v if this is first call (v==null)
		if (v==null) v=new Vector();

		if (!v.contains(here)) {	// have not visited here yet
			count=1;
			v.addElement(here);		// add here to vector

			// recursively call countColors for balls surrounding
			// this one
			try {
				if (grid[x][y-1].type==here.type)
					count+=countColors(x,y-1,v);
			} catch (Exception e) {
			}
			try {
				if (grid[x][y+1].type==here.type)
					count+=countColors(x,y+1,v);
			} catch (Exception e) {
			}
			try {
				if (grid[x+1][y].type==here.type)
					count+=countColors(x+1,y,v);
			} catch (Exception e) {
			}
			try {
				if (grid[x-1][y].type==here.type)
					count+=countColors(x-1,y,v);
			} catch (Exception e) {
			}

			// checking the ball that shift depending upon row
			if (y%2==1) {
				try {
					if (grid[x+1][y-1].type==here.type)
						count+=countColors(x+1,y-1,v);
				} catch (Exception e) {
				}
				try {
					if (grid[x+1][y+1].type==here.type)
						count+=countColors(x+1,y+1,v);
				} catch (Exception e) {
				}
			} else {
				try {
					if (grid[x-1][y-1].type==here.type)
						count+=countColors(x-1,y-1,v);
				} catch (Exception e) {
				}
				try {
					if (grid[x-1][y+1].type==here.type)
						count+=countColors(x-1,y+1,v);
				} catch (Exception e) {
				}
			}
		}
		return count;
	}

	// check to see if any balls are in the last row below the line
	public boolean belowLine() {
		int bottomRow=grid[0].length-1;
		boolean ret=false;
		for (int x=0;x<grid.length;x++) {
			if (grid[x][bottomRow]!=null) {
				ret=true;
				break;
			}
		}
		return ret;
	}

	// set game over flag and send gameover to server
	public void setGameOver() {
		gameOver=true;
		client.say("Game over");
		gameOverSnd.play();
		updatePlayerMessage(login,"Game Over");
		try {
			Thread.sleep(10000);
		} catch (Exception e) {
		}
		newGame();
	}	

	// set downward velocity on any balls which aren't connected
	// to ceiling
	public void gravity() {
		Vector hanging=new Vector();
		int count=0;

		// traverse balls connected to top
		for (int x=0;x<grid.length;x++) {
			try {
				listConnected(x,0,hanging);
			} catch (Exception e) {
			}
		}
		
		// set downward velocity on anything not in hanging vector
		for (int x=0;x<grid.length;x++) {
			for (int y=0;y<grid[0].length;y++) {
				try {
					if (grid[x][y]!=null) {
						if (!hanging.contains(grid[x][y])) {
							grid[x][y].dy=DROPSPEED;
							dropping.addElement(grid[x][y]);
							grid[x][y]=null;
							count++;
						}
					}
				} catch (Exception e) {
				}

			}
		}
		// if any dropped, send it to the server
		if (count>0) {
			dropSnd.play();
			client.say("Dropped "+count);
			getAppletContext().showStatus("you sent "+count);
			updatePlayerMessage(login,"dropped "+count);
		}
	}

	// traverse all the balls connected to the given ball in grid
	public void listConnected(int x,int y, Vector hanging) {
		try {
			if (grid[x][y]!=null) {
				if (!hanging.contains(grid[x][y])) {
					hanging.addElement(grid[x][y]);
					listConnected(x,y-1,hanging);
					listConnected(x,y+1,hanging);
					listConnected(x+1,y,hanging);
					listConnected(x-1,y,hanging);
					if (y%2==1) {
						listConnected(x+1,y-1,hanging);
						listConnected(x+1,y+1,hanging);
					} else {
						listConnected(x-1,y-1,hanging);
						listConnected(x-1,y+1,hanging);
					}
				}
			}
		} catch (Exception e) {
		}
	}

	// traverse connected colors, this time removing the balls
	public void deleteFrom(int x,int y) {
		Ball here=grid[x][y];
		grid[x][y]=null;

		try {
			if (grid[x][y-1].type==here.type)
				deleteFrom(x,y-1);
		} catch (Exception e) {
		}
		try {
			if (grid[x][y+1].type==here.type)
				deleteFrom(x,y+1);
		} catch (Exception e) {
		}
		try {
			if (grid[x+1][y].type==here.type)
				deleteFrom(x+1,y);
		} catch (Exception e) {
		}
		try {
			if (grid[x-1][y].type==here.type)
				deleteFrom(x-1,y);
		} catch (Exception e) {
		}

		// process shifted balls according to line number
		if (y%2==1) {
			try {
				if (grid[x+1][y-1].type==here.type)
					deleteFrom(x+1,y-1);
			} catch (Exception e) {
			}
			try {
				if (grid[x+1][y+1].type==here.type)
					deleteFrom(x+1,y+1);
			} catch (Exception e) {
			}
		} else {
			try {
				if (grid[x-1][y-1].type==here.type)
					deleteFrom(x-1,y-1);
			} catch (Exception e) {
			}
			try {
				if (grid[x-1][y+1].type==here.type)
					deleteFrom(x-1,y+1);
			} catch (Exception e) {
			}
		}
	}

	// lock the given ball into its nearest grid position
	public synchronized void plantBall(Ball current) {
		// set velocities to 0
		current.dx=0;
		current.dy=0;

		// check the current location for a ball
		while (hitAnother(current)) {
			current.y+=SHOOTSPEED;	// shift ball down
		}

		// get real locations snapped to grid
		current.x=getRealX(current.x+25,current.y+25);
		current.y=getRealY(current.x+25,current.y+25);

		// set that grid spot to this ball
		grid[getGridX(current.x+25,current.y+25)]
			[getGridY(current.x+25,current.y+25)]=current;

		plantSnd.play();
	}

	// key event handler
	public void keyPressed(KeyEvent e) {
		switch(e.getKeyCode()) {
			case KeyEvent.VK_LEFT:	// swing left
				angle-=swingSpeed;
				break;
			case KeyEvent.VK_RIGHT:	// swing right
				angle+=swingSpeed;
				break;
			case KeyEvent.VK_SPACE:	// shoot
				if (REDIRECT || (current.dx==0 && current.dy==0)) {
					// set dx and dy according to pointer angle
					current.dy=-SHOOTSPEED*
						Math.cos(Math.PI*(double)angle/
							(double)(MAX_ANGLE-MIN_ANGLE));
					current.dx=SHOOTSPEED*
						Math.sin(Math.PI*(double)angle/
							(double)(MAX_ANGLE-MIN_ANGLE));
				}
				break;
			case KeyEvent.VK_SHIFT:	// set swing speed upto 4
				swingSpeed=4;
				break;
		}

		// clip pointer angle to 1 step above each horizontal
		if (angle>MAX_ANGLE-1) angle=MAX_ANGLE-1;
		if (angle<MIN_ANGLE+1) angle=MIN_ANGLE+1;

		Thread.yield();
	}

	public void keyReleased(KeyEvent e) {
		switch(e.getKeyCode()) {
			case KeyEvent.VK_SHIFT:	// set swing speed back to 1
				swingSpeed=1;
				break;
		}
	}

	public void keyTyped(KeyEvent e) {
	}

	public void paint(Graphics g) {
		g.clearRect(0,0,getSize().width,getSize().height);
		// draw the grid of balls
		for (int y=0; y<grid[0].length; y++) {
			for (int x=0; x<grid.length; x++) {
				if (grid[x][y]!=null) {
					try {
						int which=grid[x][y].type;
						g.drawImage(ball[which],(int)getRealX(x,y),
							(int)getRealY(x,y),this);
					} catch (Exception e) {
					}
				}
			}
		}

		// draw dropping balls
		Enumeration enum=dropping.elements();
		try {
			while (enum.hasMoreElements()) {
				Ball b=(Ball)enum.nextElement();
				g.drawImage(ball[b.type],(int)b.x,(int)b.y,this);
			}
		} catch (Exception e) {
		}

		// draw rising balls
		enum=rising.elements();
		try {
			while (enum.hasMoreElements()) {
				Ball b=(Ball)enum.nextElement();
				g.drawImage(ball[b.type],(int)b.x,(int)b.y,this);
			}
		} catch (Exception e) {
		}

		// draw the current ball
		try {
			g.drawImage(ball[current.type],(int)current.x,
				(int)current.y,this);
		} catch (Exception e) {
		}

		// draw the next ball
		try {
			g.drawImage(ball[nextBall.type],getSize().width-51,
				getSize().height-51,this);
		} catch (Exception e) {
		}
		g.setColor(Color.white);
		g.drawRect(getSize().width-53,getSize().height-53,52,52);

		//draw boundary line
		g.setColor(Color.white);
		g.drawLine(0,(int)getRealY(0.0,
			getSize().height-25*Math.sqrt(3)),
			getSize().width-1,(int)getRealY(0.0,
			getSize().height-25*Math.sqrt(3)));

		// draw the pointer
		try {
			g.drawImage(arrows[angle-MIN_ANGLE],130,401,this);
		} catch (Exception e) {
		}

		// draw the gameover graphic if game is over
		if (gameOver) {
			try {
				g.drawImage(gameOverImg,
					(getSize().width-gameOverImg.getWidth(this))/2,
					(getSize().height-gameOverImg.getHeight(this))/2,
					this);
			} catch (Exception e) {
			}
		}

		Thread.yield();
	}

	// diagnostic mouse event to show grid locations on click
	public void mouseClicked(MouseEvent e) {
		int x,y;
		x=e.getX();
		y=e.getY();
		System.out.println("("+x+","+y+") is ("+getGridX(x,y)+
			","+getGridY(x,y)+")");
	}

	public void mouseEntered(MouseEvent e) {
	}

	public void mouseExited(MouseEvent e) {
	}

	public void mousePressed(MouseEvent e) {
	}

	public void mouseReleased(MouseEvent e) {
	}

	public void update(Graphics g) {
		Graphics og=offImg.getGraphics();

		paint(og);
		g.drawImage(offImg,0,0,this);
		og.dispose();
	}

	// pop balls connected to current ball
	public boolean pop() {
		int count=countColors(getGridX(current.x+25,current.y+25),
				getGridY(current.x+25,current.y+25),null);
		if (count>=3) {
			popSnd.play();
			deleteFrom(getGridX(current.x+25,current.y+25),
				getGridY(current.x+25,current.y+25));
		}
		return count>=3;
	}

	// send ball request to server, response comes to client thread
	public void requestNextBall() {
		// request the next ball
		client.say("Request "+ballOffset);
		ballOffset++;
	}

	// check the loginOk flag, or wait for the client thread to set
	public boolean verifyLogin() {
		boolean ret;
		client.say("Login "+login);
		while (loginOk==null) {
			try {
				wait();
			} catch (Exception e) {
			}
		}
		ret=loginOk.booleanValue();
		loginOk=null;
		return ret;
	}

	// messageListener call back. client calls this for any message
	public void messageHandler(String message) {
		System.out.println("message received: "+message);

		// server could not be contacted
		if (message.equals("No server")) {
			Frame error=new Frame("Error");
			error.add(new Label("Server Communication Failed!"));
			error.pack();
			error.addWindowListener(this);
			error.setVisible(true);
			destroy();

		// login was accepted
		} else if (message.equals("Welcome")) {
			loginOk=new Boolean(true);
			synchronized (this) {
				notify();
			}

		// login already taken
		} else if (message.equals("Login Taken")) {
			loginOk=new Boolean(false);
			synchronized (this) {
				notify();
			}

		// someone sent balls to you
		} else if (message.startsWith("Add ")) {
			addSnd.play();
			int count=0;
			int space=message.indexOf(' ',4);
			String sender=message.substring(4,space);

			for (int x=space+1;x<message.length();x+=2) {
				int type=0;
				try {
					type=Integer.parseInt(
						message.substring(x,x+1));
				} catch (Exception e) {
					e.printStackTrace();
				}

				// create rising balls and add them to vector
				Ball b=new Ball(type,(double)nextUp(),
					(double)getSize().height-25.0*Math.sqrt(3),0.0,
					-(double)DROPSPEED);
				rising.addElement(b);
				count++;
			}

			// update messages for status
			updatePlayerMessage(sender,"sent "+count);

			// update the browser status bar
			if (!gameOver) {
				getAppletContext().showStatus(sender+" sent "+count);
			}

		// response to ball request
		} else if (message.startsWith("Ball ")) {
			int type=Integer.parseInt(message.substring(
				5,message.length()));

			// rotate ball from next to current, etc
			current=nextBall;
			nextBall=new Ball(type,175,446,0,0);

		// restart command, round ended.
		} else if (message.startsWith("Restart ")) {
			showScores(message.substring(8,message.length()));
			newGame();

		// players list, sent upon login
		} else if (message.startsWith("Players ")) {
			players.removeAllElements();
			StringTokenizer st=new StringTokenizer(message," ");
			st.nextToken();		// dispose of "Players "
			while (st.hasMoreTokens()) {
				String name=st.nextToken();
				addPlayer(name);
			}

		// a new player was added, add him to the players vector
		} else if (message.startsWith("Login ")) {
			addPlayer(message.substring(6,message.length()));

		// player logged out, remove from players vector
		} else if (message.startsWith("Logout ")) {
			removePlayer(message.substring(7,message.length()));

		// a players game ended, update status string
		} else if (message.startsWith("Game over ")) {
			String player=message.substring(10,message.length());
			updatePlayerMessage(player,"Game Over");
			otherOverSnd.play();

		// time left in game
		} else if (message.startsWith("Time ")) {
			timeLeft.setText(message.substring(5,message.length()));
		// any other message is unknown
		} else {
			System.out.println("Unknown message");
		}
	}

	// add player to players vector
	public void addPlayer(String p) {
		players.addElement(new Player(p));
		buildStatus();
	}

	// remove player from players vector
	public void removePlayer(String p) {
		players.removeElement(new Player(p));
		buildStatus();
	}
	
	// check for ceiling hit or ball hit
	public boolean hitSomething(Ball current) {
		return getGridY(current.x+25,current.y+25)==0
			|| hitAnother(current);
	}

	// check for ball hit
	public boolean hitAnother(Ball current) {
		return grid[getGridX(current.x+current.dx+25-STICKYNESS,
		current.y+current.dy+25-STICKYNESS)]
		[getGridY(current.x+current.dx+25-STICKYNESS,
		current.y+current.dy+25-STICKYNESS)]!=null
		||
		grid[getGridX(current.x+current.dx+
		25+STICKYNESS,
		current.y+current.dy+25-STICKYNESS)]
		[getGridY(current.x+current.dx+25+STICKYNESS,
		current.y+current.dy+25-STICKYNESS)]!=null
		||
		grid[getGridX(current.x+current.dx+
		25-STICKYNESS,
		current.y+current.dy+25+STICKYNESS)]
		[getGridY(current.x+current.dx+25-STICKYNESS,
		current.y+current.dy+25+STICKYNESS)]!=null
		||
		grid[getGridX(current.x+current.dx+
		25+STICKYNESS,
		current.y+current.dy+25+STICKYNESS)]
		[getGridY(current.x+current.dx+25+STICKYNESS,
		current.y+current.dy+25+STICKYNESS)]!=null;
	}

	// get next position to rise an added ball
	public int nextUp() {
		nextRise+=75;
		// wrap if at end
		if (nextRise>getSize().width-50)
			nextRise-=(getSize().width-25);
		return nextRise;
	}

	// round end sequence, pop up a window with scores
	public void showScores(String stats) {
		Frame win=new Frame("Scores");
		List l=new List(15);

		timeUpSnd.play();

		StringTokenizer st=new StringTokenizer(stats," ");
		while (st.hasMoreTokens()) {
			String entry=(String)st.nextToken()+" "+
				(String)st.nextToken();
			l.add(entry);
		}

		win.add(BorderLayout.CENTER,l);
		win.pack();
		win.addWindowListener(this);
		win.setVisible(true);
		try {
			Thread.sleep(10000);
		} catch (Exception e) {
		}
		win.setVisible(false);
		win.dispose();
	}

	public void windowActivated(WindowEvent e) {
	}

	public void windowClosed(WindowEvent e) {
	}

	public void windowClosing(WindowEvent e) {
		Window source=(Window)e.getSource();
		source.setVisible(false);
	}

	public void windowDeactivated(WindowEvent e) {
	}

	public void windowDeiconified(WindowEvent e) {
	}

	public void windowIconified(WindowEvent e) {
	}

	public void windowOpened(WindowEvent e) {
	}

	// produce the status list from the players vector
	public synchronized void buildStatus() {
		status.removeAll();
		Enumeration enum=players.elements();
		while (enum.hasMoreElements()) {
			Player player=(Player)enum.nextElement();
			try {
				status.add(player.getName()+" * "+player.getMessage());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		try {
			info.pack();
		} catch (Exception e) {
		}
	}

	// change message in vector of player objects
	public synchronized void updatePlayerMessage(String p,String m) {
		Enumeration enum=players.elements();
		while (enum.hasMoreElements()) {
			Player player=(Player)enum.nextElement();
			if (p.equals(player.getName())) {
				player.setMessage(m);
			} else {
				player.setMessage("");
			}
		}
		buildStatus();
	}
}
