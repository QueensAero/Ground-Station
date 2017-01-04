
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
 * 	5. Calculate the time until the optimal drop time based on the current speed and the
 * 		distance from '4.'.
 * 
 *  
 *  ***Note: This class assumes that all coordinates are in the same UTM zone. If this is
 *  not the case then everything will break.
 *  
 */
public class GPSTargeter {
	// Inputs
	private GPSPos curPos;
	private GPSPos targetPos;
	
	// Calculated values:
	private double latError;
	private double timeToDrop;
	
	private SpeechManager speechManager;
	
	
	public GPSTargeter(GPSPos target) {
		curPos = null;
		targetPos = target;
		latError = -1;
		timeToDrop = -1;
		speechManager = new SpeechManager();
	}
	
	public void setTargetPos(GPSPos target) {
		targetPos = target;
		if(curPos != null && targetPos != null) { // If valid input, carry out calculations
			// Order matters:
			latError = computeLateralErr();
			timeToDrop = timeToDrop();
		}
	}
	
	/*
	 * Updates the current position and causes the dropTime prediction to be re-calculated.
	 */
	public void updateCurPos(GPSPos pos) {
		curPos = pos; // Don't worry, GPSPos is immutable
		if(curPos != null && targetPos != null) { // If valid input, carry out calculations
			// Order matters:
			latError = computeLateralErr();
			timeToDrop = timeToDrop();
			speechManager.reportTime(timeToDrop);
			speechManager.reportAltitude(curPos.getAltitude());
		}
	}
	
	public double getLateralError() { return latError; }
	public double getTimeToDrop() { return timeToDrop; }
	public double getDropDistance() { return dropDistance();  }  //assumes updateCurPos has already been called
	
	/* 
	 * Step '1.' in class description:
	 * 
	 * Computes shortest distance from the line defined by the plane's path
	 * to the location of the target.
	 */
	private double computeLateralErr() {
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
		
		return num / denom;
	}
	
	/*
	 * Step '2.' in class description:
	 * 
	 * Calculates direct distance from current position to the target.
	 */
	private double directDistToTarget() {
		double dist = 0;
		// Use pythagorean theorem to calculate distance between curPos and targetPos:
		dist = Math.sqrt(Math.pow((targetPos.getUTMEasting() - curPos.getUTMEasting()), 2) + 
				Math.pow((targetPos.getUTMNorthing() - curPos.getUTMNorthing()), 2));
		return dist;
	}
	
	/*
	 * Step '3.' in class description
	 * 
	 * Calculates distance along current path until nearest point to target.
	 *
	 *	Note: Relies on the fact that an accurate lateral error has already been calculated
	 */
	private double pathDistToTarget() {
		double dist = 0;
		dist = Math.sqrt(Math.pow(directDistToTarget(), 2) - Math.pow(latError, 2));		
		//System.out.print("Distance to target along path: ");
		//System.out.println(dist);
		return dist;
	}
	
	/*
	 * Step '4.' in class description
	 * 
	 * Calculates how early (distance in m before being above the target) the
	 * package must be dropped based on current speed and altitude.
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
	private final double CORRECTION_FACTOR = 0.9;
	
	private double dropDistance() {
		double fallTime = 0;
		double horizDist = 0;
		// targetPos.getAltitude() will probably be 0, but I included it just in case:
		//double heightm = (curPos.getAltitude() - targetPos.getAltitude())  / 3.28084; // convert feet to meters
		double heightm = (curPos.getAltitude() - targetPos.getAltitude()); // Altitudes are already in m
		// Calculate time for payload to fall:
		if(heightm < 0)
			heightm = 0;  //prevent NaN from sqrt
		
		fallTime = Math.sqrt(2 * heightm / 9.807); // time in seconds
		
		
				
		// Calculate horizontal distance that payload will travel in this time:
		horizDist = curPos.getVelocity() * fallTime*CORRECTION_FACTOR;
		return horizDist;
	}
	
	/*
	 * Step '5.' in class description:
	 * 
	 * Calculates time until optimal drop location.
	 */
	private double timeToDrop() {
		double distRemaining = 0;
		double time;
		distRemaining = pathDistToTarget() - dropDistance();
		// t = d/v
		time = distRemaining / curPos.getVelocity();
		return time;
	}
}
