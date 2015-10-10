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
	private StringBuffer received = new StringBuffer();
	
	public SerialCommunicator() {
		updatePortList();
	}
	
	public void removeListener(PacketListener listener) {
		for (int i = 0; i < listeners.size(); i++) {
			if (listeners.get(i).equals(listener))
				listeners.remove(i);
		}
	}
	
	public void addListener(PacketListener listener) {
		listeners.add(listener);
	}
	
	public ArrayList<String> getPortList () {
		updatePortList();
		return portStringList;
	}
	
	private void updatePortList() {
    	portList = CommPortIdentifier.getPortIdentifiers();
    	portStringList.clear();
    	while(portList.hasMoreElements()) {
    		CommPortIdentifier currPort = (CommPortIdentifier)portList.nextElement();
    		if (currPort.getPortType() == CommPortIdentifier.PORT_SERIAL) {
    			portMap.put(currPort.getName(), currPort);
    			portStringList.add(currPort.getName());
    		}
    	}
    }

    public void connect(String selectedPort) {
    	received.setLength(0);
    	selectedPortIdentifier = (CommPortIdentifier)portMap.get(selectedPort);
    	CommPort commPort;
    	try {
    		commPort = selectedPortIdentifier.open(this.getClass().getName(), timeout);
    		serialPort = (SerialPort) commPort;
    		serialPort.setSerialPortParams(57600,  SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
    		connected = true;
    		System.out.println(selectedPort + " opened succesfully.");
    	} catch (Exception e) {
    		System.out.println("Failed to connect to " + selectedPort);
    		e.printStackTrace();
    	}
		if (connected) {
			if (initStreams() == true) {
				initEventListener();
			}
		}
    }
    
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


	@Override
	public void packetReceived(String packet) {
		for (int i = 0; i < listeners.size(); i++)
			listeners.get(i).packetReceived(packet);
	}
	
	@Override
	public void invalidPacketReceived (String packet) {
		for (int i = 0; i < listeners.size(); i++)
			listeners.get(i).invalidPacketReceived(packet);
	}

	public void initEventListener() {
        try {
            serialPort.addEventListener(this);
            serialPort.notifyOnDataAvailable(true);
        }
        catch (TooManyListenersException e) {
            System.out.println("Too many listeners: " + e.toString());
        }
    }
    
    public boolean getConnected () {
    	return connected;
    }

    public void serialEvent(SerialPortEvent evt) {
        if (evt.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
            try {
                byte singleData = (byte)input.read();
                received.append(new String(new byte[] {singleData}));
                String str;
            	String temp = received.toString();
            	if (temp.contains("*") && temp.contains("&")) {
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
    
    public void write (int data) {
    	try {
    		output.write(data);
    		output.flush();
    	}
    	catch (Exception e) {
    		System.out.println("Write failed.");
    		e.printStackTrace();
    	}
    }
}