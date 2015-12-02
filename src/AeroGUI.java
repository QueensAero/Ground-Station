import java.awt.EventQueue;

import javax.swing.JFrame;

import java.awt.BorderLayout;

import javax.swing.UIManager;

import org.opencv.core.Core;

public class AeroGUI {
	private static MainWindow main;
	
	private JFrame frame;
	
	static {
        // Load the native OpenCV library
		System.loadLibrary( Core.NATIVE_LIBRARY_NAME );
	}

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
					AeroGUI window = new AeroGUI();
					window.frame.setVisible(true);
					
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public AeroGUI() {
		initialize();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frame = new JFrame("Aero GUI");
		//frame.setBounds(100, 100, 1200, 600);
		frame.setExtendedState(JFrame.MAXIMIZED_BOTH); 
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(new BorderLayout());
		
		SerialCommunicator sc = new SerialCommunicator();
		main = new MainWindow(sc);
		frame.getContentPane().add(main, BorderLayout.CENTER);
		
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
		    @Override
		    public void windowClosing(java.awt.event.WindowEvent windowEvent) {
		    	
		    	main.videoFeed.endCapture();
		    	
		    	System.out.print("test");
		    	
		    	try {
		    	    Thread.sleep(2000);                 //1000 milliseconds is one second.
		    	} catch(InterruptedException ex) {
		    	    Thread.currentThread().interrupt();
		    	}
		    	
		    	System.exit(0);

		        
		    }
		});
	}
	
	
	

}


