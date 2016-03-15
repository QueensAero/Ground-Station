import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;


import java.nio.Buffer;
import java.time.LocalDateTime;
import java.time.temporal.ChronoField;

import javax.swing.JPanel;
import javax.swing.Timer;


//transmission latency should be ~65/2 ms (half of round trip value per http://hades.mech.northwestern.edu/index.php/PIC32MX:_XBee_Wireless_Round-trip_Latency

public class Targeter extends JPanel {
	
	
	//thread variables
	Timer threadTimer; 
	
	//container for image that's created
	BufferedImage image;
	
	//size of created image
	private int cols = 250, rows = 250;

	
	//curent GPS information
	double altitude = 0, speed, lattitude, longitude, heading;  //altitude in ft, spd in m/s, heading in degress, GPS in XXYY.ZZZZ XX = degress, YY = minutes,  ZZZZ = decimal minutes
	int hour, minute, second, millisec;
	
	//tracking variables:
	//note: xDistToTarget is a positive if East, and yDistToTarget is positive if North. These are in ft
	int xPos = 45,  		//cartesian coordinate in x/y grid. +x = East.  Value in m
		prevXPos = 30, 	//previous x position (not 1 screen earlier, but 1 GPS data earlier)
		yPos = 10,  		//cartesian coordinate in x/y grid. +y = North.  Value in m
		prevYPos = 45;
	int [] prevXPoints = new int[5];
	int [] prevYPoints = new int[5];
	int prevPosIndex = 0, numPrevPoints;
	boolean payloadDropped = false;

	
	double  lateralError = 35.1553154,	//lateral error assuming optimal drop time (in m)
			timeToDrop = 13.13;			//estimated time until optimal drop (in seconds)
	
	
	
	//target Area Rings & sizing variables
	private double metersPerPix = 0.5;
	private int numRings = 4, //number of 'areas' or rings in target area
    			ringOutRadius = (int)(18.288/metersPerPix), //radius of very outside ring.  Note: assumed R = 60ft = 18.288 m
    			ringBaseL = cols/2-ringOutRadius, //x coordinate of top left corner
    			ringBaseT = rows/2 -ringOutRadius;  //y coordinate of top left corner
	
	//sub tracking class objects
	//GPSPos baseGPSposition;
	//GPSPos curGPSPosition;
	//GPSPos targetPos(....);
	//GPSTageter GSPTargeting(targetPos);
	
	
	//constructor
	public Targeter() {
		
		//set size of JPanel
		Dimension size = new Dimension(cols, rows);
		this.setPreferredSize(size);
		
		 ActionListener updateTargetArea = new ActionListener() {
		      public void actionPerformed(ActionEvent evt) {
		  			update();	
		      }
		  };
		  
		  //Question - is GPSPos class going to be created new each time, or why is it separate from GPSTargeter? 
		  // GPSposition = new GPSpos(...);
		  //GSPTargeting = new GPSTarget(
		  
		
		threadTimer = new Timer(33, updateTargetArea);  //33 ms ~30FPS
		threadTimer.start(); //note: by default Coalescing is on, meaning that if won't queue events
	
	}
	
	public void update()
	{
		//transform baseGPSPos to 'curent' time
		updateCurGPSPos();   //result will be that the curGPSPosition object contains the most recent location (after accounting for time delays)
		//GSPTargeting.updateCurPos(curGPSPosition); //GPSposition is last received point from GPS. The object is updated changed when updateGPSData is callsed
		this.repaint();
		
	
		
	}
	
	@Override
	public void paintComponent(Graphics g) {
		
	    image = new BufferedImage(cols, rows, BufferedImage.TYPE_3BYTE_BGR);  
    	Graphics2D frame = image.createGraphics();
    	
    	frame.setFont(new Font("TimesRoman", Font.PLAIN, 20)); 
   	
    	addTargetArea(frame);
    	if(!payloadDropped)
    		drawPlanePosition(frame);
    	else
    	{	frame.setColor(Color.GREEN);
    		frame.drawString("Payload Dropped!", 10, 20); 
    	}	
		g.drawImage(image, 0, 0, null);

		
	}
	
	public void setDropStatus(boolean status)
	{
		payloadDropped = status;
		
	}
	
	//draw 4 rings. Inner = 1.0 scoring multiplier, 2 = .75 etc.  each ring is 15 ft larger in radius
    void addTargetArea(Graphics2D frame)
    {
       	Ellipse2D.Double circle = new Ellipse2D.Double();
        
    	//note this assumes that each delta in radius is equal. Note: draw widest, draw next widest to overwrite, repeat until at inner ring
    	for(int i = 0; i < numRings; i++)
    	{	circle.x = ringBaseL +i*ringOutRadius/numRings;  //note: delta radius is not based on metersPerPix (as reflected by not includ. in calculation
    		circle.y = ringBaseT + i*ringOutRadius/numRings;
    		circle.height = 2*(ringOutRadius-ringOutRadius*i/numRings);  //x2 since this is a diameter, not radius
    		circle.width = 2*(ringOutRadius-ringOutRadius*i/numRings);
    		
    		//alternate between red and white colour of ring
    		if(i%2 == 0)
    			frame.setColor(Color.RED);
    		else
    			frame.setColor(Color.WHITE);

    		frame.fill(circle);
    	}
 	    	
    }
    
    //Note: want to hit point(ringCentX, ringCentY). Each pixel away from that is metersPerPix meters away. The drawing area is constrained by  (targeterStartPixX, targeterStartPixY) as TL
    // to (cols, TotRows) as the BR pt.   drawAreaWidth is the pixels 
    void drawPlanePosition(Graphics2D frame)
    {	
    	
 		frame.setColor(Color.RED);

    	
     	 if(Math.abs(xPos) > metersPerPix*cols/2 || Math.abs(yPos) > metersPerPix*rows/2) //about 300 pixels to draw plane position in
     	 {
     		
     		 frame.drawString("Outside of " + metersPerPix*cols/2 + " ft in x or y", 10, 20);    		
     	 	
     	 
     	 }
       	 else   //draw projected drop point, lateral error somewhere
     	 {
       		 //if/else to decide if writing lateral error in bottom half or top half (so doesn't overwrite the point)
     		if(yPos >= -ringOutRadius ) //bottom left
     		{
     			frame.drawString(getRing(lateralError, frame), 10, cols - 25);
     			frame.drawString("Lat Error = " + String.format( "%.1f", lateralError)+ " m", 10, cols - 45);  //done after so automatically same font colour

     			 if(altitude < 100)
     			  	 frame.drawString("Alt too low! (alt = " + String.format( "%.1f", altitude) + " ft)", 10, cols - 5);    
     			 else if(lateralError < 60)  //otherwise shouldn't drop
      				 frame.drawString("Time to Drop = " + String.format( "%.1f", timeToDrop)+ " s", 10, cols - 5);  //done after so automatically same font colour     			     			 
     		}
     		else  //top left
     		{	
     			frame.drawString(getRing(lateralError, frame), 10, 40);
     			frame.drawString("Lat Error = " + String.format( "%.1f", lateralError)+ " m", 10, 20);  //done after so automatically same font colour

     			 if(altitude < 100)
     			  	 frame.drawString("Alt too low! (alt = " + String.format( "%.1f", altitude) + " ft)", 10, 60);    
     			 else if(lateralError < 60)  //otherwise shouldn't drop
      				 frame.drawString("Time to Drop = " + String.format( "%.1f", timeToDrop)+ " s", 10, 60);  //done after so automatically same font colour       			      		
     		
     		 }
     		 
     		 //draw projected
     		 
     		 //use coordinates stuff from a targeting class
     		 
     		 double heading = 35;   //getHeading();
     		 //double xPos = targeterStartPixX - 300/2;  // targeterStartPixX - getXDistPlane()/metersPerPix;   
     		 //double yPos = targeterStartPixY - 150/2;  //targeterStartPixY - getYDistPlane()/metersPerPix;
     		 double angleSkew = 4;   //getSkewAngle();    angle away from target, positive is CCW away (ie. heading to target is 35, proj. heading is 31
     		
     		drawPoint(frame, (int)(cols/2+xPos/metersPerPix), (int)(rows/2-yPos/metersPerPix), 5);
     		drawPath(frame); 
     		 
     		 //draw plane (make sure has directionality
     		 //draw projected drop point (some math required to get angles/trajectory right)
     		 //write projected lateral error
     	    	 
     	 }    	
    	
    	
    	
    }
    
    //problems: when first getting on screen - is it ok to draw to offscreen places (I think yes)
    private void drawPath(Graphics2D frame)
    {
    	if(numPrevPoints > prevXPoints.length)  //need a full 'circular' array to complete function
    	{	
    		int index = prevPosIndex, newIndex = prevPosIndex-1;
	    	
	    	frame.setColor(Color.GREEN);
	    	frame.drawLine(xPos, yPos, prevXPoints[index], prevYPoints[index]);  //from current pt to previous point
	    	
	    	
	    	for(int i = 0; i < prevXPoints.length-1; i++)
	    	{
	    		if(newIndex < 0)
	    			newIndex = prevXPoints.length-1;
	    			
	    		frame.drawLine(prevXPoints[index], prevYPoints[index], prevXPoints[newIndex], prevYPoints[newIndex]);
	    		index = newIndex--;
	    		
	    	}
    	}
    }
    
    private String getRing(double latError, Graphics2D frame)
    {
    	double ftToMetres = 0.3048;
    	if(latError < 15*ftToMetres)
    	{	frame.setColor(Color.GREEN);	
    		return new String("Proj Ring = "+1);
    	}
    	else if(latError < 30*ftToMetres)
    	{	frame.setColor(Color.CYAN);	
    		return new String("Proj Ring = "+2);
    	}
    	else if(latError < 45*ftToMetres)
    	{	frame.setColor(Color.YELLOW);	
			return new String("Proj Ring = "+3);
    	}
    	else if(latError < 60*ftToMetres)
    	{	frame.setColor(Color.ORANGE);	
			return new String("Proj Ring = "+4);
    	}
    	else
    	{	frame.setColor(Color.RED);	
			return "Projected Outside Rings";
    	}
    	
    }
    
    private void drawPoint(Graphics2D frame, int xCent, int yCent, int size)
    {	
    	frame.setColor(Color.CYAN);
       	Ellipse2D.Double circle = new Ellipse2D.Double();
       	circle.x = xCent - size;
		circle.y = yCent - size;
		circle.height = size*2;
		circle.width = size*2;
		frame.fill(circle);
    	
    }
    

    
	public int getRows() {  return rows; }
	public int getCols() {  return cols; }
	
	public void updateGPSData(double alt, double spd, double Lat, double Long, double headng, int hr, int min, int sec, int ms ){

		altitude = alt;
		speed = spd;
		lattitude = Lat;
		longitude = Long;
		heading = headng;
		hour = hr;
		minute = min;
		second = sec;
		millisec = ms;		
		
		//baseGPSposition = new GPSPos(lattitude, longitude, speed, heading, altitude, sec, ms);
		
		LocalDateTime now = LocalDateTime.now();
		int msFromGPSCoord = getMsBetween(second, millisec, now.getSecond(), now.get(ChronoField.MILLI_OF_SECOND));
		System.out.println("Time Offset = " + msFromGPSCoord + " ms");
				
	}
	
	//go from base position (which is delayed from real-time) to current time by assuming constant speed/heading over the differnece in time
	private void updateCurGPSPos()   //reset will be that the curGPSPosition object contains the most recent location
	{
	/*	LocalDateTime now = LocalDateTime.now();
		int msFromGPSCoord = getMsBetween(baseGPSposition.getSecond(), baseGPSposition.getMillisecond(), now.getSecond(), now.get(ChronoField.MILLI_OF_SECOND));
		double curEasting = projectXForward(speed, heading, baseGPSposition.getUTMEasting(), msFromGPSCoord);
		double curNothing = projectYForward(speed, heading, baseGPSposition.getUTMNorthing(), msFromGPSCoord);
		curGPSPosition = new GPSPos(curNothing, curEasting, speed, heading, baseGPSposition.getUTMZone(), baseGPSposition.getUTMLetter());
		*/
	
	}
		
	//following functions will end up in GPSPos or GPSTargeter
	double projectXForward(double spd, double heading, double curX, int timeDiffMs)    //0 degrees = North, 90 degrees = East etc. spd in m/s, curX in m 
	{
		double angle = headingToMathAngle(heading);
		return (curX + spd*Math.cos(angle)*timeDiffMs/1000.0);

	}
	
	double projectYForward(double spd, double heading, double curY, int timeDiffMs)    //0 degrees = North, 90 degrees = East etc. spd in m/s, curX in m 
	{
		double angle = headingToMathAngle(heading);
		return (curY + spd*Math.sin(angle)*timeDiffMs/1000.0);
	}
	
	double headingToMathAngle(double heading)
	{
		//transform from compass degrees to typical math degrees
		double angle = -1*(heading - 90);
		if(angle < 0)
			angle = 360 + angle;
		
		return angle;
		
	}
	
		
	public int getMsBetween(int s1, int ms1, int s2, int ms2)
	{	int timeBtwn = 0;
		//calculate ms component
		int msTerm = ms2-ms1;
		if(msTerm < 0)
			msTerm = ms2-(ms1-1000);  //ie. Time 1 = 50.493, time 2= 51.221, this gives the correct value of = 221-(493-1000) = 728 ms
		
		//calculate seconds component
		int secondsTerm = s2-s1;
		if(secondsTerm < 0)
			secondsTerm = 1000*(s2-(s1-60));  //ie.Time 1 = 1:59.2, Time 1 = 2:01.1,  this gives correct value of = 1000*(1-(59-60))= 2000ms (from the seconds term)
		
		//assume will never be off by an > hour (if that's the case we are horribly wrong anyways). this is ok with hours wrapping over (see above secondsTerm)
		timeBtwn = secondsTerm + msTerm;
		
		if(timeBtwn > 5000)  //if >5 seconds, point likely an error, so return the average assumed offset of 1 second (NOTE: change this based on testing)
		{	
			System.out.println("Offset: " + timeBtwn + " ms rejected (too high)");
			timeBtwn = 1000;
		}
					
		return timeBtwn;
	}
	
	//This has been tested (although not rigourously)
	double decimalDegMin2Degree(double decimalDegreeMin)  //accepts format AAAYY.ZZZZZ  AAA = degrees,, YY = minutes,  ZZZZZZ = decimal minutes 
	{												//assumes the N/S & E/W sign convention is followed, (N = +ve, W = -ve), This is currently done in MainWindow in analyzePacket
		
		int baseDegree = (int)(decimalDegreeMin/100.0);  	//extracts AAA (the cast will remove the shifted digits /100.0 ->  AAA.YYZZZZZ, cast -> AAA
		double baseDegMins = decimalDegreeMin - 100*baseDegree;  //AAAYY.ZZZ - AAA*100 = AAAYY.ZZ - AAA00.0 = YY.ZZZZ
		return baseDegree + baseDegMins/60.0;  //1 degree minute = 1/60 deegrees
	}
	
	//problem: how to set first value without getting to here (will have some dumb boolean variable checking everytime
	private void updatePreviousPoints(int newXPt, int newYPt)
	{	
		
		if(++prevPosIndex >= prevXPoints.length)  //>= because if allocated n, then max index is n-1
			prevPosIndex = 0;		
		
		prevXPoints[prevPosIndex] = xPos;
		xPos = newXPt;
		prevXPoints[prevPosIndex] = xPos;
		yPos = newYPt;
		numPrevPoints++;
		
	}
}
