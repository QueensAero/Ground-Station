import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
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
	final static int timeout = 2000;
	final static int NEW_LINE_ASCII = 10;
	final static int BAUD_RATE = 9600;
	
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
				initEventListener();  //add event listeners to input and output streams
			}
		}
    }
    
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
     *  instead should replace with something like:
     *  
     *  while(input.available != 0)  ... keep reading single bytes until this is 0  */    
    public void serialEvent(SerialPortEvent evt) {
        if (evt.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
            try {
            	byte singleData = (byte)input.read();
                System.out.print("Serial Event ");
                System.out.println(singleData + " ");
                
                received.append(new String(new byte[] {singleData}));
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
    		output.write(data);
    		output.flush();  //clear the output buffer - don't let it wait
    	}
    	catch (Exception e) {
    		System.out.println("Write failed.");
    		e.printStackTrace();
    	}
    }
}
