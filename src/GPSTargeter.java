


public class GPSTargeter {

	private double targetLatt = 111.1293, targetLong = 230.1231;
	private double currentLatt, currentLong, curHeading;
	
	private double altitude_meters;
	
	private boolean dropRequested = false;  //have a 'abort' type code - if an error, then 
	
	
	private static double ag = 9.8;
	
	
	//constructor
	GPSTargeter(){
		
		
		
	}
	
	
	public void setValues(double curLat, double curLong, double altitude, double heading){
		currentLatt = curLat;
		currentLong = curLong;
		altitude_meters = altitude/3.2808;  //convert the value to meters for targeting
		curHeading = heading;
		
	}
	
	//True = ready to drop (or in XYZ seconds ready),  false = not ready to drop
	public boolean checkDrop(){
		
		
		//model distance between current point and target
		double distance = distance(currentLatt, targetLatt, currentLong, targetLong, altitude_meters, 0);  //0 -> dropzone at height 0 meters
		double time = Math.sqrt(2*altitude_meters/ag);  // d= 1/2 a t^2  -->  t = sqrt(2d/a).  Assume initial y velocity is 0
		double headingToTarget = bearing(currentLatt, currentLong, targetLatt, targetLong);
		
		//check the heading is within an acceptable range
		//if(Math.abs(headingToTarget - curHeading) < )
		{
			
			
		}
		
		
		//model left/right targeting
		
		
		
		
		return false;
	}
	
	
	/*
	 * Calculate distance between two points in latitude and longitude taking
	 * into account height difference. If you are not interested in height
	 * difference pass 0.0. Uses Haversine method as its base.
	 * 
	 * lat1, lon1 Start point lat2, lon2 End point el1 Start altitude in meters
	 * el2 End altitude in meters
	 * @returns Distance in Meters
	 */
	private double distance(double lat1, double lat2, double lon1, double lon2, double el1, double el2) {

	    final int R = 6371; // Radius of the earth

	    Double latDistance = Math.toRadians(lat2 - lat1);
	    Double lonDistance = Math.toRadians(lon2 - lon1);
	    Double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
	            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
	            * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
	    Double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	    double distance = R * c * 1000; // convert to meters

	    double height = el1 - el2;

	    distance = Math.pow(distance, 2) + Math.pow(height, 2);

	    return Math.sqrt(distance);
	}
	
	private double bearing(double lat1, double lon1, double lat2, double lon2){
		  double longitude1 = lon1;
		  double longitude2 = lon2;
		  double latitude1 = Math.toRadians(lat1);
		  double latitude2 = Math.toRadians(lat2);
		  double longDiff= Math.toRadians(longitude2-longitude1);
		  double y= Math.sin(longDiff)*Math.cos(latitude2);
		  double x=Math.cos(latitude1)*Math.sin(latitude2)-Math.sin(latitude1)*Math.cos(latitude2)*Math.cos(longDiff);

		  return (Math.toDegrees(Math.atan2(y, x))+360)%360;
	}
	
	
	
	
}
