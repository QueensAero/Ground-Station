import java.awt.EventQueue;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;


import javax.swing.JFrame;
import java.awt.BorderLayout;
import javax.swing.UIManager;

public class AeroGUI {
	// Logger to be used by all classes in the project. (This line should be at the top of every class)
	private static final Logger LOGGER = Logger.getLogger(AeroGUI.class.getName());
	private static FileHandler fh;
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
		// Set up logging  
	    try {  
	        // The possible log levels are: FINEST, FINER, FINE, CONFIG, INFO, WARNING, SEVERE
	    	// By default, INFO and higher is logged to the console
	    	// FINER will be reserved for received data packets only!!!
	    	String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
	    	fh = new FileHandler(timeStamp + "_groundstation.log", true); // Don't overwrite existing file 
	        fh.setFormatter(new SimpleFormatter());
	    	fh.setLevel(Level.ALL); // Everything will be logged to the file
	        LOGGER.addHandler(fh);
	        LOGGER.setLevel(Level.ALL);
	        LOGGER.fine("Logging initialized succesfully.");
	        LOGGER.fine("Starting Aero GroundStation");

	    } catch (SecurityException e) {  
	        e.printStackTrace();  
	    } catch (IOException e) {  
	        e.printStackTrace();  
	    }  
		
		frame = new JFrame("Aero GUI");
		//frame.setBounds(100, 100, 1200, 600);
		frame.setExtendedState(JFrame.MAXIMIZED_BOTH);  //set to full screen 
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(new BorderLayout());
		
		
		//declare Serial Communicator class
		SerialCommunicator sc = new SerialCommunicator();
		
		//declare mainwindow class, passing the SC instance to it
		main = new MainWindow(sc, frame);
		
		frame.getContentPane().add(main, BorderLayout.CENTER);
		
		//this overrides the closing event, in an attempt to properly terminate the application and end VideoFeed thread
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
		    @Override
		    public void windowClosing(java.awt.event.WindowEvent windowEvent) {
		    	
		    	LOGGER.fine("CLosing Window.");
		    	main.videoFeed.endCapture();  //Comment this out to remove OpenCV dependance
		    	main.console.close();
		    	main.planeMessageConsole.close();  
		    	System.exit(0);  //terminate the program		        
		    }
		});
	}
}
