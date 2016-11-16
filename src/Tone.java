import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class Tone extends Thread {
    private int currentPeriodms;
    private boolean lineStopped;
    AudioFormat af;
    SourceDataLine line;
    
    public Tone() throws LineUnavailableException {
    	currentPeriodms = -1;
    	lineStopped = true;
    	af = new AudioFormat(Note.SAMPLE_RATE, 8, 1, true, true);
		line = AudioSystem.getSourceDataLine(af);
    	line.open(af, Note.SAMPLE_RATE);
    }
    
    public void run() {
    	while(true) {
    		if(currentPeriodms < 0) {
    			// Don't play anything
    			if(!lineStopped) {
    				line.flush();
    				line.stop();
    			}
    			try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    		} else {
    			if(lineStopped) {
    				line.start();
    			}
				play(line, Note.A4, currentPeriodms);
				play(line, Note.REST, currentPeriodms);
				line.drain(); // Wait for tone to finish playing
    		}
    	} // End while(...)
    } // End run()
    
    /**
     * The period of the tones being emitted. A value less than 0 will result in no tones.
     * Maximum allowed period is 2000 ms.
     * @param period
     */
    public void setCurrentPeriod(int period) {
    	if(period > 2000) {
    		currentPeriodms = 2000;
    	} else {
    		currentPeriodms  = period;
    	}
    }

    private void play(SourceDataLine line, Note note, int ms) {
        ms = Math.min(ms, Note.SECONDS * 1000);
        int length = Note.SAMPLE_RATE * ms / 1000;
        int count = line.write(note.data(), 0, length);
    }
}

enum Note {

    REST, A4, A4$, B4, C4, C4$, D4, D4$, E4, F4, F4$, G4, G4$, A5;
    public static final int SAMPLE_RATE = 16 * 1024; // ~16KHz
    public static final int SECONDS = 2;
    private byte[] sin = new byte[SECONDS * SAMPLE_RATE];

    Note() {
        int n = this.ordinal();
        if (n > 0) {
            double exp = ((double) n - 1) / 12d;
            double f = 440d * Math.pow(2d, exp);
            for (int i = 0; i < sin.length; i++) {
                double period = (double)SAMPLE_RATE / f;
                double angle = 2.0 * Math.PI * i / period;
                sin[i] = (byte)(Math.sin(angle) * 127f);
            }
        }
    }

    public byte[] data() {
        return sin;
    }
}