
import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.sin;

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
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;


/**
 * Copyright (c) Bartosz Zaczynski, 2010
 * http://syntaxcandy.blogspot.com
 */
public class SpeedGauge { //implements Gauge {
	private static final Logger LOGGER = Logger.getLogger(AeroGUI.class.getName());
	
	private static final float DEG_2_RAD = (float) (PI / 180.0f);
	private static final float STEP_DEGREES = 240.0f / 10.0f;
	private static final float ARROW_MAX_HEIGHT = 15.0f;
	private static final float ARROW_MIN_HEIGHT = 3.0f;
	
	
	private double kmh = 0;
	private double km = 0;
	
	private int xCenter;
	private int yCenter;
	private int radius;
	private int arrowPadding;
	private int maxVal;
	private String unit;

	
	private Font cleanFont;
	private Font lcdFont;
	
	private Shape background;
	private Shape pivot;
	private GeneralPath bigRuler;
	private GeneralPath smallRuler;
	private RoundRectangle2D distanceBox;
	private RoundRectangle2D speedBox;
	private Shape shadow;
	private Paint gradient;
	
	private Point _p1, _p2, _p3, _p4;
	private Point center;
	
	public SpeedGauge(int xCenter, int yCenter, int radius, int maxVal, String unit) throws FontFormatException, IOException {
		
		this.xCenter = xCenter;
		this.yCenter = yCenter;
		this.radius = radius;
		this.maxVal = maxVal;
		this.unit = unit;
		
		cleanFont = new Font("TimesRoman", Font.BOLD, 12);
		lcdFont = new Font("Arial Black", Font.BOLD, 12);
		//FontLoader loader = FontLoader.getInstance();
		//cleanFont = loader.getFont("LondonBetween.ttf", radius / 8.0f);
		//lcdFont = loader.getFont("digital-7.ttf", radius / 8.0f);
		
		background = getCircle(xCenter, yCenter, 0.85f * radius);
		pivot = getCircle(xCenter, yCenter, radius / 10.0f);
		
		makeRuler();
		makeDistanceBox();
		makeSpeedBox();
		makeShadow();
		makePoints();
	}
	
	//@Override
	public void draw(Graphics2D graphics) {
		drawBackground(graphics);
		//drawRuler(graphics);
		drawDigits(graphics);
		//drawDistance(graphics);
		drawSpeed(graphics);
		drawArrow(graphics);
	}
	
	//@Override
	public void updateValue(double vel){ //,double dist){   //TrackPoint trackPoint, Map<String, Extension> extensions) {
		kmh = vel; //3.6 * trackPoint.velocityMetersPerSecond;
		//km = dist;  //trackPoint.distanceMeters / 1000.0;
	}
	
	private Shape getCircle(float xCenter, float yCenter, float radius) {
		
		float x = xCenter - radius;
		float y = yCenter - radius;
		float diameter = 2 * radius;
		
		return new Ellipse2D.Float(x, y, diameter, diameter);
	}
	
	private void makeRuler() {
		
		float smallRadius = 0.9f * radius;
		arrowPadding = (int) (radius - smallRadius) / 2;
		
		Shape external = getCircle(xCenter, yCenter, radius);
		Shape internal = getCircle(xCenter, yCenter, smallRadius);
		
		bigRuler = new GeneralPath();
		bigRuler.append(external, false);
		bigRuler.append(internal, false);
		
		for (float degree = 150.0f; degree <= 390.0f; degree += STEP_DEGREES) {
			
			float radians = DEG_2_RAD * degree;
			
			int x1 = (int) (radius * cos(radians)) + xCenter;
			int y1 = (int) (radius * sin(radians)) + yCenter;
			
			int x2 = (int) ((smallRadius - 0.025 * radius) * cos(radians)) + xCenter;
			int y2 = (int) ((smallRadius - 0.025 * radius) * sin(radians)) + yCenter;
			
			bigRuler.moveTo(x1, y1);
			bigRuler.lineTo(x2, y2);
		}
		
		smallRuler = new GeneralPath();
		for (float degree = 150.0f; degree < 390.0f; degree += STEP_DEGREES) {
			
			float radians = DEG_2_RAD * (degree + 12.0f);
			int x1 = (int) (radius * cos(radians)) + xCenter;
			int y1 = (int) (radius * sin(radians)) + yCenter;
			
			float coeff = 0.05f;
			
			int x2 = (int) ((smallRadius + coeff * radius) * cos(radians)) + xCenter;
			int y2 = (int) ((smallRadius + coeff * radius) * sin(radians)) + yCenter;
			
			smallRuler.moveTo(x1, y1);
			smallRuler.lineTo(x2, y2);
		}
	}
	
	private void makeDistanceBox() {
		
		int width = (int) (0.9 * radius);
		int height = (int) (0.15 * radius);
		
		distanceBox = new RoundRectangle2D.Float(
			xCenter - width / 2, yCenter - 0.35f * radius,
			width, height,
			10, 10
		);
	}
	
	private void makeSpeedBox() {
		
		int width = (int) (0.9 * radius);
		int height = (int) (0.3 * radius);
		
		speedBox = new RoundRectangle2D.Float(
			xCenter - width / 2, yCenter + height / 2 + 0.075f * radius,
			width, height,
			20, 120
		);
	}
	
	private void makeShadow() {
		
		float r = radius / 6.0f;
		shadow = getCircle(xCenter, yCenter, r);
		
		gradient = new RadialGradientPaint(
			xCenter, yCenter, r,
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
	
	private void makePoints() {
		
		_p1 = new Point(0, -ARROW_MAX_HEIGHT / 2.0f);
		_p2 = new Point(0, ARROW_MAX_HEIGHT / 2.0f);
		_p3 = new Point(-radius*0.9 + arrowPadding, ARROW_MIN_HEIGHT / 2.0f);  //added 0.9 scaling to reduce arrow length
		_p4 = new Point(-radius*0.9 + arrowPadding, -ARROW_MIN_HEIGHT / 2.0f);  //same ^
		
		center = new Point(xCenter, yCenter);
	}
	
	private void drawBackground(Graphics2D graphics) {
		graphics.setStroke(new BasicStroke(1.0f));
		graphics.setColor(new Color(0.7f, 0.0f, 0.0f, 0.6f));
		graphics.fill(background);
	}
	
	private void drawRuler(Graphics2D graphics) {
		
		graphics.setColor(new Color(1.0f, 1.0f, 1.0f, 0.5f));
		
		graphics.setStroke(new BasicStroke(2.5f));
		//graphics.draw(bigRuler);
		
		graphics.setStroke(new BasicStroke(1.5f));
		//graphics.draw(smallRuler);
	}
	
	private void drawDigits(Graphics2D graphics) {
		
		graphics.setFont(cleanFont);
		graphics.setColor(Color.WHITE);
		
		int numMarkers = 5;
		for (int i = 0; i < numMarkers; i++) {
			
			int totalDegreeSpan = 240;  //based on original implementation
			float degreeStep = 1.0f*totalDegreeSpan/(numMarkers-1);
			float degrees = 150.0f + i * degreeStep;
			float radians = DEG_2_RAD * degrees;
			
			float r = 0.65f * radius;  //changed from original 0.75f
			float x = (float) (r * cos(radians)) + xCenter;
			float y = (float) (r * sin(radians)) + yCenter;
			
			int value = i * maxVal/(numMarkers-1); //10;
			String text = "" + value;
			
			FontMetrics metrics = graphics.getFontMetrics();
			Rectangle2D bounds = metrics.getStringBounds(text, graphics);
			
			x -= bounds.getWidth() / 2;
			y -= bounds.getHeight() / 2;
			
			graphics.drawString("" + value, x, y + radius / 10.0f);
		}
	}
	
	private void drawDistance(Graphics2D graphics) {
		
		graphics.setStroke(new BasicStroke(1.5f));
		
		graphics.setColor(new Color(0.0f, 0.0f, 0.0f, 0.6f));
		graphics.fill(distanceBox);
		
		graphics.setColor(new Color(0.0f, 0.0f, 0.0f, 0.7f));
		graphics.draw(distanceBox);
		
		String text = getDistanceAsText();
		graphics.setFont(lcdFont);
		
		FontMetrics metrics = graphics.getFontMetrics();
		
		int padding = 5;
		int x = (int) (
			distanceBox.getWidth() - metrics.stringWidth(text)
			+ distanceBox.getX()
			- padding
		);
		
		int y = (int) (
			(distanceBox.getHeight() - metrics.getMaxAscent()) / 2
			+ distanceBox.getY()
			+ metrics.getAscent()
		);
		
		graphics.setColor(new Color(1.0f, 1.0f, 1.0f, 0.8f));
		graphics.drawString(text, x, y);
	}
	
	private void drawSpeed(Graphics2D graphics) {
		
		graphics.setStroke(new BasicStroke(1.5f));
		
		graphics.setColor(new Color(0.0f, 0.0f, 0.0f, 0.6f));
		graphics.fill(speedBox);
		
		graphics.setColor(new Color(0.0f, 0.0f, 0.0f, 0.7f));
		graphics.draw(speedBox);
		
		String text = getSpeedAsText();
		graphics.setFont(cleanFont.deriveFont(radius / 4.5f));
		
		FontMetrics metrics = graphics.getFontMetrics();
		
		int x = (int) (
			(speedBox.getWidth() - metrics.stringWidth(text)) / 2
			+ speedBox.getX()
		);
		
		int y = (int) (
			(speedBox.getHeight() - metrics.getMaxAscent()) / 2
			+ speedBox.getY()
			+ metrics.getAscent()
		);
		
		graphics.setColor(new Color(1.0f, 1.0f, 1.0f, 0.8f));
		graphics.drawString(text, x, y);
	}
	
	private void drawArrow(Graphics2D graphics) {
		
		// shadow
		graphics.setPaint(gradient);
		graphics.setStroke(new BasicStroke(1.0f));
		graphics.fill(shadow);
		
		// arrow
		//double degrees = (kmh * -2.4) + 30;
		double degrees = -240*kmh/maxVal+30;  //CCW = positive, pointing left = neutral position
		double radians = degrees * PI / 180.0;
		
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
	
	private String getDistanceAsText() {
		return String.format(Locale.US, "%06.1f km", km);
	}
	
	private String getSpeedAsText() {
		return String.format(Locale.US, "%3.0f " + unit, kmh);
	}
}