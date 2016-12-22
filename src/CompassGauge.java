import static java.lang.Math.PI;
import static java.lang.Math.log;
import static java.lang.Math.tan;
import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;

public class CompassGauge {
	private static final Logger LOGGER = Logger.getLogger(AeroGUI.class.getName());
	
	private static final int MAP_SIZE_METERS = 100;
	
	//replace trackpoint
	public Date utcTime;
	public double latitudeDegrees;
	public double longitudeDegrees;
	public double altitudeMeters;
	public double elapsedSeconds;
	public double distanceMeters;
	public double velocityMetersPerSecond;
	
	
	private static final float ARROW_MAX_HEIGHT = 15.0f;
	private static final float ARROW_MIN_HEIGHT = 3.0f;
	
	
	private double angleRadians = 0;
	
	private double minLongitude;
	private double maxLatitude;
	
	private int radius;
	private int xOffset;
	private int yOffset;
	
	private double heading = 0;
	
	
	private GeneralPath path;
	private Shape circle;
	private Shape dot;
	private Shape pivot;
	private Shape shadow;
	private Paint gradient;
	
	private Font font;
	
	private Point _p1, _p2, _p3, _p4;
	private Point center;

	public CompassGauge(int mapSizePixels, int x, int y) throws FontFormatException, IOException {
	
		radius = mapSizePixels / 2;
		xOffset = x + radius;
		yOffset = y + radius;
		
		//path = getTrackPath(track, mapSizePixels);
		circle = new Ellipse2D.Float(x, y, mapSizePixels, mapSizePixels);
		
		int radius = 15;
		dot = new Ellipse2D.Double(
			xOffset - radius / 2,
			yOffset - radius / 2,
			radius, radius
		);
		
		
		pivot = getCircle(xOffset, yOffset, radius / 10.0f);
		font = new Font("TimesRoman", Font.BOLD, 12);
		makePoints();
		makeShadow();
	}
	
	//@Override
	public void draw(Graphics2D graphics) {	
		//drawTrack(graphics);
		drawCompass(graphics);
		//graphics.setTransform(new AffineTransform());
		drawArrow(graphics);
		drawDot(graphics);
	}
	
	
	private AffineTransform getTransform() {
		AffineTransform transform = new AffineTransform();
		transform.rotate(0, xOffset, yOffset);  //replace 0 with 'angleradians' if you want the actual gauge to rotate (not just arrow)
		return transform;
	}
	
	private void drawArrow(Graphics2D graphics) {
		// shadow
		graphics.setPaint(gradient);
		graphics.setStroke(new BasicStroke(1.0f));
		graphics.fill(shadow);
		
		// arrow
		//double degrees = (kmh * -2.4) + 30;
		//double degrees = -240*kmh/maxVal+30;  //CCW = positive, pointing left = neutral position
		//double radians = degrees * PI / 180.0;
		double radians  = angleRadians;
		
		Point p1 = _p1.rotate(radians).toScreen(center);
		Point p2 = _p2.rotate(radians).toScreen(center);
		Point p3 = _p3.rotate(radians).toScreen(center);
		Point p4 = _p4.rotate(radians).toScreen(center);
		
		GeneralPath arrow = new GeneralPath();
		arrow.moveTo(p1.x, p1.y);
		arrow.lineTo(p2.x, p2.y);
		arrow.lineTo(p3.x, p3.y);
		arrow.lineTo(p4.x, p4.y);
		arrow.closePath();
		
		graphics.setColor(Color.WHITE);
		graphics.fill(arrow);
		
		graphics.setColor(new Color(0.4f, 0.4f, 0.4f));
		graphics.draw(arrow);
		
		// pivot
		graphics.setStroke(new BasicStroke(1.5f));
		
		graphics.setColor(Color.BLACK);
		graphics.fill(pivot);
		
		graphics.setColor(Color.BLACK);
		graphics.draw(pivot);
	}

	private void makePoints() {
		int arrowPadding = 5;  //small inside
		
		_p1 = new Point(0, -ARROW_MAX_HEIGHT / 2.0f);
		_p2 = new Point(0, ARROW_MAX_HEIGHT / 2.0f);
		_p3 = new Point(-radius + arrowPadding, ARROW_MIN_HEIGHT / 2.0f);
		_p4 = new Point(-radius + arrowPadding, -ARROW_MIN_HEIGHT / 2.0f);
		
		center = new Point(xOffset, yOffset);
	}
	
	private Shape getCircle(float xCenter, float yCenter, float radius) {
		
		float x = xCenter - radius;
		float y = yCenter - radius;
		float diameter = 2 * radius;
		
		return new Ellipse2D.Float(x, y, diameter, diameter);
	}
	
	//@Override
	public void updateValue(double heading){
		//angleRadians = headingToMathAngle(heading)*PI/180.0;
		angleRadians = headingToWhatThisClassWants(heading)*PI/180.0;
		//current = trackPoint;
		//CompassExtension extension = (CompassExtension) extensions.get(CompassExtension.KEY);
		//angleRadians = extension.getAngleRadians();
	}
	
	private double headingToWhatThisClassWants(double heading)
	{
		//transform from compass degrees to whatever this class uses
		heading *= -1;
		heading += 270;
		return heading;
	}
	
	private void makeShadow() {
		float r = radius / 6.0f;
		shadow = getCircle(xOffset, yOffset, r);
		
		gradient = new RadialGradientPaint(
				xOffset, yOffset, r,
			new float[]{
				0.5f,
				1.0f
			},
			new Color[]{
				new Color(0.0f, 0.0f, 0.0f, 1.0f),
				new Color(0.0f, 0.0f, 0.0f, 0.0f)
			},
			MultipleGradientPaint.CycleMethod.NO_CYCLE
		);
	}
	
	private void drawDot(Graphics2D graphics) {
		graphics.setColor(new Color(0.41f, 0.57f, 0.69f, 1));  //originally alpha channel (transparancy was 0.7f, is now 1)
		graphics.fill(dot);
		
		graphics.setColor(new Color(0.0f, 0.17f, 0.29f, 0.8f));
		//graphics.setStroke(new BasicStroke(2.0f));
		graphics.draw(dot);
	}
	
	private void drawCompass(Graphics2D graphics) {
		graphics.setColor(new Color(1.0f, 1.0f, 1.0f, 0.7f));
		graphics.setStroke(new BasicStroke(2.0f));
		graphics.draw(circle);
		
		graphics.setFont(font);
		FontMetrics metrics = graphics.getFontMetrics();
		
		String[] letters = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
		
		double angle = 0.0;
		double step = Math.PI / 4;
		
		graphics.setColor(new Color(1.0f, 1.0f, 1.0f, 0.9f));
		for (int i = 0; i < letters.length; i++) {
			
			String text = "" + letters[i];
			
			int textWidth = metrics.stringWidth(text);
			int textHeight = metrics.getMaxAscent();
			
			int x = xOffset - textWidth / 2;
			int y = yOffset + textHeight / 2;
			
			int padding = 5;
			
			/* This was changed from the orginal code, because it does not follow the convention 
			 * of restoring the original transform when applying new transformations. By not
			 * restoring, the scaling transform in VideoFeed gets messed up. 
			 */
			/***** REMOVED: *****/
			//AffineTransform transform = getTransform();
			//transform.rotate(angle, xOffset, yOffset);
			//transform.translate(0, -radius + textHeight / 2 + padding);
			//graphics.setTransform(transform);
			//graphics.drawString(text, x, y);
			//angle += step;
			/***** ADDED: *****/
			AffineTransform saveXform = graphics.getTransform(); // Save current transform so it can be restored afterward
			AffineTransform newXform = getTransform();
			newXform.rotate(angle, xOffset, yOffset);
			newXform.translate(0, -radius + textHeight / 2 + padding);
			graphics.transform(newXform); // Apply scaling transform
			graphics.drawString(text, x, y);
			angle += step;
			graphics.setTransform(saveXform); // Restore initial transform
			/***** END *****/
		}
	} // End drawCompass
}
