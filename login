	public void getLogin() {
		loginInput=new TextField("Davis",10);
		loginInput.addActionListener(this);
		loginFrame=new Frame("Login Please");
		loginFrame.add(BorderLayout.CENTER,loginFrame);
		loginFrame.pack();
		loginFrame.setVisible(true);
	}
		
}
