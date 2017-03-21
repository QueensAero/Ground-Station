import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Logger;

import org.jcodec.scale.AWTUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.common.NIOUtils;
import org.jcodec.common.SeekableByteChannel;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.containers.mp4.Brand;
import org.jcodec.containers.mp4.MP4Packet;
import org.jcodec.containers.mp4.TrackType;
import org.jcodec.containers.mp4.muxer.FramesMP4MuxerTrack;
import org.jcodec.containers.mp4.muxer.MP4Muxer;
import org.jcodec.scale.ColorUtil;
import org.jcodec.scale.Transform;

/**
 * Class to encode the live video stream.
 * Uses the JCodec library for the encoding.
 * This library currently only supports h.264 encoding (generates an mp4 file).
 * 
 * Note: This class was created based on these existing classes in the JCodec library.
 * They had to be adjusted in order to support slower frame rates. Notice that verison 0.1.9 is used.
 * Be careful about upgrading this because the library seemed to be under very active development.:
 * 
 * https://github.com/jcodec/jcodec/blob/0.1.9/src/main/java/org/jcodec/api/SequenceEncoder.java
 * https://github.com/jcodec/jcodec/blob/0.1.9/javase/src/main/java/org/jcodec/api/awt/SequenceEncoder.java
 * 
 * Helps to explain how timescale and frame duration work:
 * https://github.com/jcodec/jcodec/issues/21
 * 
 * @author Ryan Dick
 *
 */
public class VideoEncoder {
	private static final Logger LOGGER = Logger.getLogger(AeroGUI.class.getName());
	
    private SeekableByteChannel ch;
    private Picture toEncode;
    private Transform transform;
    private H264Encoder encoder;
    private ArrayList<ByteBuffer> spsList;
    private ArrayList<ByteBuffer> ppsList;
    private FramesMP4MuxerTrack outTrack;
    private ByteBuffer _out;
    private int frameNo; // Really the image number
    private int frameIndex; // Frame index (same image may be displayed for multiple video frames if the images arrive too slowly)
    private MP4Muxer muxer;
    
	private Long lastImgArrival;
	private boolean firstImgHasArrived;
	private static final Double FRAME_RATE = 25.0; //fps
	
	
    public VideoEncoder(String filename) throws IOException {
    	File out = new File(filename); 
        this.ch = NIOUtils.writableFileChannel(out);

        // Muxer that will store the encoded frames
        muxer = new MP4Muxer(ch, Brand.MP4);

        // Add video track to muxer
        outTrack = muxer.addTrack(TrackType.VIDEO, 25);

        // Allocate a buffer big enough to hold output frames
        _out = ByteBuffer.allocate(1920 * 1080 * 6);

        // Create an instance of encoder
        encoder = new H264Encoder();

        // Transform to convert between RGB and YUV
        transform = ColorUtil.getTransform(ColorSpace.RGB, encoder.getSupportedColorSpaces()[0]);

        // Encoder extra data ( SPS, PPS ) to be stored in a special place of
        // MP4
        spsList = new ArrayList<ByteBuffer>();
        ppsList = new ArrayList<ByteBuffer>();
        
        frameNo = 0;
        frameIndex = 0;
        firstImgHasArrived = false;
    }
	
	public VideoEncoder() throws IOException {
		//Initialize the timestamp
		Date date = new Date(new Timestamp(System.currentTimeMillis()).getTime());
		SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy_h-mm-ss-SSS");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		String startDate = new String(sdf.format(date));
		String filename = "video_" + startDate + ".mp4";
		
		File out = new File(filename); 
        this.ch = NIOUtils.writableFileChannel(out);

        // Muxer that will store the encoded frames
        muxer = new MP4Muxer(ch, Brand.MP4);

        // Add video track to muxer
        outTrack = muxer.addTrack(TrackType.VIDEO, 25);

        // Allocate a buffer big enough to hold output frames
        _out = ByteBuffer.allocate(1920 * 1080 * 6);

        // Create an instance of encoder
        encoder = new H264Encoder();

        // Transform to convert between RGB and YUV
        transform = ColorUtil.getTransform(ColorSpace.RGB, encoder.getSupportedColorSpaces()[0]);

        // Encoder extra data ( SPS, PPS ) to be stored in a special place of
        // MP4
        spsList = new ArrayList<ByteBuffer>();
        ppsList = new ArrayList<ByteBuffer>();
        
        frameNo = 0;
        frameIndex = 0;
        firstImgHasArrived = false;
	}
	
	/*public void encodeImage(BufferedImage img) {
		if(!firstImgHasArrived) {
			lastImg = cloneBufferedImage(img);
			lastImgArrival = System.nanoTime();
			firstImgHasArrived = true;
		} else {
			LOGGER.warning("Got here1.");
			Long curTime = System.nanoTime();
			Long elapsedTime = curTime - lastImgArrival;
			lastImgArrival = System.nanoTime(); // Update this value as early as possible
			Double elapsedTime_ms = elapsedTime.doubleValue() / 1000000.0;
			LOGGER.warning("elapsed time:" + elapsedTime_ms);
			
			Double timePerFrame = 1000.0 / (double)FRAME_RATE; // 1000 ms / 60 fps
			Double numFrames = elapsedTime_ms / timePerFrame;
			int numFrames_round = (int) Math.round(numFrames);
			System.out.println("Num frames: " + numFrames_round);
			if(numFrames_round > 30) {
				// Frame rate is too slow, we are going to start blocking the program with encoding
				// TODO
			}
			
			for(int i = 0; i < numFrames_round; i++) {
				try {
					enc.encodeImage(lastImg);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			lastImg = cloneBufferedImage(img);
		}
	}*/
	
	public void encodeImage(BufferedImage bi) throws IOException {
		if(!firstImgHasArrived) {
			lastImgArrival = System.nanoTime();
			firstImgHasArrived = true;
		} else {
			Picture pic = AWTUtil.fromBufferedImage(bi);
			Long curTime = System.nanoTime();
			Long elapsedTime = curTime - lastImgArrival;
			lastImgArrival = System.nanoTime(); // Update this value as early as possible
			Double elapsedTime_secs = elapsedTime.doubleValue() / 1000000000.0;
			double frames = elapsedTime_secs / (1.0 / FRAME_RATE);
			int numFrames_round = (int) Math.round(frames);
			
			if (toEncode == null) {
	            toEncode = Picture.create(pic.getWidth(), pic.getHeight(), encoder.getSupportedColorSpaces()[0]);
	        }

	        // Perform conversion
	        transform.transform(pic, toEncode);

	        // Encode image into H.264 frame, the result is stored in '_out' buffer
	        _out.clear();
	        ByteBuffer result = encoder.encodeFrame(toEncode, _out);

	        // Based on the frame above form correct MP4 packet
	        spsList.clear();
	        ppsList.clear();
	        H264Utils.wipePS(result, spsList, ppsList);
	        H264Utils.encodeMOVPacket(result);
	        
	        
	        // Add packet to video track
	        outTrack.addFrame(new MP4Packet(result, frameIndex, 25, numFrames_round, frameNo, true, null, frameIndex, 0));
	        frameIndex += numFrames_round;
	        frameNo++;
		}
	}

    public void finish() throws IOException {
        // Push saved SPS/PPS to a special storage in MP4
        outTrack.addSampleEntry(H264Utils.createMOVSampleEntry(spsList, ppsList, 4));

        // Write MP4 header and finalize recording
        muxer.writeHeader();
        NIOUtils.closeQuietly(ch);
    }
	
	/*static BufferedImage cloneBufferedImage(BufferedImage bi) {
		 ColorModel cm = bi.getColorModel();
		 boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
		 WritableRaster raster = bi.copyData(null);
		 return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
	}
	*/
	
}
