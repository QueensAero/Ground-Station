import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.swing.JPanel;

import org.opencv.core.Mat;
import org.opencv.core.Core;
import org.opencv.core.MatOfInt;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.highgui.VideoCapture;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import java.lang.Math;




public class VideoFeed extends JPanel implements Runnable {
	
	private Image img;
	private VideoCapture cap;
	private Mat CVimg; // Matrix for storing image
	
	//colour 'constants'
	Scalar BLUE = new Scalar(255,0,0);
	Scalar RED = new Scalar (0,0,255);
	Scalar GREEN = new Scalar(0,255,0);
	Scalar WHITE = new Scalar (255,255,255);
	Scalar BLACK = new Scalar (0,0,0);
	Scalar GRAY = new Scalar (115,115,115);

	
	private final static int rows = 480;
	private static final int cols = 640;
	private final int fpR = 100, fpC = 640;
	//http://ninghang.blogspot.ca/2012/11/list-of-mat-type-in-opencv.html lists the types
	private Mat flightPanel = new Mat(fpR, fpC, 16, GRAY);  //24 = CV_8UC4, 16 = CV_8UC3 ->  is the type used in the webcam feed - might be different for stream


	private int FrameNum = 0;
	private boolean recordingVideo = false;
	String startDate;   
 
	
	
	private double rollAng;  
	private double airSpd; 
	private double altitude; 
	private DecimalFormat df = new DecimalFormat("#000.00");
	Rect altDrawArea = new Rect(new Point(60,0), new Point(175,50));
	Rect airSpdDrawArea = new Rect(new Point(275,0), new Point(500,50));

	
	//thread details
	Thread VFThread;
	boolean endThread = false;

	
	public VideoFeed() {
		// Register the default camera
		cap = new VideoCapture(0);
		// Check if video capturing is enabled]
		if (!cap.isOpened()) { System.out.println("Could not open video feed.");}
		CVimg = new Mat();
		
		Core.putText(flightPanel, "Alt:", new Point(5,50), 0, 1, BLACK, 2);  //last 4: font type, size, colour, thickness
		Core.putText(flightPanel, "Spd:", new Point(200,50), 0, 1, BLACK, 2);  //last 4: font type, size, colour, thickness
		
		//initialize the timestamp
		Date date = new Date(new Timestamp(System.currentTimeMillis()).getTime());
		SimpleDateFormat sdf = new SimpleDateFormat("MM.dd.yyyy_h.mm.ss");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		startDate = new String(sdf.format(date));	
		
		//Dowling did this
		VFThread = new Thread(this, "Video Feed Thread");
		VFThread.start();
	}
	
	/* Thread Run Function -> currently just calls the update function */
	public void run()
	{
    	while(!endThread){
    		update();
    	}	 
	     System.out.println("Video Capture Thread Ended" );
	}
	
	/* Update Function -> grabs frame, converts to BufferedImage, and calls repaint  */
	public void update() {
		cap.read(CVimg);  //read a new frame
		
		//if the frame is not NULL, then prepare it for display
		if (CVimg != null && !CVimg.empty()) {  
			
			FrameNum++;
			processFrame();  
			
			img = toBufferedImage(CVimg);  //convert from CV Mat to BufferedImage
			
			//set the size of the painting space
			Dimension size = new Dimension(img.getWidth(null), img.getHeight(null));
			setPreferredSize(size);
			setMinimumSize(size);
			setMaximumSize(size);
			setSize(size);
			
			//repaint the JFrame (paintComponent will be called)
			this.repaint();
		}	
	}

	/*This overrides the JFrame (??) function to paint the video frame (stored in class object "img" in the drawing frame */ 
    @Override
	public void paintComponent(Graphics g) {
		g.drawImage(img, 0, 0, null);
	}
        
    /*convert from OpenCV Image storage container (Mat object) to a Java image container that can be painted -> type BufferedImage  */  
	public static Image toBufferedImage(Mat m){
        // Code from http://stackoverflow.com/questions/15670933/opencv-java-load-image-to-gui

        // Check if image is grayscale or color
      int type = BufferedImage.TYPE_BYTE_GRAY;
      if ( m.channels() > 1 ) {
          type = BufferedImage.TYPE_3BYTE_BGR;
      }
        // Transfer bytes from Mat to BufferedImage
      int bufferSize = m.channels()*m.cols()*m.rows();
      byte [] b = new byte[bufferSize];
      m.get(0,0,b); // get all the pixels
      BufferedImage image = new BufferedImage(m.cols(), m.rows(), type);
      final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
      System.arraycopy(b, 0, targetPixels, 0, b.length);
      return image;
  }
	
	/*function to end the video capture and display thread */
	public void endCapture(){	endThread = true;	}
	
	public void setRecordStatus(){
		
		if(recordingVideo)  //stop recording
		{	
			recordingVideo = true;
			
			//set timestamp
			Date date = new Date(new Timestamp(System.currentTimeMillis()).getTime());
			SimpleDateFormat sdf = new SimpleDateFormat("MM.dd.yyyy_h.mm.ss");
			sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
			startDate = new String(sdf.format(date));			
		}
		else //stop recording video
		{
			recordingVideo = false;
		}
		
	}
	
	/* Function to process the frame, extending its size to add flight details (incomplete) */
	private void processFrame(){
		
		//for testing, only save a few images. Save unedited image (otherwise no way to get it back)
		if(FrameNum < 5 && !recordingVideo)
			Highgui.imwrite("Images" + File.separator + startDate + "  " + FrameNum + ".jpg", CVimg, new MatOfInt(5));  //parameter at end is quality (0 = lowest, 100 = highest)
		
		
		//will need to do this for the actual frames from the camera to check the image dimensions
		//System.out.println("Rows = " + matrix.rows() + "  Cols = " + matrix.cols());
		//System.out.println("FP Rows = " + flightPanel.rows() + "  Cols = " + flightPanel.cols());
		
		updateStatus();  //get's the new values of roll/pitch/alt etc.
		drawHorizon();	//draw the lines representing horizon
		
		if(FrameNum % 5 == 0)  //avoid updating too fast for increased readability
			printInfo();  //print values to image

		//add the flightPanel to the matrix
		CVimg.push_back(flightPanel);
				
	}
	
	
	//some constants for the drawHorizon Function
	Point origin = new Point(cols/2, rows/2);
	private final static int r = (cols-100)/2;  //radius of line
	private final static int verticalOffset = 100;
	
	private void drawHorizon(){		//let 0 degrees be neutral, and -45 degrees be /  and 45 degrees be \
		
		//some math to use polar coodinates
		double cosTheta = Math.cos(rollAng*Math.PI/180);
		double sinTheta = Math.sin(rollAng*Math.PI/180);		
		int dx =  (int)(r*cosTheta), dy = (int) (r*sinTheta);  //distances from the origin to end of line along x and y axis
		int xoff = (int) (verticalOffset*sinTheta), yoff = (int) (-verticalOffset*cosTheta);  //offset for the upper/lower lines
		
		//draw the actual lines
		Core.line(CVimg, new Point(origin.x + dx, origin.y + dy), new Point(origin.x-dx, origin.y - dy),GREEN, 1, Core.LINE_AA, 0);  //middle line
		Core.line(CVimg, new Point(origin.x + xoff + dx/2, origin.y + yoff + dy/2), new Point(origin.x+xoff-dx/2, origin.y + yoff - dy/2),GREEN, 1, Core.LINE_AA, 0);  //upper line
		Core.line(CVimg, new Point(origin.x - xoff + dx/2, origin.y - yoff + dy/2), new Point(origin.x - xoff-dx/2, origin.y - yoff - dy/2),GREEN, 1, Core.LINE_AA, 0); //lower line
		
	}
	
	int sign=1;
	private void updateStatus(){
		//These values will need to come from accessors
		
		
		
		rollAng+= sign;  //to watch it move
		if(rollAng > 45 || rollAng <-45) sign = -sign;
		
		airSpd = 0.25*(FrameNum%140);
		altitude = 0.5*(FrameNum%311);
		//possible GPS, pitch, etc.
		
	}
	
	private void printInfo(){
		
		//refill old text area 
		Core.rectangle(flightPanel, altDrawArea.tl(), altDrawArea.br(), GRAY, -1);  //-1 indicates it's filled in
		Core.rectangle(flightPanel, airSpdDrawArea.tl(), airSpdDrawArea.br(), GRAY, -1);
		
		//draw text -> last 4 arguements: font type, size, colour, thickness
		Core.putText(flightPanel, df.format(altitude), new Point(altDrawArea.x,altDrawArea.y+altDrawArea.height-1), 0, 1, BLACK, 2);  
		Core.putText(flightPanel, df.format(airSpd), new Point(airSpdDrawArea.x,airSpdDrawArea.y+airSpdDrawArea.height-1), 0, 1, BLACK, 2);  
		
	}

}
