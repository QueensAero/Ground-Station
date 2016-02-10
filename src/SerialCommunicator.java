import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.TooManyListenersException;

/* Commented by Ryan Dowling, Dec. 2015
 * Not positive in all comments, but best guesses (I didn't write this code)  
 */





interface PacketListener {
	void packetReceived(String packet);
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
    private static String COMMAND_MODE_CHAR = "+", A = "A", T = "T", C = "C", N = "N", CR = "\r", COMMAND_MODE_OK = "OK\r";
    private static int TIMEOUT_ENTER_COMMAND_MODE = 1500;
    
	final static int timeout = 2000;
	final static int NEW_LINE_ASCII = 10;
	final static int BAUD_RATE = 57600;  //57600;  //9600
	
	private StringBuffer received = new StringBuffer();
	
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
				
				//this function gets into bypass mode
				bypassModeRoutine();				
				
				initEventListener();  //add event listeners to input and output streams
			}
		
			
		
			
			
		}
    }
    
    public void bypassModeRoutine(){
    	
    	boolean enteredBypassMode = false;
		int numTries = 0, maxNumTries = 10;
		
		enteredBypassMode = checkInBypassMode();
		
		if(!enteredBypassMode)
		{	
			while(numTries++ < maxNumTries)
			{
				flushInputBuffer();					
				enterBypassMode();	
				flushInputBuffer();					
		
				enteredBypassMode = checkInBypassMode();
				
				if(enteredBypassMode)
					break;
				
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {					}
				
			}
		}
		
		if(enteredBypassMode)
			System.out.println("Successfully entered bypass mode (" + numTries + " tries)");
		else
			System.out.println("Hit" + maxNumTries + " tries, didn't enter bypass mode ");

		flushInputBuffer();
    	
    }
    
    public void enterBypassMode(){
    	
       	// Send the command mode sequence.
    	try { 		  		
    		
    		output.write("\n".getBytes());
    		output.write("B".getBytes());
    		    		
    	} catch (IOException e) {		}
    }
    
    //get rid of everything in input serial buffer (mainly used during enterBypassMode procedure
    private void flushInputBuffer()
    {
    	byte[] flush = new byte[256];
       	try {
			while(input.read(flush) > 0);  //keep calling it flushes so fast data is still arriving

		} catch (IOException e) {				}    	
    }
    
    //if what is sent gets echoed back, this is the behaviour  
    boolean checkInBypassMode(){
    	
    	try {
			output.write("o".getBytes());
			
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {		}

			
			if(input.read() == -1)
				return true;
			else 
				return false;			
			
		} catch (IOException e) {
		}
		return false;
    }
    
    
    /* These next two functions are never used, but are left as a reference  
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
	public void packetReceived(String packet) {
		for (int i = 0; i < listeners.size(); i++)
			listeners.get(i).packetReceived(packet);
	}
	

    //this calles the invalidPacketReceived function in the MainWindow Class
    //(actually calls it for all listeners, but should only be one listener??)
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
     */    
    public void serialEvent(SerialPortEvent evt) {
        if (evt.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
            try {
            	
            	//incoming data is read into an integer.  
            	int integerData = input.read();   //leave as an integer to avoid having to case byte (unsigned) into a char (sort of signed)
            	byte singleData = (byte)integerData;
            	System.out.println("Character Received: " + (char)integerData + " (int value = " + integerData + ")");
            	received.append(new String(new byte[] {singleData}));
            	
            	/*  Code to read more than one value is receiving data at high baud rate (since event only generated after buffer has been cleared)
            	  
            	int singleData; boolean newLine = false;
            	System.out.print("Recevied: ");
            	while((singleData = input.read()) > -1)
            	{
            		if(singleData != 64)    //64 = @, which is the character used to test if the arduino has entered bypass mode. 
            		{
            			newLine = true;
            			received.append(new String(new byte[] {(byte)singledata}));
            			System.out.print((char)singleData);            		
            		}            	 
            	}
            	System.out.println("");
            	  
            	  
            	  
            	 
            	char c;
            	while((c = (char) input.read()) > -1) // read() returns -1 when buffer is empty
            	{	
            		if(
            			
            		received.append(c);
                	System.out.print("appended " + (char)input.read() + " ");
            	}
            	*/
            	
            	
                String str;
            	String temp = received.toString();
            	if (temp.contains("*") && temp.contains("&")) {  //* is start character,  & is finish character
            		if (temp.indexOf("*") < temp.indexOf("&")) {
            			str = temp.substring(temp.indexOf("*")+1, temp.indexOf("&"));
            			temp = temp.substring(temp.indexOf("&")+1, temp.length());
            			received.delete(0, received.length());
            			received.insert(0, temp);
            			packetReceived(str);
            		}
            		else {
            			temp = temp.substring(temp.indexOf("*"));
            			received.delete(0, received.length());
            			received.insert(0, temp);
            			invalidPacketReceived(temp);
            		}
            	}            }
            catch (Exception e) {
                System.out.println("Failed to read data.");
                e.printStackTrace();
            }
        }
    }
    
    //output method: send an integer to this function, and it sends over Xbee to arduino
    public void write (int data) {
    	try {
    		System.out.println("printing " + (char)data);
    		output.write(data);
    		output.flush();  //clear the output buffer - don't let it wait
    	}
    	catch (Exception e) {
    		System.out.println("Write failed.");
    		e.printStackTrace();
    	}
    }
}
