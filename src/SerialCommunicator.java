import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.TooManyListenersException;
import java.util.logging.Logger;

/* Commented by Ryan Dowling, Dec. 2015
 * Not positive in all comments, but best guesses (I didn't write this code)  
 */

interface PacketListener {
	void packetReceived(String packet, byte[] byteArray);
	void invalidPacketReceived(String packet);
}


public class SerialCommunicator implements SerialPortEventListener, PacketListener {
	private static final Logger LOGGER = Logger.getLogger(AeroGUI.class.getName());
	
	ArrayList<PacketListener> listeners = new ArrayList<PacketListener>();
	
	private HashMap<String, CommPortIdentifier> portMap = new HashMap<String, CommPortIdentifier>();    	
	private CommPortIdentifier selectedPortIdentifier;
	private Enumeration<CommPortIdentifier> portList;
	private ArrayList<String> portStringList = new ArrayList<String>();
	private SerialPort serialPort;
	private boolean connected = false;
    private InputStream input;
    private OutputStream output;
    
	final static int timeout = 2000;
	final static int BAUD_RATE = 115200;  
	final static int START_CHAR = 42;
	final static int END_CHAR = 101;
	final static int DATA_PACKET_L = 37;  //was 27 before changes



	
	private StringBuffer received = new StringBuffer();
	private byte[] receivedBytes = new byte[800];
	private byte[] receivedPacket = new byte[800];
	private int byteInd = 0, packetStartInd = -1, packetEndInd = -1;
	
	//constructor
	public SerialCommunicator() {
		updatePortList();
	}
	
	public void removeListener(PacketListener listener) {
		for (int i = 0; i < listeners.size(); i++) {
			if (listeners.get(i).equals(listener))
				listeners.remove(i);
		}
	}
	
	//called by MainWindow to make MainWindow a listener to this class
	public void addListener(PacketListener listener) {
		listeners.add(listener);
	}
	
	//accessor that updates the port list (calls FN) then returns string containing the available ports
	public ArrayList<String> getPortList () {
		updatePortList();
		return portStringList;
	}
	
	public String getCurrentPortName() {
		if(connected)
			return serialPort.getName();
		else
			return "";
	}
	
	//update the available COM serial ports list
	private void updatePortList() {
		
		//get a list of the available COM ports
    	portList = CommPortIdentifier.getPortIdentifiers();
    	
    	//clear the old list
    	portStringList.clear();
    	
    	//loop through each available COM port
    	while(portList.hasMoreElements()) {
    		
    		//get the information for the next element
    		CommPortIdentifier currPort = (CommPortIdentifier)portList.nextElement();
    		
    		//if it's a serial port, than add it to list of available COM Serial Ports
    		if (currPort.getPortType() == CommPortIdentifier.PORT_SERIAL) {
    			portMap.put(currPort.getName(), currPort);
    			portStringList.add(currPort.getName());
    		}
    	}
    }

	//Connect to selected COM port
    public void connect(String selectedPort) {
    	
    	received.setLength(0);  //information received is set (or reset) to 0 for new connection
    	byteInd = 0;
    	packetStartInd = packetEndInd = -1;
    	
    	selectedPortIdentifier = (CommPortIdentifier)portMap.get(selectedPort); //get information of the selected port 
    	CommPort commPort;  //temporary variable see below - is it even necessary?
    	
    	
    	try {
    		commPort = selectedPortIdentifier.open(this.getClass().getName(), timeout);
    		serialPort = (SerialPort) commPort;
    		//Why not just do: serialPort = (SerialPort) selectedPortIdentifier.open(this.getClass().getName(), timeout);
    		
    		//set parameters (Baud rate, #data bits, # stop bits, bit parity
    		serialPort.setSerialPortParams(BAUD_RATE,  SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
    		
    		
    		//update status bool and print success to console
    		connected = true;  
    		LOGGER.info(selectedPort + " opened succesfully.");
    		
    	} catch (Exception e) {
    		LOGGER.warning("Failed to connect to " + selectedPort);
    		e.printStackTrace();
    	}
    	
    	//in connected, then call FN to initialize the input and output streams. 
		if (connected) {
			if (initStreams() == true) {		
				
				//IF doing anything with command mode, do it here, before event listener 'catches' return packets
				int maxNumTries = 2, numTries = 0;
				while(++numTries <= maxNumTries && !initXBeeMode());  //Try twice before giving up			
				
				//After this call, the listener handles all incoming bytes
				initEventListener();  //add event listeners to input and output streams
			}
		}
    }
    
    //get rid of everything in input serial buffer
    private void flushInputBuffer()
    {
    	int numRemoved = 0;  //in case data comes too fast, have a limit of how much to flush
       	try {
       		while(true)
       		{
       			if(input.available() > 0 && numRemoved++ < 200)  //will be while still data
       				input.read();  //keep calling, flushing one bytes at a time
       			else       								
       				break;
       		}
		} catch (IOException e) {				}    	
    }
    
    
    
    private static String COMMAND_MODE_CMD = "+++", SWITCH_TO_AT_CMD = "ATAP0\r", EXIT_COMMAND_MODE_CMD = "ATCN\r", COMMAND_MODE_OK = "OK\r";
    private static int INTO_CMD_MODE_TIMEOUT = 3000, RESPONSE_TIMEOUT = 750; 

    
    private boolean initXBeeMode()  {
    	
    	/* This function is critical. When the XBee restarts, it defaults to API mode. In API mode, every received packet will have things appended to it.
    	 * So it will still receive informatinon. HOWEVER: the packets sent to the XBee are in API mode, and thus will be ignored by the XBee on the plane. THerefore we won't 
    	 * be able to send any commands, such as 'drop', 'enable autodrop' etc.
    	 * 
    	 */
    	
    	int resp = 0;
    	
    	//Enter command mode
    	if((resp = sendCmdAndWaitForOK(COMMAND_MODE_CMD, INTO_CMD_MODE_TIMEOUT)) == CMD_FAILED)
    	{
			LOGGER.warning("Failed when sending AT Command: " + COMMAND_MODE_CMD + "  (Enter command mode)");
			return false;
    	}
    	else if(resp == CMD_ALREADY_IN_TRANSPARENT)  //we are already in transparent mode, no need to try the rest
    		return true;    		
    	
    	
    	//Switch to transparent mode
    	if((resp = sendCmdAndWaitForOK(SWITCH_TO_AT_CMD, RESPONSE_TIMEOUT)) == CMD_FAILED)
    	{
			LOGGER.warning("Failed when sending AT Command: " + SWITCH_TO_AT_CMD + "  (Switch to transparent)");
			return false;
    	}
    	else if(resp == CMD_ALREADY_IN_TRANSPARENT)  //we are already in transparent mode, no need to try the rest
    		return true; 
    	
    	//Exit Command Mode
    	if((resp = sendCmdAndWaitForOK(EXIT_COMMAND_MODE_CMD, RESPONSE_TIMEOUT)) == CMD_FAILED)
    	{
			LOGGER.warning("Failed when sending AT Command: " + EXIT_COMMAND_MODE_CMD + "   (Exit command mode)");
			return false;
    	}
    	//No need for else if 
    	
    	LOGGER.info("Successfully initialized ground station XBee");
    	
    	return true;  //If succesfull in all above steps, XBee init successfully  
    	
		
	}
    
    //Returns - 0 = failed (no/incorrect resp), 1 = success, 2 = getting lots of data, must already be in transparent mode
    final static int CMD_FAILED = 0,  //NOTE -> if changes, must also update in SerialCommunicator
    				 CMD_SUCCESS = 1,
    				 CMD_ALREADY_IN_TRANSPARENT = 2;
    private int sendCmdAndWaitForOK(String cmd, int timeout)
    {
		
		try {
			//Remove anything sitting waiting to be processed
			flushInputBuffer();
			
			// Send the command mode sequence.
			output.write(cmd.getBytes());
			
			long st = System.currentTimeMillis();
			StringBuffer response = new StringBuffer();
			String responseStr;
			int newByte;
			
			while(System.currentTimeMillis() - st  < timeout)
			{
				if(input.available() > 0)
				{
					newByte = input.read();
					response.append(new String(new byte[] {(byte)newByte}));
					responseStr = response.toString();									
			
					if(responseStr.contains(COMMAND_MODE_OK))
					{
						//LOGGER.info("Successful Response: " + response);
						return CMD_SUCCESS;	
					}
					
					/* If in API mode, it can still receive transparent messages, but it will append with API details
					 * So don't add something like this (left as example of what may seem like a good option but is not)
					if(response.length() >= 15)  //we must already be in transparent mode and receiving a lots of data
					{
						LOGGER.info("Already in AT Mode at startup, response: " + response);
						return CMD_ALREADY_IN_TRANSPARENT;
					}*/
				}				
			}
					
		} catch (Exception e)
		{
			LOGGER.warning("Exception when sending AT Command: " + cmd);
		}
		
		//If don't get the right sequence above, we failed
		return CMD_FAILED;
    	
    }

    
    //disconnect from COM port
    public void disconnect() {
    	received.delete(0, received.length());
    	try {
    		serialPort.removeEventListener();
    		serialPort.close();
    		connected = false;
    		LOGGER.info("Disconnect from " + serialPort.getName());
    	} catch (Exception e) {
    		LOGGER.warning("Failed to close " + serialPort.getName());
    		e.printStackTrace();
    	}
    }
    
    //attempt to begin an input and output stream on COM port
    public boolean initStreams () {
    	try {
    		input = serialPort.getInputStream();
    		output = serialPort.getOutputStream();
    		return true;
    	} catch (Exception e) {
    		LOGGER.warning("Failed to initialize IO streams.");
    		e.printStackTrace();
    	}
    	return false;
    }

    //this is called from serialEvent function, and calles the packetReceived function in the MainWindow Class
    //(actually calls it for all listeners, but should only be one listener??)
	@Override
	public void packetReceived(String packet, byte[] byteArray) {
		for (int i = 0; i < listeners.size(); i++)
			listeners.get(i).packetReceived(packet, byteArray);
	}
	

    //this calles the invalidPacketReceived function in the MainWindow Class
	@Override
	public void invalidPacketReceived (String packet) {
		for (int i = 0; i < listeners.size(); i++)
			listeners.get(i).invalidPacketReceived(packet);
	}

	//set up this class to handle Serial events and be notified if data available
	public void initEventListener() {
        try {
            serialPort.addEventListener(this);  //makes this class a SerialPortEvent listener (will activate serialEvent FN below I think)
            serialPort.notifyOnDataAvailable(true);  //create event when input data available
            
        }
        catch (TooManyListenersException e) {
            LOGGER.warning("Too many listeners: " + e.toString());
        }
    }
    
	//accessor to get status of connection to COM port
    public boolean getConnected () {  return connected;  }

    
    //handles the DATA_AVAILABLE event -> which is generated when 1 byte received, and not regenerated
    public void serialEvent(SerialPortEvent evt) {
        if (evt.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
        	
        	int newByte; 
        	String packet = "";
        	
        	
            try {
            	
	        	
	        	//Reset all variables (believe they already will be, but if exception happens may not be)
				byteInd = 0;
				packetStartInd = packetEndInd = -1;
				if(received.length() > 0)  //not sure if this is necessary, but add to be safe
					received.delete(0, received.length());
      	
	        	//THIS LOOP GETS A COMPLETE PACKET
	        	while(true)   // (newByte = input.read()) > -1)
	        	{	
		        	//note: input.read blocks until data is available, so don't have to worry about going through with no data
	        		newByte = input.read();
					received.append(new String(new byte[] {(byte)newByte}));
					receivedBytes[byteInd] = (byte)newByte;  //shouldn't lose information, since newByte is int between 0-255
					//System.out.print(newByte + " ");
					
					//IF we receive the start character AND we are not currently in the middle of a valid packet, we assume 
					//to be at the start of a valid packet (if already have a packetStartInd, we assume the '*' is part of bytewise data message
					if(newByte == START_CHAR && packetStartInd == -1) 						
					{	
						packetStartInd = byteInd;
					}
					
					//If at current byte and previous byte are both 'END_CHAR', then we have a correct 'end packet sequence'
					//Note byteEnd has already been incremented at this pt, hence [byteInd - 2] to get the previous byte received
					else if(byteInd >= 2 && receivedBytes[byteInd-1] == END_CHAR && receivedBytes[byteInd] == END_CHAR && packetStartInd != -1) 
					{
						//System.out.println("End Sequence: " + receivedBytes[byteInd-1] + " " + receivedBytes[byteInd-1]);
						packetEndInd = byteInd;  //(Note the '-1' since already incremented byteInd)
						break;
						
						/* Notes on the chances of a false 'end packet sequence' (ie. END_CHAR showing up as back to back bytes in a float)
						 *1. Without accoutns for distribution, it's 1/65025, or with 6-7 floats received is ~0.01%)
						 *2. I did the analysis of actual distribution (excel file 'randomFloatByteDistribution') and out of 4 bytes, 
						 * the top byte can never be END_CHAR, and in for the other bytes it is less likely (so even lower than 0.01%)
						 *3. The 'seconds' value is sent last, and is a short between 0-60, and therefore never has the value END_CHAR. 
						 * This removes the possibility that 1/256 packets it ends one too short
						 */
					}	
					
					//increment byte Index
					byteInd++;
					
					//If the received buffer is too long (somehow missed end, corrupted data, etc). Wipe clean and get a fresh start
					//TODO - Maybe have the start be '**' instead of '*'					
					if(byteInd > 4*DATA_PACKET_L) 
					{
						LOGGER.warning("Invalid Packet (Too long), resetting received buffer");
						byteInd = 0;
						received.delete(0, received.length());      	
						packetStartInd = packetEndInd = -1;
		    			invalidPacketReceived(received.toString());		

					}
		       	}  
        	       	
	             //LOGGER.info("Packet Received: " + packet + "  [Start, End] = [" + packetStartInd + ", " + packetEndInd + "]");
	        	//After reaching here, we have had a '*' <some values> then 'ee'
	        	//We now want to determine whether it is a valid packet -> certain characteristics about start/end index should be true
	                   	
	        	//Conditions:
				//i) The start index is less than the end index AND
				//ii) The end index is greater than 0 AND 
				//iii) The start index is not -1 (otherwise we haven't started packet). This could occur on first connection, 
						//when receiving the tail end of previous packet, would reach this point without a '*' being detected
	        	if(packetStartInd < packetEndInd && packetEndInd > 0 && packetStartInd >= 0)
	        	{	     
		        	packet = received.substring(packetStartInd, packetEndInd + 1);  //+1 since end non-inclusive
		        	System.arraycopy(receivedBytes, packetStartInd, receivedPacket, 0, packetEndInd - packetStartInd);  //extract packet in bytes
		        	//NOTE - we may lose a byte (I'm not sure), but that byte is 'e' and is not important for any message
		        	
					packetReceived(packet, receivedBytes.clone());     									
	        	}
				//Bad Packet (if above not fulfilled, bad packet)
				else   
				{
					LOGGER.warning("Bad Start/End Packet Ind: EndInd = " + packetEndInd + " Start: "  + packetStartInd + " L = " + (packetEndInd-packetStartInd));
					
					StringBuffer msg = new StringBuffer();
					for(int i = 0; i <= byteInd; i++)
					{
						msg.append(receivedBytes[i] + " ");  //Put byte array into string of numbers
					}				
	    			invalidPacketReceived(msg.toString());		
				}
				
			
	        }
	        catch (Exception e) {
	            LOGGER.warning("Exception while receiving data. Exception: " + e.toString() + "\nData is: " + packet);
	            e.printStackTrace();
	        }
            
        	//Regardless of the 'case', we reset the variables and delete the received 
			byteInd = 0;
			packetStartInd = packetEndInd = -1;
			if(received.length() > 0)  //not sure if this is necessary, but add to be safe
				received.delete(0, received.length());          
            
        }
    }
    
    //output method: send an integer to this function, and it sends over Xbee to arduino
    public void write (int data) {
    	try {
    		//System.out.println("printing " + (char)data);
    		output.write(data);
    		output.flush();  //clear the output buffer - don't let it wait
    	}
    	catch (Exception e) {
    		LOGGER.warning("Write failed.");
    		e.printStackTrace();
    	}
    }
    
    // Send byte array
    public void write(byte[] b) {
    	try {
    		output.write(b);
    		output.flush();
    	} catch(Exception e) {
    		LOGGER.warning("Byte write failed.");
    		e.printStackTrace();
    	}
    }
    
}
