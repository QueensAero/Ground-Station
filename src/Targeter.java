import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;


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
	double altitudeFt = 0, altitudeMetres = 0, speed = 0, lattitude = 0, longitude = 0, heading = 0;  //altitude in ft, spd in m/s, heading in degress, GPS in XXYY.ZZZZ XX = degress, YY = minutes,  ZZZZ = decimal minutes
	int  second = 0, millisec = 0;
	
	//tracking variables:
	//note: xDistToTarget is a positive if East, and yDistToTarget is positive if North. These are in ft
	int xPos,  		//cartesian coordinate in x/y grid. +x = East.  Value in m
		yPos;  		//cartesian coordinate in x/y grid. +y = North.  Value in m
	boolean payloadDropped = false;

	
	double  lateralError = 35.1553154,	//lateral error assuming optimal drop time (in m)
			timeToDrop = 13.13;			//estimated time until optimal drop (in seconds)
	
	private int TIME_DELAY_MS_BEFORE_DROP = 500;  //constant offset time between sending drop command (on ground) to receiving it and servo rotating to release payload
	private double FT_TO_METRES = 0.3048;  
	
	//target Area Rings & sizing variables
	private double metersPerPix = 0.5;
	private int numRings = 4, //number of 'areas' or rings in target area
    			ringOutRadius = (int)(18.288/metersPerPix), //radius of very outside ring in PIXELS.  Note: assumed R = 60ft = 18.288 m
    			ringBaseL = cols/2-ringOutRadius, //x coordinate of top left corner
    			ringBaseT = rows/2 -ringOutRadius;  //y coordinate of top left corner
	
	//GPSPos objects (initialize positions to some offset from target. Currently target is a pt behind ILC
	double targetLatt = 4413.7078, targetLong = -7629.5106, startOffset = 0.5; //0.5 GPS minutes ~ 500 metres, use as a non-zero starting point (for before GPS fix)
	GPSPos baseGPSposition; 
	GPSPos curGPSPosition; 
	GPSPos targetPos;   //this is currently a point just behind ILC. NOTE the NEGATIVE on Long component to account for west
	GPSTargeter GSPTargeting; 
	
	
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
		  
	
		LocalDateTime now = LocalDateTime.now();	//to have a current timestamp
		int tempInitV = 4, tempInitAlt = 35, tempInitHeading = 42; 
		int initXOff = -100, initYOff = -100;
		targetPos = new GPSPos(targetLatt, targetLong,0,0,0,0,0);   //this is currently a point just behind ILC. NOTE the NEGATIVE on Long component to account for west
		baseGPSposition = new GPSPos(targetPos.getUTMZone(), targetPos.getUTMLetter(), targetPos.getUTMNorthing()+initYOff, targetPos.getUTMEasting()+initXOff, 
									tempInitV, tempInitAlt, tempInitHeading, now.getSecond(),now.get(ChronoField.MILLI_OF_SECOND));  //start -initYOff S & -initXOff W of target 
		GSPTargeting = new GPSTargeter(targetPos);
		  
		
		threadTimer = new Timer(100, updateTargetArea);  //33 ms ~30FPS
		threadTimer.start(); //note: by default Coalescing is on, meaning that if won't queue events
	
	}
	
	public void update()
	{
		//transform baseGPSPos to 'curent' time
		transformBasetoCurGPSPos();   //result will be that the curGPSPosition object contains the most recent location (after accounting for time delays)
		GSPTargeting.updateCurPos(curGPSPosition); //GPSposition is last received point from GPS. The object is updated changed when updateGPSData is callsed
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
    	
    	if(curGPSPosition != null)   //at start will be a few frames where this could happen
     	{ 	//plane position
     		 double planePosXMetres = curGPSPosition.getUTMEasting() - targetPos.getUTMEasting();
     		 double planePosYMetres = curGPSPosition.getUTMNorthing() - targetPos.getUTMNorthing();
     		 drawPoint(frame, (int)(cols/2+planePosXMetres/metersPerPix), (int)(rows/2-planePosYMetres/metersPerPix), 5, Color.YELLOW);
     		 
     		 double estDropPosXMetres = planePosXMetres + GSPTargeting.getDropDistance()*Math.cos(curGPSPosition.getHeading()*Math.PI/180);
     		 double estDropPosYMetres = planePosYMetres + GSPTargeting.getDropDistance()*Math.sin(curGPSPosition.getHeading()*Math.PI/180);
     		 drawPoint(frame, (int)(cols/2+estDropPosXMetres/metersPerPix), (int)(rows/2-estDropPosYMetres/metersPerPix), 5, Color.CYAN);

     		lateralError = GSPTargeting.getLateralError();
     		timeToDrop = GSPTargeting.getTimeToDrop();
     		altitudeFt = curGPSPosition.getAltitude()/FT_TO_METRES;
     				
     		 System.out.println("(Xplane, Yplane) = (" + planePosXMetres + ", " + planePosYMetres + ") and (Xdrop, Ydrop) = (" + estDropPosXMetres + ", " + estDropPosYMetres + ")");
 		
    	
    		frame.setColor(Color.RED);
		    	
	     	 if(Math.abs(estDropPosXMetres) > metersPerPix*cols/2 || Math.abs(estDropPosYMetres) > metersPerPix*rows/2) //about 300 pixels to draw plane position in
	     	  		 frame.drawString("Outside of " + metersPerPix*cols/2 + " m in x or y", 10, 20);    		
	     	 	     	 
	       	 else   //draw projected drop point, lateral error somewhere
	     	 {
	       		 //if/else to decide if writing lateral error in bottom half or top half (so doesn't overwrite the point)
	     		if(planePosYMetres >= -ringOutRadius*metersPerPix) //bottom left
	     		{
	     			frame.drawString(getRing(lateralError, frame), 10, cols - 25);
	     			frame.drawString("Lat Error = " + String.format( "%.1f", lateralError)+ " m", 10, cols - 45);  //done after so automatically same font colour
	
	     			 if(altitudeFt < 100)
	     			  	 frame.drawString("Alt too low! (alt = " + String.format( "%.1f", altitudeFt) + " ft)", 10, cols - 5);    
	     			 else if(lateralError < 60)  //otherwise shouldn't drop
	      				 frame.drawString("Time to Drop = " + String.format( "%.1f", timeToDrop)+ " s", 10, cols - 5);  //done after so automatically same font colour     			     			 
	     		}
	     		else  //top left
	     		{	
	     			frame.drawString(getRing(lateralError, frame), 10, 40);
	     			frame.drawString("Lat Error = " + String.format( "%.1f", lateralError)+ " m", 10, 20);  //done after so automatically same font colour
	
	     			 if(altitudeFt < 100)
	     			  	 frame.drawString("Alt too low! (alt = " + String.format( "%.1f", altitudeFt) + " ft)", 10, 60);    
	     			 else if(lateralError < 60)  //otherwise shouldn't drop
	      				 frame.drawString("Time to Drop = " + String.format( "%.1f", timeToDrop)+ " s", 10, 60);  //done after so automatically same font colour       			      		
	     		
	     		 }
	     		 
	     		   	 
	     	 }  
     	}
    	
    	
    	
    }
    
    
    
    private String getRing(double latError, Graphics2D frame)
    {
    	
    	if(latError < 15*FT_TO_METRES)
    	{	frame.setColor(Color.GREEN);	
    		return new String("Proj Ring = "+1);
    	}
    	else if(latError < 30*FT_TO_METRES)
    	{	frame.setColor(Color.CYAN);	
    		return new String("Proj Ring = "+2);
    	}
    	else if(latError < 45*FT_TO_METRES)
    	{	frame.setColor(Color.YELLOW);	
			return new String("Proj Ring = "+3);
    	}
    	else if(latError < 60*FT_TO_METRES)
    	{	frame.setColor(Color.ORANGE);	
			return new String("Proj Ring = "+4);
    	}
    	else
    	{	frame.setColor(Color.RED);	
			return "Projected Outside Rings";
    	}
    	
    }
    
    private void drawPoint(Graphics2D frame, int xCent, int yCent, int size, Color colour)
    {	
    	frame.setColor(colour);
       	Ellipse2D.Double circle = new Ellipse2D.Double();
       	circle.x = xCent - size;
		circle.y = yCent - size;
		circle.height = size*2;
		circle.width = size*2;
		frame.fill(circle);
    	
    }
    

    
	public int getRows() {  return rows; }
	public int getCols() {  return cols; }
	
	public void updateGPSData(double alt, double spd, double Lat, double Long, double headng, int sec, int ms ){

		altitudeFt = alt;
		altitudeMetres = alt*FT_TO_METRES;
		speed = spd;
		lattitude = Lat;
		longitude = Long;
		heading = headng;
		second = sec;
		millisec = ms;		
		
		baseGPSposition = new GPSPos(lattitude, longitude, speed, heading, altitudeMetres, sec, ms);
		
		LocalDateTime now = LocalDateTime.now();
		int msFromGPSCoord = getMsBetween(second, millisec, now.getSecond(), now.get(ChronoField.MILLI_OF_SECOND));
		System.out.println("Time Offset = " + msFromGPSCoord + " ms");
				
	}
	
	//go from base position (which is delayed from real-time) to current time by assuming constant speed/heading over the differnece in time
	private void transformBasetoCurGPSPos()   //reset will be that the curGPSPosition object contains the most recent location
	{
		LocalDateTime now = LocalDateTime.now();
		int msFromGPSCoord = getMsBetween(baseGPSposition.getSecond(), baseGPSposition.getMilliSecond(), now.getSecond(), now.get(ChronoField.MILLI_OF_SECOND));
		double curNorthing = projectYForward(baseGPSposition.getVelocity(), baseGPSposition.getHeading(), baseGPSposition.getUTMNorthing(), msFromGPSCoord +TIME_DELAY_MS_BEFORE_DROP);
		double curEasting = projectXForward(baseGPSposition.getVelocity(), baseGPSposition.getHeading(), baseGPSposition.getUTMEasting(), msFromGPSCoord+TIME_DELAY_MS_BEFORE_DROP); 
		curGPSPosition = new GPSPos(baseGPSposition.getUTMZone(), baseGPSposition.getUTMLetter(), curNorthing, curEasting, baseGPSposition.getVelocity(), 
										baseGPSposition.getAltitude(), baseGPSposition.getHeading(), now.getSecond(), now.get(ChronoField.MILLI_OF_SECOND));
			
	}
		
	//following functions will end up in GPSPos or GPSTargeter
	double projectXForward(double spd, double heading, double curX, int timeDiffMs)    //0 degrees = North, 90 degrees = East etc. spd in m/s, curX in m 
	{
		double angle = headingToMathAngle(heading);
		return (curX + spd*Math.cos(angle*Math.PI/180)*timeDiffMs/1000.0);

	}
	
	double projectYForward(double spd, double heading, double curY, int timeDiffMs)    //0 degrees = North, 90 degrees = East etc. spd in m/s, curX in m 
	{
		double angle = headingToMathAngle(heading);
		return (curY + spd*Math.sin(angle*Math.PI/180)*timeDiffMs/1000.0);
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
		
		//calculate seconds component. This is slightly harder since the amount of seconds difference depends on ms relationship. Hence the added if(ms1 > ms2)
		int secondsTerm = 1000*(s2-s1);
		if(secondsTerm < 0)
			secondsTerm = 1000*(s2-(s1-60));  
		
		if(ms1 > ms2)
			secondsTerm = secondsTerm -1000;  //minus 1000 ms 
		
		
		//assume will never be off by an > hour (if that's the case we are horribly wrong anyways). this is ok with hours wrapping over (see above secondsTerm)
		timeBtwn = secondsTerm + msTerm;
		
		//TEMP - change to something like 5000 later
		if(timeBtwn > 60000)  //if >5 seconds, point likely an error, so return the average assumed offset of 1 second (NOTE: change this based on testing)
		{	
			System.out.println("Offset: " + timeBtwn + " ms rejected (too high)");
			timeBtwn = 1000;
		}
					
		return timeBtwn;
	}
	
	
	
	
	/* Below are functions to save a series of points and display the path. They are untested
	
	int	prevXPos = 30, 	//previous x position (not 1 screen earlier, but 1 GPS data earlier)
		prevYPos = 45;
	int prevPosIndex = 0, numPrevPoints;

	int [] prevXPoints = new int[5];
	int [] prevYPoints = new int[5];
	
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
	 
	 
	private void updatePreviousPoints(int newXPt, int newYPt)
	{	
		
		if(++prevPosIndex >= prevXPoints.length)  //>= because if allocated n, then max index is n-1
			prevPosIndex = 0;		
		
		prevXPoints[prevPosIndex] = xPos;
		xPos = newXPt;
		prevXPoints[prevPosIndex] = xPos;
		yPos = newYPt;
		numPrevPoints++;
		
	}*/
}
