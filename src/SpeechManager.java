import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;

/**
 * Class to manage speech feedback.
 * Provides periodic speech updates of the current altitude and estimated time to drop.
 * This class handles creation of new threads for speech and avoids overlaps of speech updates.
 */
public class SpeechManager {
	Voice voice;
	boolean currentlyReportingTime;
	long timeOfLastTimeReport;
	
	//
	// Will not use a queue, because we don't want to report outdated data.
	// Altitude and countdown get updated much more frequently than we can speak, so just wait for the
	// first update after we're done talking.
	// 
	// - 
	//
	
	SpeechManager() {
		VoiceManager voiceManager = VoiceManager.getInstance();
		voice = voiceManager.getVoice("kevin"); // Kevin has the nicest voice
		//voice.setRate(60);
		voice.allocate();
		
		currentlyReportingTime = false;
		timeOfLastTimeReport = 100;
		
	}
	
	/**
	 * Decide whether it is appropriate to report the altitude again,
	 * and if so then call the speak() function to do so.
	 * 
	 * @param altitude - in feet
	 */
	public void reportAltitude(float altitude) {
		/*// If already reporting altitude, just wait
		if(currentlyReportingAltitude) {
			return;
		}
		
		// If the altitude is low then this must be reported with high priority
		if(altitude < 110) {
			if(!currentlyReporting) {
				
		} else { // We are high enough that it is less urgent that we report the altitude
			
		}
		
		// Only report the altitude if it has been at least 2 seconds since the last report
		if((System.currentTimeMillis() - timeOfLastAltitudeReport) > 2000) {
			if(altitude < 110) { // If the altitude is low then this must be reported with high priority
				if(!currentlyReporting) {
					
			} else { // We are high enough that it is less urgent that we report the altitude
				
			}
		}*/
	}
	
	public void reportTime(double time) {
		// Try to report the time at 10 secs, 5 secs, 4 secs, 3 secs, 2 secs, 1 secs, drop
		if(	(timeOfLastTimeReport > 10 && time < 10) ||
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
			}
		}
	}
	
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
	
	private void speak(String script) {
		Thread t = new Thread() {
		    public void run() {
				voice.speak(script);
				setCurrentlyReportingTime(false);
		    }  
		}; // End Thread t
		t.start();
	} // End speak(...)
}


