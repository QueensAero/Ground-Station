import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
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

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

/* Commented by Ryan Dowling, Dec. 2015
 * Not positive in all comments, but best guesses (I didn't write this code)  
 */

public class MainWindow extends JPanel implements PacketListener {
	
	public VideoFeed videoFeed;
	private JComboBox commPortSelector;
	private JButton btnRefresh, btnConnect; //connection buttons
	private JButton btnEnable, btnSave, btnClearData;
	private JButton btnDrop, btnCamLeft, btnCamRight, btnCamCenter, btnSensorReset, btnPlaneRestart; //servo control buttons
	private JButton btnStartRecording;  //button to start/stop recording
	public PrintStream console; //to display all console messages
	public PrintStream planeMessageConsole, dataLogger;
	private JTextArea planeMessageTextArea, dataLoggerTextArea, consoleTextArea;
	private JTextField servoControlTextBox;
	private JLabel lblRoll, lblPitch, lblSpeed, lblAlt, lblAltAtDrop; //labels to display the values
	private SerialCommunicator serialComm;
	JDialog calibrator;
	long connectTime;
	boolean btnsEnabled = false;
	boolean packageDropped = false; //status of the drop
	
	//constructor
	public MainWindow (SerialCommunicator sc) {
		serialComm = sc;
		initializeComponents();
		initializeButtons();
		
	}
	
	/*
	public void onShutDown(){ 
	    	
		//need to close the text output streams for program to terminate properly
		closeOutputStream();
    	
    
	}
	
	private void closeOutputStream(){
		this.console.close();
    	this.dataLogger.close();
    	this.planeMessageConsole.close();  
		
	}*/
	
	//set enabled setting for all plane control buttons at once
	private void setControlButtons (boolean val) {
		btnDrop.setEnabled(val);
		btnCamLeft.setEnabled(val);
		btnCamRight.setEnabled(val);
		btnCamCenter.setEnabled(val);
		btnSensorReset.setEnabled(val);
		btnPlaneRestart.setEnabled(val);
		servoControlTextBox.setEditable(val);
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
			//when type a char into box (this is treated like a button)
			servoControlTextBox.addKeyListener(new KeyAdapter() {
				public void keyPressed(KeyEvent e) {
					char val = e.getKeyChar();
					if (validChar(val)) {
						planeMessageConsole.println("Key sent: " + val);
						serialComm.write(val);
						if (val == 'P') {
							dropPackage();
						}
					}
					servoControlTextBox.setText("");
				}
			});
			
			
			
			//when 'Clear' pressed.  Clears the console and plane messages window 
			btnClearData.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					dataLoggerTextArea.setText("");
					dataLogger.println("TIME\tROLL\tPITCH\tALT\tSPEED");
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
						}
						else {
							btnSensorReset.setEnabled(false);
							btnPlaneRestart.setEnabled(false);
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
					serialComm.write('P');  //send drop command to arduino
					dropPackage();  //set's flags, prints current time and altitude
				}
			});
			
			//when 'Cam Left' pressed
			btnCamLeft.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					serialComm.write('a');
					planeMessageConsole.println("Cam left sent.");
				}
			});
			
			//when 'Cam Right' pressed
			btnCamRight.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					serialComm.write('d');
					planeMessageConsole.println("Cam right sent.");
				}
			});
			
			//when 'Cam Center' pressed
			btnCamCenter.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					serialComm.write('x');
					planeMessageConsole.println("Centre camera sent.");
				}
			});
			
			//when "reset sensor' pressed
			btnSensorReset.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					serialComm.write('r');
					planeMessageConsole.println("Reset sent.");
					dataLoggerTextArea.setText("");
					dataLogger.println("TIME\tROLL\tPITCH\tALT\tSPEED");
				}
			});
			
			//when 'Plane Reset' pressed 
			btnPlaneRestart.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					serialComm.write('q');  //send the command to plane
					planeMessageConsole.println("Restart sent."); 
					dataLoggerTextArea.setText("");  //delete old text
					dataLogger.println("TIME\tROLL\tPITCH\tALT\tSPEED");  //reprint information line
				}
			});
			
			//when Start/Stop Recording is pressed
			btnStartRecording.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					
					videoFeed.toggleRecordingStatus();  //call to function in VideoFeed to toggle the recording status
				}
			});
	}/* END INITIALIZE BUTTONS */
	
	//called when drop command sent. Updates flag, prints out time and altitude of drop
	private void dropPackage() {
		planeMessageConsole.println("Drop package sent.");
		double time = (System.currentTimeMillis() - connectTime) / 1000.0;
		System.out.println(time + "s: Package dropped at: "+lblAlt.getText());
		lblAltAtDrop.setText(lblAlt.getText());
		
		packageDropped = true; //flag variable
	}
	
	
	//mess of a function intializing the layout of the GUI
	private void initializeComponents() {
		this.setLayout(new GridLayout(0, 2, 0, 0));
		JPanel panel = new JPanel();
		panel.setLayout(new GridLayout(4, 1, 0, 0));
		
		JPanel topPanel = new JPanel();
		topPanel.setLayout(new GridLayout(2, 1, 0, 0));
		panel.add(topPanel);
		
		JPanel dataControlPanel = new JPanel();
		dataControlPanel.setBorder(new TitledBorder(new EtchedBorder(), "Data"));
		dataControlPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		JLabel roll, pitch, speed, alt, altAtDrop;
		roll = new JLabel("Roll:");
		pitch = new JLabel("Pitch:");
		speed = new JLabel("Speed:");
		alt = new JLabel("Alt:");
		altAtDrop = new JLabel("Alt at Drop:");

		lblRoll = new JLabel("");
		lblPitch = new JLabel("");
		lblSpeed = new JLabel("");
		lblAlt = new JLabel("");
		lblAltAtDrop = new JLabel("");
		
		lblRoll.setForeground(Color.GREEN);
		lblPitch.setForeground(Color.GREEN);
		lblSpeed.setForeground(Color.GREEN);
		lblAlt.setForeground(Color.GREEN);
		lblAltAtDrop.setForeground(Color.GREEN);
		
		dataControlPanel.add(roll);
		dataControlPanel.add(lblRoll);
		dataControlPanel.add(pitch);
		dataControlPanel.add(lblPitch);
		dataControlPanel.add(speed);
		dataControlPanel.add(lblSpeed);
		dataControlPanel.add(alt);
		dataControlPanel.add(lblAlt);
		dataControlPanel.add(altAtDrop);
		dataControlPanel.add(lblAltAtDrop);
		
		JPanel commPortControlPanel = new JPanel();
		commPortControlPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		commPortControlPanel.setBorder(new TitledBorder(new EtchedBorder(), "Comm. Port"));
		commPortSelector = new JComboBox();
		updateCommPortSelector();
		commPortControlPanel.add(commPortSelector);

		topPanel.add(dataControlPanel);
		topPanel.add(commPortControlPanel);

		btnRefresh = new JButton("Refresh");
		commPortControlPanel.add(btnRefresh);
		
		btnConnect = new JButton("Connect");
		commPortControlPanel.add(btnConnect);
		
		btnClearData = new JButton("Clear");
		commPortControlPanel.add(btnClearData);
		
		JPanel servoControlPanel = new JPanel();
		servoControlPanel.setBorder(new TitledBorder(new EtchedBorder(), "Controls"));

		panel.add(servoControlPanel);
		servoControlPanel.setLayout(new GridLayout(0, 2, 0, 0));
		
		JPanel servoButtonPanel = new JPanel();
		servoButtonPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		servoControlPanel.add(servoButtonPanel);
		
		btnEnable = new JButton("Enable/Disable Resets");
		servoButtonPanel.add(btnEnable);
		
		btnSave = new JButton("Save");
		servoButtonPanel.add(btnSave);
		
		btnDrop = new JButton("Drop");
		servoButtonPanel.add(btnDrop);
		
		btnCamLeft = new JButton("Cam Left");
		servoButtonPanel.add(btnCamLeft);
		
		btnCamRight = new JButton("Cam Right");
		servoButtonPanel.add(btnCamRight);
		
		btnCamCenter = new JButton("Cam Center");
		servoButtonPanel.add(btnCamCenter);
		
		btnSensorReset = new JButton("Sensor Reset");
		servoButtonPanel.add(btnSensorReset);
		
		btnPlaneRestart = new JButton("Plane Restart");
		servoButtonPanel.add(btnPlaneRestart);
		
		//RJD added
		btnStartRecording = new JButton("Start/Stop Recording");
		servoButtonPanel.add(btnStartRecording);
		
		
		JPanel servoTextPanel = new JPanel();
		servoControlPanel.add(servoTextPanel);
		commPortControlPanel.setLayout(new FlowLayout(FlowLayout.CENTER));

		servoControlTextBox = new JTextField();
		servoTextPanel.add(servoControlTextBox);
		servoControlTextBox.setColumns(10);
		servoTextPanel.add(new JLabel("Valid Chars are: P (drop), a (cam left), d (cam right), x (cam center)"));
		JPanel planeMessagePanel = new JPanel();
		planeMessagePanel.setBorder(new TitledBorder(new EtchedBorder(), "Plane Messages"));
		planeMessagePanel.setLayout(new BorderLayout());
		planeMessageTextArea = new JTextArea();
		JScrollPane planeMessageScroller = new JScrollPane(planeMessageTextArea);
		planeMessageConsole = new PrintStream(new TextAreaOutputStream(planeMessageTextArea));
		planeMessageScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		planeMessagePanel.add(planeMessageScroller);
		panel.add(planeMessagePanel);
		
		JPanel consolePanel = new JPanel();
		consolePanel.setBorder(new TitledBorder(new EtchedBorder(), "Console"));
		panel.add(consolePanel);
		consoleTextArea = new JTextArea();
		JScrollPane consoleScroller = new JScrollPane(consoleTextArea);
		console = new PrintStream(new TextAreaOutputStream(consoleTextArea));
		consolePanel.setLayout(new BorderLayout());
		consoleScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		System.setOut(console);
		System.setErr(console);
		consolePanel.add(consoleScroller);
		
		
		JPanel panel_1 = new JPanel();
		panel_1.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		
		
		int videoW = 720, videoH = 576, fpH = 200;  //VideoGrabber
		//int videoW = 640, videoH = 480, fpH = 200;  //webcam

				
		JPanel videoFeedArea = new JPanel();

		videoFeedArea.setLayout(new FlowLayout());

		
		videoFeedArea.setBorder(new TitledBorder(new EtchedBorder(), "Video Feed"));
		videoFeedArea.setMinimumSize(new Dimension(videoW, videoH + fpH));
		videoFeedArea.setPreferredSize(new Dimension(videoW, videoH + fpH));
		videoFeedArea.setMaximumSize(new Dimension(videoW, videoH + fpH));
		videoFeed = new VideoFeed();
		videoFeedArea.add(videoFeed);
		
		c.fill = GridBagConstraints.NONE;
		c.gridx = 0;
		c.gridy = 1;
		panel_1.add(videoFeedArea, c);

		JPanel logPanel = new JPanel();
		logPanel.setLayout(new BorderLayout());
		dataLoggerTextArea = new JTextArea();
		dataLoggerTextArea.setBorder(new TitledBorder(new EtchedBorder(), "Data Logger"));
		dataLoggerTextArea.setMinimumSize(new Dimension(640, 300));	//added
		JScrollPane dataLoggerScroller = new JScrollPane(dataLoggerTextArea);
		dataLogger = new PrintStream(new TextAreaOutputStream(dataLoggerTextArea));
		dataLoggerScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		logPanel.setBorder(new TitledBorder(new EtchedBorder(), "Data Logger"));
		logPanel.add(dataLoggerScroller, BorderLayout.CENTER);
		
		c.fill = GridBagConstraints.BOTH;
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1.0;
		c.weighty = 1.0;
		
		panel_1.add(logPanel, c);
		dataLogger.println("TIME\tROLL\tPITCH\tALT\tSPEED");

		this.add(panel);
		this.add(panel_1);
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
	
	//does checking for the character entered into GUI - checks there is an associated command
	private boolean validChar (char ch) {
		if (ch == 'P' || ch == 'a' || ch == 'd'|| ch == 'x') {
			return true;
		}
		return false;
	}
	
	//called from packetReceived, which is called by Serial communcator.  Analyzes a complete packet
	private void analyzePacket (String str) {
		double time = (System.currentTimeMillis() - connectTime) / 1000.0;
		if (str.substring(0, 1).equals("p")) {
			String [] strArr = str.split("%");
			double [] dblArr = new double [5];
			try {
				for (int i = 1; i < 5; i++) {
					dblArr[i] = Double.parseDouble(strArr[i]);
					dblArr[i] *= 100;
					dblArr[i] = Math.round(dblArr[i]) / 100.0;
				}
			} catch (Exception e) {
				System.out.println(time + "s: Encountered an invalid packet: \"" + str + "\"");
			}
			lblRoll.setText(""+dblArr[1]);
			lblPitch.setText(""+dblArr[2]);
			lblAlt.setText(""+dblArr[3]);
			lblSpeed.setText(""+dblArr[4]);
			dataLogger.println(time + "\t" + dblArr[1] + "\t" + dblArr[2] + "\t" + dblArr[3] + "\t" + dblArr[4]);
			
			//Update data in VideoFeed Class
			videoFeed.updateValues(dblArr[1], dblArr[2], dblArr[3], dblArr[4], packageDropped);
			
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
