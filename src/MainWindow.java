import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;

import javax.swing.JPanel;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
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
	
	public VideoFeed videoFeed;
	public Targeter targeter;
	private JComboBox commPortSelector;
	private JButton btnRefresh, btnConnect; //connection buttons
	private JButton btnEnable, btnSave, btnClearData, btnRequestAltAtDrop;
	private JButton btnDrop, btnSensorReset, btnPlaneRestart; //servo control buttons
	private JButton btnStartRecording, btnEnterBypass, btnRestartStream;  //button to start/stop recording
	private InputMap keyboardIn;
	public PrintStream console; //to display all console messages
	public PrintStream planeMessageConsole, dataLogger;
	private JTextArea planeMessageTextArea, dataLoggerTextArea, consoleTextArea;
	private JLabel lblRoll, lblPitch, lblSpeed, lblAlt, lblLatt, lblLong, lblHead, lblSec, lblMs, lblAltAtDrop; //labels to display the values
	private SerialCommunicator serialComm;
	JDialog calibrator;
	long connectTime;
	boolean btnsEnabled = false;
	
	//constructor
	public MainWindow (SerialCommunicator sc) {
		serialComm = sc;
		initializeComponents();
		initializeButtons();
		
	}
	
		
	//set enabled setting for all plane control buttons at once
	private void setControlButtons (boolean val) {
		btnDrop.setEnabled(val);
		btnSensorReset.setEnabled(val);
		btnPlaneRestart.setEnabled(val);
		btnEnterBypass.setEnabled(val);
		btnRequestAltAtDrop.setEnabled(val);
		btnsEnabled = val;
	}
	
	//function called to update list of available COM ports
	private void updateCommPortSelector() {
		commPortSelector.removeAllItems();
		ArrayList<String> temp = serialComm.getPortList();
		System.out.println("Available serial ports:");

		for (int i = 0; i < temp.size(); i++) {
			commPortSelector.addItem(temp.get(i));
			System.out.print(temp.get(i) + ",");
		}
		System.out.println();
	}
	
	//function containing/initializing all the GUI button action listeners
	private void initializeButtons() {
		
		//since original state is not connected to COM, initially set all control buttons to disabled
		setControlButtons(false);
		
			
		/* ACTION LISTENERS */	
			
				
			//when 'Clear' pressed.  Clears the console and plane messages window 
			btnClearData.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					dataLoggerTextArea.setText("");
					dataLogger.println("TIME, ROLL, PITCH, ALT, SPEED, LATT, LONG, HEAD, TIME");
					planeMessageTextArea.setText("");
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
							btnEnterBypass.setEnabled(true);
							btnRequestAltAtDrop.setEnabled(true);
						}
						else {
							btnSensorReset.setEnabled(false);
							btnPlaneRestart.setEnabled(false);
							btnStartRecording.setEnabled(false);
							btnEnterBypass.setEnabled(false);
							btnRequestAltAtDrop.setEnabled(false);
						}
					}
					else {
						System.out.println("Cannot enable sensor/plane resets. Comm. port not connected.");
					}
				}
			});
			
			
			//when 'Save' pressed -> handles the saving of the received data area
			btnSave.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
	                JFileChooser saveFile = new JFileChooser();
	                saveFile.addChoosableFileFilter(new FileNameExtensionFilter("Text File (*.txt)", ".txt"));
	                saveFile.setFileSelectionMode(JFileChooser.FILES_ONLY);
	                int val = saveFile.showSaveDialog(null);
	                if (val == JFileChooser.APPROVE_OPTION) {                		
	                	String filename = saveFile.getSelectedFile().toString();
	                	try {
	                	if (saveFile.getFileFilter().getDescription().equals("Text File (*.txt)"))
	                		filename += ".txt";
	                	} catch (Exception err) {}
	                	FileWriter fstream = null;
	                	BufferedWriter writer = null;
	                	String messages = planeMessageTextArea.getText();
	                	String data = dataLoggerTextArea.getText();
	                	System.out.println("Attempting to save data as \"" + filename + "\"");
	                	try {
	                		fstream = new FileWriter(filename);
	                		writer = new BufferedWriter(fstream);
	                		writer.write("Plane Messages:\n");
	                		writer.write(messages);
	                		writer.write("Data:\n");
							writer.write(data);
							writer.close();
							System.out.println("File saved succesfully.");
	                	} catch (IOException err) {
	                		System.out.println("Error saving file.");
	                		err.printStackTrace();
	                	}
	                }
				}
			});
			
			//when 'Drop' pressed
			btnDrop.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					dropPackage();  //sends command, set's flags, prints current time and altitude
				}
			});
			
			//this maps the keyboard shortcut CTRL+SHIFT+D to the drop command
			this.getActionMap().put("dropHandle", new AbstractAction(){
				@Override
				public void actionPerformed(ActionEvent e) {
					if(btnDrop.isEnabled())
						dropPackage();
					else
						planeMessageConsole.println("Shortcut ignored, drop not yet enabled");
	            }				
			});
			
			this.getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_D,KeyEvent.CTRL_DOWN_MASK + KeyEvent.SHIFT_DOWN_MASK),"dropHandle");
			
			
			
			
			//when "reset sensor' pressed
			btnSensorReset.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					serialComm.write('r');
					planeMessageConsole.println("Reset sent.");
					dataLoggerTextArea.setText("");
					dataLogger.println("TIME, ROLL, PITCH, ALT, SPEED, LATT, LONG, HEAD, TIME");
				}
			});
			
			//when 'Plane Reset' pressed 
			btnPlaneRestart.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					serialComm.write('q');  //send the command to plane
					planeMessageConsole.println("Restart sent."); 
					dataLoggerTextArea.setText("");  //delete old text
					dataLogger.println("TIME, ROLL, PITCH, ALT, SPEED, LATT, LONG, HEAD, TIME");
				}
			});
			
			//when Start/Stop Recording is pressed
			btnStartRecording.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					
					videoFeed.toggleRecordingStatus();  //call to function in VideoFeed to toggle the recording status
				}
			});
			
			//when "Send '+++'" pressed
			btnEnterBypass.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					
					serialComm.bypassModeRoutine();
				}
			});
			
			btnRestartStream.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					
					videoFeed.restartVideoStream();
				}
			});
			
			btnRequestAltAtDrop.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					serialComm.write('g');  //send command to request the altitude at drop value					
				}
			});
			
			
	}/* END INITIALIZE BUTTONS */
	
	//called when drop command sent. Updates flag, prints out time and altitude of drop
	private void dropPackage() {
		serialComm.write('P');  //send drop command to arduino
		planeMessageConsole.println("Drop package sent.");
		double time = (System.currentTimeMillis() - connectTime) / 1000.0;
		System.out.println(time + "s: Package dropped at: "+lblAlt.getText() + " ft");
		lblAltAtDrop.setText(lblAlt.getText());
		videoFeed.changeDropStatus(true);	
		targeter.setDropStatus(true);
		
	}
	
	
	//mess of a function intializing the layout of the GUI
	private void initializeComponents() {
		
		/*general idea is that GridLayout is overriding structure:  two columns, each of the same size (as required for GridLayout
		* Next each of those column can have panels within them with a grid layout. Ie. left side (called leftPanel) has 5 columns
		* Right side 
		*/
				
		this.setLayout(new GridLayout(0, 2, 0, 0));  //set overall layout

		
		JPanel leftPanel = new JPanel();
		//leftPanel.setLayout(new GridLayout(4, 1, 0, 0));  //changed from 4 rows to 5 rows, to move dataLogger to left side
		leftPanel.setLayout(new GridBagLayout());
		
		
		//Top panel eventually has the data control panel &  commPortControl panel added to it.  This is top left 2 panels
		//JPanel topPanel = new JPanel();
		//topPanel.setLayout(new GridLayout(2, 1, 0, 0));
		
		//this is used to size the buttons panels to minimum possible size (when adding to leftPanel weight will be 0)
		JPanel buttonsPanels = new JPanel();
		buttonsPanels.setLayout(new GridLayout(3, 1, 0, 0));

		
		//data control panel. Top left panel containing curent roll/spd/pitch/alt
		JPanel dataPanel = new JPanel();
		dataPanel.setBorder(new TitledBorder(new EtchedBorder(), "Data"));
		dataPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		Font dataPanelFont = new Font(Font.SANS_SERIF, 0, 18);
		dataPanel.setFont(dataPanelFont);
		JLabel roll, pitch, speed, alt, latt, longit, head, sec, ms, altAtDrop;
		roll = new JLabel("Roll:");
		roll.setFont(dataPanelFont);
		pitch = new JLabel("Pitch:");
		pitch.setFont(dataPanelFont);
		speed = new JLabel("Speed:");
		speed.setFont(dataPanelFont);
		alt = new JLabel("Alt:");
		alt.setFont(dataPanelFont);
		latt = new JLabel("\nLatt:");
		latt.setFont(dataPanelFont);
		longit = new JLabel("Long:");
		longit.setFont(dataPanelFont);
		head = new JLabel("Heading:");
		head.setFont(dataPanelFont);
		sec = new JLabel("Time:");
		sec.setFont(dataPanelFont);
		ms = new JLabel(".");
		ms.setFont(dataPanelFont);
		altAtDrop = new JLabel("Alt at Drop:");
		altAtDrop.setFont(dataPanelFont);


		//If wanted to clean this up make an array of JLabels...
		lblRoll = new JLabel("");
		lblPitch = new JLabel("");
		lblSpeed = new JLabel("");
		lblAlt = new JLabel("");
		lblLatt = new JLabel("");
		lblLong = new JLabel("");
		lblHead = new JLabel("");
		lblSec = new JLabel("");
		lblMs = new JLabel("");
		lblAltAtDrop = new JLabel("");
		
		
		//lblRoll.setForeground(Color.GREEN);  //left as sample how to change font colour
	
		dataPanel.add(roll);
		dataPanel.add(lblRoll);
		dataPanel.add(pitch);
		dataPanel.add(lblPitch);
		dataPanel.add(speed);
		dataPanel.add(lblSpeed);
		dataPanel.add(alt);
		dataPanel.add(lblAlt);
		
		dataPanel.add(latt);
		dataPanel.add(lblLatt);
		dataPanel.add(longit);
		dataPanel.add(lblLong);
		dataPanel.add(head);
		dataPanel.add(lblHead);
		dataPanel.add(sec);
		dataPanel.add(lblSec);
		dataPanel.add(ms);
		dataPanel.add(lblMs);		
		dataPanel.add(altAtDrop);
		dataPanel.add(lblAltAtDrop);
		
		
		
		
		//Panel containing connect/disconnect/refresh buttons for connecting to Comm Port
		JPanel commPortControlPanel = new JPanel();
		commPortControlPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		commPortControlPanel.setBorder(new TitledBorder(new EtchedBorder(), "Comm. Port"));
		commPortSelector = new JComboBox();
		commPortControlPanel.add(commPortSelector);

		//topPanel.add(dataPanel);
		//topPanel.add(commPortControlPanel);

		btnRefresh = new JButton("Refresh");
		commPortControlPanel.add(btnRefresh);
		
		btnConnect = new JButton("Connect");
		commPortControlPanel.add(btnConnect);
		
		btnClearData = new JButton("Clear");
		commPortControlPanel.add(btnClearData);
		commPortControlPanel.setLayout(new FlowLayout(FlowLayout.CENTER));

		
		
		//servo button panel
		JPanel servoButtonPanel = new JPanel();
		servoButtonPanel.setBorder(new TitledBorder(new EtchedBorder(), "Control Buttons"));
		servoButtonPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		
		btnEnable = new JButton("Enable/Disable Resets");
		servoButtonPanel.add(btnEnable);
		
		btnSave = new JButton("Save");
		servoButtonPanel.add(btnSave);
		
		btnDrop = new JButton("Drop");
		servoButtonPanel.add(btnDrop);
		
		//R Dowlling added
		btnEnterBypass = new JButton("Enter Bypass");
		servoButtonPanel.add(btnEnterBypass);
		
		btnSensorReset = new JButton("Sensor Reset");
		servoButtonPanel.add(btnSensorReset);
		
		btnPlaneRestart = new JButton("Plane Restart");
		servoButtonPanel.add(btnPlaneRestart);
		
		btnRequestAltAtDrop = new JButton("Get Alt @ Drop");
		servoButtonPanel.add(btnRequestAltAtDrop);
		
		btnStartRecording = new JButton("Start/Stop Recording");
		servoButtonPanel.add(btnStartRecording);
		
		btnRestartStream = new JButton("Restart Video");
		servoButtonPanel.add(btnRestartStream);
		
		

		
		//Condense above three jpanels into the buttons panel
		buttonsPanels.add(dataPanel);
		buttonsPanels.add(commPortControlPanel);
		buttonsPanels.add(servoButtonPanel);
		
		
		//Panel containing the plane messages.  This is above Console panel
		JPanel planeMessagePanel = new JPanel();
		planeMessagePanel.setBorder(new TitledBorder(new EtchedBorder(), "Plane Messages"));
		planeMessagePanel.setLayout(new BorderLayout());
		planeMessageTextArea = new JTextArea();
		JScrollPane planeMessageScroller = new JScrollPane(planeMessageTextArea);
		planeMessageConsole = new PrintStream(new TextAreaOutputStream(planeMessageTextArea));
		planeMessageScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		DefaultCaret planeMessageCaret = (DefaultCaret)planeMessageTextArea.getCaret();
		planeMessageCaret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
		planeMessagePanel.add(planeMessageScroller);
		
		
		//Panel containing the console (System.out is mapped to here)
		JPanel consolePanel = new JPanel();
		consolePanel.setBorder(new TitledBorder(new EtchedBorder(), "Console"));
		consoleTextArea = new JTextArea();
		JScrollPane consoleScroller = new JScrollPane(consoleTextArea);
		console = new PrintStream(new TextAreaOutputStream(consoleTextArea));
		consolePanel.setLayout(new BorderLayout());
		consoleScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		DefaultCaret consoleCaret = (DefaultCaret)consoleTextArea.getCaret();
		consoleCaret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
		System.setOut(console);
		System.setErr(console);
		consolePanel.add(consoleScroller);

		
		//panel cotaining the data logging
		JPanel logPanel = new JPanel();
		logPanel.setLayout(new BorderLayout());
		dataLoggerTextArea = new JTextArea();
		dataLoggerTextArea.setBorder(new TitledBorder(new EtchedBorder(), "Data Logger"));
		dataLoggerTextArea.setMinimumSize(new Dimension(640, 300));	//added, don't think it does anything
		JScrollPane dataLoggerScroller = new JScrollPane(dataLoggerTextArea);
		dataLogger = new PrintStream(new TextAreaOutputStream(dataLoggerTextArea));
		dataLoggerScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		DefaultCaret dataLoggerCater = (DefaultCaret)dataLoggerTextArea.getCaret();
		dataLoggerCater.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
		logPanel.setBorder(new TitledBorder(new EtchedBorder(), "Data Logger"));
		logPanel.add(dataLoggerScroller, BorderLayout.CENTER);
		dataLogger.println("TIME, ROLL, PITCH, ALT, SPEED, LATT, LONG, HEAD, TIME");

			
		//
		GridBagConstraints c = new GridBagConstraints(); //constraints for the overall grid (I think)
		c.gridx = 0;
		c.gridy = GridBagConstraints.RELATIVE;
		c.weightx = 1;
		c.weighty = 0;
		c.fill = GridBagConstraints.BOTH;
		
		
		//Add the sub JPanels to the LHS JPanel
		//leftPanel.add(dataPanel);
		//leftPanel.add(commPortControlPanel);
		//leftPanel.add(servoControlPanel);
		leftPanel.add(buttonsPanels,c);
		c.weighty = 1;
		leftPanel.add(planeMessagePanel,c);
		c.weighty = 0.5;
		leftPanel.add(consolePanel,c);  
		c.weighty = 1;
		leftPanel.add(logPanel,c);  //panel_1.add(logPanel, c);
		
		//once System.out has area it's mapped to in GUI, we can update Comm ports
		updateCommPortSelector();
		
		
		//RIGHT HAND SIDE
		JPanel rightPanel = new JPanel();
		rightPanel.setLayout(new GridBagLayout());  //changed from 4 rows to 5 rows, to move dataLogger to left side
		
		//declare videoFeed class
		videoFeed = new VideoFeed();
		int videoW, videoH, fpHeight = 200;
		if(videoFeed.getVideoSrc() == 0)
		{
			videoW = 640; videoH = 480;   //webcam
		}
		else
		{
			videoW = 720; videoH = 576;  //VideoGrabber
		}
		int totalVidHeight = videoH + fpHeight;
		
		
		//create videofeed jpanel
		JPanel videoFeedArea = new JPanel();
		videoFeedArea.setMinimumSize(new Dimension(videoW, totalVidHeight));
		videoFeedArea.add(videoFeed);
		
		
		//create target jpanel on right side (this has the rings)
		targeter = new Targeter();
		JPanel targeterPanel = new JPanel();
		targeterPanel.setMinimumSize(new Dimension(targeter.getCols(), targeter.getRows()));
		targeterPanel.add(targeter);
		
		//define constraints for adding JPanels to the right side
		GridBagConstraints rightPanelC = new GridBagConstraints(); //constraints for the overall grid (I think)
		rightPanelC.gridx = 0;
		rightPanelC.gridy = 0;
		rightPanelC.anchor = GridBagConstraints.PAGE_START;
		
		//add subpanels to right sdie
		rightPanel.add(videoFeedArea,rightPanelC);
		rightPanelC.gridy = 1;
		rightPanel.add(targeter,rightPanelC);

		
		//add the left and right panel to overal JPanel (this class extends JPanel)
		this.add(leftPanel);
		this.add(rightPanel);
	}
	
	//called from SerialCommunicator?
	public void invalidPacketReceived(String packet) {
		double time = (System.currentTimeMillis() - connectTime) / 1000.0;
		System.out.println(time + "s: Invalid packet recieved:" +packet);
	}
	
	//called from SerialCommunicator?
	public void packetReceived(String packet) {
		//System.out.println(packet);
		analyzePacket(packet);
	}
	
	//*p%ROLL%PITCH%ALTITUDE%AIRSPEED%LATTITUDE%LONGITUDE%HEADING%second%ms&
	
	//called from packetReceived, which is called by Serial communcator.  Analyzes a complete packet
	private void analyzePacket (String str) {
		double time = (System.currentTimeMillis() - connectTime) / 1000.0;
		if (str.substring(0, 1).equals("p")) {
			//with bytewise representation
			//http://stackoverflow.com/questions/13469681/how-to-convert-4-bytes-array-to-float-in-java
			//something like 
			
			
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
			}
			
			dblArr[3] *= 0.514444;  //CONVERT from knots TO m/s
			dblArr[5] *= -1;		//acount for the fact it should have 'W' attached (western hemisphere == negative longitude)
			
			//print to status area (top left)
			lblRoll.setText(""+dblArr[0]);  
			lblPitch.setText(""+dblArr[1]);
			lblAlt.setText(""+dblArr[2]);
			lblSpeed.setText(""+dblArr[3]);
			lblLatt.setText(""+dblArr[4]);  
			lblLong.setText(""+dblArr[5]);
			lblHead.setText(""+dblArr[6]);  
			lblSec.setText(""+timeArr[0]);
			lblMs.setText(""+timeArr[1]);
			
			//print to logging screen
			dataLogger.println(time + "," + dblArr[0] + "," + dblArr[1] + "," + dblArr[2] + "," + dblArr[3] + "," + dblArr[4] + "," + dblArr[5] + "," + dblArr[6] 
									+ "," + + timeArr[0] + "." + timeArr[1]  );
			
			//Update data in VideoFeed Class (it separately logs, 
			videoFeed.updateValues(dblArr[0], dblArr[1], dblArr[2], dblArr[3], dblArr[4], dblArr[5], dblArr[6], timeArr[0], timeArr[1]);
						
			//update targeting stuff
			targeter.updateGPSData(dblArr[2], dblArr[3], dblArr[4], dblArr[5], dblArr[6], timeArr[0], timeArr[1]);

			
		}
		else if (str.substring(0, 1).equals("a")) {  //have requested the altitude at drop be returned
			String altAsString = str.substring(str.indexOf("a") + 1);  //remove *a from front
			altAsString = altAsString.substring(0, altAsString.indexOf("%"));  //remove % from end
			double altitudeAtDrop = Double.parseDouble(altAsString);			//parse remaining string double
			planeMessageConsole.println("Altitude at drop = " + altitudeAtDrop);  //print result to console
		}
		else if (str.substring(0, 1).equals("s")) {
			planeMessageConsole.println(time + "s: Start");
		}
		else if (str.substring(0, 1).equals("k")) {
			planeMessageConsole.println(time + "s: Reset Acknowledge");
		}
		else if (str.substring(0, 1).equals("q")) {
			planeMessageConsole.println(time + "s: Restart Acknowledge");
		}
		else if (str.substring(0, 1).equals("x")) {
			planeMessageConsole.println(time + "s: Camera Reset Acknowledge");
		}
		else if (str.substring(0, 1).equals("e")) {
			planeMessageConsole.println(time + "s: Error");
		}
		else if (str.substring(0, 1).equals("y")) {
			planeMessageConsole.println(time + "s: Drop Acknowledge");
		}
		else if (str.substring(0, 1).equals("1")) {
			planeMessageConsole.println(time + "s: MPU6050 Ready");
		}
		else if (str.substring(0, 1).equals("2")) {
			planeMessageConsole.println(time + "s: MPU6050 Failed");
		}
		else if (str.substring(0, 1).equals("3")) {
			planeMessageConsole.println(time + "s: DMP Ready");
		}
		else if (str.substring(0, 1).equals("4")) {
			planeMessageConsole.println(time + "s: DMP Failed");
		}
		else if (str.substring(0, 1).equals("5")) {
			planeMessageConsole.println(time + "s: MPU6050 Initializing");
		}
	}
}
