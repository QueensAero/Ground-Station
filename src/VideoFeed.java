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


/*Note:  sending '&' starts the calibration.  (maybe)
 * 
 * 
 * 
 */


public class VideoFeed extends JPanel implements Runnable {
	
	//image size variables
	private final static int rows = 480;  //rows in frame from camera
	private static final int cols = 640;	//columns in frame from camera
	private final int fpR = 100, fpC = cols;	//rows and columns in flight display panel	
	
	//Flight panel areas (defined by rectanges)
	Rect altDrawArea = new Rect(new Point(60,0), new Point(175,50));
	Rect airSpdDrawArea = new Rect(new Point(275,0), new Point(500,50));
	
	//Timing/timestamping variables
	private int FrameNum = 0; 
	private long time,  videoRecStartTime;
 	String startDate;   
	private DecimalFormat df = new DecimalFormat("#000.00");
		
	//thread variables
	Thread VFThread;
	private volatile boolean endThread = false;

	//Buffered Image container
	private Image img;  

	//state variables (containing information about current state of plane)
	private double rollAng, pitchAng, airSpd, altitude; 
	boolean isDropped = false;  //whether the payload has been dropped
	private boolean recordingVideo = false;
	int currentRecordingFN = 0;
	double loggedData[][];  //contain all 
	
	
	
	
		
	
	
	/******************openCV DEPENDANCE STARTS **********************************/
	//colour 'constants'
	private Scalar BLUE = new Scalar(255,0,0);
	private Scalar RED = new Scalar (0,0,255);
	private Scalar GREEN = new Scalar(0,255,0);
	private Scalar WHITE = new Scalar (255,255,255);
	private Scalar BLACK = new Scalar (0,0,0);
	private Scalar GRAY = new Scalar (115,115,115);
	
	//Video capture object (handles stream)
	private VideoCapture cap;
	
	//Matricies (for holding images)
	private Mat combinedImgCV = new Mat(rows + fpR, cols, 16, GRAY);  //full image to be rendered
	private Mat flightPanel = combinedImgCV.submat(new Rect(new Point(0, rows), new Point(cols, rows+fpR)));  
	private Mat CVimg = combinedImgCV.submat(new Rect(new Point(0, 0), new Point(cols, rows)));; // Matrix for storing from camera
	
	//Note: image formats -> 24 = CV_8UC4, 16 = CV_8UC3 ->  is the type used in the webcam feed - might be different for stream
	//http://ninghang.blogspot.ca/2012/11/list-of-mat-type-in-opencv.html lists the types

	public VideoFeed() {
		// Register the default camera
		cap = new VideoCapture(0);   //0 = webcam, 1 = Analog2USB device
		
		// Check if video capturing is enabled]
		if (!cap.isOpened()) { System.out.println("Could not open video feed.");}
		
		Core.putText(flightPanel, "Alt:", new Point(5,50), 0, 1, BLACK, 2);  //last 4: font type, size, colour, thickness
		Core.putText(flightPanel, "Spd:", new Point(200,50), 0, 1, BLACK, 2);  //last 4: font type, size, colour, thickness
		
		//initialize the timestamp
		Date date = new Date(new Timestamp(System.currentTimeMillis()).getTime());
		SimpleDateFormat sdf = new SimpleDateFormat("MM.dd.yyyy_h.mm.ss");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		startDate = new String(sdf.format(date));	
		
		//Thread declarations
		VFThread = new Thread(this, "Video Feed Thread");
		VFThread.start();
		
		//initialize time
		time = System.currentTimeMillis();
	}
	

	
	/* Update Function -> grabs frame, converts to BufferedImage, and calls repaint  */
	public void update() {
		cap.read(CVimg);  //read a new frame -> this also updates the matrix combinedImgCV since CVimg is a subset of that
		
		//if the frame is not NULL, then prepare it for display
		if (CVimg != null && !CVimg.empty()) {  
			
			FrameNum++;
			processFrame();  //updates both the
			
			img = toBufferedImage(combinedImgCV);  //convert from CV Mat to BufferedImage
			
			//set the size of the painting space
			Dimension size = new Dimension(img.getWidth(null), img.getHeight(null));
			setPreferredSize(size);
			setMinimumSize(size);
			setMaximumSize(size);
			setSize(size);
			
			//repaint the JFrame (paintComponent will be called)
			this.repaint();
			
			//framerate code (prints frame rate every 500 frames)
			if(FrameNum % 500 == 0 && FrameNum != 0)
			{
				System.out.println("FR = " + 500*1000/(System.currentTimeMillis() - time));  time = System.currentTimeMillis();
			}
		}	
	}

   
   
	
	/* Function to process the frame, extending its size to add flight details (incomplete) */
	private void processFrame(){
		
		//for testing, only save a few images. Save unedited image (otherwise no way to get it back)
		if(recordingVideo  && currentRecordingFN < 10)
		{	//Highgui.imwrite("Images" + File.separator + startDate + "_" + currentRecordingFN++ + ".jpg", CVimg, new MatOfInt(5));  //parameter at end is quality (0 = lowest, 100 = highest)
			Highgui.imwrite("C:" + File.separator + "Users" + File.separator + "Ryan"+ File.separator + "Documents" + File.separator + "Current Files" + File.separator +
					"Aero" + File.separator + "Images" + File.separator + startDate + "_" + currentRecordingFN++ + ".jpg", CVimg, new MatOfInt(5));  //parameter at end is quality (0 = lowest, 100 = highest)

			logData();
		}
		else if(currentRecordingFN == 10)  //limit to 10 so that doesn't endlessly save images
		{	currentRecordingFN++;  //increase so it doesn't get into here again
			toggleRecordingStatus();			
		}
			
		
		//will need to do this for the actual frames from the camera to check the image dimensions
		//System.out.println("Rows = " + matrix.rows() + "  Cols = " + matrix.cols());
		
		//TEMPORARY -> will actually be upated by MainWindow class calling updateValues(...)
		updateStatus();  //just for testing
		
		
		//drawHorizon();	//draw the lines representing horizon
		
		if(FrameNum % 5 == 0)  //avoid updating too fast for increased readability
			printInfoOnScreen();  //print values to image
				
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
	
	
	private void printInfoOnScreen(){
		
		//refill old text area 
		Core.rectangle(flightPanel, altDrawArea.tl(), altDrawArea.br(), GRAY, -1);  //-1 indicates it's filled in
		Core.rectangle(flightPanel, airSpdDrawArea.tl(), airSpdDrawArea.br(), GRAY, -1);
		
		//draw text -> last 4 arguements: font type, size, colour, thickness
		Core.putText(flightPanel, df.format(altitude), new Point(altDrawArea.x,altDrawArea.y+altDrawArea.height-1), 0, 1, BLACK, 2);  
		Core.putText(flightPanel, df.format(airSpd), new Point(airSpdDrawArea.x,airSpdDrawArea.y+airSpdDrawArea.height-1), 0, 1, BLACK, 2);  
		
	}
	
	
	/******************openCV DEPENDANCE ENDS **********************************/

	/* Thread Run Function -> currently just calls the update function */
	public void run()
	{
    	while(!endThread){
    		update();  //continuously call update function
    	}	 
	     System.out.println("Video Capture Thread Ended" );
	}
	
	/* DUMMY UPDATE FUNCTION 
	  
	  public void update() {
			
	}
	*/
	
	/*This overrides the JFrame (??) function to paint the video frame (stored in class object "img" in the drawing frame */ 
    @Override
	public void paintComponent(Graphics g) {
		g.drawImage(img, 0, 0, null);
	}
	
	/*function to end the video capture and display thread */
	public void endCapture(){	endThread = true;	}
	
	public void toggleRecordingStatus(){
		
		if(!recordingVideo)  //start recording
		{	
			recordingVideo = true;
			currentRecordingFN = 1;  //reset
			
			//set timestamp
			Date date = new Date(new Timestamp(System.currentTimeMillis()).getTime());
			SimpleDateFormat sdf = new SimpleDateFormat("MM.dd.yyyy_h.mm.ss");
			sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
			startDate = new String(sdf.format(date));		
			
			videoRecStartTime = System.currentTimeMillis();

			System.out.println("Recording Set to ON");
			
			//START OUTPUT STREAM
			Path logPath = Paths.get("Images" + File.separator + startDate + "_log.txt");
			
			// Create "Images" folder if it does not exist:
			try {
				Files.createDirectories(logPath.getParent());
			} catch (IOException e2) {
				System.err.println("Could not create directory: " + logPath.getParent());
			}
			
			// Create log file:
		        try {
		            Files.createFile(logPath);
		        } catch (FileAlreadyExistsException e) {
		            System.err.println("File already exists: " + logPath);
		        } catch (IOException e) {
		        	System.err.println("Could not create file: " + logPath);
			}
	        
		        String s = "Frame,time,roll,pitch,airSpeed,altitude";
		        try {
		            Files.write(logPath, s.getBytes(), StandardOpenOption.APPEND);
		        } catch (IOException e) {
		        // It's really hard to recover from an IOException. Should probably just notify user and stop recording.
		            System.err.println("Could not write to file: " + logPath);
		        }
		}
		else //stop recording video
		{
			recordingVideo = false;
			System.out.println("Recording Set to OFF");
			//CLOSE OUTPUT STREAM FILE -> need to set this up
		}
		
	}
	
	public boolean getRecordStatus() {  return recordingVideo;  }
	
	//if too many values, might want to send as enum/struct type? does java have that
	public void updateValues(double roll, double pitch, double alt, double airspeed, boolean dropped)  //add more as necessary (ie. GPS).
	{
		rollAng = roll;
		airSpd = airspeed;
		altitude = alt;
		pitchAng = pitch;
		isDropped = dropped;
				
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
	
	private void logData() {
	
		//set this up, separate with space or tab
		//Write FrameNumber
		//Write Timestamp (videoRecStartTime - System.getTimeMillis();
		//Write Altitude
		//Write AirSpd
		//write Pitch
		//write roll
		String t = Long.toString(System.currentTimeMillis() - videoRecStartTime);
		String s = Integer.toString(FrameNum) + "," + t + "," + rollAng + "," + pitchAng + "," + airSpd + "," + altitude + "\n";
		
		try {
			// I believe that this opens and closes the file every time we write a line. (Every frame in the video feed)
			// If this is too slow, then we will have to either set up a BufferedWriter and just close it when we are
			// done recording, OR we could simply save the data in an array and then print it all when we're done recording.
			Files.write(logPath, s.getBytes(), StandardOpenOption.APPEND);
		} catch (IOException e) {
			// It's really hard to recover from an IOException. Should probably just notify user and stop recording.
			System.err.println("Could not write to file: " + logPath);
		}		
			
	}
	


}


