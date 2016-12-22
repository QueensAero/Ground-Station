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
 */
public class SpeechManager {
	private static final Logger LOGGER = Logger.getLogger(AeroGUI.class.getName());
	private double FT_TO_METRES = 0.3048;  
	
	Voice voice;
	boolean currentlyReportingTime;
	double timeOfLastTimeReport;
	
	Tone toneManager;
	
	SpeechManager() {
		// Stuff for time countdown:
		VoiceManager voiceManager = VoiceManager.getInstance();
		voice = voiceManager.getVoice("kevin"); // Kevin has the nicest voice
		//voice.setRate(60);
		voice.allocate();
		
		currentlyReportingTime = false;
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
			
			if(startReportingTime()) {
				int timeRemaining = (int) Math.round(time);
				String script = Integer.toString(timeRemaining);
				speak(script);
				timeOfLastTimeReport = time;
			}
		}
	} // End reportTime(...)
	
	public synchronized boolean startReportingTime() {
		if(currentlyReportingTime == true) {
			return false;
		} else {
			currentlyReportingTime = true;
			return true;
		}
	}
	
	public synchronized void setCurrentlyReportingTime(boolean b) {
		currentlyReportingTime = b;
	}
	
	private void speak(final String script) {
		// Start a new thread to speak in the background to avoid blocking the program
		Thread t = new Thread() {
		    public void run() {
				voice.speak(script);
				setCurrentlyReportingTime(false);
		    }  
		}; // End Thread t
		t.start();
	} // End speak(...)
}


