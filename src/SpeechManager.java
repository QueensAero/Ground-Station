import java.util.logging.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;

/**
 * Class to manage speech feedback.
 * Provides periodic speech updates of the current altitude and estimated time to drop.
 * This class handles creation of new threads for speech and avoids overlaps of speech updates.
 * 
 * This class follows thw 'singleton' class pattern, meaning that only a single instance can exist.
 */
public class SpeechManager {
	private static final Logger LOGGER = Logger.getLogger(AeroGUI.class.getName());
	private double FT_TO_METRES = 0.3048;
	
	private static SpeechManager sm;
	
	Voice voice;
	boolean currentlySpeaking;
	double timeOfLastTimeReport;
	
	Tone toneManager;
	
	// Constructor is private so that it cannot be called externally. To get an instance, call getInstance() instead.
	private SpeechManager() {
		// Stuff for time countdown:
		VoiceManager voiceManager = VoiceManager.getInstance();
		voice = voiceManager.getVoice("kevin"); // Kevin has the nicest voice
		//voice.setRate(60);
		voice.allocate();
		
		currentlySpeaking = false;
		timeOfLastTimeReport = 100;
		
		// Stuff for altitude warning tone:
		try {
			toneManager = new Tone();
		} catch (LineUnavailableException e) {
			// TODO Auto-generated catch block
			LOGGER.warning("Failed to create Tone.");
			e.printStackTrace();
		}
		toneManager.start();
	}
	
	static { sm = new SpeechManager(); }
	
	public static SpeechManager getInstance() {
		return sm;
	}
	
	/**
	 * Decide whether it is appropriate to report the altitude again,
	 * and if so then call the speak() function to do so.
	 * 
	 * @param altitude - in meters
	 */
	public void reportAltitude(double altitude) {
		double altFt = altitude / FT_TO_METRES;
		if(altFt < 100) {
			toneManager.setCurrentPeriod(50); // Fast tone
		} else if(altFt < 110) {
			toneManager.setCurrentPeriod(600); // Slow tone
		} else {
			toneManager.setCurrentPeriod(-1); // No tone
		}
	}
	
	public void reportTime(double time) {
		if(time > 13) { // If we are far away reset
			timeOfLastTimeReport = time;
			
			// Try to report the time at 10 secs, 5 secs, 4 secs, 3 secs, 2 secs, 1 secs, drop
		} else if((timeOfLastTimeReport > 10 && time < 10) ||
			(timeOfLastTimeReport > 5 && time < 5) ||
			(timeOfLastTimeReport > 4 && time < 4) ||
			(timeOfLastTimeReport > 3 && time < 3) ||
			(timeOfLastTimeReport > 2 && time < 2) ||
			(timeOfLastTimeReport > 1 && time < 1) ||
			(timeOfLastTimeReport > 0 && time < 0)) {
			
			// Try once, but if we're already speaking, give up
			if(startSpeaking()) {
				int timeRemaining = (int) Math.round(time);
				String script = Integer.toString(timeRemaining);
				speak(script);
				timeOfLastTimeReport = time;
			}
		}
	} // End reportTime(...)
	
	public void reportNewMessage(final String msg) {
		// Loop in a new thread until the speechManager is available
		// A new thread is used to avoid blocking in MainWindow while we wait to talk
		Thread t = new Thread() {
		    public void run() {
		    	// Block until whatever is currently speaking is done
				// I didn't bother to create a queue, because there shouldn't be enough
				// messages that contention becomes an issue
		    	while(!startSpeaking()) {
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
		    	speak(msg);
		    }  
		}; // End Thread t
		t.start();
	}
	
	private synchronized boolean startSpeaking() {
		if(currentlySpeaking == true) {
			return false;
		} else {
			currentlySpeaking = true;
			return true;
		}
	}
	
	private synchronized void setCurrentlySpeaking(boolean b) {
		currentlySpeaking = b;
	}
	
	private void speak(final String script) {
		// Start a new thread to speak in the background to avoid blocking the program
		Thread t = new Thread() {
		    public void run() {
				voice.speak(script);
				setCurrentlySpeaking(false);
		    }  
		}; // End Thread t
		t.start();
	} // End speak(...)
}


