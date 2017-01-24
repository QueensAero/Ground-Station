import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

public class CommPortDialog extends JDialog {
	private static final Logger LOGGER = Logger.getLogger(AeroGUI.class.getName());
	private JComboBox<String> commPortSelector;
	private SerialCommunicator serialComm;
	private JButton btnRefresh, btnConnect;
    private MainWindow parent;

    private JOptionPane optionPane;

    private String btnString1 = "Enter";
    private String btnString2 = "Cancel";
    
    public CommPortDialog(MainWindow parent, SerialCommunicator communicator) {
    	this.parent = parent;
    	serialComm = communicator;
    	setContentPane(initializeCommPortControlPanel());
    	
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
		/*btnClearData = new JButton("Clear");
		commPortControlPanel.add(btnClearData, c2);
		c2.gridx = 4;
		c2.gridy = 1;*/
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
				parent.updateSerialConnection(commPortSelector.getSelectedItem().toString());
				updateCommPortSelector();
			}
		});
		updateCommPortSelector();
		
		return commPortControlPanel;
	}
	
	//function called to update list of available COM ports
	private void updateCommPortSelector() {
		commPortSelector.removeAllItems();
		String portList;
		if(!serialComm.getConnected()) {
			ArrayList<String> temp = serialComm.getPortList();
			portList = "Available serial ports:";
	
			for (int i = 0; i < temp.size(); i++) {
				commPortSelector.addItem(temp.get(i));
				portList += temp.get(i) + ",";
			}
			commPortSelector.setEnabled(true);
			btnConnect.setText("Connect");
		} else { // We are already connected, so just display the name of the current connection and don't present the option to change
			String tmpName = serialComm.getCurrentPortName();
			commPortSelector.addItem(tmpName);
			commPortSelector.setSelectedItem(tmpName);
			portList = "Currently connected to: " + tmpName;
			commPortSelector.setEnabled(false); // Disable the selector, you can't pick another connection until you disconnect.
			btnConnect.setText("Disconnect");
		}
		LOGGER.info(portList);
	}

}
