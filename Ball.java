/* Ball class to represent any ball in the game.
 */
public class Ball {
	public int type;
	public double x;
	public double y;
	public double dx;
	public double dy;

	public Ball(int type,double x,double y,double dx,double dy) {
		this.type=type;
		this.x=x;
		this.y=y;
		this.dx=dx;
		this.dy=dy;
	}
	public boolean equals(Object o) {
		return this==o;
	}
}
