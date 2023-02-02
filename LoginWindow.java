/* LoginWindow class
 * this class is used to pop up the login dialog at the beginning of
 * the game
 */
import java.awt.*;
import java.awt.event.*;

public class LoginWindow extends Dialog
	implements ActionListener {
	private String login;
	private TextField loginInput;

	public LoginWindow(Frame owner) {
		super(owner,true);
		setTitle("Login Please");
		loginInput=new TextField(20);
		loginInput.addActionListener(this);
		loginInput.setEditable(true);
		add(BorderLayout.CENTER,loginInput);
		pack();
		show();
	}

	public void actionPerformed(ActionEvent e) {
		Object source=e.getSource();

		if (source==loginInput)  {
			login=loginInput.getText();
			setVisible(false); // returns from the dialog.
		}
	}

	// return the login name obtained by the dialog
	public String getLogin() {
		return login;
	}
}
