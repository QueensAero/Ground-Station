import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;


import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.util.logging.Logger;

import javax.swing.JPanel;
import javax.swing.Timer;


//transmission latency should be ~65/2 ms (half of round trip value per http://hades.mech.northwestern.edu/index.php/PIC32MX:_XBee_Wireless_Round-trip_Latency

public class Targeter extends JPanel {
	private static final Logger LOGGER = Logger.getLogger(AeroGUI.class.getName());
	
	//thread variables
	Timer threadTimer; 
	
	//container for image that's created
	BufferedImage targetImage;
	BufferedImage textImage;
	
	//size of created image (ensure is a square)
	private int cols = 400, rows = cols;
	//TODO - have size scale to current size allocated to targeter
	
	//curent GPS information
	double altitudeFt = 0, altitudeMetres = 0, speed = 0, lattitudeDDM = 0, longitudeDDM = 0, heading = 0;  //altitude in ft, spd in m/s, heading in degress, GPS in XXYY.ZZZZ XX = degress, YY = minutes,  ZZZZ = decimal minutes
	int  second = 0, millisec = 0;
	int msFromGPSCoord = 0;
	
	//tracking variables:
	public boolean payloadDropped = false;
	private double  planePosXMetres = 0,			// these are cartesian coordinate in x/y grid. +x = East.  Value in m
					planePosYMetres = 0, 
					estDropPosXMetres = 0,
					estDropPosYMetres = 0, 
					actEstDropPosXMeters = 0,  //when dropped this holds the location is was estimated to hit baed on targeter
					actEstDropPosYMeters = 0,
					targetXMeters = 0,
					targetYMeters  = 0;  

	
	public double  lateralError = 0,	//lateral error assuming optimal drop time (in m)
					timeToDrop = 0;			//estimated time until optimal drop (in seconds)
	
	private int TIME_DELAY_MS_BEFORE_DROP = 500;  //estimated constant offset time between sending drop command (on ground) to receiving it and servo rotating to release payload
	private double FT_TO_METRES = 0.3048;  
	private int JPANEL_WIDTH = 1920/2;   //note assumes screen is 1920*1080
	
	//target Area Rings & sizing variables
	private double scale = 1;
	private int numRings = 4; //number of 'areas' or rings in target area
	private int minAreaSizeMeters = 300;  //Note only half of this is on either side of rings  
	private double metersPerPix = minAreaSizeMeters/(double)cols;

	
	private int ringOutRadiusPix = (int)(18.288/metersPerPix); //radius of very outside ring in PIXELS.  Note: assumed R = 60ft = 18.288 m

	
	
	//GPSPos objects (initialize positions to some offset from target. Currently target is a pt behind ILC
	double targetLattDDM = 4413.724, targetLongDDM = -7629.492;  //behind ILC
	//double targetLatt = 4413.64328, targetLong = -7629.33616;  //fence post or far basebaal diamond

	public GPSPos baseGPSposition; 
	public GPSPos curGPSPosition; 
	public GPSPos targetPos;   //this is currently a point just behind ILC. NOTE the NEGATIVE on Long component to account for west
	public GPSTargeter GSPTargeting;
	
	private boolean autoDropEnabled;
	
	
	//constructor
	public Targeter() {
		autoDropEnabled = false; // Assume that autoDrop is disabled initially
		//set size of JPanel
		this.setMinimumSize(new Dimension(550, rows));
		
		 ActionListener updateTargetArea = new ActionListener() {
		      public void actionPerformed(ActionEvent evt) {
		  			update();	
		      }
		  };
		  
	
		LocalDateTime now = LocalDateTime.now();	//to have a current timestamp
		double tempInitV = 15, tempInitAltMeters = 34, tempInitHeading = 45;   
		int initXOff = -350, initYOff = -350;  //base initial position in meters from target position
		targetPos = new GPSPos(targetLattDDM, targetLongDDM,0,0,0,0,0);   //this is currently just behind ILC. NOTE how Long component declared as negative to account for west
		baseGPSposition = new GPSPos(targetPos.getUTMZone(), targetPos.getUTMLetter(), targetPos.getUTMNorthing()+initYOff, targetPos.getUTMEasting()+initXOff, 
									tempInitV, tempInitAltMeters, tempInitHeading, now.getSecond(),now.get(ChronoField.MILLI_OF_SECOND));  //start -initYOff S & -initXOff W of target 
		GSPTargeting = new GPSTargeter(targetPos);
		
		//Get the values (in meters) in UTM coordinates
		targetXMeters = targetPos.getUTMEasting();
		targetYMeters = targetPos.getUTMNorthing();
		  
		
		threadTimer = new Timer(33, updateTargetArea);  //33 ms ~30FPS
		threadTimer.start(); //note: by default Coalescing is on, meaning that if won't queue events
	
	}
	
	public void setTargetPos(double targetLatDDM, double targetLonDDM) {
		targetPos = new GPSPos(targetLatDDM, targetLonDDM, 0, 0, 0, 0, 0);
		GSPTargeting.setTargetPos(targetPos);
	}
	
	public double getTargetLattDDM() { return targetLattDDM; }
	public double getTargetLongDDM() { return targetLongDDM; }

	public GPSPos getTargetPos() { return targetPos; }
	public GPSPos getbaseGPSPos() { return baseGPSposition; }


	public double getEstDropPosXMetres() { return estDropPosXMetres; }
	public double getEstDropPosYMetres() { return estDropPosYMetres; }
	public double actEstDropPosXMeters() { return actEstDropPosXMeters; }
	public double actEstDropPosYMeters() { return actEstDropPosYMeters; }
	
	public void setAutoDropEnabled(boolean enabled) {
		autoDropEnabled = enabled;
	}
	
	public boolean getAutoDropEnabled() {
		return autoDropEnabled;
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
		
		super.paintComponent(g); //prevents repainting
		
		updatePlaneCharacteristics();  //update all tracking values used in drawing below
		updateScale();		

		
		//Paint the image
	    targetImage = new BufferedImage(cols, rows, BufferedImage.TYPE_3BYTE_BGR);  
    	Graphics2D targetImageFrame = targetImage.createGraphics();
    	targetImageFrame.setFont(new Font("TimesRoman", Font.PLAIN, 20)); 
   	
    	addTargetArea(targetImageFrame);
		drawPlanePosition(targetImageFrame);

    	
		g.drawImage(targetImage, JPANEL_WIDTH/2-cols/2, 0, null);  //center the image
		
		
		//paint the text
		textImage = new BufferedImage(JPANEL_WIDTH/2-cols/2, rows, BufferedImage.TYPE_3BYTE_BGR);
    	addText(textImage.createGraphics());
		g.drawImage(textImage, 0, 0, null);  //center the image
	
	}
	
	public void setDropStatus(boolean status)
	{
		payloadDropped = status;
		
		if(payloadDropped)
		{
			actEstDropPosXMeters = estDropPosXMetres;
			actEstDropPosYMeters = estDropPosYMetres;
		}
		
	}
	
	private void updatePlaneCharacteristics()
	{
		if(curGPSPosition != null) 
		{
			planePosXMetres = curGPSPosition.getUTMEasting() - targetPos.getUTMEasting();
			planePosYMetres = curGPSPosition.getUTMNorthing() - targetPos.getUTMNorthing();
			estDropPosXMetres = planePosXMetres + GSPTargeting.getDropDistance()*Math.cos(curGPSPosition.getMathAngle()*Math.PI/180);
			estDropPosYMetres = planePosYMetres + GSPTargeting.getDropDistance()*Math.sin(curGPSPosition.getMathAngle()*Math.PI/180);
				
			lateralError = GSPTargeting.getLateralError();
	 		timeToDrop = GSPTargeting.getTimeToDrop();
	 		altitudeFt = curGPSPosition.getAltitudeFt();
		}
		
	}
	
	private void addText(Graphics2D textFrame)
	{
		//set to look like background to be clean
		textFrame.setColor(new Color(240,240,240));  
		textFrame.fillRect(0, 0, textImage.getWidth(), textImage.getHeight());
		
    	//set font colour and size
		textFrame.setFont(new Font("TimesRoman", Font.PLAIN, 20)); 
		textFrame.setColor(Color.BLACK);
    	textFrame.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);  //make the text looke nice
    	
    	
    	int startTextX = 50,
    		yTextSpace = 25,
    		yTextMult = 1;
    	
    	textFrame.drawString("Pos: (" + String.format( "%.1f", estDropPosXMetres) + ", " + String.format( "%.1f", estDropPosYMetres) + ")", startTextX, yTextSpace*yTextMult++);
    	double dataAge = System.currentTimeMillis() - baseGPSposition.getSystemTime();
    	textFrame.drawString("Data Age: " + String.format( "%.2f",(System.currentTimeMillis() - baseGPSposition.getSystemTime())/1000.0) + " seconds", startTextX, yTextSpace*yTextMult++);
    	
    	if(!payloadDropped)
    	{		   		
		   		textFrame.drawString(getRing(lateralError), startTextX, yTextSpace*yTextMult++);
		   		textFrame.drawString("Lat Error = " + String.format( "%.1f", lateralError)+ " m", startTextX, yTextSpace*yTextMult++);  
		   		textFrame.drawString("Ms Offset: " + msFromGPSCoord + " ms", startTextX, yTextSpace*yTextMult++); 
		   		
	 			//note: optimal drop time always shown, even if projected outside ringe 
	 			textFrame.drawString("Time to Drop = " + String.format( "%.1f", timeToDrop)+ " s", startTextX, yTextSpace*yTextMult++);      			     			 
		   		
	 			 if(altitudeFt < 100)
	 				textFrame.drawString("Alt too low! (alt = " + String.format( "%.1f", altitudeFt) + " ft)", startTextX, yTextSpace*yTextMult++);    

    	}
    	else
    	{	textFrame.drawString("Payload Dropped!", startTextX, yTextSpace*yTextMult++); 
    		textFrame.drawString("Est Drop Pos: ("+ String.format( "%.1f", actEstDropPosXMeters) + ", " + String.format( "%.1f", actEstDropPosYMeters) + ")", startTextX, yTextSpace*yTextMult++);
    	
    	}
    	
				
	}
	
	private void updateScale()
	{
		double maxD = Math.max(Math.abs(planePosXMetres), Math.abs(planePosYMetres));
		maxD = maxD*1.1;  //Do this so it's a little bit inside
		
		if(maxD < minAreaSizeMeters/2)
			scale = 1;
		else
		{
			scale = (minAreaSizeMeters/2)/maxD;			
		}
		
	}
	
	//draw 4 rings. Inner = 1.0 scoring multiplier, 2 = .75 etc.  each ring is 15 ft larger in radius
	private void addTargetArea(Graphics2D frame)
    {	
		
		frame.setColor(new Color(255,255,255));  
		frame.fillRect(0, 0, targetImage.getWidth(), targetImage.getHeight());
		
		
       	Ellipse2D.Double circle = new Ellipse2D.Double();
        
		double tlX = cols/2 - ringOutRadiusPix*scale;
		double tlY = rows/2 - ringOutRadiusPix*scale;

		
    	//note this assumes that each delta in radius is equal. Note: draw widest, draw next widest to overwrite, repeat until at inner ring
    	for(int i = 0; i < numRings; i++)
    	{	   		
    		circle.x = tlX +i*ringOutRadiusPix/numRings*scale;  //note: delta radius is not based on metersPerPix (as reflected by not includ. in calculation
    		circle.y = tlY + i*ringOutRadiusPix/numRings*scale;
    		circle.height = 2*(ringOutRadiusPix-ringOutRadiusPix*i/numRings)*scale;  //x2 since this is a diameter, not radius
    		circle.width = 2*(ringOutRadiusPix-ringOutRadiusPix*i/numRings)*scale;
    		
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
	private void drawPlanePosition(Graphics2D frame)
    {	
		
    	
    	if(curGPSPosition != null)  // && Math.abs(estDropPosXMetres) < metersPerPix*cols/2 && Math.abs(estDropPosYMetres) < metersPerPix*rows/2)
    	{
    		//draw plane (yellow), draw est drop position (cyan)
     		drawPoint(frame, (int)(cols/2+planePosXMetres/metersPerPix*scale), (int)(rows/2-planePosYMetres/metersPerPix*scale), 5, Color.BLACK);   //plane
     		drawPoint(frame, (int)(cols/2+estDropPosXMetres/metersPerPix*scale), (int)(rows/2-estDropPosYMetres/metersPerPix*scale), 5, Color.BLUE); //est drop position
     		
     		//draw heading (second point should be project well off screen
     		double factor = 10000000; //make sure it's offscreen
     		
     		frame.setColor(Color.BLACK);
     		frame.drawLine((int)(cols/2+planePosXMetres/metersPerPix*scale), (int)(rows/2-planePosYMetres/metersPerPix*scale), 
     								(int)(cols/2+planePosXMetres/metersPerPix*scale+factor*Math.cos(curGPSPosition.getMathAngle()*Math.PI/180)), 
     								(int)(rows/2-planePosYMetres/metersPerPix*scale-factor*Math.sin(curGPSPosition.getMathAngle()*Math.PI/180)));
     	 
     	}    	
    	
    }
    
    
    
    private String getRing(double latError)
    {  	
    	if(latError < 15*FT_TO_METRES)
    		return new String("Proj Ring = "+1);
    	else if(latError < 30*FT_TO_METRES)
    		return new String("Proj Ring = "+2);
    	else if(latError < 45*FT_TO_METRES)
			return new String("Proj Ring = "+3);
    	else if(latError < 60*FT_TO_METRES)
			return new String("Proj Ring = "+4);
    	else
			return "Projected Outside Rings";
    	
    	
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
	
	public void updateGPSData(double altitudeFt, double spd, double LatDDM, double LongDDM, double headng, int sec, int ms ){

		this.altitudeFt = altitudeFt;
		this.altitudeMetres = altitudeFt*FT_TO_METRES;
		this.speed = spd;
		this.lattitudeDDM = LatDDM;
		this.longitudeDDM = LongDDM;
		this.heading = headng;
		this.second = sec;
		this.millisec = ms;
		
				
		baseGPSposition = new GPSPos(lattitudeDDM, longitudeDDM, speed, altitudeMetres,heading, sec, ms);
		
		LocalDateTime now = LocalDateTime.now();
		msFromGPSCoord = getMsBetween(second, millisec, now.getSecond(), now.get(ChronoField.MILLI_OF_SECOND));
				
	}
	
	//go from base position (which is delayed from real-time) to current time by assuming constant speed/heading over the differnece in time
	private void transformBasetoCurGPSPos()   //reset will be that the curGPSPosition object contains the most recent location
	{
		LocalDateTime now = LocalDateTime.now();
		msFromGPSCoord = getMsBetween(baseGPSposition.getSecond(), baseGPSposition.getMilliSecond(), now.getSecond(), now.get(ChronoField.MILLI_OF_SECOND));
		double curNorthing = projectYForward(baseGPSposition.getVelocityMPS(), baseGPSposition.getMathAngle(), baseGPSposition.getUTMNorthing(), msFromGPSCoord +TIME_DELAY_MS_BEFORE_DROP);
		double curEasting = projectXForward(baseGPSposition.getVelocityMPS(), baseGPSposition.getMathAngle(), baseGPSposition.getUTMEasting(), msFromGPSCoord+TIME_DELAY_MS_BEFORE_DROP); 
		curGPSPosition = new GPSPos(baseGPSposition.getUTMZone(), baseGPSposition.getUTMLetter(), curNorthing, curEasting, baseGPSposition.getVelocityMPS(), 
										baseGPSposition.getAltitudeM(), baseGPSposition.getHeading(), now.getSecond(), now.get(ChronoField.MILLI_OF_SECOND));
		
		//note the reasons why getHeading() vs. getMathAngle() are used
				
	}
		
	//following functions will end up in GPSPos or GPSTargeter
	private double projectXForward(double spd, double angle, double curX, int timeDiffMs)    //0 degrees = North, 90 degrees = East etc. spd in m/s, curX in m 
	{
		return (curX + spd*Math.cos(angle*Math.PI/180)*timeDiffMs/1000.0);

	}
	
	private double projectYForward(double spd, double angle, double curY, int timeDiffMs)    //0 degrees = North, 90 degrees = East etc. spd in m/s, curX in m 
	{
		return (curY + spd*Math.sin(angle*Math.PI/180)*timeDiffMs/1000.0);
	}
	
		
	public int getMsBetween(int s1, int ms1, int s2, int ms2)
	{	int timeBtwn = 0;
		
		//System.out.println("S1.ms1 = " + s1 + "." + ms1 + )
	
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
		if(timeBtwn > 10000)  //if >10 seconds, point likely an error, so return the average assumed offset of 1 second (NOTE: change this based on testing)
		{	
			//System.out.println("Offset: " + timeBtwn + " ms rejected (too high)");
			timeBtwn = 10000;
		}
		else if (timeBtwn < 0)
		{
			timeBtwn = 0;
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

