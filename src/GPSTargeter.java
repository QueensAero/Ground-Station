import java.util.logging.Logger;

/*
 * 
 * 
 * This class predicts the time until drop and the lateral error at the recommended
 * drop time using the following algorithm:
 * 	1. Determine distance between the target and the line represented by the current
 * 		heading. (This is the predicted lateral error at the optimal drop time if the
 * 		heading is not corrected)
 * 	2. Determine distance to target from current position using Pythagorean Theorem.
 * 		(Direct path)
 * 	3. Determine distance to target along current path by using Pythagorean Theorem, 
 * 		the distance calculated in '2.', and distance calculated in '1.'.
 * 	4. Determine how early package must be dropped (distance) based on current altitude
 * 		and speed.
 *  5. Calculate estimated drop position and it's distance to the target
 * 	6. Calculate the time until the optimal drop time based on the current speed and the
 * 		distance from '4.'.
 * 
 *  
 *  ***Note: This class assumes that all coordinates are in the same UTM zone. If this is
 *  not the case then everything will break.
 *  
 */
public class GPSTargeter {
	private static final Logger LOGGER = Logger.getLogger(AeroGUI.class.getName());
	// Inputs
	private GPSPos curPos;
	private GPSPos targetPos;
	
	
	private SpeechManager speechManager;
	
	
	//Values from each step. All values in meters or seconds
    double 	lateralError, //1. 
    		directDistanceToTarget, //2.
    		distAlongPathToMinLateralErr, //3.
    		horizDistance, //4.
    		distFromEstDropPosToTarget, //5.
    		estDropEasting,
    		estDropNorthing,
    		timeTillDrop;  //6.
	
	
	
	public GPSTargeter(GPSPos target) {
		curPos = null;
		targetPos = target;
		lateralError = timeTillDrop = directDistanceToTarget = distAlongPathToMinLateralErr = horizDistance = distFromEstDropPosToTarget = -1;
		estDropEasting =  estDropNorthing = timeTillDrop;

		speechManager = SpeechManager.getInstance();
		speechManager.reportTime(12);
		speechManager.reportTime(9.7);
	}
	
	public void setTargetPos(GPSPos target) {
		targetPos = target;
		update();
	}
	
	/*
	 * Updates the current position and causes the dropTime prediction to be re-calculated.
	 */
	public void updateCurPos(GPSPos pos) {
		curPos = pos; // Don't worry, GPSPos is immutable
		update();
	}
	
	public void update()
	{	
		
		if(curPos != null && targetPos != null) { // If valid input, carry out calculations

			// Order matters:
			calculateLateralError();
			calculateDirectDistanceToTarget();
			calculateDistAlongPathToMinLateralErr();
			calculateHorizDistance(); 
			calculateTimeTillDrop();
			calculateDistFromEstDropPosToTarget();
			
			speechManager.reportTime(timeTillDrop);
			speechManager.reportAltitude(curPos.getAltitudeFt());  //FEET
		}
		
		
	}
	
	public double getLateralError() { return lateralError; }
	public double getTimeToDrop() { return timeTillDrop; }
	public double getEstDropEasting() { return estDropEasting; }
	public double getEstDropNorthing() { return estDropNorthing; }

	//public double getDropDistance() { return dropDistance();  }  //assumes updateCurPos has already been called
	
	/* 
	 * Step '1.' in class description:
	 * 
	 * Computes shortest distance from the line defined by the plane's path
	 * to the location of the target.
	 */
	private void calculateLateralError() {
		// See "Line defined by two points" at 
		// https://en.wikipedia.org/wiki/Distance_from_a_point_to_a_line
		// From the wikipedia equation:
		// P1 => prevPos
		// P2 => curPos
		// (x0, y0) => targetPos
		
		// P1:
		// Generate an arbitrary point to define the line (far enough away to avoid rounding errors)
		double x1 = curPos.getUTMEasting() + Math.cos(curPos.getMathAngle() / 180 * Math.PI) * 1000;
		double y1 = curPos.getUTMNorthing() + Math.sin(curPos.getMathAngle() / 180 * Math.PI) * 1000;
		
		// P2:
		double x2 = curPos.getUTMEasting();
		double y2 = curPos.getUTMNorthing();
		
		// Target
		double x0 = targetPos.getUTMEasting();
		double y0 = targetPos.getUTMNorthing();
		
		// Calculate numerator in equation: (it's a big equation...)
		double num = (y2 - y1) * x0;
		num -= (x2 - x1) * y0;
		num += x2 * y1;
		num -= y2 * x1;
		num = Math.abs(num);
		
		// Calculate denominator in equation:
		double denom = Math.pow(y2 - y1, 2.0);
		denom += Math.pow(x2 - x1, 2.0);
		denom = Math.sqrt(denom);
		
		lateralError =  num / denom;
	}
	
	/*
	 * Step '2.' in class description:
	 * 
	 * Calculates direct distance from current position to the target.
	 */
	private void calculateDirectDistanceToTarget() {
		
		// Use pythagorean theorem to calculate distance between curPos and targetPos:
		directDistanceToTarget = Math.sqrt(Math.pow((targetPos.getUTMEasting() - curPos.getUTMEasting()), 2) + 
				Math.pow((targetPos.getUTMNorthing() - curPos.getUTMNorthing()), 2));
	}
	
	/*
	 * Step '3.' in class description
	 * 
	 * Calculates distance along current path until nearest point to target.
	 * Note: Relies on the fact that an accurate lateral error has already been calculated
	 */
	private void calculateDistAlongPathToMinLateralErr() {
		distAlongPathToMinLateralErr = Math.sqrt(Math.pow(directDistanceToTarget, 2) - Math.pow(lateralError, 2));		
	}
	
	
	/*
	 * Step '4.' in class description
	 * 
	 * Calculates how early (distance in m before being above the target) the
	 * package must be dropped based on current speed, delay receving data, time since received data, delay sending command and altitude.
	 * 
	 * *** Note: right now this is ignores air resistance and is very unrealistic.
	 * Some thoughts on air resistance: terminal falling velocity 35-45 m/s ish, which would be reached at 4.1s or 82m / 270ft
	 * It will accelerate slower once above low speeds
	 * @ 10 m/s estimated air resistance acceleration would be -0.6 m/s^2. If fall time on the order of 3s, this would
	 * only affect speed vi = 10, vf = 8.2, vavg = 9.1.  So not a lot, and is partially offset by reduced fall speed
	 *  Approximate Equations: m = 2kg, cd =1 CSA = 0.02m^2, rho = 1.25   a = 0.5*cd*rho*CSA*v^2/m = 0.00625*v^2
	 *  Overall approximation of 0.9 correction factor
	 *  See MATLAB script for more details
	 */
	private final double CORRECTION_FACTOR = 0.9,
						 SERVO_OPEN_DELAY = 250,  //ms
						 RX_TRANSMISSION_DELAY = 250,
						 TX_TRANSMISSION_DELAY = 250;
	
	private void calculateHorizDistance() {
		// targetPos.getAltitude() will probably be 0, but I included it just in case:
		double heightm = (curPos.getAltitudeM() - targetPos.getAltitudeM()); // Altitudes are already in m
		// Calculate time for payload to fall:
		if(heightm < 0)
		{
			heightm = 0;  //prevent NaN from sqrt
		}
		
		double fallTime = Math.sqrt(2 * heightm / 9.807); // time in seconds
		
		
				
		// Calculate horizontal distance that payload will travel in this time:
		double distanceDuringFall = curPos.getVelocityMPS() * fallTime * CORRECTION_FACTOR;
	    double distanceFromDataAge = curPos.getVelocityMPS()*(System.currentTimeMillis() - curPos.getSystemTime())/1000.0;
		double distanceFromLatencies = curPos.getVelocityMPS()*(SERVO_OPEN_DELAY + RX_TRANSMISSION_DELAY + TX_TRANSMISSION_DELAY)/1000.0;
		horizDistance = distanceDuringFall + distanceFromDataAge + distanceFromLatencies;
	}
	
	/*
	  Step 5
	  Calculate where we end up in relation to target 
	 */
	 
	 private void calculateDistFromEstDropPosToTarget() {

	  estDropEasting = curPos.getUTMEasting() + Math.cos(curPos.getMathAngle() / 180 * Math.PI) * horizDistance;
	  estDropNorthing = curPos.getUTMNorthing() + Math.sin(curPos.getMathAngle() / 180 * Math.PI) * horizDistance;

	  distFromEstDropPosToTarget = Math.sqrt(Math.pow(targetPos.getUTMNorthing() - estDropNorthing, 2) + Math.pow(targetPos.getUTMEasting() - estDropEasting, 2));

	}
	
	/*
	 * Step '6.' in class description:
	 * 
	 * Calculates time until optimal drop location.
	 */
	private void calculateTimeTillDrop() {
		
	    //Need to handle case where moving away from target (PLANE is past target) and moving towards.
	    //Note - you do not need to hangle the case where plane is before target and drop location is after - this falls under 
	    //'moving towards' and can be handled the same way.
		
	    //Moving away from target
	    if(distFromEstDropPosToTarget > directDistanceToTarget)
	    {
	        timeTillDrop = (-distAlongPathToMinLateralErr - horizDistance) / curPos.getVelocityMPS();   //now distAlongPathToMinLateralErr is a negative
	    }
	    else
	    {
	        timeTillDrop = (distAlongPathToMinLateralErr - horizDistance) / curPos.getVelocityMPS();
	    }

	}
}
