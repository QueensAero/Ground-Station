import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;

import javax.swing.JPanel;

import org.opencv.core.Mat;
import org.opencv.core.Core;
import org.opencv.core.MatOfInt;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.highgui.VideoCapture;
import org.opencv.highgui.Highgui;


public class VideoFeed extends JPanel implements Runnable {
	private Image img;
	private VideoCapture cap;
	private Mat matrix; // Matrix for storing image
	
	private final int fpR = 100, fpC = 640;
	private Mat flightPanel = new Mat(fpR, fpC, 16);  //16 = CV_8UC3,  is the type used in the webcam feed - might be different for stream
	
	private int FrameNum = 0;
	
	
	//RJD added
	Thread VFThread;
	boolean endThread = false;

	
	public VideoFeed() {
		// Register the default camera
		cap = new VideoCapture(0);
		// Check if video capturing is enabled]
		if (!cap.isOpened()) { System.out.println("Could not open video feed.");}
		matrix = new Mat();
		
		//initialize flight panel to be grey
		//NOTE -> Java byte is -127 to 128, BUT it supports the cast ie. byte(255) = -1 in java which is interpreted as 255
		for(int x = 0; x<fpC; x++ )
			for(int y = 0; y < fpR; y++)
				flightPanel.put(y, x, new byte[]{(byte)110, (byte)110, (byte)110}); 
		
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
		cap.read(matrix);  //read a new frame
		
		//if the frame is not NULL, then prepare it for display
		if (matrix != null && !matrix.empty()) {  
			
			FrameNum++;
			processFrame();
			
			img = toBufferedImage(matrix);  //convert from CV Mat to BufferedImage
			
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
		
		//System.out.println("Rows = " + matrix.rows() + "  Cols = " + matrix.cols());
		//System.out.println("FP Rows = " + flightPanel.rows() + "  Cols = " + flightPanel.cols());

		Core.putText(flightPanel, "Alt:" + Integer.toString(10) + "  Air Spd:" + Integer.toString(30), new Point(5,50), 0, 1, new Scalar(0, 0, 0), 3);  //last 4: font type, size, colour, thickness

		matrix.push_back(flightPanel);
		
		if(FrameNum < 10)
			Highgui.imwrite("Images" + File.separator + "test" + FrameNum + ".jpg", matrix, new MatOfInt(5));
		
	}
	

}
