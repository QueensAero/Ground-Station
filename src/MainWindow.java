import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;

import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.Timer;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.DefaultCaret;

/* Commented by Ryan Dowling, Dec. 2015
 * Not positive in all comments, but best guesses (I didn't write this code)  
 * 
 * Note: for the dimensions/GUI layout to work, it assumes a screen resolution of 1920*1080.  If that's not the resolution, it will be all messed up.
 * 
 */

public class MainWindow extends JPanel implements PacketListener {
	private static final Logger LOGGER = Logger.getLogger(AeroGUI.class.getName());
	private SpeechManager speechManager;
	
	private MainWindow me;
	private StreamHandler consoleLogger;
	public VideoFeed videoFeed;
	public Targeter targeter;
	private JComboBox commPortSelector;
	//private JButton btnRefresh, btnConnect; //connection buttons
	//private JButton btnEnable, btnClearData, btnRequestAltAtDrop, btnRequestBattV;
	//private JButton btnAutoDropOn, btnAutoDropOff, btnDrop, btnCloseDropBay, btnSensorReset, btnPlaneRestart; //servo control buttons
	private JButton btnEnable, btnRequestAltAtDrop, btnRequestBattV;
	private JButton btnAutoDropOn, btnAutoDropOff, btnDrop, btnCloseDropBay, btnSensorReset, btnPlaneRestart; //servo control buttons
	private JButton btnStartRecording, btnRestartStream, btnResetDrop;  //button to start/stop recording

	public PrintStream console; //to display all console messages
	private JTextArea consoleTextArea;
	private JLabel lblAlt, lblAltAtDrop; //labels to display the values
	private SerialCommunicator serialComm;
	private TextAreaOutputStream outputStream;
	JDialog calibrator;
	long connectTime;
	boolean btnsEnabled = false;
	private JFrame parentFrame;
	//thread variables
	Timer threadTimer;
	
	// Menu Bar Variables
	private JMenuBar menuBar;
	private JMenu menu, gpsSubmenu, commSubmenu;
	
	//logging variables
	SimpleDateFormat sdf;
	String startDate;
	long startTime = 0;
	
	
	//Status Variables
	double altFeet ,speedMPS, lattitudeDDM , longitudeDDM , heading , HDOP , msSinceLastValidHDOP , GPSAlt_M;
	int fixQuality;
	
	//constructor
	public MainWindow (SerialCommunicator sc, JFrame frame) {
		serialComm = sc;
		speechManager = SpeechManager.getInstance();
		initializeComponents();
		initializeButtons();
		me = this;
		parentFrame = frame;
		startTime = System.currentTimeMillis();
	}
	
	private void logData() {

		//Log format: "T_sinceStart,data_age,RecFrame,FR,roll,pitch,speed,alt(ft),latt,long,heading,latErr,timeToDrop,EstDropPosX,EstDropPosY,isDropped?,altAtDrop,ExpectedDropX,ExpectedDropY\n";
		//^ is slightly out of date
		GPSPos basePos = targeter.getbaseGPSPos();  //This is last received GPS point (NOT projected forward based on delay)
		GPSPos targetPos = targeter.getTargetPos();  //This is last received GPS point (NOT projected forward based on delay)

		
		String s = Long.toString(System.currentTimeMillis()-startTime) + "," + Long.toString(targeter.getDataAge()) + ","  + ","+ videoFeed.currentRecordingFN +","+ 
					String.format("%.2f", videoFeed.frameRate) 	+ "," + String.format("%.4f",speedMPS) + "," 
					+ String.format("%.4f",altFeet) + "," + String.format("%.4f",basePos.getUTMEasting()) + ","	+ String.format("%.4f",basePos.getUTMNorthing()) + "," 
					+ String.format("%.3f",heading) + "," + String.format("%.3f",targeter.lateralError) + "," + String.format("%.4f",targeter.timeToDrop)
					+ "," + String.format( "%.1f",targeter.getEstDropPosXMetres())  + "," + String.format( "%.1f", targeter.getEstDropPosYMetres()) + "," 
					+ videoFeed.isDropped + "," + String.format("%.1f", videoFeed.altAtDropFt) + "," +  String.format("%.4f",targeter.actEstDropPosXMeters()) + "," 
					+ String.format("%.4f",targeter.actEstDropPosYMeters()) + "," + String.format("%.2f", HDOP) + "," 
					+ String.format("%.0f", msSinceLastValidHDOP) + ","  + Integer.toString(fixQuality) + ","  + String.format("%.2f", GPSAlt_M) +  "," 
					+ String.format("%.2f", targetPos.getUTMEasting()) 	+ "," + String.format("%.2f", targetPos.getUTMNorthing())  + "\n";
		
		LOGGER.finer(s); // Log to file only
	}
		
	//set enabled setting for all plane control buttons at once
	public void setControlButtons (boolean val) {
		btnDrop.setEnabled(val);
		btnAutoDropOn.setEnabled(val);
		btnAutoDropOff.setEnabled(val);
		btnCloseDropBay.setEnabled(val);
		btnSensorReset.setEnabled(val);
		btnPlaneRestart.setEnabled(val);
		btnRequestAltAtDrop.setEnabled(val);
		btnRequestBattV.setEnabled(val);
		btnsEnabled = val;
	}
	
	//function containing/initializing all the GUI button action listeners
	private void initializeButtons() {
		
		//since original state is not connected to COM, initially set all control buttons to disabled
		setControlButtons(false);
			
		/* ACTION LISTENERS */	
				
			//when 'Clear' pressed.  Clears the console window 
			/*
			 * TODO
			 * btnClearData.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					consoleTextArea.setText("");
				}
			});*/
			
			//when 'Enable/Disable Resets' Pressed
			btnEnable.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					btnsEnabled = !btnsEnabled;  //status or resets enabled.
					//want to turn of resets once plane is ready to fly (otherwise could accidentally erase/reset all data during flight
					//need resets originally since sensors take some time to 'settle' on startup
					if (serialComm.getConnected()) {
						if (btnsEnabled) {
							btnSensorReset.setEnabled(true);
							btnPlaneRestart.setEnabled(true);
							btnStartRecording.setEnabled(true);
							btnRequestAltAtDrop.setEnabled(true);
						}
						else {
							btnSensorReset.setEnabled(false);
							btnPlaneRestart.setEnabled(false);
							btnStartRecording.setEnabled(false);
							btnRequestAltAtDrop.setEnabled(false);
						}
					}
					else {
						LOGGER.warning("Cannot enable sensor/plane resets. Comm. port not connected.");
					}
				}
			});
			
			btnAutoDropOn.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					// Toggle autoDrop enable, the plane should respond to tell us what mode it is in
					serialComm.write('a');
					LOGGER.info("Sent 'Turn on Auto-Drop' Msg.");
				}
			});
			
			btnAutoDropOff.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					// Toggle autoDrop enable, the plane should respond to tell us what mode it is in
					serialComm.write('n');
					LOGGER.info("Sent 'Turn off Auto-Drop' Msg.");
				}
			});
			
			//when 'Drop' pressed
			btnDrop.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					dropPackage();  //sends command, sets flags, prints current time and altitude
				}
			});
			
			// When 'Close Drop Bay' clicked
			btnCloseDropBay.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					serialComm.write('c');
					LOGGER.info("Close drop bay sent.");
				}
			});
			
			//this maps the keyboard shortcut CTRL+SHIFT+D to the drop command
			this.getActionMap().put("dropHandle", new AbstractAction(){
				@Override
				public void actionPerformed(ActionEvent e) {
					if(btnDrop.isEnabled())
						dropPackage();
					else
						LOGGER.warning("Shortcut ignored, drop not yet enabled");
	            }				
			});
			this.getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_D,KeyEvent.CTRL_DOWN_MASK + KeyEvent.SHIFT_DOWN_MASK),"dropHandle");
			
			//when "reset sensor' pressed
			btnSensorReset.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					serialComm.write('r');
					LOGGER.info("Reset sent.");
				}
			});
			
			//when 'Plane Reset' pressed 
			btnPlaneRestart.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					serialComm.write('q');  //send the command to plane
					LOGGER.info("Restart sent."); 
					videoFeed.changeDropStatus(false);	
					targeter.setDropStatus(false);
				}
			});
			
			//when Start/Stop Recording is pressed
			btnStartRecording.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					videoFeed.toggleRecordingStatus();  //call to function in VideoFeed to toggle the recording status
				}
			});
			
			btnRestartStream.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					videoFeed.restartVideoStream();   //OpenCV Dependance
				}
			});
			
			btnResetDrop.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					lblAltAtDrop.setText(" ");
					videoFeed.changeDropStatus(false);	
					targeter.setDropStatus(false);
				}
			});
			
			btnRequestAltAtDrop.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					serialComm.write('g');  //send command to request the altitude at drop value
					LOGGER.info("Altitude at drop requested.");
				}
			});
			
			btnRequestBattV.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					serialComm.write('b');  //send command to request the altitude at drop value
					LOGGER.info("Requested battery voltage.");
				}
			});
			
			
	}/* END INITIALIZE BUTTONS */
	
	//called when drop command sent. Updates flag, prints out time and altitude of drop
	private void dropPackage() {
		serialComm.write('o');  //send drop command to arduino
		LOGGER.info("Drop package sent.");
		//Wait until acknowledge to update
		
	}
	
	
	private void packageDropAcknowledge()
	{
		double time = (System.currentTimeMillis() - connectTime) / 1000.0;
		System.out.println(time + "s: Package dropped at: "+lblAlt.getText() + " ft");
		lblAltAtDrop.setText(lblAlt.getText());
		videoFeed.changeDropStatus(true);	
		targeter.setDropStatus(true);
	}
	
	//Call after requested the altitude at drop from plane
	private void updateDropDetails(double altAtDrop)
	{
		lblAltAtDrop.setText(String.format("%0.2f", altAtDrop));
		videoFeed.changeDropStatus(true);	
		targeter.setDropStatus(true);
	}
	
	public JMenuBar createMenuBar() {
        //Create the menu bar.
        menuBar = new JMenuBar();
        menuBar.setForeground(Color.RED);// Red until we send GPS coords
        //Build the first menu.
        menu = new JMenu("Setup");
        menuBar.add(menu);
 
        // GPS Submenu
        gpsSubmenu = new JMenu("GPS Target");
        gpsSubmenu.setForeground(Color.red);
        
        
        JMenuItem menuItem = new JMenuItem("Update GPS Target");
        menuItem.addActionListener(new ActionListener() {	
        public void actionPerformed(ActionEvent e) {
        		GPSTargetDialog gpsTargetDlg = new GPSTargetDialog(parentFrame, "GPS Target", me, targeter.getTargetLattDDM(), targeter.getTargetLongDDM());
        		gpsTargetDlg.pack();
        		gpsTargetDlg.setVisible(true);
        	}
        });
        gpsSubmenu.add(menuItem);
        
        menuItem = new JMenuItem("Send Target to Plane");
        menuItem.setForeground(Color.RED);
        menuItem.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent e) {
        		//Message format: tXXXX%YYYYe
        		// XXXX-> latitude (DDM)
        		// YYYY-> longitude (DDM)
        		byte[] targetMessage = new byte[19];// start byte + 8-byte latitude + separator byte + 8-byte longitude + End byte = (2*8) + 3 = 19 bytes
        		targetMessage[0] = 't';
        		byte[] lat = doubleToBytes(targeter.getTargetLattDDM());
        		byte[] lon = doubleToBytes(targeter.getTargetLongDDM());
        		for(int i = 0; i < 8; i++)
        			targetMessage[i + 1] = lat[i];
        		targetMessage[9] = '%';
        		for(int i = 0; i < 8; i++)
        			targetMessage[i + 10] = lon[i];
        		targetMessage[18] = 'e';
        		
        		serialComm.write(targetMessage);
        		LOGGER.info("Sent target latitude and longitude to plane.");
        	}
        });
        gpsSubmenu.add(menuItem);
        
 
        // Comm Submenu
        commSubmenu = new JMenu("Comm. Port");
        menuItem = new JMenuItem("Select Comm. Port");
        menuItem.addActionListener(new ActionListener() {	
            public void actionPerformed(ActionEvent e) {
            	CommPortDialog commPortDlg = new CommPortDialog(me, serialComm);
            	commPortDlg.pack();
            	commPortDlg.setVisible(true);
            }
        });
        commSubmenu.add(menuItem);
       
        menu.add(gpsSubmenu);
        menu.add(commSubmenu);

        menuBar.add(menu);
        
		return menuBar;
	}
	
	//mess of a function intializing the layout of the GUI
	private void initializeComponents() {
		this.setLayout(new BorderLayout());
		/***** Left Side: *****/
		JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new GridBagLayout());
		// Minimum width is required to ensure that left panel displays properly.
		// If frame isn't big enough, take space away from right Panel
		int minWidth = 400;
		leftPanel.setMinimumSize(new Dimension(minWidth, 700));
		GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
		int preferredWidth = gd.getDisplayMode().getWidth()/2;  //half of screen width
		leftPanel.setPreferredSize(new Dimension(preferredWidth, 700));
		
		// Constraint object used for all panels within leftPanel:
		GridBagConstraints c = new GridBagConstraints();
		
		// dataPanel:
		JPanel dataPanel = initializeDataPanel(); // Top left panel containing roll, pitch, speed , altitude, etc.
		dataPanel.setMinimumSize(new Dimension(minWidth, 100));
		dataPanel.setPreferredSize(new Dimension(preferredWidth, 100));
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 1;
		c.gridheight = 1;
		c.weightx = 1;
		c.weighty = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		leftPanel.add(dataPanel, c);
		
		// servoButtonPanel
		JPanel servoPanel = initializeServoButtonPanel();
		servoPanel.setMinimumSize(new Dimension(minWidth, 100));
		servoPanel.setPreferredSize(new Dimension(650, 150));
		c.gridx = 0;
		c.gridy = 2;
		c.gridwidth = 1;
		c.gridheight = 1;
		c.weightx = 1;
		c.weighty = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		leftPanel.add(servoPanel, c);

		// consolePanel
		//Panel containing the console (System.out is mapped to here)
		JPanel consolePanel = new JPanel();
		consolePanel.setBorder(new TitledBorder(new EtchedBorder(), "Console"));
		consolePanel.setMinimumSize(new Dimension(minWidth, 60));
		consolePanel.setPreferredSize(new Dimension(preferredWidth, 200));
		consoleTextArea = new JTextArea();
		JScrollPane consoleScroller = new JScrollPane(consoleTextArea);
		outputStream = new TextAreaOutputStream(consoleTextArea);
		console = new PrintStream(outputStream);
		consolePanel.setLayout(new BorderLayout());
		consoleScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		DefaultCaret consoleCaret = (DefaultCaret)consoleTextArea.getCaret();
		consoleCaret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
		consoleLogger = new StreamHandler(console, new SimpleFormatter()){
			// Override publish so that log records get flushed immediately
			// Without this, the log handler may not print until the buffer fills up
	        @Override
	        public synchronized void publish(final LogRecord record) {
	            super.publish(record);
	            flush();
	        }
		};
		LOGGER.addHandler(consoleLogger);
		consoleLogger.setLevel(Level.INFO); // All logging records with level of INFO or higher will be displayed in the 'console'
		System.out.println("From this point onward, stdout and stderr will be redirected and only displayed in the GUI and in the log." );
		System.setOut(console); // stdout and stderr will also be logged to the console
		System.setErr(console);

		consolePanel.add(consoleScroller);
		c.gridx = 0;
		c.gridy = 5;
		c.gridwidth = 1;
		c.gridheight = 1;
		c.weightx = 1;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		leftPanel.add(consolePanel, c);
			
		/***** Right Side: *****/
		// Video Feed:
		JPanel videoFeedPanel = new JPanel();
		videoFeedPanel.setLayout(new BorderLayout());
		videoFeedPanel.setMinimumSize(new Dimension(400, 250));
		videoFeed = new VideoFeed();
		videoFeedPanel.add(videoFeed, BorderLayout.CENTER);
		
		// GPS Targeter:
		JPanel gpsTargeterPanel = new JPanel();
		gpsTargeterPanel.setLayout(new BorderLayout());
		gpsTargeterPanel.setMinimumSize(new Dimension(350, 400));
		targeter = new Targeter();
		gpsTargeterPanel.add(targeter, BorderLayout.CENTER);
		
		// Create a vertical split pane between the video feed and GPS targeter
		JSplitPane vertSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, videoFeedPanel, gpsTargeterPanel);
		vertSplitPane.setOneTouchExpandable(true);
		vertSplitPane.setResizeWeight(1.0); // Bottom component stays same size when window is resized
		
		JPanel rightPanel = new JPanel();
		rightPanel.setLayout(new BorderLayout());
		rightPanel.setMinimumSize(new Dimension(400, 700));
		rightPanel.add(vertSplitPane, BorderLayout.CENTER);
		
		// Create a split pane between the left and right panels
		JSplitPane horizSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
		horizSplitPane.setOneTouchExpandable(true);

		this.add(horizSplitPane, BorderLayout.CENTER);

	}
	
	private JPanel initializeServoButtonPanel() {
		//servo button panel
		JPanel servoButtonPanel = new JPanel();
		servoButtonPanel.setBorder(new TitledBorder(new EtchedBorder(), "Control Buttons"));
		servoButtonPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		
		btnEnable = new JButton("Enable/Disable Resets");
		servoButtonPanel.add(btnEnable);
		
		btnAutoDropOn = new JButton("Enable AutoDrop");
		servoButtonPanel.add(btnAutoDropOn);
		
		btnAutoDropOff = new JButton("Disable AutoDrop");
		servoButtonPanel.add(btnAutoDropOff);
		
		btnDrop = new JButton("Drop");
		servoButtonPanel.add(btnDrop);
		
		btnCloseDropBay = new JButton("Close Drop Bay");
		servoButtonPanel.add(btnCloseDropBay);
		
		btnSensorReset = new JButton("Sensor Reset");
		servoButtonPanel.add(btnSensorReset);
		
		btnPlaneRestart = new JButton("Plane Restart");
		servoButtonPanel.add(btnPlaneRestart);
		
		btnRequestAltAtDrop = new JButton("Get Alt @ Drop");
		servoButtonPanel.add(btnRequestAltAtDrop);
		
		btnRequestBattV = new JButton("Check Battery");
		servoButtonPanel.add(btnRequestBattV);
		
		btnResetDrop = new JButton("Reset Drop");
		servoButtonPanel.add(btnResetDrop);		
		
		btnStartRecording = new JButton("Start/Stop Recording");
		servoButtonPanel.add(btnStartRecording);
		
		btnRestartStream = new JButton("Restart Video");
		servoButtonPanel.add(btnRestartStream);
		
		return servoButtonPanel;
	}
	
	
	private JPanel initializeDataPanel() {
		//data control panel. Top left panel containing curent roll/spd/pitch/alt
		JPanel dataPanel = new JPanel();
		dataPanel.setBorder(new TitledBorder(new EtchedBorder(), "Data"));
		//dataPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		dataPanel.setLayout(new GridLayout(0,2));

		
		Font dataPanelFont = new Font(Font.SANS_SERIF, 0, 25);
		dataPanel.setFont(dataPanelFont);
		
		JLabel altTxt, altAtDropTxt;
		altTxt = new JLabel("Alt (ft):");
		altTxt.setFont(dataPanelFont);
		altAtDropTxt = new JLabel("\tAlt at Drop (ft):");
		altAtDropTxt.setFont(dataPanelFont);
		
		//If wanted to clean this up make an array of JLabels...
		lblAlt = new JLabel("");
		lblAlt.setFont(dataPanelFont);
		lblAltAtDrop = new JLabel("");
		lblAltAtDrop.setFont(dataPanelFont);
	
		dataPanel.add(altTxt);
		dataPanel.add(lblAlt);
		dataPanel.add(altAtDropTxt);
		dataPanel.add(lblAltAtDrop);
		
		return dataPanel;
	}
	
	// Called by the gps target dialog:
	public void updateGPSTarget(Double lat, Double lon) {
		// Update labels here if we decide to display the target latitude / longitude
		targeter.setTargetPos(lat,  lon);
	}
	
	// Handles both connect and disconnect based on whether or not we are currently connected
	public void updateSerialConnection(String commPort) {
		if (!serialComm.getConnected()) {
			//String commPort = commPortSelector.getSelectedItem().toString();
			serialComm.connect(commPort);
			if (serialComm.getConnected()) {
				connectTime = System.currentTimeMillis();
				serialComm.addListener(MainWindow.this);
				//btnConnect.setText("Disconnect");
				setControlButtons(true);
			} else {
				LOGGER.warning("Failed to connect to: " + commPort);
			}
			
		} else { // Already connected to a port
			serialComm.disconnect();
			if (!serialComm.getConnected()) {
				serialComm.removeListener(MainWindow.this);
				//btnConnect.setText("Connect");
				setControlButtons(false);
			} else {
				LOGGER.warning("Could not disconnect from serial connection");
			}
		}
	}
	
	//called from SerialCommunicator?
	public void invalidPacketReceived(String packet) {
		double time = (System.currentTimeMillis() - connectTime) / 1000.0;
	
		//TODO - log the invalid packet (check this is correct  with Ick)
		if(packet.length() > 0)
			LOGGER.warning("Invalid Packet Received at " + time + " s, Raw String: " + packet);
		else
			LOGGER.warning("Invalid Empty Packet Received at " + time + " s");

	}
	
	//called from SerialCommunicator
	public void packetReceived(String packet, byte[] byteArray) {
		analyzePacket(packet, byteArray);
	}
	
	//*p%ROLL%PITCH%ALTITUDE%AIRSPEED%LATTITUDE%LONGITUDE%HEADING%second%ms&  is old way
	//*pXXXXYYYYZZZZAAAABBBBCCCCDDDDsttee    is updated old way, 35 bytes longW
	//with XXXX = four bytes which combined make a float, s = 1 byte making uint8_t, tt = 2 bytes making uint16_t, 
	//*pXXXXYYYYZZZZAAAABBBBsttee is the newer way -> no longer sending roll or pitch, 27 bytes long
	//Ie. in order, it's alt, spd, lat, long, heading
	//Next new way:  *pXXXXYYYYZZZZAAAABBBBCCCCDDDDfee
	//Ie. In order it's alt, spd, lat, long, heading, HDOP, msSinceLastValidHDOP, fix quality
	
	final static int ALTATDROP_PACKET_L = 8,
					BATT_LEVEL_PACKET_L = 8;
	
	final static int ACKNOWLEDGE_PACKET_L = 4;  //This is common to a bunch of 'acknowledge' type messages


	//called from packetReceived, which is called by Serial communcator.  Analyzes a complete packet
	private void analyzePacket (String str, byte[] byteArray) {
		double time = (System.currentTimeMillis() - connectTime) / 1000.0;

		
		int byteArrayInd = 2;  //The start of data is at index 2 (0th = '*',  1st = 'p' or 'a')
			
		//DATA PACKET
		if(str.substring(1, 2).equals("p") && str.length() == SerialCommunicator.DATA_PACKET_L){ //'p' indicates data packet
			
			double [] dblArr = new double [8];
			
			for(int x = 0; x < dblArr.length; x++)  //extract 5 float values (which are cast to double)
			{	
				dblArr[x] = extractFloat(byteArray[byteArrayInd++],byteArray[byteArrayInd++],byteArray[byteArrayInd++],byteArray[byteArrayInd++]);
				
				//rounding code
				if(x != 2 && x != 3)  //round to 2 decimal places if not Latt & Long
					dblArr[x] = Math.round(100*dblArr[x])/100.0;
				else  //round to 4 decimal places (latt and long - note this is the precision the GPS reports with, so no point going beyond)
					dblArr[x] = Math.round(10000*dblArr[x])/10000.0;	
			
			}
			
			
			
			fixQuality = extractuInt8(byteArray[byteArrayInd++]);  //Fix Qual is uInt8, since 0-7 or so
			
			//Setup variables from double array
			altFeet = dblArr[0];
			speedMPS = dblArr[1];  
			lattitudeDDM = dblArr[2];
			longitudeDDM = dblArr[3];
			if(longitudeDDM > 0)
			{
				longitudeDDM = -longitudeDDM; //We are always in western hemisphere, so longitude always is negative
			}
			heading = dblArr[4];
			HDOP = dblArr[5];
			msSinceLastValidHDOP = dblArr[6];
			GPSAlt_M = dblArr[7];

			
			//print altitude to status area (top left)
			lblAlt.setText(""+altFeet);				
			
			//UNITS - dblArr[0] is in FEET - both functions below expect altitude in feet. Altitude is logged in feet as well
					
			//Update data in VideoFeed Class 
			videoFeed.updateValues(altFeet, speedMPS, lattitudeDDM, longitudeDDM, heading);
						
			//Send updated data to targeter
			targeter.updateGPSData(altFeet, speedMPS, lattitudeDDM, longitudeDDM, heading, HDOP, msSinceLastValidHDOP, fixQuality);

			logData(); // Log the new state each time new data is received 

		}
		//RETURN AFTER REQUESTING ALT AT DROP
		else if (str.substring(1, 2).equals("a") && str.length() == ALTATDROP_PACKET_L) {  
			
			//Extract the float of the altitude
			double altitudeAtDrop = extractFloat(byteArray[byteArrayInd++],byteArray[byteArrayInd++],byteArray[byteArrayInd++],byteArray[byteArrayInd++]);
			LOGGER.info("Altitude at drop = " + altitudeAtDrop);  //print result to console
			updateDropDetails(altitudeAtDrop);

		}
		//BATTERY LEVEL
		else if(str.substring(1, 2).equals("w") && str.length() == BATT_LEVEL_PACKET_L)
		{
			double batteryV = extractFloat(byteArray[byteArrayInd++],byteArray[byteArrayInd++],byteArray[byteArrayInd++],byteArray[byteArrayInd++]);
				
			//Convert Voltage into state of charge (roughly)
			//https://www.google.ca/search?q=lipo+voltage+percentage+chart&source=lnms&tbm=isch&sa=X&ved=0ahUKEwjf3YzrtuXRAhUDL8AKHWAeA20Q_AUICCgB#imgrc=W3tAf46IsrwmMM%3A
			
			//Crude LUT
			long index = Math.round(((batteryV - 10.5)*10));   //10.5 -> 0, 10.6 -> 1 .... 12.6 -> 21
			int[] battP = {0, 1, 2, 3, 4, 5, 6, 8, 10, 13, 16, //up to 11.5V
							20, 24, 30, 40, 45, 50, 60, 70, 80, 90, 100 }; //up to 12.6
			if(index < 0)
				index = 0;
			else if(index >= battP.length)
				index = battP.length - 1;
			LOGGER.info("Battery Voltage = " + Math.round(100*batteryV)/100.0 + " V, Battery Percentage = " + battP[(int)index] + " %");  //print result to console
		}
		//REMAINING ARE ACKNOWLEGEMENT MESSAGES
		else if(str.length() == ACKNOWLEDGE_PACKET_L) {	
			if (str.substring(1, 2).equals("s")) {
				LOGGER.info(time + "s: Communicator Initialized Successfully");
			} else if (str.substring(1, 2).equals("k")) {
				LOGGER.info(time + "s: Reset Acknowledge");
			} else if (str.substring(1, 2).equals("r")) {
				LOGGER.info(time + "s: Plane Ready");
			} else if (str.substring(1, 2).equals("q")) {
				LOGGER.info(time + "s: Restart Acknowledge");
			} else if (str.substring(1, 2).equals("x")) {
				LOGGER.info(time + "s: Camera Reset Acknowledge");
			} else if (str.substring(1, 2).equals("e")) {
				LOGGER.info(time + "s: Error");
			} else if (str.substring(1, 2).equals("y")) {
				LOGGER.info(time + "s: Drop Acknowledge");
				speechManager.reportNewMessage("Drop acknowledge.");
			} else if (str.substring(1, 2).equals("b")) {
				LOGGER.info(time + "s: Auto Drop ON confirmation.");
				targeter.setAutoDropEnabled(true);
				speechManager.reportNewMessage("Auto-Drop enabled confirmation.");
			} else if (str.substring(1, 2).equals("d")) {
				LOGGER.info(time + "s: Auto Drop OFF confirmation.");
				targeter.setAutoDropEnabled(false);
				speechManager.reportNewMessage("Auto-Drop disabled confirmation.");
			} else if (str.substring(1, 2).equals("o")) {
				LOGGER.info(time + "s: Open Drop Bay Acknowledge");
				speechManager.reportNewMessage("Open Drop Bay Acknowledge.");
				packageDropAcknowledge();  //updates details of drop in GUI
			} else if(str.substring(1, 2).equals("c")) {
				LOGGER.info(time + "s: Drop Bay Closing (either auto or commanded)");
				speechManager.reportNewMessage("Drop Bay Closing.");
			} else if (str.substring(1, 2).equals("1")) {
				LOGGER.info(time + "s: MPU6050 Ready");
			} else if (str.substring(1, 2).equals("2")) {
				LOGGER.info(time + "s: MPU6050 Failed");
			} else if (str.substring(1, 2).equals("3")) {
				LOGGER.info(time + "s: DMP Ready");
			} else if (str.substring(1, 2).equals("4")) {
				LOGGER.info(time + "s: DMP Failed");
			} else if (str.substring(1, 2).equals("5")) {
				LOGGER.info(time + "s: MPU6050 Initializing");
			} else if (str.substring(1, 2).equals("g")) {
				LOGGER.info(time + "s: GPS Target Received");
				// Set text back to black to remove red warning colour
				menuBar.setForeground(Color.BLACK);
				gpsSubmenu.setForeground(Color.BLACK);
				gpsSubmenu.getItem(1).setForeground(Color.BLACK);
			} else {
				LOGGER.warning(time + "s: Unknown Message Character of '" + str.substring(1, 2) + "'");
			}
			
		}
		//IF NONE OF THE ABOVE, INVALID PACKET
		else
		{
			StringBuffer msg = new StringBuffer();
			for(int i = 0; i < byteArray.length; i++)
			{
				msg.append(byteArray[i] + " ");  //Put byte array into string of numbers
			}
			invalidPacketReceived(msg.toString());			
		}
	}
	
	public static byte[] doubleToBytes(double value) {
	    byte[] bytes = new byte[8];
	    
	    // If you are having issues with this then make sure that you consider Endianness!!!!!!!!!
	    long lng = Double.doubleToLongBits(value);
	    for(int i = 0; i < 8; i++) 
	    	bytes[i] = (byte)((lng >> ((i) * 8)) & 0xff);
	    
	    //ByteBuffer.wrap(bytes).putDouble(value);
	    return bytes;
	}
	
	double extractFloat(byte byte1, byte byte2, byte byte3, byte byte4)  //byte 1 is lowermost byte
	{
			
		int asInt = (byte1 & 0xFF) | ((byte2 & 0xFF) << 8) | ((byte3 & 0xFF) << 16)  | ((byte4 & 0xFF) << 24);
		return (double)Float.intBitsToFloat(asInt);
		
	}
	
	int extractInt(byte byte1, byte byte2, byte byte3, byte byte4)  //byte 1 is lowermost byte
	{
			
		return (byte1 & 0xFF) | ((byte2 & 0xFF) << 8) | ((byte3 & 0xFF) << 16)  | ((byte4 & 0xFF) << 24);
		
	}
	
	int extractuInt8(byte byte1)  //byte 1 is lowermost byte
	{
			
		return (int)(byte1 & 0xFF);
		
	}
	
	int extractuInt16(byte byte1, byte byte2)  //byte 1 is lowermost byte
	{
			
		return (int)(((byte2 & 0xFF) << 8)  | ((byte1 & 0xFF)));
		
	}
}
