import java.util.logging.Logger;

public class GPSPos {
	private static final Logger LOGGER = Logger.getLogger(AeroGUI.class.getName());
	
	//Purposely don't have below - since it may not be initialized if constructor uses UTM coordinates
	//private double latitudeDDM;
	//private double longitudeDDM;
	
	private double velocityMPS; // In m/s
	private double altitudeM; // In meters
	
	private double heading; // In degrees (E = 0, N = 90, W = 180, S = 270)
	private double mathAngle;  //in polar coordinates
	private long systemTime;
	
	private int utmZone;
	private char utmLetter;
	private double utmEasting;
	private double utmNorthing;
	
	/*
	 * Constructor that accepts position as latitude and longitude (in degrees).
	 */
	public GPSPos(double latDDM, double lonDDM, double velocityMPS, double altitudeM, double direction)
	{
		this.systemTime = System.currentTimeMillis();
		this.velocityMPS = velocityMPS;
		this.altitudeM = altitudeM;
		this.heading = direction;  //This is in degrees, with 0 = N, 90 = E
		mathAngle = heading2MathAngle(direction);
		convertDeg2UTM(decimalDegMin2Degree(latDDM), decimalDegMin2Degree(lonDDM)); // Updates all utm attributes
		
		//System.out.println("Utm Zone, Letter, N, E: " + utmZone + ", " + utmLetter + ", " + utmNorthing + ", " + utmEasting);

		
	}
	
	/*
	 * Constructor that accepts position as UTM coordinates.
	 */
	public GPSPos(int utmZone, char utmLetter, double utmNorthing, double utmEasting, double velocityMPS, double altitudeM, double direction)
	{
		this.systemTime = System.currentTimeMillis();
		this.velocityMPS = velocityMPS;
		this.altitudeM = altitudeM;
		this.heading = direction;
		mathAngle = heading2MathAngle(direction);
		
		this.utmZone = utmZone;
		this.utmLetter = utmLetter;
		this.utmNorthing = utmNorthing;
		this.utmEasting = utmEasting;
		
		//System.out.println("Utm Zone, Letter, N, E: " + utmZone + ", " + utmLetter + ", " + utmNorthing + ", " + utmEasting);
		
	}
	
	
	//public double getLatitude() {return latitude; };
	//public double getLongitude() {return longitude; };
	public double getVelocityMPS() { return velocityMPS;	}
	public double getAltitudeM() { return altitudeM; }
	public double getAltitudeFt() { return altitudeM*3.28084; }
	public double getHeading() { return heading;  }
	public double getMathAngle() { return mathAngle; }
	public double getUTMEasting() { return utmEasting; }
	public double getUTMNorthing() { return utmNorthing; }
	public int getUTMZone() { return utmZone; }
	public char getUTMLetter(){  return utmLetter;  }
	public long getSystemTime() { return systemTime; }
	
	double heading2MathAngle(double heading)
	{
		// Transform from compass degrees to typical math degrees
		double angle = -1*(heading - 90);
		if(angle < 0)
			angle = 360 + angle;	
		return angle;	
	}
	
	/*
	 * Convert from AAAYY.ZZZZZ to decimal degrees.
	 * 		- AAA = degrees
	 * 		- YY = minutes
	 * 		- ZZZZZZ = decimal minutes
	 */
	double decimalDegMin2Degree(double decimalDegreeMin)  //accepts format AAAYY.ZZZZZ  AAA = degrees,, YY = minutes,  ZZZZZZ = decimal minutes 
	{
		// Assumes the N/S & E/W sign convention is followed (N = +ve, W = -ve).
		// This is currently done in MainWindow in analyzePacket
		
		int baseDegree = (int)(decimalDegreeMin / 100.0);  	// Extracts AAA (the cast will remove the shifted digits /100.0 ->  AAA.YYZZZZZ, cast -> AAA)
		double baseDegMins = decimalDegreeMin - 100 * baseDegree;  // AAAYY.ZZZ - AAA*100 = AAAYY.ZZ - AAA00.0 = YY.ZZZZ
		return baseDegree + baseDegMins/60.0;  // 1 degree minute = 1/60 degrees
	}
	
	/*
	 * Converts the object's 'lat' and 'lon' attributes into UTM coordinates and 
	 * saves the result into the utm* attributes.
	 */
	private void convertDeg2UTM(double lat, double lon)
    {
        utmZone = (int) Math.floor(lon/6+31);
        if (lat<-72) 
            utmLetter='C';
        else if (lat<-64) 
            utmLetter='D';
        else if (lat<-56)
            utmLetter='E';
        else if (lat<-48)
            utmLetter='F';
        else if (lat<-40)
            utmLetter='G';
        else if (lat<-32)
            utmLetter='H';
        else if (lat<-24)
            utmLetter='J';
        else if (lat<-16)
            utmLetter='K';
        else if (lat<-8) 
            utmLetter='L';
        else if (lat<0)
            utmLetter='M';
        else if (lat<8)  
            utmLetter='N';
        else if (lat<16) 
            utmLetter='P';
        else if (lat<24) 
            utmLetter='Q';
        else if (lat<32) 
            utmLetter='R';
        else if (lat<40) 
            utmLetter='S';
        else if (lat<48) 
            utmLetter='T';
        else if (lat<56) 
            utmLetter='U';
        else if (lat<64) 
            utmLetter='V';
        else if (lat<72) 
            utmLetter='W';
        else
            utmLetter='X';
        
        utmEasting=0.5*Math.log((1+Math.cos(lat*Math.PI/180)*Math.sin(lon*Math.PI/180-(6*utmZone-183)*Math.PI/180))/(1-Math.cos(lat*Math.PI/180)*Math.sin(lon*Math.PI/180-(6*utmZone-183)*Math.PI/180)))*0.9996*6399593.62/Math.pow((1+Math.pow(0.0820944379, 2)*Math.pow(Math.cos(lat*Math.PI/180), 2)), 0.5)*(1+ Math.pow(0.0820944379,2)/2*Math.pow((0.5*Math.log((1+Math.cos(lat*Math.PI/180)*Math.sin(lon*Math.PI/180-(6*utmZone-183)*Math.PI/180))/(1-Math.cos(lat*Math.PI/180)*Math.sin(lon*Math.PI/180-(6*utmZone-183)*Math.PI/180)))),2)*Math.pow(Math.cos(lat*Math.PI/180),2)/3)+500000;
        utmNorthing = (Math.atan(Math.tan(lat*Math.PI/180)/Math.cos((lon*Math.PI/180-(6*utmZone -183)*Math.PI/180)))-lat*Math.PI/180)*0.9996*6399593.625/Math.sqrt(1+0.006739496742*Math.pow(Math.cos(lat*Math.PI/180),2))*(1+0.006739496742/2*Math.pow(0.5*Math.log((1+Math.cos(lat*Math.PI/180)*Math.sin((lon*Math.PI/180-(6*utmZone -183)*Math.PI/180)))/(1-Math.cos(lat*Math.PI/180)*Math.sin((lon*Math.PI/180-(6*utmZone -183)*Math.PI/180)))),2)*Math.pow(Math.cos(lat*Math.PI/180),2))+0.9996*6399593.625*(lat*Math.PI/180-0.005054622556*(lat*Math.PI/180+Math.sin(2*lat*Math.PI/180)/2)+4.258201531e-05*(3*(lat*Math.PI/180+Math.sin(2*lat*Math.PI/180)/2)+Math.sin(2*lat*Math.PI/180)*Math.pow(Math.cos(lat*Math.PI/180),2))/4-1.674057895e-07*(5*(3*(lat*Math.PI/180+Math.sin(2*lat*Math.PI/180)/2)+Math.sin(2*lat*Math.PI/180)*Math.pow(Math.cos(lat*Math.PI/180),2))/4+Math.sin(2*lat*Math.PI/180)*Math.pow(Math.cos(lat*Math.PI/180),2)*Math.pow(Math.cos(lat*Math.PI/180),2))/3);
        if (utmLetter<'M')
            utmNorthing = utmNorthing + 10000000;
        
        //Round readability (and note that 0.01 is in 'm', so the error is negligable
        utmEasting=Math.round(utmEasting*100)*0.01;
        utmNorthing=Math.round(utmNorthing*100)*0.01;
    }
}
