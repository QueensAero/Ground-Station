import java.awt.EventQueue;

import javax.swing.JFrame;

import java.awt.BorderLayout;

import javax.swing.UIManager;





public class AeroGUI {
	//member variables
	private static MainWindow main;
	private JFrame frame;
	
	

	/** MAIN - Launches the application.	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				
							
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
					AeroGUI window = new AeroGUI();  //create an instance of AeroGUI class
					window.frame.setVisible(true);  //set the GUI to visiable
					
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/** Create the application.	 */
	public AeroGUI() {
		initialize();
	}

	/**	 Initialize the contents of the frame. */
	private void initialize() {
		frame = new JFrame("Aero GUI");
		//frame.setBounds(100, 100, 1200, 600);
		frame.setExtendedState(JFrame.MAXIMIZED_BOTH);  //set to full screen 
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(new BorderLayout());
		
		//declare Serial Communicator class
		SerialCommunicator sc = new SerialCommunicator();
		
		//declare mainwindow class, passing the SC instance to it
		main = new MainWindow(sc);
		
		frame.getContentPane().add(main, BorderLayout.CENTER);
		
		//this overrides the closing event, in an attempt to properly terminate the application and end VideoFeed thread
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
		    @Override
		    public void windowClosing(java.awt.event.WindowEvent windowEvent) {
		    	
		    	
		    	main.videoFeed.endCapture();  //Comment this out to remove OpenCV dependance
		    	main.console.close();
		    	main.planeMessageConsole.close();  
		    	main.dataLogger.close();
		    	
		    	
		    	System.exit(0);  //terminate the program		        
		    }
		});
	}
	
	
	

}


