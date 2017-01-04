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

/* Commented by Ryan Dowling, Dec. 2015
 * Not positive in all comments, but best guesses (I didn't write this code)  
 */

interface PacketListener {
	void packetReceived(String packet, byte[] byteArray);
	void invalidPacketReceived(String packet);
}


public class SerialCommunicator implements SerialPortEventListener, PacketListener {
	
	String packet;
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
	final static int NEW_LINE_ASCII = 10;
	final static int BAUD_RATE = 115200;  //57600;  //9600
	final static int START_CHAR = 42;
	final static int END_CHAR = 101;
	final static int DATA_PACKET_L = 27;


	
	private StringBuffer received = new StringBuffer();
	private byte[] receivedBytes = new byte[200];
	private int byteInd = 0, packetStartInd = -1, packetEndInd = -1;
	private int mostRecentEndCharInd = -1;
	
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
    	mostRecentEndCharInd = -1;
    	
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
    		System.out.println(selectedPort + " opened succesfully.");
    		
    	} catch (Exception e) {
    		System.out.println("Failed to connect to " + selectedPort);
    		e.printStackTrace();
    	}
    	
    	//in connected, then call FN to initialize the input and output streams. 
		if (connected) {
			if (initStreams() == true) {			
				initEventListener();  //add event listeners to input and output streams
			}
		}
    }
    
    //get rid of everything in input serial buffer (mainly used during enterBypassMode procedure
    private void flushInputBuffer()
    {
    	byte[] flush = new byte[256];
       	try {
			while(input.read(flush) > 0);  //keep calling it flushes so fast data is still arriving

		} catch (IOException e) {				}    	
    }
    
    
    
    /* These next two functions are never used, but are left as a reference.  They can be easily accomplished with XCTU
    private static String COMMAND_MODE_CHAR = "+", A = "A", T = "T", C = "C", N = "N", CR = "\r", COMMAND_MODE_OK = "OK\r";
    private static int TIMEOUT_ENTER_COMMAND_MODE = 1500; 
    
    private boolean enterCommandMode()  {
		
		// Enter in AT command mode (send '+++'). The process waits 1,5 seconds for the 'OK\n'.
		byte[] readData = new byte[256];
		byte[] testData = new byte[256];
		
		try {
			// Send the command mode sequence.
			output.write(COMMAND_MODE_CHAR.getBytes());
			output.write(COMMAND_MODE_CHAR.getBytes());
			output.write(COMMAND_MODE_CHAR.getBytes());			
			
			output.flush();
			
			// Wait some time to let the module generate a response.
			Thread.sleep(TIMEOUT_ENTER_COMMAND_MODE);
			
			
			
			// Read data from the device (it should answer with 'OK\r').
			int readBytes = input.read(readData);
			if (readBytes < COMMAND_MODE_OK.length())
			{	System.out.println("Failed to enter command mode, # bytes too low (= " + readBytes + ")");
				String readString = new String(readData, 0, readBytes);
				System.out.println(readString);
				return false;
			}
			
			// Check if the read data is 'OK\r'.
			String readString = new String(readData, 0, readBytes);
			if (!readString.contains(COMMAND_MODE_OK))
			{	System.out.println(readString);
				return false;
			}
			// Read data was 'OK\r'.
			return true;
		} catch (IOException e) {
		} catch (InterruptedException e) {
		}
		return false;
	}
    
    private boolean exitCommandMode(){
    	
    	byte[] readData = new byte[256];
		try {
			// Send the command mode sequence.
			output.write(A.getBytes());
			output.write(T.getBytes());
			output.write(C.getBytes());
			output.write(N.getBytes());
			output.write(CR.getBytes());


			
			// Wait some time to let the module generate a response.
			Thread.sleep(TIMEOUT_ENTER_COMMAND_MODE);
			
			// Read data from the device (it should answer with 'OK\r').
			int readBytes = input.read(readData);
			if (readBytes < COMMAND_MODE_OK.length())
				return false;
			
			// Check if the read data is 'OK\r'.
			String readString = new String(readData, 0, readBytes);
			if (!readString.contains(COMMAND_MODE_OK))
				return false;
			
			return true;

			
		} catch (IOException e) {
		} catch (InterruptedException e) {
		}
		
		return false;
    	
    }
    */

    
    //disconnect from COM port
    public void disconnect() {
    	received.delete(0, received.length());
    	try {
    		serialPort.removeEventListener();
    		serialPort.close();
    		connected = false;
    		System.out.println("Disconnect from " + serialPort.getName());
    	} catch (Exception e) {
    		System.out.println("Failed to close " + serialPort.getName());
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
    		System.out.println("Failed to initialize IO streams.");
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
            System.out.println("Too many listeners: " + e.toString());
        }
    }
    
	//accessor to get status of connection to COM port
    public boolean getConnected () {  return connected;  }

    
    /*handles the DATA_AVAILABLE event -> which is generated when 1 byte received, and not regenerated
     *  if new data is receieved BEFORE processing this event.  This is poorly setup - it should read until not available
     *  not just read a single byte: otherwise, if a two bytes are received before the 1st is processed, it will forever be
     *  behind 'real' time and the buffer will increase in length
     *  
     *  I'm not sure how true ^ is. I am also unsure if having it wait until the end of a packet can cause certain aspects of the GUI
     *  to hang as it's waiting for a complete message
     */    
    public void serialEvent(SerialPortEvent evt) {
        if (evt.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
            try {
            	
	        	int newByte; 
      	
	        	//THIS LOOP GETS A COMPLETE PACKET
	        	//note: this blocks until data is available, so don't have to worry about going through while with repeated data
	        	while((newByte = input.read()) > -1)
	        	{	
       		
					received.append(new String(new byte[] {(byte)newByte}));
					receivedBytes[byteInd++] = (byte)newByte;  //shouldn't lose information, since newByte is int between 0-255

					//If the received buffer is too long (somehow missed end, corrupted data, etc). Wipe clean and get a fresh start
					//TODO - analyze threshold. At first connect, might be many characters into packet, and still want to get that first one
						//So have threshold at double DATA_PACKET_L??
					//TODO - Maybe have the start be '**' instead of '*'					
					if(byteInd > 2*DATA_PACKET_L) 
					{
						System.out.println("Invalid Packet (Too long), resetting received buffer");
						byteInd = 0;
						received.delete(0, received.length());      	
						packetStartInd = packetEndInd = -1;
					}
					
					//IF we receive the start character AND we are not currently in the middle of a valid packet, we assume 
					//to be at the start of a valid packet (if already have a packetStartInd, we assume the '*' is part of bytewise data message
					if(newByte == START_CHAR && packetStartInd == -1) 						
					{	
						packetStartInd = byteInd-1;
					}
					
					/* UNTESTED CLEARER WAY OF DOING BELOW  */
					//If at current byte and previous byte are both 'END_CHAR', then we have a correct 'end packet sequence'
					//Note byteEnd has already been incremented at this pt, hence [byteInd - 2] to get the previous byte received
					else if(byteInd >= 2 && receivedBytes[byteInd-2] == END_CHAR && newByte == END_CHAR) 
					{
						packetEndInd = byteInd-1;  //(Note the '-1' since already incremented byteInd)
						break;
						
						/* Notes on the chances of a false 'end packet sequence' (ie. END_CHAR showing up as back to back bytes in a float)
						 *1. Without accoutns for distribution, it's 1/65025, or with 6-7 floats received is ~0.01%)
						 *2. I did the analysis of actual distribution (excel file 'randomFloatByteDistribution') and out of 4 bytes, 
						 * the top byte can never be END_CHAR, and in for the other bytes it is less likely (so even lower than 0.01%)
						 *3. The 'seconds' value is sent last, and is a short between 0-60, and therefore never has the value END_CHAR. 
						 * This removes the possibility that 1/256 packets it ends one too short
						 */

					}				
					
					/******* OLD METHOD *************  -> REMOVE AFTER TESTING
					//IF we receive the end character. For it to be the end of the packet, the end character must come twice (Checked inside)
					else if(newByte == END_CHAR)  
					{	
						//IF the current byte index is equal to one greater than the last received END_CHAR's index (and it's not index 0), 
						//then we have a correct 'end packet sequence' so leave the while loop 
						if(byteInd == (mostRecentEndCharInd+1) && byteInd != 0)
						{	
							packetEndInd = byteInd;  //this points indexOf("&")+1
							break; //analyze the packet        
						}
						//We set the index of most recent END_CHAR to the current index
						else
							mostRecentEndCharInd = byteInd;  //first time through it will set to true. If two adjacent 'e' received, then next time will get into first if. Otherwise will reset this
						
						
					}	*/
	        	}     	
	            	
	        	String temp = received.toString();
	        	String str; 
	        	
	        	//After reaching here, we have had a '*' <some values> then 'ee'
	        	//We now want to determine whether it is a valid packet -> certain characteristics about start/end index should be true
	                   	
	        	//Conditions:
				//i) The start index is less than the end index AND
				//ii) The end index is greater than 0 AND 
				//iii) The start index is not -1 (otherwise we haven't started packet). This could occur on first connection, 
						//when receiving the tail end of previous packet, would reach this point without a '*' being detected 
				if(packetEndInd == (packetStartInd+DATA_PACKET_L) && temp.substring(packetStartInd+1, packetStartInd+2).equals("p"))  
				{
					//If not a data packet (! 'p' thing) OR it is the correct length to be a data packet, then it's good)
					if(!temp.substring(packetStartInd+1, packetStartInd+2).equals("p") || packetEndInd == (packetStartInd+DATA_PACKET_L))
					{
						str = temp.substring(packetStartInd+1, packetEndInd-1);  //* and ee are removed by this function (end ind is non-inclusive)
		    			packetReceived(str, receivedBytes.clone());     				
					}
					else  //Data packet with a bad length (filter before attempting to analyze it, as that will access out of bounds indicies)
					{
						System.out.println("Bad Data Packet of Length " + (packetEndInd-packetStartInd));
		    			invalidPacketReceived(temp);		
					}
				}
				//Bad Packet (if above not fulfilled, bad packet)
				else   
				{
					System.out.println("Bad Start/End Packet Ind, Length = " + (packetEndInd-packetStartInd));				
	    			invalidPacketReceived(temp);		
				}
				
				//Regardless of the 'case', we reset the variables and delete the received 
				byteInd = 0;
				packetStartInd = packetEndInd = mostRecentEndCharInd = -1;
				if(received.length() > 0)  //not sure if this is necessary, but add to be safe
					received.delete(0, received.length());
				
	        }
	        catch (Exception e) {
	            System.out.println("Failed to read data.");
	            e.printStackTrace();
	        }
                        
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
    		System.out.println("Write failed.");
    		e.printStackTrace();
    	}
    }
}
