import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;





import javax.swing.JPanel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.Timer;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.DefaultCaret;

/* Commented by Ryan Dowling, Dec. 2015
 * Not positive in all comments, but best guesses (I didn't write this code)  
 * 
 * Note: for the dimensions/GUI layout to work, it assumes a screen resolution of 1920*1080.  If that's not the resolution, it will be all messed up.
 * 
 */

public class MainWindow extends JPanel implements PacketListener {
	private static final Logger LOGGER = Logger.getLogger(AeroGUI.class.getName());
	private StreamHandler consoleLogger;
	public VideoFeed videoFeed;
	public Targeter targeter;
	private JComboBox commPortSelector;
	private JButton btnRefresh, btnConnect; //connection buttons
	private JButton btnEnable, btnClearData, btnRequestAltAtDrop;
	private JButton btnToggleAutoDrop, btnDrop, btnCloseDropBay, btnSensorReset, btnPlaneRestart; //servo control buttons
	private JButton btnStartRecording, btnRestartStream, btnResetDrop;  //button to start/stop recording
	private JButton btnUpdateTarget; // Opens dialog to edit GPS target
	public PrintStream console; //to display all console messages
	private JTextArea consoleTextArea;
	private JLabel lblRoll, lblPitch, lblSpeed, lblAlt, lblHead, lblTS, lblAltAtDrop; //labels to display the values
	private JLabel lblLat, lblLon;
	private SerialCommunicator serialComm;
	JDialog calibrator;
	long connectTime;
	boolean btnsEnabled = false;
	private JFrame parentFrame;
	private GPSTargetDialog gpsTargetDialog;
	//thread variables
	Timer threadTimer; 
	
	//logging variables
	private Path logPath;
	SimpleDateFormat sdf;
	String startDate;
	long startTime = 0;
	
	//constructor
	public MainWindow (SerialCommunicator sc, JFrame frame) {
		serialComm = sc;
		
		initializeComponents();

		initializeButtons();
		
		parentFrame = frame;
		
		gpsTargetDialog = new GPSTargetDialog(frame, "GPS Target", this);
		gpsTargetDialog.pack();
	}
	
	private void logData() {
		LocalDateTime now = LocalDateTime.now();
		//Log format: "T_sinceStart,data_time,real_time,RecFrame,FR,roll,pitch,speed,alt(ft),latt,long,heading,latErr,timeToDrop,EstDropPosX,EstDropPosY,isDropped?,altAtDrop,ExpectedDropX,ExpectedDropY\n";
		String s = Long.toString(System.currentTimeMillis()-startTime) + "," + (targeter.baseGPSposition.getSecond() + targeter.baseGPSposition.getMilliSecond()/1000.0) + ","  + now.getHour() 
					+ "." + now.getMinute() +"."+ (now.getSecond() + now.get(ChronoField.MILLI_OF_SECOND)/1000.0) +","+ videoFeed.currentRecordingFN +","+ String.format("%.2f", videoFeed.frameRate) 
					+ "," + String.format("%.4f",videoFeed.rollAng) + "," + String.format("%.4f",videoFeed.pitchAng) + "," + String.format("%.4f",videoFeed.airSpd) + "," 
					+ String.format("%.4f",videoFeed.altitude) + "," + String.format("%.4f",videoFeed.lattitude) + ","	+ String.format("%.4f",videoFeed.longitude) + "," 
					+ String.format("%.3f",targeter.heading) + "," + String.format("%.3f",targeter.lateralError) + "," + String.format("%.4f",targeter.timeToDrop)
					+ "," + String.format( "%.1f",targeter.getEstDropPosXMetres())  + "," + String.format( "%.1f", targeter.getEstDropPosYMetres()) + "," 
					+ videoFeed.isDropped + "," + String.format("%.1f", videoFeed.altAtDrop) + "," +  String.format("%.4f",targeter.actEstDropPosXMeters()) + "," 
					+ String.format("%.4f",targeter.actEstDropPosYMeters()) + "\n";
		LOGGER.finer(s); // Log to file only
	}
		
	//set enabled setting for all plane control buttons at once
	private void setControlButtons (boolean val) {
		btnDrop.setEnabled(val);
		btnToggleAutoDrop.setEnabled(val);
		btnCloseDropBay.setEnabled(val);
		btnSensorReset.setEnabled(val);
		btnPlaneRestart.setEnabled(val);
		btnRequestAltAtDrop.setEnabled(val);
		btnsEnabled = val;
	}
	
	//function called to update list of available COM ports
	private void updateCommPortSelector() {
		commPortSelector.removeAllItems();
		ArrayList<String> temp = serialComm.getPortList();
		String portList = "Available serial ports:";

		for (int i = 0; i < temp.size(); i++) {
			commPortSelector.addItem(temp.get(i));
			portList += temp.get(i) + ",";
		}
		LOGGER.info(portList);
	}
	
	//function containing/initializing all the GUI button action listeners
	private void initializeButtons() {
		
		//since original state is not connected to COM, initially set all control buttons to disabled
		setControlButtons(false);
			
		/* ACTION LISTENERS */	
				
			//when 'Clear' pressed.  Clears the console window 
			btnClearData.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					consoleTextArea.setText("");
				}
			});
			
			//when 'Refresh' pressed.  Refreshes the list of available COM ports
			btnRefresh.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					updateCommPortSelector();
				}
			});
			
			/*when 'Connect' pressed. if(connected) Tries to connect to the selected COM port.  This also initializes the time, some flags, 
			enables the plane control buttons, changes label to disconnect.  else - it disconnects from current COM port	 */
			btnConnect.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if (!serialComm.getConnected()) {
						String commPort = commPortSelector.getSelectedItem().toString();
						serialComm.connect(commPort);
						if (serialComm.getConnected()) {
							connectTime = System.currentTimeMillis();
							serialComm.addListener(MainWindow.this);
							btnConnect.setText("Disconnect");
							setControlButtons(true);
						}
					}
					else {
						serialComm.disconnect();
						if (!serialComm.getConnected()) {
							serialComm.removeListener(MainWindow.this);
							btnConnect.setText("Connect");
							setControlButtons(false);
						}
					}
				}
			});
			
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
			
			btnToggleAutoDrop.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					// Toggle autoDrop enable, the plane should respond to tell us what mode it is in
					serialComm.write('a');
					LOGGER.info("Toggle Auto-Drop sent.");
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
			
			btnUpdateTarget.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					gpsTargetDialog.setVisible(true);
				}
			});
	}/* END INITIALIZE BUTTONS */
	
	//called when drop command sent. Updates flag, prints out time and altitude of drop
	private void dropPackage() {
		serialComm.write('o');  //send drop command to arduino
		LOGGER.info("Drop package sent.");
		double time = (System.currentTimeMillis() - connectTime) / 1000.0;
		System.out.println(time + "s: Package dropped at: "+lblAlt.getText() + " ft");
		lblAltAtDrop.setText(lblAlt.getText());
		videoFeed.changeDropStatus(true);	
		targeter.setDropStatus(true);
	}
	
	
	//mess of a function intializing the layout of the GUI
	private void initializeComponents() {
		this.setLayout(new BorderLayout());
		
		/***** Left Side: *****/
		JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new GridBagLayout());
		// Minimum width is required to ensure that left panel displays properly.
		// If frame isn't big enough, take space away from right Panel
		int minWidth = 650;
		leftPanel.setMinimumSize(new Dimension(minWidth, 700));
		GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
		int preferredWidth = gd.getDisplayMode().getWidth()/2;  //half of screen width
		leftPanel.setPreferredSize(new Dimension(preferredWidth, 700));
		
		// Constraint object used for all panels within leftPanel:
		GridBagConstraints c = new GridBagConstraints();
		
		// dataPanel:
		JPanel dataPanel = initializeDataPanel(); // Top left panel containing roll, pitch, speed , altitude, etc.
		dataPanel.setMinimumSize(new Dimension(minWidth, 50));
		dataPanel.setPreferredSize(new Dimension(preferredWidth, 50));
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 1;
		c.gridheight = 1;
		c.weightx = 1;
		c.weighty = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		leftPanel.add(dataPanel, c);
		
		// commPortControlPanel:
		//Panel containing connect/disconnect/refresh buttons for connecting to Comm Port
		JPanel commPortControlPanel = initializeCommPortControlPanel();
		commPortControlPanel.setMinimumSize(new Dimension(minWidth, 80));
		commPortControlPanel.setPreferredSize(new Dimension(650, 80));
		c.gridx = 0;
		c.gridy = 1;
		c.gridwidth = 1;
		c.gridheight = 1;
		c.weightx = 1;
		c.weighty = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		leftPanel.add(commPortControlPanel, c);
		
		// servoButtonPanel
		JPanel servoPanel = initializeServoButtonPanel();
		servoPanel.setMinimumSize(new Dimension(minWidth, 100));
		servoPanel.setPreferredSize(new Dimension(650, 100));
		c.gridx = 0;
		c.gridy = 2;
		c.gridwidth = 1;
		c.gridheight = 1;
		c.weightx = 1;
		c.weighty = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		leftPanel.add(servoPanel, c);
		
		// gpsTargetPanel
		JPanel gpsTargetPanel = new JPanel();
		gpsTargetPanel.setBorder(new TitledBorder(new EtchedBorder(), "GPS Target Location"));
		gpsTargetPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		gpsTargetPanel.setMinimumSize(new Dimension(minWidth, 60));
		gpsTargetPanel.setPreferredSize(new Dimension(preferredWidth, 60));
		Font gpsTargetPanelFont = new Font(Font.SANS_SERIF, 0, 16);
		gpsTargetPanel.setFont(gpsTargetPanelFont);
		JLabel latLabel, lonLabel;
		latLabel = new JLabel("Latitude:");
		latLabel.setFont(gpsTargetPanelFont);
		lonLabel = new JLabel("Longitude:");
		lonLabel.setFont(gpsTargetPanelFont);
		gpsTargetPanelFont = new Font(Font.SANS_SERIF, Font.BOLD, 16);  //change values to bolded
		lblLat = new JLabel("");
		lblLat.setFont(gpsTargetPanelFont);
		lblLon = new JLabel("");
		lblLon.setFont(gpsTargetPanelFont);
		btnUpdateTarget = new JButton("Update Target");
		gpsTargetPanel.add(btnUpdateTarget);
		gpsTargetPanel.add(latLabel);
		gpsTargetPanel.add(lblLat);
		gpsTargetPanel.add(lonLabel);
		gpsTargetPanel.add(lblLon);
		gpsTargetPanel.add(btnUpdateTarget);
		c.gridx = 0;
		c.gridy = 3;
		c.gridwidth = 1;
		c.gridheight = 1;
		c.weightx = 1;
		c.weighty = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		leftPanel.add(gpsTargetPanel, c);
		
		// consolePanel
		//Panel containing the console (System.out is mapped to here)
		JPanel consolePanel = new JPanel();
		consolePanel.setBorder(new TitledBorder(new EtchedBorder(), "Console"));
		consolePanel.setMinimumSize(new Dimension(minWidth, 60));
		consolePanel.setPreferredSize(new Dimension(preferredWidth, 200));
		consoleTextArea = new JTextArea();
		JScrollPane consoleScroller = new JScrollPane(consoleTextArea);
		console = new PrintStream(new TextAreaOutputStream(consoleTextArea));
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
		videoFeedPanel.setMinimumSize(new Dimension(300, 250));
		videoFeed = new VideoFeed();
		videoFeedPanel.add(videoFeed, BorderLayout.CENTER);
		
		// GPS Targeter:
		JPanel gpsTargeterPanel = new JPanel();
		gpsTargeterPanel.setLayout(new BorderLayout());
		gpsTargeterPanel.setMinimumSize(new Dimension(400, 250));
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
		
		lblLat.setText(Double.toString(targeter.getTargetPos().getLatitude()));
		lblLon.setText(Double.toString(targeter.getTargetPos().getLongitude()));
	}
	
	private JPanel initializeServoButtonPanel() {
		//servo button panel
		JPanel servoButtonPanel = new JPanel();
		servoButtonPanel.setBorder(new TitledBorder(new EtchedBorder(), "Control Buttons"));
		servoButtonPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		
		btnEnable = new JButton("Enable/Disable Resets");
		servoButtonPanel.add(btnEnable);
		
		btnToggleAutoDrop = new JButton("Enable AutoDrop");
		servoButtonPanel.add(btnToggleAutoDrop);
		
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
		
		btnResetDrop = new JButton("Reset Drop");
		servoButtonPanel.add(btnResetDrop);		
		
		btnStartRecording = new JButton("Start/Stop Recording");
		servoButtonPanel.add(btnStartRecording);
		
		btnRestartStream = new JButton("Restart Video");
		servoButtonPanel.add(btnRestartStream);
		
		
		return servoButtonPanel;
	}
	
	private JPanel initializeCommPortControlPanel() {
		JPanel commPortControlPanel = new JPanel();
		commPortControlPanel.setLayout(new GridBagLayout());
		commPortControlPanel.setBorder(new TitledBorder(new EtchedBorder(), "Comm. Port"));
		GridBagConstraints c2 = new GridBagConstraints();
		c2.gridx = 0;
		c2.gridy = 0;
		c2.gridwidth = 3;
		c2.gridheight = 1;
		c2.weightx = 0;
		c2.weighty = 0;
		c2.anchor = GridBagConstraints.CENTER;
		commPortSelector = new JComboBox();
		commPortControlPanel.add(commPortSelector, c2);
		updateCommPortSelector();
		c2.gridx = 1;
		c2.gridy = 1;
		c2.gridwidth = 1;
		c2.gridheight = 1;
		c2.anchor = GridBagConstraints.CENTER;
		btnRefresh = new JButton("Refresh");
		commPortControlPanel.add(btnRefresh, c2);
		c2.gridx = 2;
		c2.gridy = 1;
		btnConnect = new JButton("Connect");
		commPortControlPanel.add(btnConnect, c2);
		c2.gridx = 3;
		c2.gridy = 1;
		btnClearData = new JButton("Clear");
		commPortControlPanel.add(btnClearData, c2);
		c2.gridx = 4;
		c2.gridy = 1;

		return commPortControlPanel;
	}
	private JPanel initializeDataPanel() {
		//data control panel. Top left panel containing curent roll/spd/pitch/alt
		JPanel dataPanel = new JPanel();
		dataPanel.setBorder(new TitledBorder(new EtchedBorder(), "Data"));
		dataPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		
		Font dataPanelFont = new Font(Font.SANS_SERIF, 0, 16);
		dataPanel.setFont(dataPanelFont);
		
		JLabel roll, pitch, speed, alt, head, sec, ms, altAtDrop;
		roll = new JLabel("Roll:");
		roll.setFont(dataPanelFont);
		pitch = new JLabel("Pitch:");
		pitch.setFont(dataPanelFont);
		speed = new JLabel("Speed:");
		speed.setFont(dataPanelFont);
		alt = new JLabel("Alt:");
		alt.setFont(dataPanelFont);
		head = new JLabel("Heading:");
		head.setFont(dataPanelFont);
		sec = new JLabel("Time:");
		sec.setFont(dataPanelFont);
		ms = new JLabel(".");
		ms.setFont(dataPanelFont);
		altAtDrop = new JLabel("Alt at Drop:");
		altAtDrop.setFont(dataPanelFont);
		
		dataPanelFont = new Font(Font.SANS_SERIF, Font.BOLD, 16);  //change values to bolded

		//If wanted to clean this up make an array of JLabels...
		lblRoll = new JLabel("");
		lblRoll.setFont(dataPanelFont);
		lblPitch = new JLabel("");
		lblPitch.setFont(dataPanelFont);
		lblSpeed = new JLabel("");
		lblSpeed.setFont(dataPanelFont);
		lblAlt = new JLabel("");
		lblAlt.setFont(dataPanelFont);
		lblHead = new JLabel("");
		lblHead.setFont(dataPanelFont);
		lblTS = new JLabel("");
		lblTS.setFont(dataPanelFont);
		lblAltAtDrop = new JLabel("");
		lblAltAtDrop.setFont(dataPanelFont);
	
		dataPanel.add(roll);
		dataPanel.add(lblRoll);
		dataPanel.add(pitch);
		dataPanel.add(lblPitch);
		dataPanel.add(speed);
		dataPanel.add(lblSpeed);
		dataPanel.add(alt);
		dataPanel.add(lblAlt);
		dataPanel.add(head);
		dataPanel.add(lblHead);
		dataPanel.add(sec);
		dataPanel.add(lblTS);
		dataPanel.add(altAtDrop);
		dataPanel.add(lblAltAtDrop);
		
		return dataPanel;
	}
	
	// Called by the gps target dialog:
	public void updateGPSTarget(Double lat, Double lon) {
		lblLon.setText(Double.toString(lon));
		lblLat.setText(Double.toString(lat));
		targeter.setTargetPos(lat,  lon);
	}
	
	//called from SerialCommunicator?
	public void invalidPacketReceived(String packet) {
		double time = (System.currentTimeMillis() - connectTime) / 1000.0;
		System.out.println(time + "s: Invalid packet recieved:" +packet);
	}
	
	//called from SerialCommunicator?
	public void packetReceived(String packet, byte[] byteArray) {
		//System.out.println(packet);
		analyzePacket(packet, byteArray);
	}
	
	//*p%ROLL%PITCH%ALTITUDE%AIRSPEED%LATTITUDE%LONGITUDE%HEADING%second%ms&  is old way
	//*pXXXXYYYYZZZZAAAABBBBCCCCDDDDsttee    is updated old way, 35 bytes longW
	//with XXXX = four bytes which combined make a float, s = 1 byte making uint8_t, tt = 2 bytes making uint16_t, 
	//*pXXXXYYYYZZZZAAAABBBBsttee is the newer way -> no longer sending roll or pitch, 27 bytes long
	
	
	//called from packetReceived, which is called by Serial communcator.  Analyzes a complete packet
	private void analyzePacket (String str, byte[] byteArray) {
		double time = (System.currentTimeMillis() - connectTime) / 1000.0;
		
		int byteArrayInd = 0;
			
		if(str.substring(0, 1).equals("p")){ //'p' indicates data packet
			
			double [] dblArr = new double [5]; //was 4 before, added LAT/Long/Heading/
			int [] timeArr = new int[2];  //S, MS
			
			for(int x = 0; x < 7; x++)  //extract 7 float values (which are cast to double)
			{	
				dblArr[x] = extractFloat(byteArray[byteArrayInd++],byteArray[byteArrayInd++],byteArray[byteArrayInd++],byteArray[byteArrayInd++]);
				
				//rounding code
				if(x != 4 && x != 5)  //round to 2 decimal places
					dblArr[x] = Math.round(100*dblArr[x])/100.0;
				else  //round to 4 decimal places (latt and long)
					dblArr[x] = Math.round(10000*dblArr[x])/10000.0;	
			
			}
			
			//extract time values
			timeArr[1] = extractuInt16(byteArray[byteArrayInd++],byteArray[byteArrayInd++]);
			timeArr[0] = extractuInt8(byteArray[byteArrayInd++]);  //seconds as uint8, since only need 0-60
			
			
			/*Old method
		if (str.substring(0, 1).equals("p")) {
			String [] strArr = str.split("%");
			double [] dblArr = new double [7]; //was 4 before, added LAT/Long/Heading/
			int [] timeArr = new int[2];  //S, MS
			try {
				for (int i = 1; i < 8; i++)  //start at 1 - since the first string is *p 
				{	dblArr[i-1] = Double.parseDouble(strArr[i]);
					
					//ensure no extra trailing 0 decimal places are displayed (prints nicer)
					if(i != 5 && i != 6)  //round to 2 decimal places
						dblArr[i-1] = Math.round(100*dblArr[i-1])/100.0;
					else  //round to 4 decimal places (latt and long)
						dblArr[i-1] = Math.round(10000*dblArr[i-1])/10000.0;									
				
				}
				
				for (int j = 8; j < 10; j++)
					timeArr[j-8] = Integer.parseInt(strArr[j]);
				
				
				
			} catch (Exception e) {
				System.out.println(time + "s: Encountered an invalid packet: \"" + str + "\"");
			}*/
			
			dblArr[3] = Math.round(10000*dblArr[3]*0.514444)/10000.0;  //CONVERT from knots TO m/s
			dblArr[5] *= -1;		//acount for the fact it should have 'W' attached (western hemisphere == negative longitude)
			
			//print to status area (top left)
			int ind = 0;
			//lblRoll.setText(""+dblArr[ind++]);  lblPitch.setText(""+dblArr[ind++]);	
			lblAlt.setText(""+dblArr[ind++]);	lblSpeed.setText(""+dblArr[ind++]);	
			//Skip lattitude, longitude
			lblHead.setText(""+dblArr[ind + 2]);  lblTS.setText(""+(timeArr[0]+timeArr[1]/1000.0));	
			
			LocalDateTime now = LocalDateTime.now();
			
			//Update data in VideoFeed Class 
			videoFeed.updateValues(dblArr[0], dblArr[1], dblArr[2], dblArr[3], dblArr[4], dblArr[5], dblArr[6], timeArr[0], timeArr[1]);
						
			//update targeting stuff
			targeter.updateGPSData(dblArr[2], dblArr[3], dblArr[4], dblArr[5], dblArr[6], timeArr[0], timeArr[1]);

			logData(); // Log the new state each time new data is received 
		}
		else if (str.substring(0, 1).equals("a")) {  //have requested the altitude at drop be returned
			String altAsString = str.substring(str.indexOf("a") + 1);  //remove a from front
			altAsString = altAsString.substring(0, altAsString.indexOf("%"));  //remove % and anything after it
			try{
				double altitudeAtDrop = Double.parseDouble(altAsString);			//parse remaining string double
				LOGGER.info("Altitude at drop = " + altitudeAtDrop);  //print result to console
			} catch(Exception e){  //Invalid double - still want to see what we got back
				
				System.out.print("Error while parsing returned altitude - raw string is:  ");
				System.out.println(str);
			}
		}
		else if (str.substring(0, 1).equals("s")) {
			LOGGER.info(time + "s: Start");
		}
		else if (str.substring(0, 1).equals("k")) {
			LOGGER.info(time + "s: Reset Acknowledge");
		}
		else if (str.substring(0, 1).equals("q")) {
			LOGGER.info(time + "s: Restart Acknowledge");
		}
		else if (str.substring(0, 1).equals("x")) {
			LOGGER.info(time + "s: Camera Reset Acknowledge");
		}
		else if (str.substring(0, 1).equals("e")) {
			LOGGER.info(time + "s: Error");
		}
		else if (str.substring(0, 1).equals("y")) {
			LOGGER.info(time + "s: Drop Acknowledge");
		}
		else if (str.substring(0, 1).equals("b")) {
			LOGGER.info(time + "s: Auto Drop ON confirmation.");
			targeter.setAutoDropEnabled(true);
			btnToggleAutoDrop.setText("Disable AutoDrop");
		}
		else if (str.substring(0, 1).equals("d")) {
			LOGGER.info(time + "s: Auto Drop OFF confirmation.");
			targeter.setAutoDropEnabled(false);
			btnToggleAutoDrop.setText("Enable AutoDrop");
		}
		else if (str.substring(0, 1).equals("o")) {
			LOGGER.info(time + "s: Open Drop Bay Acknowledge");
		}
		else if(str.substring(0, 1).equals("c")) {
			LOGGER.info(time + "s: Close Drop Bay Acknowledge");
		}
		else if (str.substring(0, 1).equals("1")) {
			LOGGER.info(time + "s: MPU6050 Ready");
		}
		else if (str.substring(0, 1).equals("2")) {
			LOGGER.info(time + "s: MPU6050 Failed");
		}
		else if (str.substring(0, 1).equals("3")) {
			LOGGER.info(time + "s: DMP Ready");
		}
		else if (str.substring(0, 1).equals("4")) {
			LOGGER.info(time + "s: DMP Failed");
		}
		else if (str.substring(0, 1).equals("5")) {
			LOGGER.info(time + "s: MPU6050 Initializing");
		}
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
