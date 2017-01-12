import javax.swing.JOptionPane;
import javax.swing.JDialog;
import javax.swing.JTextField;
import java.beans.*; //property change stuff
import java.util.logging.Logger;
import java.awt.*;
import java.awt.event.*;

class GPSTargetDialog extends JDialog implements ActionListener, PropertyChangeListener {
	private static final Logger LOGGER = Logger.getLogger(AeroGUI.class.getName());
    private String typedText = null;
    private JTextField latTextField;
    private JTextField lonTextField;
    private MainWindow parent;

    private JOptionPane optionPane;

    private String btnString1 = "Enter";
    private String btnString2 = "Cancel";

    /**
     * Returns null if the typed string was invalid;
     * otherwise, returns the string as the user entered it.
     */
    public String getValidatedText() {
        return typedText;
    }

    /** Creates the reusable dialog. */
    public GPSTargetDialog(Frame aFrame, String aWord, MainWindow parent) {
        super(aFrame, true);
        this.parent = parent;
        setTitle("GPS Target Coordinates");

        latTextField = new JTextField(10);
        lonTextField = new JTextField(10);

        //Create an array of the text and components to be displayed.
        String msgString1 = "Enter target GPS coords:";
        String msgString2 = "Latitude:";
        String msgString3 = "Longitude:";
        Object[] array = {msgString1, msgString2, latTextField, msgString3, lonTextField};

        //Create an array specifying the number of dialog buttons
        //and their text.
        Object[] options = {btnString1, btnString2};

        //Create the JOptionPane.
        optionPane = new JOptionPane(array,
                                    JOptionPane.QUESTION_MESSAGE,
                                    JOptionPane.YES_NO_OPTION,
                                    null,
                                    options,
                                    options[0]);

        //Make this dialog display it.
        setContentPane(optionPane);

        //Handle window closing correctly.
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent we) {
                /*
                 * Instead of directly closing the window,
                 * we're going to change the JOptionPane's
                 * value property.
                 */
                    optionPane.setValue(new Integer(JOptionPane.CLOSED_OPTION));
            }
        });

        //Ensure the text field always gets the first focus.
        addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent ce) {
                latTextField.requestFocusInWindow();
            }
        });

        //Register an event handler that puts the text into the option pane.
        latTextField.addActionListener(this);
        lonTextField.addActionListener(this);

        //Register an event handler that reacts to option pane state changes.
        optionPane.addPropertyChangeListener(this);
    }

    /** This method handles events for the text field. */
    public void actionPerformed(ActionEvent e) {
        optionPane.setValue(btnString1); // same as clicking btn 1
    }

    /** This method reacts to state changes in the option pane. */
    public void propertyChange(PropertyChangeEvent e) {
        String prop = e.getPropertyName();

        if (isVisible()
         && (e.getSource() == optionPane)
         && (JOptionPane.VALUE_PROPERTY.equals(prop) ||
             JOptionPane.INPUT_VALUE_PROPERTY.equals(prop))) {
            Object value = optionPane.getValue();

            if (value == JOptionPane.UNINITIALIZED_VALUE) {
                //ignore reset
                return;
            }

            //Reset the JOptionPane's value.
            //If you don't do this, then if the user
            //presses the same button next time, no
            //property change event will be fired.
            optionPane.setValue(JOptionPane.UNINITIALIZED_VALUE);

            if (btnString1.equals(value)) { // If form submitted
                double lat = Double.parseDouble(latTextField.getText());
                double lon = Double.parseDouble(lonTextField.getText());
                parent.updateGPSTarget(lat, lon);
                clearAndHide();
            } else { //user closed dialog or clicked cancel
                clearAndHide();
            }
        }
    }

    /** This method clears the dialog and hides it. */
    public void clearAndHide() {
        latTextField.setText(null);
        lonTextField.setText(null);
        setVisible(false);
    }
}
