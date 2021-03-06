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
	private static final SpeechManager SPEECH = SpeechManager.getInstance();
	private static final Integer HDOP_WARNING_THRESH = 1000; // 1000 ms - Threshold at which point a verbal warning will be triggered
	
	//thread variables
	Timer threadTimer; 
	
	//container for image that's created
	BufferedImage targetImage;
	BufferedImage textImage;
	
	//size of created image (ensure is a square)
	private int cols = 400, rows = cols;
	//TODO - have size scale to current size allocated to targeter
	
	//curent GPS information
	double altitudeFt = 0, altitudeMetres = 0, speed = 0, lattitudeDDM = 0, longitudeDDM = 0, heading = 0, 
				HDOP = 30, msSinceLastValidHDOP = 10000;
	int fixQuality = 0;  
		
	//altitude in ft, spd in m/s, heading in degress, GPS in XXYY.ZZZZ XX = degress, YY = minutes,  ZZZZ = decimal minutes
	
	//tracking variables:
	public boolean payloadDropped = false;
	private double  planePosXMetres = 0,			// these are cartesian coordinate in x/y grid. +x = East.  Value in m
					planePosYMetres = 0, 
					estDropPosXMetres = 0,
					estDropPosYMetres = 0, 
					actEstDropPosXMeters = 0,  //when dropped this holds the location is was estimated to hit baed on targeter
					actEstDropPosYMeters = 0;
	private long dataAge;  

	
	public double  lateralError = 0,	//lateral error assuming optimal drop time (in m)
					timeToDrop = 0;			//estimated time until optimal drop (in seconds)
	
	private double FT_TO_METRES = 0.3048;  
	private int JPANEL_WIDTH = 1920/2;   //note assumes screen is 1920*1080
	
	//target Area Rings & sizing variables
	private double scale = 1;
	private int numRings = 4; //number of 'areas' or rings in target area
	private int minAreaSizeMeters = 300;  //Note only half of this is on either side of rings  
	private double metersPerPix = minAreaSizeMeters/(double)cols;

	
	private int ringOutRadiusPix = (int)(18.288/metersPerPix); //radius of very outside ring in PIXELS.  Note: assumed R = 60ft = 18.288 m

	
	
	//double targetLattDDM = 4413.724, targetLongDDM = -7629.492;  //behind ILC
	double targetLattDDM = 4413.5906, targetLongDDM = -7629.3796;  //Pitchers mound of baseball diamond in city park

	//GPSPos objects (initialize positions to some offset from target. Currently target is a pt behind ILC
	public GPSPos baseGPSposition; 
	public GPSPos targetPos;   //this set just above. Note the negative on the Longitude
	public GPSTargeter GSPTargeting;
	
	private boolean autoDropEnabled;
	
	// These variables are used to keep track of whether a verbal warning has been triggered recently
	// regarding a communication failure (long time since last message received), or a large HDOP.
	// This is necessary to avoid repeatedly warning the user of the same issue.
	private boolean recentCommFailureWarning = false;
	private boolean lastHDOPBad = false;
	
	
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
		  
	
		double tempInitV = 15, tempInitAltMeters = 34, tempInitHeading = 45;   
		int initXOff = -350, initYOff = -350;  //base initial position in meters from target position
		targetPos = new GPSPos(targetLattDDM, targetLongDDM,0,0,0);   //this is currently just behind ILC. NOTE how Long component declared as negative to account for west
		baseGPSposition = new GPSPos(targetPos.getUTMZone(), targetPos.getUTMLetter(), targetPos.getUTMNorthing()+initYOff, targetPos.getUTMEasting()+initXOff, 
									tempInitV, tempInitAltMeters, tempInitHeading);  //start -initYOff S & -initXOff W of target 
		GSPTargeting = new GPSTargeter(targetPos);
		GSPTargeting.updateCurPos(baseGPSposition);  //update current position to base on

		
		threadTimer = new Timer(33, updateTargetArea);  //33 ms ~30FPS
		threadTimer.start(); //note: by default Coalescing is on, meaning that if won't queue events
	
	}
	
	public void setTargetPos(double targetLatDDM, double targetLonDDM) {
		targetPos = new GPSPos(targetLatDDM, targetLonDDM, 0, 0, 0);
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
	public long getDataAge() 			 { return dataAge;	}
	
	public void setAutoDropEnabled(boolean enabled) {
		autoDropEnabled = enabled;
	}
	
	public boolean getAutoDropEnabled() {
		return autoDropEnabled;
	}
	
	
	public void update()
	{
		//transform baseGPSPos to 'curent' time
    	dataAge = System.currentTimeMillis() - baseGPSposition.getSystemTime();
		GSPTargeting.update(); 
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
		if(baseGPSposition != null) 
		{
			planePosXMetres = baseGPSposition.getUTMEasting() - targetPos.getUTMEasting();
			planePosYMetres = baseGPSposition.getUTMNorthing() - targetPos.getUTMNorthing();
			
			estDropPosXMetres = GSPTargeting.getEstDropEasting() -  targetPos.getUTMEasting();
			estDropPosYMetres = GSPTargeting.getEstDropNorthing() -  targetPos.getUTMNorthing();
				
			lateralError = GSPTargeting.getLateralError();
	 		timeToDrop = GSPTargeting.getTimeToDrop();
	 		altitudeFt = baseGPSposition.getAltitudeFt();
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
    	if((dataAge/1000.0) > 3.0) { // If no new data has been received in 3.0 seconds
    		if(!recentCommFailureWarning) {
    			recentCommFailureWarning = true;
    			SPEECH.reportNewMessage("Communication Failure.");
    			LOGGER.warning("Data age has exceeded 3 seconds.");
    		}
    		textFrame.setColor(Color.RED);
    	} else {
    		recentCommFailureWarning = false;
    	}
    	textFrame.drawString("Data Age: " + String.format( "%.2f",(dataAge/1000.0)) + " seconds", startTextX, yTextSpace*yTextMult++);
    	textFrame.setColor(Color.BLACK);
    	
    	if(!payloadDropped)
    	{		   		
		   		textFrame.drawString(getRing(lateralError), startTextX, yTextSpace*yTextMult++);
		   		textFrame.drawString("Lat Error = " + String.format( "%.1f", lateralError)+ " m", startTextX, yTextSpace*yTextMult++);  
		   		
	 			//note: optimal drop time always shown, even if projected outside ringe 
	 			textFrame.drawString("Time to Drop = " + String.format( "%.1f", timeToDrop)+ " s", startTextX, yTextSpace*yTextMult++);      			     			 
		   		
	 			 if(altitudeFt < 100)
	 				textFrame.drawString("Alt too low! (alt = " + String.format( "%.1f", altitudeFt) + " ft)", startTextX, yTextSpace*yTextMult++);    

    	}
    	else
    	{	textFrame.drawString("Payload Dropped!", startTextX, yTextSpace*yTextMult++); 
    		textFrame.drawString("Est Drop Pos: ("+ String.format( "%.1f", actEstDropPosXMeters) + ", " + String.format( "%.1f", actEstDropPosYMeters) + ")", startTextX, yTextSpace*yTextMult++);
    	
    	}
    	textFrame.drawString("HDOP = " + HDOP, startTextX, yTextSpace*yTextMult++);
    	if(fixQuality == 0) {
    		textFrame.drawString("Fix Quality = " + fixQuality + " (No Fix)", startTextX, yTextSpace*yTextMult++);
    	} else if (fixQuality == 1) {
    		textFrame.drawString("Fix Quality = " + fixQuality + " (SPS Fix)", startTextX, yTextSpace*yTextMult++);
    	} else if (fixQuality == 2) {
    		textFrame.drawString("Fix Quality = " + fixQuality + " (DGPS Fix)", startTextX, yTextSpace*yTextMult++);
    	} else {
    		textFrame.drawString("Fix Quality = " + fixQuality, startTextX, yTextSpace*yTextMult++);
    	}
    	
    	if(msSinceLastValidHDOP > HDOP_WARNING_THRESH) {
    		textFrame.setColor(Color.RED);
    		if(!lastHDOPBad) {
    			lastHDOPBad = true;
    			SPEECH.reportNewMessage("Invalid H DOP.");
    			LOGGER.warning("HDOP has been invalid for more than 1 second.");
    		}
    	} else {
    		lastHDOPBad = false;
    	}
    	

    	textFrame.drawString("Valid HDOP = " + msSinceLastValidHDOP + " ms", startTextX, yTextSpace*yTextMult++);
		textFrame.setColor(Color.BLACK);		
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
		
    	
    	if(baseGPSposition != null)  // && Math.abs(estDropPosXMetres) < metersPerPix*cols/2 && Math.abs(estDropPosYMetres) < metersPerPix*rows/2)
    	{
    		//draw plane (yellow), draw est drop position (cyan)
     		drawPoint(frame, (int)(cols/2+planePosXMetres/metersPerPix*scale), (int)(rows/2-planePosYMetres/metersPerPix*scale), 5, Color.BLACK);   //plane
     		drawPoint(frame, (int)(cols/2+estDropPosXMetres/metersPerPix*scale), (int)(rows/2-estDropPosYMetres/metersPerPix*scale), 5, Color.BLUE); //est drop position
     		
     		//draw heading (second point should be project well off screen
     		double factor = 10000000; //make sure it's offscreen
     		
     		frame.setColor(Color.BLACK);
     		frame.drawLine((int)(cols/2+planePosXMetres/metersPerPix*scale), (int)(rows/2-planePosYMetres/metersPerPix*scale), 
     								(int)(cols/2+planePosXMetres/metersPerPix*scale+factor*Math.cos(baseGPSposition.getMathAngle()*Math.PI/180)), 
     								(int)(rows/2-planePosYMetres/metersPerPix*scale-factor*Math.sin(baseGPSposition.getMathAngle()*Math.PI/180)));
     	 
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
	
	public void updateGPSData(double altitudeFt, double spd, double LatDDM, double LongDDM, double headng, double HDOP, double msSinceLastValidHDOP, int fixQuality ){

		this.altitudeFt = altitudeFt;
		this.altitudeMetres = altitudeFt*FT_TO_METRES;
		this.speed = spd;
		this.lattitudeDDM = LatDDM;
		this.longitudeDDM = LongDDM;
		this.heading = headng;
		this.HDOP = HDOP;
		this.msSinceLastValidHDOP = msSinceLastValidHDOP;
		this.fixQuality = fixQuality;
		
				
		baseGPSposition = new GPSPos(lattitudeDDM, longitudeDDM, speed, altitudeMetres,heading);
		GSPTargeting.updateCurPos(baseGPSposition);				
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

