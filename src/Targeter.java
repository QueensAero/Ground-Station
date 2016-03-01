import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;


import java.nio.Buffer;

import javax.swing.JPanel;


//transmission latency should be ~65/2 ms (half of round trip value per http://hades.mech.northwestern.edu/index.php/PIC32MX:_XBee_Wireless_Round-trip_Latency

public class Targeter extends JPanel implements Runnable{
	
	
	
	
	
	//container for image that's created
	BufferedImage image;
	
	//size of created image
	private int cols = 250, rows = 250;

	
	//tracking variables:
	//note: xDistToTarget is a positive if East, and yDistToTarget is positive if North. These are in ft
	int xPosFt = 45,  		//cartesian coordinate in x/y grid. +x = East.  Value in feet
		prevXPosFt = 30, 	//previous x position (not 1 screen earlier, but 1 GPS data earlier)
		yPosFt = 10,  		//cartesian coordinate in x/y grid. +y = North.  Value in feet
		prevYPosFt = 45;
	int [] prevXPoints = new int[5];
	int [] prevYPoints = new int[5];
	int prevPosIndex = 0, numPrevPoints;

	
	double  altitude = 101,  			//current altitude (in ft)
			lateralError = 35.1553154,	//lateral error assuming optimal drop time (in ft)
			timeToDrop = 13.13;			//estimated time until optimal drop (in seconds)
	
	
	
	//target Area Rings & sizing variables
	private int numRings = 4, //number of 'areas' or rings in target area
    			feetPerPix = 2,
    			ringOutRadius = 60/feetPerPix, //radius of very outside ring.  
    			ringBaseL = cols/2-ringOutRadius, //x coordinate of top left corner
    			ringBaseT = rows/2 -ringOutRadius;  //y coordinate of top left corner
	
	
	
	
	//constructor
	public Targeter() {
		
		//set size of JPanel
		Dimension size = new Dimension(cols, rows);
		this.setPreferredSize(size);
	}
	
	//infinite loop that runs.  Continuously repaints the image, then waits 15 ms, then repeats
	public void run()
	{
		boolean endThread = false;
		
    	while(!endThread){
    		this.repaint();  //continuously call update function
    		
    		try { Thread.sleep(15);	} catch (InterruptedException e) {}  
    	}	 
	    
    	System.out.println("Targeter Thread Ended" );
	}
	
	@Override
	public void paintComponent(Graphics g) {
		
	    image = new BufferedImage(cols, rows, BufferedImage.TYPE_3BYTE_BGR);  
    	Graphics2D frame = image.createGraphics();
    	
    	frame.setFont(new Font("TimesRoman", Font.PLAIN, 20)); 
   	
    	addTargetArea(frame);
    	drawPlanePosition(frame);

		g.drawImage(image, 0, 0, null);

		
	}
	
	//draw 4 rings. Inner = 1.0 scoring multiplier, 2 = .75 etc.  each ring is 15 ft larger in radius
    void addTargetArea(Graphics2D frame)
    {
       	Ellipse2D.Double circle = new Ellipse2D.Double();
        
    	//note this assumes that each delta in radius is equal. Note: draw widest, draw next widest to overwrite, repeat until at inner ring
    	for(int i = 0; i < numRings; i++)
    	{	circle.x = ringBaseL +i*ringOutRadius/numRings;  //note: delta radius is not based on feetPerPixel (as reflected by not includ. in calculation
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
    
    //Note: want to hit point(ringCentX, ringCentY). Each pixel away from that is 2 feet. The drawing area is constrained by  (targeterStartPixX, targeterStartPixY) as TL
    // to (cols, TotRows) as the BR pt.   drawAreaWidth is the pixels 
    void drawPlanePosition(Graphics2D frame)
    {	
    	
 		frame.setColor(Color.RED);

    	
     	 if(Math.abs(xPosFt) > feetPerPix*cols/2 || Math.abs(yPosFt) > feetPerPix*rows/2) //about 300 pixels to draw plane position in
     	 {
     		
     		 frame.drawString("Outside of " + feetPerPix*cols/2 + " ft in x or y", 10, 20);    		
     	 	
     	 
     	 }
       	 else   //draw projected drop point, lateral error somewhere
     	 {
       		 //if/else to decide if writing lateral error in bottom half or top half (so doesn't overwrite the point)
     		if(yPosFt >= -ringOutRadius ) //bottom left
     		{
     			frame.drawString(getRing(lateralError, frame), 10, cols - 25);
     			frame.drawString("Lat Error = " + String.format( "%.1f", lateralError)+ " ft", 10, cols - 45);  //done after so automatically same font colour

     			 if(altitude < 100)
     			  	 frame.drawString("Alt too low! (alt = " + String.format( "%.1f", altitude) + " ft)", 10, cols - 5);    
     			 else if(lateralError < 60)  //otherwise shouldn't drop
      				 frame.drawString("Time to Drop = " + String.format( "%.1f", timeToDrop)+ " s", 10, cols - 5);  //done after so automatically same font colour     			     			 
     		}
     		else  //top left
     		{	
     			frame.drawString(getRing(lateralError, frame), 10, 40);
     			frame.drawString("Lat Error = " + String.format( "%.1f", lateralError)+ " ft", 10, 20);  //done after so automatically same font colour

     			 if(altitude < 100)
     			  	 frame.drawString("Alt too low! (alt = " + String.format( "%.1f", altitude) + " ft)", 10, 60);    
     			 else if(lateralError < 60)  //otherwise shouldn't drop
      				 frame.drawString("Time to Drop = " + String.format( "%.1f", timeToDrop)+ " s", 10, 60);  //done after so automatically same font colour       			      		
     		
     		 }
     		 
     		 //draw projected
     		 
     		 //use coordinates stuff from a targeting class
     		 
     		 double heading = 35;   //getHeading();
     		 //double xPos = targeterStartPixX - 300/2;  // targeterStartPixX - getXDistPlane()/2;   '/2' since two feet per pixel
     		 //double yPos = targeterStartPixY - 150/2;  //targeterStartPixY - getYDistPlane()/2;
     		 double angleSkew = 4;   //getSkewAngle();    angle away from target, positive is CCW away (ie. heading to target is 35, proj. heading is 31
     		
     		drawPoint(frame, cols/2+xPosFt/feetPerPix, rows/2-yPosFt/feetPerPix, 5);
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
	    	frame.drawLine(xPosFt, yPosFt, prevXPoints[index], prevYPoints[index]);  //from current pt to previous point
	    	
	    	
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
    	if(latError < 15)
    	{	frame.setColor(Color.GREEN);	
    		return new String("Proj Ring = "+1);
    	}
    	else if(latError < 30)
    	{	frame.setColor(Color.CYAN);	
    		return new String("Proj Ring = "+2);
    	}
    	else if(latError < 45)
    	{	frame.setColor(Color.YELLOW);	
			return new String("Proj Ring = "+3);
    	}
    	else if(latError < 60)
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
	
	public void updatePos(double Lat, double Long, double heading, double speed){
		//will receive new Lat and Long
		//decode these into the x/y grid coordinates
		//call updatePreviousPoints to ensure that old points are saved correctly
		
	}
	
	//problem: how to set first value without getting to here (will have some dumb boolean variable checking everytime
	private void updatePreviousPoints(int newXPt, int newYPt)
	{	
		
		if(++prevPosIndex >= prevXPoints.length)  //>= because if allocated 10, then max index is 9
			prevPosIndex = 0;		
		
		prevXPoints[prevPosIndex] = xPosFt;
		xPosFt = newXPt;
		prevXPoints[prevPosIndex] = xPosFt;
		yPosFt = newYPt;
		numPrevPoints++;
		
	}
}
