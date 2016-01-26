import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.JPanel;

import org.opencv.core.Mat;
import org.opencv.core.Core;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.highgui.VideoCapture;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import java.lang.Math;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;


/*Notes:  
 * This uses OpenCV libaray to handle the streaming from the Analog to USB device.  The OpenCV dependance is as minimal as posisbe
 * By commenting out the block of code marked by "openCV DEPENDANCE STARTS", as well as a few function calls to those functions (including one in AeroGUI 
 * class which is called for shutdown), this can function without having OpenCV installed. The only thing missing *should* be the video itself.
 * Dependencies:  InitCV -> from constructor, getImage -> in "update()", endCapture -> in AeroGUI "windowClosing()", code block (can block comment this out) 
 * 
 * 
 * Webcam is often used for testing. To switch between webcam mode and videograbber mode, there are several steps:
 * 1) in the OpenCV dependance section ->  cap = new VideoCapture(1);   //0 = webcam, 1 = Analog2USB device
 * 2) right below: uncomment to the other line (image size variables) 
 * 3) Main window: int videoW = 720, videoH = 576, fpH = 200;  //VideoGrabber line 
 * 
 */


public class VideoFeed extends JPanel implements Runnable {
	
	//image size variables (1st = webcam) (2nd = analog 2 usb)
	private final static int rows = 480, cols = 640, fpR = 200, fpC = cols;  //rows, columns in frame from camera, rows, columns in flight display panel
	//private final static int rows = 576, cols = 720, fpR = 200, fpC = cols;  //rows, columns in frame from camera, rows, columns in flight display panel

	
	//Timing/timestamping/file storing variables
	private int FrameNum = 0; 
	private long time,  videoRecStartTime;
 	String startDate;   
	//private DecimalFormat df = new DecimalFormat("#000.00");
	private Path logPath;
	SimpleDateFormat sdf;
		
	//thread variables
	Thread VFThread;
	private volatile boolean endThread = false;

	//Buffered Image container
	private Image img; // = new BufferedImage(cols, rows+fpR, BufferedImage.TYPE_3BYTE_BGR);

	//state variables (containing information about current state of plane)
	private double rollAng= 5, pitchAng = 6, airSpd = 7, altitude = 8; 
	boolean isDropped = false;  double altAtDrop = 0; //whether the payload has been dropped
	private boolean recordingVideo = false; boolean streamActive = false, imageRealloc = false;
	int currentRecordingFN = 0;

	
	//Gauge graphics
	SpeedGauge speedGauge;
	SpeedGauge altGauge;
	
	//Constructor
	public VideoFeed() {
		
		//OpenCV Dependence
		initOpenCV();
		
		//initialize the timestamp
		Date date = new Date(new Timestamp(System.currentTimeMillis()).getTime());
		sdf = new SimpleDateFormat("MM.dd.yyyy_h.mm.ss");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		startDate = new String(sdf.format(date));	
		
		/* For the visual speedometer/altimeter gauge */
		int radius = 90, yCent = rows + fpR/2;
		try {
			speedGauge = new SpeedGauge(100,yCent,radius, 40, "m/s");
		} catch (FontFormatException e) {
			System.out.print("gauge init error");
		} catch (IOException e) {
			System.out.print("gauge init error");
		}
		
		try {
			altGauge = new SpeedGauge(300,yCent,radius, 300, "ft");
		} catch (FontFormatException e) {
			System.out.print("gauge init error");
		} catch (IOException e) {
			System.out.print("gauge init error");
		}		
		
		
		//set the size of the painting space
		Dimension size = new Dimension(cols, rows + fpR);
		this.setPreferredSize(size);
		this.setMinimumSize(size);
		this.setMaximumSize(size);	
		this.setSize(size);
		//this.revalidate();
				
				
		
		//Thread declarations
		VFThread = new Thread(this, "Video Feed Thread");
		time = System.currentTimeMillis();
		VFThread.start();
	}
		
	
	/******************openCV DEPENDANCE STARTS **********************************/
	
	//this is something weird that has to be done to properly load the OpenCV library
	static {  System.loadLibrary( Core.NATIVE_LIBRARY_NAME );	}
		
	//Video capture object (handles stream)
	private VideoCapture cap;
	
	//Matricies (for holding images)
	private Mat CVimg = new Mat(rows, cols, 16);
	//http://ninghang.blogspot.ca/2012/11/list-of-mat-type-in-opencv.html lists the types, 16 = CV_8UC3

	private void initOpenCV(){
		
		cap = new VideoCapture(0);   //0 = webcam, 1 = Analog2USB device
		
		// Check if video capturing is enabled]
		if (!cap.isOpened()) { System.out.println("Could not open video feed.");}
		
	}
	
	private Image getImage(){
		
		cap.read(CVimg);  //read a new frame -> this also updates the matrix combinedImgCV since CVimg is a subset of that

		//will need to do this for the actual frames from the camera to check the image dimensions
		//System.out.println("Rows = " + CVimg.rows() + "  Cols = " + CVimg.cols());
				
		if (CVimg != null && !CVimg.empty() && CVimg.rows() != 0 && CVimg.cols() != 0) {  
				streamActive = true;  return toBufferedImage(CVimg);  //convert from CV Mat to BufferedImage
		}
		else
		{	//System.out.println("No image grabbed, making a blank image");
			CVimg = new Mat(rows, cols, 16, new Scalar(110,110,110));
			streamActive = true;
			return toBufferedImage(CVimg);
		}
	}
	
	//function to end the video capture and display thread 
	public void endCapture(){	endThread = true;	cap.release();}


	 //convert from OpenCV Image container (Mat) to Java image container (Image or BufferedImage)   
	public static Image toBufferedImage(Mat m){
	    // Code from http://stackoverflow.com/questions/15670933/opencv-java-load-image-to-gui
		
	    // Check if image is grayscale or color
	    int type = BufferedImage.TYPE_BYTE_GRAY;
	    if ( m.channels() > 1 ) {       type = BufferedImage.TYPE_3BYTE_BGR;    }
	    	    
	    // Transfer bytes from Mat to BufferedImage
	    int bufferSize = m.channels()*cols*rows;   //m.cols()*m.rows();
	    byte [] b = new byte[bufferSize];
	    m.get(0,0,b); // get all the pixels
	    BufferedImage image = new BufferedImage(cols, rows+fpR, type);  //new BufferedImage(m.cols(), m.rows(), type);
	    final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
	    System.arraycopy(b, 0, targetPixels, 0, b.length);
	    return image;
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
	

	/* Update Function -> grabs frame, converts to BufferedImage, and calls repaint  */
	private void update() {
		  
		streamActive = false; //will be set to TRUE if function below is sucessful. 
		
		//OpenCV Dependance
		img = getImage();  //can comment this out, will still display gauges		
		
		if(img == null || !streamActive)  //failed to get video, still want to display everything else
		{
			if(imageRealloc) { //hasn't had time to render last image. Using Thread.sleep() can be dangerous...
				try { Thread.sleep(25);	} catch (InterruptedException e) {}  return; }
			img = new BufferedImage(cols, rows+fpR, BufferedImage.TYPE_3BYTE_BGR);
			imageRealloc = true;
		}
		FrameNum++;
		  
	
		updateStatus();  //just for testing
		speedGauge.updateValue(airSpd);
		altGauge.updateValue(altitude);
						
		
		
		//repaint the JFrame (paintComponent will be called)
		this.repaint();
		
		//framerate code (prints frame rate every 500 frames)
		if(FrameNum % 500 == 0 && FrameNum != 0)
		{
			System.out.println("FR = " + 500*1000/(System.currentTimeMillis() - time));  time = System.currentTimeMillis();
		}	
				
	}
	
	/*This overrides the JFrame (??) function to paint the video frame (stored in class object "img" in the drawing frame */ 
    @Override
	public void paintComponent(Graphics g) {

    	
    	if(img != null)
    	{
        	//create a graphics object from the img, which will be edited -> this allows the edited image to be saved
	    	Graphics temp = img.getGraphics();
	
	    	if(temp == null) System.out.print("null");
			
	    	
			speedGauge.draw((Graphics2D)temp);
			altGauge.draw((Graphics2D)temp);
			
			temp.setFont(new Font("TimesRoman", Font.PLAIN, 20)); 
			if(isDropped)
			{
				temp.setColor(Color.GREEN);
				temp.drawString("Payload Dropped", cols - 200, rows + 30);
				temp.drawString("Height At Drop = " + (int)altAtDrop + " ft", cols - 200, rows + 60);
			}
			else
			{
				temp.setColor(Color.RED);
				temp.drawString("Payload Not Dropped", cols - 200, rows + 30); 			
			}
			
			
			doRecording(temp);
					
						  
			//drawHorizon((Graphics2D)temp);
    	}
    	
    	
    	
		//draw the image to the screen
		g.drawImage(img, 0, 0, null);
		
		imageRealloc = false;

	}
	
	int maxFrames = 100;  //TEMPORARY for testing to ensure it doesn't record a crazy amount of frames
    private void doRecording(Graphics temp)
    {
    	//check whether to save video		  
		if(recordingVideo  && currentRecordingFN < maxFrames)
		{	saveFrame();	
			logData();		
			currentRecordingFN++;	
			
			//write recording status (already have font loaded)
			temp.setColor(Color.RED);
			temp.drawString("RECORDING", cols - 200, rows + 150); 
		
		}
		else if(currentRecordingFN == maxFrames)
		{ 	currentRecordingFN++;  toggleRecordingStatus();			}
    }

    
    
    private void saveFrame(){
    	
    		//can change wher you want to save to
    		File outputfile = new File("C:" + File.separator + "Users" + File.separator + "Ryan"+ File.separator + "Documents" + File.separator + "Current Files" + File.separator +
						"Aero" + File.separator + "Images" + File.separator + startDate + "_" + currentRecordingFN + ".jpg");
			
    		/* This way is simpler but doesn't give quality options:  
			try {
				ImageIO.write((RenderedImage) img, "jpg", outputfile);
			} catch (IOException e1) { System.out.println("Error Saving");}
			 */
			//http://stackoverflow.com/questions/17108234/setting-jpg-compression-level-with-imageio-in-java
			
			ImageWriter jpgWriter = ImageIO.getImageWritersByFormatName("jpg").next();
			ImageWriteParam jpgWriteParam = jpgWriter.getDefaultWriteParam();
			jpgWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			jpgWriteParam.setCompressionQuality(0.8f);

			ImageOutputStream outputStream;
			try {
				outputStream = new FileImageOutputStream(outputfile);
				jpgWriter.setOutput(outputStream);

			} catch (FileNotFoundException e) {
				System.out.print("Failed to save image");
			} catch (IOException e) {
				System.out.print("Failed to save image");
			} 
			
			IIOImage outputImage = new IIOImage((BufferedImage)img, null, null);
			
			try {
				jpgWriter.write(null, outputImage, jpgWriteParam);
			} catch (IOException e) {
				System.out.print("Failed to save image");
			}
			
			jpgWriter.dispose();						
		
    }
    

	
	/* Starts/stops the recording AND output log file */
	public void toggleRecordingStatus(){
		
		if(!recordingVideo)  //start recording
		{	
			recordingVideo = true;	currentRecordingFN = 1;  //reset
			
			//set timestamp
			Date date = new Date(new Timestamp(System.currentTimeMillis()).getTime());
			startDate = new String(sdf.format(date));		
			
			videoRecStartTime = System.currentTimeMillis();

			System.out.println("Recording Set to ON");
			
			//START OUTPUT STREAM
			logPath = Paths.get("C:" + File.separator + "Users" + File.separator + "Ryan"+ File.separator + "Documents" + File.separator + "Current Files" + File.separator +
					"Aero" + File.separator + "Images" + File.separator + startDate + "_log.txt");
			
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
	        
		        String s = "Frame,time,roll,pitch,airSpeed,altitude\n";
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
		if(isDropped != dropped)
			altAtDrop = alt;
		isDropped = dropped;
		
	}
	
	
	int sign=1;
	private void updateStatus(){
		//this changes to random values for testing visualization
		rollAng+= sign;  //to watch it move
		if(rollAng > 45 || rollAng <-45) sign = -sign;
		
		airSpd = 0.25*(FrameNum%300);
		altitude = 0.5*(FrameNum%311);
		//possible GPS, pitch, etc.
		
	}
	
	private void logData() {
	
		String t = Long.toString(System.currentTimeMillis() - videoRecStartTime);
		String s = Integer.toString(currentRecordingFN) + "," + t + "," + rollAng + "," + pitchAng + "," + airSpd + "," + altitude + "\n";
		
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
	

	//some constants for the drawHorizon Function
	Point origin = new Point(cols/2, rows/2);
	private final static int r = (cols-100)/2;  //radius of line
	private final static int verticalOffset = 100;
	private void drawHorizon(Graphics2D g){		//let 0 degrees be neutral, and -45 degrees be /  and 45 degrees be \
		
		//some math to use polar coodinates
		double cosTheta = Math.cos(rollAng*Math.PI/180);
		double sinTheta = Math.sin(rollAng*Math.PI/180);		
		int dx =  (int)(r*cosTheta), dy = (int) (r*sinTheta);  //distances from the origin to end of line along x and y axis
		int xoff = (int) (verticalOffset*sinTheta), yoff = (int) (-verticalOffset*cosTheta);  //offset for the upper/lower lines
		
		//middle line
		GeneralPath mid = new GeneralPath();
		mid.moveTo(origin.x + dx, origin.y + dy);
		mid.lineTo(origin.x-dx, origin.y - dy);
		mid.closePath();
		
		GeneralPath top = new GeneralPath();
		top.moveTo(origin.x + xoff + dx/2, origin.y + yoff + dy/2);
		top.lineTo(origin.x+xoff-dx/2, origin.y + yoff - dy/2);
		top.closePath();

		GeneralPath bot = new GeneralPath();
		bot.moveTo(origin.x - xoff + dx/2, origin.y - yoff + dy/2);
		bot.lineTo(origin.x - xoff-dx/2, origin.y - yoff - dy/2);
		bot.closePath();

		g.setStroke(new BasicStroke(2.0f));  //2.0f is thickness
		g.setColor(new Color(0f,1.0f,0f));
		g.draw(mid);
		g.draw(top);
		g.draw(bot);
		
		
	}
	
	
}


