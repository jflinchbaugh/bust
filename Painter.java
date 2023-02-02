/* Painter class
 * loops with a pause calling repaint
 */
import java.awt.*;

public class Painter implements Runnable {
	private int period;			// sleep time
	private Component obj;		// object with the repaint method
	private boolean suspended;	// thread control
	private boolean running;	// thread control

	public Painter(Component obj,int period) {
		suspended=true;
		running=true;
		this.obj=obj;
		this.period=period;
	}
	
	public void run() {
		while (running) {
			while (suspended && running) {
				synchronized (this) {
					try {
						wait();
					} catch (InterruptedException e) {
					}
				}
			}
			if (!running) break;

			obj.repaint();
			try {
				Thread.sleep(period);
			} catch (InterruptedException e) {
			}
		}
	}

	// thread control functions
	public void resume() {
		suspended=false;
		synchronized (this) {
			notify();
		}
	}
	public void suspend() {
		suspended=true;
	}
	public void stop() {
		running=false;
		synchronized (this) {
			notify();
		}
	}
	public void start() {
		resume();
	}
}
