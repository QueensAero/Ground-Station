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
	void packetReceived(String packet, byte[] byteArray, int byteArrayInd);
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
	
	private StringBuffer received = new StringBuffer();
	private byte[] receivedBytes = new byte[200];
	private int byteInd = 0, packetStart = -1, packetEnd = -1;
	private int firstEndCharInd = -1;
	
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
    	packetStart = packetEnd = -1;
    	firstEndCharInd = -1;
    	
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
	public void packetReceived(String packet, byte[] byteArray, int byteArrayInd) {
		for (int i = 0; i < listeners.size(); i++)
			listeners.get(i).packetReceived(packet, byteArray, byteArrayInd);
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
     */    
    public void serialEvent(SerialPortEvent evt) {
        if (evt.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
            try {
            	
	        	int singleData; 
      	
	        	//note: have to be careful since 
	        	while((singleData = input.read()) > -1)
	        	{	
       		
					received.append(new String(new byte[] {(byte)singleData}));
					receivedBytes[byteInd++] = (byte)singleData;  //shouldn't lose information, since singleData is int between 0-255

					           			
					if(byteInd > 80)  //something missed the end, reset
					{
						System.out.println("Resetting byte Index (since > 80)");
						byteInd = 0;
						received.delete(0, received.length());      	
						packetStart = packetEnd = -1;
		
					}
					
					if(singleData == 42 && packetStart == -1)  // 42 = '*'   the start of packet character, and == -1 to ensure it isn't reset						
					{	packetStart = byteInd-1;

					}
					else if(singleData == 101)  //101 = 'e' which is end of packet character.  It must come twice (avoid case where float bytewise rep. has end of packet character in it
					{	
											
						if(firstEndCharInd != -1 && byteInd == (firstEndCharInd+1))
						{	packetEnd = byteInd;  //this points indexOf("&")+1
							break; //analyze the packet        
						}
						else
							firstEndCharInd = byteInd;  //first time through it will set to true. If two adjacent 'e' received, then next time will get into first if. Otherwise will reset this
						
						//note seconds are last sent, and they can never be 'e' (since value between 0-60. 
					}	
	        	}     	
	            	
	        	String temp = received.toString();
	        	String str; 			            			
	            
	        	//If have * and ee, both packetEnd and packetStart will be non-zero. Two cases: 
	        	// if there are exactly 35 characters, and second character is p, then have a data packet
	        	//If packetStart < packetEnd, and packetStart >= 0 and packetEnd >= 0, then it's a simple message (ie. Reset Acknowledged)
	        	//if packetEnd is not -1 and neither of those are true, it's a bad packet and we reset
				if(packetEnd == (packetStart+35) && temp.substring(packetStart+1, packetStart+2).equals("p"))  //data string
				{
					str = temp.substring(packetStart+1, packetEnd-1);  //* and & are removed by this function
	    			packetReceived(str, receivedBytes.clone(), packetStart+2);  //+2 to get do start of data (past *p)
					byteInd = 0;
					packetStart = packetEnd = firstEndCharInd = -1;
	    			received.delete(0, received.length());
	    				
				}
				else if(packetStart < packetEnd && packetEnd >= 0 && packetStart >= 0) 
				{
					str = temp.substring(packetStart+1, packetEnd-1);  //* and & are removed by this function
	        		packetReceived(str, receivedBytes.clone(), -1);  //-1 indicates non-data string         
	        		byteInd = 0;
					packetStart = packetEnd = firstEndCharInd = -1;
	    			received.delete(0, received.length());
					
				}
				else if(packetEnd != -1)  //if packetEnd is not -1 and doesn't fit above boxes, means we have bad packet
				{
					System.out.println("PE - PS = " + (packetEnd-packetStart));
					received.delete(0, received.length());
	    			invalidPacketReceived(temp);
	    			byteInd = 0;
					packetStart = packetEnd = firstEndCharInd = -1;
				}
	        }
	        catch (Exception e) {
	            System.out.println("Failed to read data.");
	            e.printStackTrace();
	        }
            	 
            	
            /* Old way - works for sure, save just in case	
            	
            	// Code to read more than one value is receiving data at high baud rate (since event only generated after buffer has been cleared)
            	int singleData; 
            	//System.out.print("Recevied: ");
            	while((singleData = input.read()) > -1)
            	{	
            		if(singleData != 64 && singleData != 224)    //64 = @, 'test' character. If at 9600 baud, @ is sent as 224  
            		{
            			received.append(new String(new byte[] {(byte)singleData}));   			
            		           			

            			if(singleData == 38)  //38 = '&' which is end of packet character.  
            				break;
            		}	
            		else
            			System.out.println("Received test character");
            		
            			
            	}
            
                     	          	
                String str;
            	String temp = received.toString();
            	if (temp.contains("*") && temp.contains("&")) {  //* is start character,  & is finish character
            		if (temp.indexOf("*") < temp.indexOf("&")) {
            			str = temp.substring(temp.indexOf("*")+1, temp.indexOf("&"));  //* and & are removed by this function
            			temp = temp.substring(temp.indexOf("&")+1, temp.length());
            			received.delete(0, received.length());
            			received.insert(0, temp);
            			packetReceived(str);
            		}
            		else {
            			temp = temp.substring(temp.indexOf("*"));  //this assumes that the received values from * onwards are part of a new, valid packet
            			received.delete(0, received.length());
            			received.insert(0, temp);
            			invalidPacketReceived(temp);
            		}
            	}            }
            catch (Exception e) {
                System.out.println("Failed to read data.");
                e.printStackTrace();
            }
            
            */
            
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
