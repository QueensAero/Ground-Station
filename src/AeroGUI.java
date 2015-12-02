import java.awt.EventQueue;

import javax.swing.JFrame;

import java.awt.BorderLayout;

import javax.swing.UIManager;

import org.opencv.core.Core;

public class AeroGUI {
	//member variables
	private static MainWindow main;
	private JFrame frame;
	
	//this is something weird that has to be done to properly load the OpenCV library
	static {  System.loadLibrary( Core.NATIVE_LIBRARY_NAME );	}

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
		
		SerialCommunicator sc = new SerialCommunicator();
		main = new MainWindow(sc);
		frame.getContentPane().add(main, BorderLayout.CENTER);
		
		
		//this overrides the closing event, in an attempt to properly terminate the application and end VideoFeed thread
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
		    @Override
		    public void windowClosing(java.awt.event.WindowEvent windowEvent) {
		    	
		    	main.videoFeed.endCapture();  //end VideoFeed thread, currently doesn't work...		    	
		    	
		    	System.exit(0);  //terminate the program		        
		    }
		});
	}
	
	
	

}


