import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Logger;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.JPanel;
import javax.swing.Timer;

import org.opencv.core.Mat;
import org.opencv.core.Core;
import org.opencv.highgui.VideoCapture;
import org.opencv.highgui.Highgui;
import java.lang.Math;

/*Notes:  
 * This uses OpenCV libaray to handle the streaming from the Analog to USB device.  The OpenCV dependance is as minimal as posisbe
 * By commenting out the block of code marked by "openCV DEPENDANCE STARTS", as well as a few function calls to those functions (including one in AeroGUI 
 * class which is called for shutdown), this can function without having OpenCV installed. The only thing missing *should* be the video itself.
 * Dependencies:  
 * 1. InitCV call -> from this classes constructor
 * 2. getImage call -> in the function (in the class) called "update()"
 * 3. endCapture -> in AeroGUI class "windowClosing()"
 * 4. The code block indicated by ******** OpenCV Depednace starts****  -> block comments work well for this 
 * 
 * 
 * Webcam is often used for testing. To switch between webcam mode and videograbber mode, there is one step:
 * 1) right below: first line in class declaring vidRows/cols/fpR/fpC variables, switch to other line 
 * 
 * 
 * Worst case scenario testing:
 * 
 * 1. OpenCV streaming fails - make sure doesn't hang program!!
 * 2. Serial communication stuff
 * 
 */


public class VideoFeed extends JPanel{
	private static final Logger LOGGER = Logger.getLogger(AeroGUI.class.getName());
	
	//image size variables (1st = webcam) (2nd = analog 2 usb)
	//private int rows = 480, cols = 640, videoSource = 0;  //rows, columns in frame from camera, rows, columns in flight display panel
	private int rows = 525, cols = 720, videoSource = 0;  //rows, columns in frame from camera, rows, columns in flight display panel
	
	//note: there are ~1000 vertical pixels to work with.  This takes up 150+576 = 776, leaving ~250 for other stuff
	
	//Timing/timestamping/file storing variables
	private int FrameNum = 0; 
	private long time;
 	String startDate;   
	//private DecimalFormat df = new DecimalFormat("#000.00");
	SimpleDateFormat sdf;
	
	private int JPANEL_WIDTH = 1920/2;   //note assumes screen is 1920*1080

		
	//thread variables
	Timer threadTimer; 
	
	//Buffered Image container
	private BufferedImage img; // = new BufferedImage(cols, rows+fpR, BufferedImage.TYPE_3BYTE_BGR);

	//state variables (containing information about current state of plane)
	public double airSpdMps = 7, altitudeFt = 8; 
	public double lattitudeDDM, longitudeDDM;
	public boolean isDropped = false;  
	public double altAtDropFt = 0, heading = 270; //whether the payload has been dropped
	private boolean recordingVideo = false; boolean streamActive = false;
	public int currentRecordingFN = 0;
	public double frameRate = 0;
	
	private ImageWriter jpgWriter;
	private ImageWriteParam jpgWriteParam;
	
	private VideoEncoder enc;
	
	//Constructor
	public VideoFeed() {
		
		//OpenCV Dependence
		initOpenCV();
		
		//initialize the timestamp
		Date date = new Date(new Timestamp(System.currentTimeMillis()).getTime());
		sdf = new SimpleDateFormat("MM.dd.yyyy_h.mm.ss.SSS");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		startDate = new String(sdf.format(date));	
		 
		//define image writing settings
		jpgWriter = ImageIO.getImageWritersByFormatName("jpg").next();
		jpgWriteParam = jpgWriter.getDefaultWriteParam();
		jpgWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		jpgWriteParam.setCompressionQuality(0.8f);
		
		//set the size of the painting space
		Dimension size = new Dimension(cols, rows); 
		this.setPreferredSize(size);
		ActionListener updateStream = new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				update();
			}
		};
		
		threadTimer = new Timer(33, updateStream);  //33 ms ~30FPS
		threadTimer.start(); //note: by default Coalescing is on, meaning that it won't queue events
	}
	
	public int getVideoSrc(){	return videoSource;	}
	
	
	/******************openCV DEPENDANCE STARTS **********************************/
	
	//this is something weird that has to be done to properly load the OpenCV library
	static { System.loadLibrary( Core.NATIVE_LIBRARY_NAME ); }
		
	//Video capture object (handles stream)
	private VideoCapture cap;
	
	//Matricies (for holding images)
	private Mat CVimg = new Mat(rows, cols, 16);
	//http://ninghang.blogspot.ca/2012/11/list-of-mat-type-in-opencv.html lists the types, 16 = CV_8UC3

	private void initOpenCV() {
		
		cap = new VideoCapture(videoSource);   //0 = webcam, 1 = Analog2USB device
		// Check if video capturing is enabled]
		if (!cap.isOpened()) { 
			LOGGER.warning("Could not open video feed.");
		} else {
			int dimension = (int) cap.get(Highgui.CV_CAP_PROP_FRAME_WIDTH);
			if(dimension != 0) {
				cols = dimension;
			} else {
				LOGGER.warning("Could not get opencv frame width. Assuming: " + cols);
			}
			dimension = (int) cap.get(Highgui.CV_CAP_PROP_FRAME_HEIGHT);
			if(dimension != 0) {
				rows = dimension;
			} else {
				LOGGER.warning("Could not get opencv frame height. Assuming: " + rows);
			}
		}
		
	}
	
	private BufferedImage getImageCV(){
		
		boolean success = false;
		
		if(cap.isOpened() )
			success = cap.read(CVimg);  //read a new frame -> this also updates the matrix combinedImgCV since CVimg is a subset of that

				
		if (success && CVimg != null && !CVimg.empty() && CVimg.rows() != 0 && CVimg.cols() != 0) {  
				streamActive = true; 
				//System.out.print("test");
				return toBufferedImage(CVimg);  //convert from CV Mat to BufferedImage				
		} else {
			//System.out.println("No image grabbed, making a blank image");
			return new BufferedImage(cols, rows, BufferedImage.TYPE_3BYTE_BGR);
		}
	}
	
	//function to end the video capture and display thread 
	public void endCapture(){ threadTimer.stop(); cap.release(); }

	 //convert from OpenCV Image container (Mat) to Java image container (Image or BufferedImage)   
	public BufferedImage toBufferedImage(Mat m) {
	    // Code from http://stackoverflow.com/questions/15670933/opencv-java-load-image-to-gui
		
	    // Check if image is grayscale or color
	    int type = BufferedImage.TYPE_BYTE_GRAY;
	    if ( m.channels() > 1 ) {       type = BufferedImage.TYPE_3BYTE_BGR;    }
	    	    
	    // Transfer bytes from Mat to BufferedImage
	    int bufferSize = m.channels()*cols*rows;   //m.cols()*m.rows();
	    byte [] b = new byte[bufferSize];
	    m.get(0,0,b); // get all the pixels
	    BufferedImage image = new BufferedImage(cols, rows, type);  //new BufferedImage(m.cols(), m.rows(), type);
	    final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
	    System.arraycopy(b, 0, targetPixels, 0, b.length);
	    return image;
	}
	
	//in case stream gets lost and can't recover itself during operation. Button on GUI calls this function
	public void restartVideoStream()
	{
		cap.release();  //close it
		cap = new VideoCapture(videoSource);   //re-open it
		
		// Check if video capturing is enabled
		if (!cap.isOpened()) { LOGGER.warning("Could not re-open video feed.");}
		
	}
	
	/******************openCV DEPENDANCE ENDS **********************************/
	
	
	/* Update Function -> grabs frame, converts to BufferedImage, and calls repaint  */
	private void update() {
		  
		streamActive = false; //will be set to TRUE if function below is sucessful. 
		
		boolean usingCV = false;
		//OpenCV Dependance (can comment out and below should be fine, though without video stream)
		img = getImageCV(); usingCV = true;
		
		if(!usingCV)
			img = getImageNonCV();
		
		FrameNum++;
						
		//repaint the JFrame (paintComponent will be called)
		this.repaint();	
			
	}
	
	/*This overrides the JFrame function to paint the video frame (stored in class object "img" in the drawing frame */ 
    @Override
	public void paintComponent(Graphics g) {
		
    	if(img != null) {
    		super.paintComponent(g); //prevents repainting
    		
        	//create a graphics object from the img, which will be edited -> this allows the edited image to be saved
    		Graphics2D modifiedFrame = (Graphics2D) g;
    		
			// Calculate Scaling Factor:
			double scaleFactor = 1;
			double scaleFactorx = 1;
			double scaleFactory = 1;
			scaleFactory = ((double)this.getHeight()) / ((double)rows); // Maximum possible vertical scaling factor
			scaleFactorx = ((double)this.getWidth()) / ((double)cols); // Maximum possible horizontal scaling factor
			scaleFactor = Math.min(scaleFactorx, scaleFactory); // Do not distort image, scale by same amount in both directions
			
					
			// Apply scaling trasnform:
			AffineTransform saveXform = modifiedFrame.getTransform(); // Save current transform so it can be restored afterward
			AffineTransform scaleXform = new AffineTransform();
			scaleXform.scale(scaleFactor, scaleFactor);
			modifiedFrame.transform(scaleXform); // Apply scaling transform
    		
			
			modifiedFrame.drawImage(img, (int)(JPANEL_WIDTH/2-cols*scaleFactor/2), 0, null);
    		//Graphics2D modifiedFrame = img.createGraphics();

			modifiedFrame.setFont(new Font("TimesRoman", Font.PLAIN, 20)); 
			modifiedFrame.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);  //make the text looke nice
			
			if(isDropped)
			{
				modifiedFrame.setColor(Color.GREEN);
				modifiedFrame.drawString("Payload Dropped", cols - 200, rows - 30);
				modifiedFrame.drawString("Height At Drop = " + (int)altAtDropFt + " ft", cols - 200, rows - 60);
			}
			else
			{
				modifiedFrame.setColor(Color.RED);
				modifiedFrame.drawString("Payload Not Dropped", cols - 200, rows - 30); 			
			}
			
			if(FrameNum % 5 == 0)
			{   //framerate code
				long curTime = System.currentTimeMillis();
				frameRate = 5.0*1000.0/(System.currentTimeMillis()-time);
				time = curTime;
			}
			modifiedFrame.setColor(Color.GREEN);
			modifiedFrame.drawString("FR: " + (int)frameRate, 5, 20);

		
			doRecording(modifiedFrame);
			
			// This has been removed, because we currently do not measure the roll:
			//drawHorizon(modifiedFrame);


			modifiedFrame.setTransform(saveXform); // Restore initial transform
    	} else {
    		LOGGER.warning("No image to render");
    	}

    }
    
    private BufferedImage getImageNonCV(){
	 	return  new BufferedImage(cols, rows, BufferedImage.TYPE_3BYTE_BGR);
    	
    	/*BufferedImage bi = null;
    	try {
    		bi = ImageIO.read(new File("plane.png")); // for testing without openCV (Just a png that I was using with the same dimensions as the video stream)
    	} catch(IOException e) {
    		
    	}
    	return bi;  */
    } 
	
	int maxFrames = 100000;  //@ 30 FPS = ~ 1 hour and @100Kb/frame = 10 GB of frames
    private void doRecording(Graphics temp)
    {
    	//check whether to save video		  
		if(recordingVideo  && currentRecordingFN < maxFrames)
		{	saveFrame();	
			currentRecordingFN++;	
			
			//write recording status (already have font loaded)
			temp.setColor(Color.RED);
			temp.drawString("RECORDING", cols - 200, rows - 20); 
		
		}
		else if(currentRecordingFN == maxFrames)
		{ 	currentRecordingFN++;  toggleRecordingStatus();			}
    }

    
    
    private void saveFrame() {
    		//can change wher you want to save to
    		//File outputfile = new File("C:" + File.separator + "Users" + File.separator + "Ryan"+ File.separator + "Documents" + File.separator + "Current Files" + File.separator +
			//			"Aero" + File.separator + "Images" + File.separator + startDate + "_" + currentRecordingFN + ".jpg");
    	
    		/*new File("VideoFrames").mkdirs();
    		File outputfile = new File("VideoFrames" + File.separator + startDate + "_" + currentRecordingFN + ".jpg");
					

			ImageOutputStream outputStream;
			try {
				outputStream = new FileImageOutputStream(outputfile);
				jpgWriter.setOutput(outputStream);

			} catch (FileNotFoundException e) {
				LOGGER.warning("Failed to save image");
			} catch (IOException e) {
				LOGGER.warning("Failed to save image");
			} 
			
			IIOImage outputImage = new IIOImage((BufferedImage)img, null, null);
			*/
			try {
				enc.encodeImage(img);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			/*
			try {
				jpgWriter.write(null, outputImage, jpgWriteParam);
			} catch (IOException e) {
				LOGGER.warning("Failed to save image");
			}
			*/
    }
    

	
	/* Starts/stops the recording AND output log file */
	public void toggleRecordingStatus(){
		if(cap.isOpened()) {
			if(!recordingVideo) {  //start recording
				recordingVideo = true;	currentRecordingFN = 1;  //reset
				try {
					enc = new VideoEncoder();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				LOGGER.info("Recording Set to ON");
				
			} else { //stop recording video
				recordingVideo = false;
				try {
					enc.finish();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				LOGGER.info("Recording Set to OFF");
			}
		} else {
			LOGGER.warning("Recording not started because camera capture is not open.");
		}
	}
	
	public boolean getRecordStatus() {  return recordingVideo;  }
	
	//if too many values, might want to send as enum/struct type? does java have that
	public void updateValues(double altitudeFt, double airspeedMps, double lattDDM, double longitDDM, double head)  //add more as necessary (ie. GPS).
	{
		this.airSpdMps = airspeedMps;
		this.altitudeFt = altitudeFt;
		this.lattitudeDDM = lattDDM;
		this.longitudeDDM = longitDDM;
		this.heading = head;
	
		
	}
	
	public void changeDropStatus(boolean dropped)  //this function will really only ever be called to set it to 'true'
	{
		if(dropped)
			altAtDropFt = altitudeFt; //whatever the last received altitude was. This is close enough
		
		isDropped = dropped;
	}

	
	
	/**********  No longer track roll, but keep this as it could look cool if we have it in the future **********
	// Draws the horizon on the video feed to give a more intuitive representation of the current roll
	private void drawHorizon(Graphics2D g) {	//let 0 degrees be neutral, and -45 degrees be \  and 45 degrees be /
		//some constants for the drawHorizon Function
		Point origin = new Point(cols/2, rows/2);
		int r = (cols-100)/2;  //radius of line
		int verticalOffset = 100;
		double angle = -rollAng;  //when I made this i guessed wrong 
		
		//some math to use polar coodinates
		double cosTheta = Math.cos(angle*Math.PI/180);
		double sinTheta = Math.sin(angle*Math.PI/180);		
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
	}  */
	
	
}


