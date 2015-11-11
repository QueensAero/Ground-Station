import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.text.DecimalFormat;

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
	
	private final int r = 480, c = 640;
	private final int fpR = 100, fpC = 640;
	//http://ninghang.blogspot.ca/2012/11/list-of-mat-type-in-opencv.html lists the types
	private Mat flightPanel = new Mat(fpR, fpC, 16);  //24 = CV_8UC4, 16 = CV_8UC3 ->  is the type used in the webcam feed - might be different for stream
	
	private int FrameNum = 0;
	
	
	private double rollAng;  
	private double airSpd; 
	private double altitude; 
	private DecimalFormat df = new DecimalFormat("#.00");
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
		
		//initialize flight panel to be grey
		//NOTE -> Java byte is -127 to 128, BUT it supports the cast ie. byte(255) = -1 in java which is interpreted as 255
		for(int x = 0; x<fpC; x++ )
			for(int y = 0; y < fpR; y++)
				flightPanel.put(y, x, new byte[]{(byte)110, (byte)110, (byte)110}); 
		
		Core.putText(flightPanel, "Alt:", new Point(5,50), 0, 1, new Scalar(0, 0, 0), 2);  //last 4: font type, size, colour, thickness
		Core.putText(flightPanel, "Spd:", new Point(200,50), 0, 1, new Scalar(0, 0, 0), 2);  //last 4: font type, size, colour, thickness

		
		//RJD added
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
	
	
	/* Function to process the frame, extending its size to add flight details (incomplete) */
	private void processFrame(){
		
		//for testing, only save a few images. Save unedited image (otherwise no way to get it back)
		if(FrameNum < 10)
			Highgui.imwrite("Images" + File.separator + "test" + FrameNum + ".jpg", CVimg, new MatOfInt(5));  //parameter at end is quality (0 = lowest, 100 = highest)
		
		//will need to do this for the actual frames from the camera to check the image dimensions
		//System.out.println("Rows = " + matrix.rows() + "  Cols = " + matrix.cols());
		//System.out.println("FP Rows = " + flightPanel.rows() + "  Cols = " + flightPanel.cols());
		
		
		updateStatus();
		drawHorizon();		
		printInfo();

		//add the flightPanel to the matrix
		CVimg.push_back(flightPanel);
				
	}
	
	
	//need to change the fundamental running of this function - have fixed line length for each section, and rotate/translate lines to be centered on middle line
	private void drawHorizon(){
		
		int dy = (int) (((c-100)/2)*Math.tan(rollAng*Math.PI/180));
		Core.line(CVimg, new Point(50,r/2-dy), new Point(c-50,r/2+dy), new Scalar(0,255,0), 1, Core.LINE_AA, 0); //middle line
		Core.line(CVimg, new Point(125,r/2-dy*(c-250)/(c-100)+50), new Point(c-125,r/2+dy*(c-250)/(c-100)+50), new Scalar(0,255,0), 1, Core.LINE_AA, 0);  //line below
		Core.line(CVimg, new Point(200,r/2-dy*(c-400)/(c-100)+100), new Point(c-200,r/2+dy*(c-400)/(c-100)+100), new Scalar(0,255,0), 1, Core.LINE_AA, 0);  //line below
		Core.line(CVimg, new Point(125,r/2-dy*(c-250)/(c-100)-50), new Point(c-125,r/2+dy*(c-250)/(c-100)-50), new Scalar(0,255,0), 1, Core.LINE_AA, 0);  //line above
		Core.line(CVimg, new Point(200,r/2-dy*(c-400)/(c-100)-100), new Point(c-200,r/2+dy*(c-400)/(c-100)-100), new Scalar(0,255,0), 1, Core.LINE_AA, 0);  //line below
		
	}
	
	private void updateStatus(){
		//These values will need to come from accessors
		rollAng = FrameNum%180;  //to watch it move
		airSpd = 10.5*(FrameNum%12);
		altitude = 40*(FrameNum%22);
		//possible GPS, pitch, etc.
		
	}
	
	private void printInfo(){
		
		//refill old text area
		Core.rectangle(flightPanel, altDrawArea.tl(), altDrawArea.br(), new Scalar(110,110,110), -1);
		Core.rectangle(flightPanel, airSpdDrawArea.tl(), airSpdDrawArea.br(), new Scalar(110,110,110), -1);
		
		//draw text
		Core.putText(flightPanel, df.format(altitude), new Point(altDrawArea.x,altDrawArea.y+altDrawArea.height-1), 0, 1, new Scalar(0, 0, 0), 2);  //last 4: font type, size, colour, thickness
		Core.putText(flightPanel, df.format(airSpd), new Point(airSpdDrawArea.x,airSpdDrawArea.y+airSpdDrawArea.height-1), 0, 1, new Scalar(0, 0, 0), 2);  //last 4: font type, size, colour, thickness


		
	}

}
