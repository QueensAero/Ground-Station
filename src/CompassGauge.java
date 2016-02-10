//package syntaxcandy.gauge;

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
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.io.IOException;
import java.util.Date;
import java.util.Map;

/*
import syntaxcandy.gps.ext.CompassExtension;
import syntaxcandy.gps.ext.Extension;
import syntaxcandy.gps.track.Track;
import syntaxcandy.gps.track.TrackPoint;
import syntaxcandy.util.Earth;
import syntaxcandy.util.FontLoader;
import syntaxcandy.util.MercatorProjection;
import syntaxcandy.util.Point;
*/

/**
 * Copyright (c) Bartosz Zaczynski, 2010
 * http://syntaxcandy.blogspot.com
 */

/*
 * 
 * public class TrackPoint {
	
	public Date utcTime;
	public double latitudeDegrees;
	public double longitudeDegrees;
	public double altitudeMeters;
	public double elapsedSeconds;
	public double distanceMeters;
	public double velocityMetersPerSecond;
}



public class MercatorProjection {
	
	private static final double QUARTER = PI / 4.0;
	
	private double scaleFactor;
	
	public MercatorProjection(double scaleFactor) {
		this.scaleFactor = scaleFactor;
	}
	
	public Point compute(Point point) {
		return compute(point.x, point.y);
	}
	
	public Point compute(double longitude, double latitude) {
		
		double x = scaleFactor * longitude;
		double y = scaleFactor * toDegrees(log(tan(QUARTER + toRadians(latitude) / 2.0)));
		
		return new Point(x, y);
	}
}



 */


public class CompassGauge { //implements Gauge {
	
	private static final int MAP_SIZE_METERS = 100;
	
	//private MercatorProjection projection;
	//private TrackPoint current;
	
	//replace trackpoint
	public Date utcTime;
	public double latitudeDegrees;
	public double longitudeDegrees;
	public double altitudeMeters;
	public double elapsedSeconds;
	public double distanceMeters;
	public double velocityMetersPerSecond;
	
	
	
	
	
	private double angleRadians;
	
	private double minLongitude;
	private double maxLatitude;
	
	private int radius;
	private int xOffset;
	private int yOffset;
	
	private GeneralPath path;
	private Shape circle;
	private Shape dot;
	
	private Font font;
	
	//public CompassGauge(Track track, int mapSizePixels, int x, int y) throws FontFormatException, IOException {
	public CompassGauge(int mapSizePixels, int x, int y) throws FontFormatException, IOException {
	
		radius = mapSizePixels / 2;
		xOffset = x + radius;
		yOffset = y + radius;
		
		//path = getTrackPath(track, mapSizePixels);
		circle = new Ellipse2D.Float(x, y, mapSizePixels, mapSizePixels);
		
		int radius = 12;
		dot = new Ellipse2D.Double(
			xOffset - radius / 2,
			yOffset - radius / 2,
			radius, radius
		);
		
		//FontLoader loader = FontLoader.getInstance();
		//font = loader.getFont("LondonBetween.ttf", mapSizePixels / 15.0f);
		
		font = new Font("TimesRoman", Font.BOLD, 12);
	}
	
	//@Override
	public void draw(Graphics2D graphics) {
		
		//drawTrack(graphics);
		drawDot(graphics);
		drawCompass(graphics);
		
		graphics.setTransform(new AffineTransform());
	}
	
	private AffineTransform getTransform() {
		AffineTransform transform = new AffineTransform();
		transform.rotate(angleRadians, xOffset, yOffset);
		return transform;
	}
	
	/*
	@Override
	public void updateValue(TrackPoint trackPoint, Map<String, Extension> extensions) {
		
		current = trackPoint;
		
		CompassExtension extension = (CompassExtension) extensions.get(CompassExtension.KEY);
		angleRadians = extension.getAngleRadians();
	}
	
	public MercatorProjection getProjection() {
		return projection;
	}
	
	private GeneralPath getTrackPath(Track track, int mapSizePixels) {
		
		double mapSizeDegrees = MAP_SIZE_METERS / Earth.METERS_PER_DEGREE;
		double scaleFactor = mapSizePixels / mapSizeDegrees;
		projection = new MercatorProjection(scaleFactor);
		
		findMinMax(track);
		
		GeneralPath path = new GeneralPath();
		
		int numPoints = track.getNumPoints();
		for (int i = 0; i < numPoints; i++) {
			
			TrackPoint trackPoint = track.getPoint(i);
			Point point = toScreen(trackPoint);
			
			if (i == 0) {
				path.moveTo(point.x, point.y);
			} else {
				path.lineTo(point.x, point.y);
			}
		}
		
		return path;
	}
	
	private void findMinMax(Track track) {
		
		minLongitude = 180;
		maxLatitude = 0;
		
		int numPoints = track.getNumPoints();
		for (int i = 0; i < numPoints; i++) {
			
			TrackPoint trackPoint = track.getPoint(i);
			double lon = trackPoint.longitudeDegrees;
			double lat = trackPoint.latitudeDegrees;
			
			if (lon < minLongitude) minLongitude = lon;
			if (lat > maxLatitude) maxLatitude = lat;
		}
	}
	
	private Point toScreen(TrackPoint trackPoint) {
		
		double lon = trackPoint.longitudeDegrees - minLongitude;
		double lat = maxLatitude - trackPoint.latitudeDegrees;
		
		Point point = projection.compute(new Point(lon, lat));
		return new Point(point.x, point.y);
	}
	
	
	
	
	private void drawTrack(Graphics2D graphics) {
		
		Point center = toScreen(current);
		
		AffineTransform transform = getTransform();
		transform.translate(xOffset - center.x, yOffset - center.y);
		
		graphics.setClip(circle);
		graphics.setTransform(transform);
		
		graphics.setColor(new Color(1.0f, 1.0f, 1.0f, 0.8f));
		graphics.setStroke(new BasicStroke(4.0f));
		graphics.draw(path);
		
		graphics.setClip(null);
		graphics.setTransform(getTransform());
	}  */
	
	private void drawDot(Graphics2D graphics) {
		
		graphics.setColor(new Color(0.41f, 0.57f, 0.69f, 0.7f));
		graphics.fill(dot);
		
		graphics.setColor(new Color(0.0f, 0.17f, 0.29f, 0.8f));
		graphics.setStroke(new BasicStroke(2.0f));
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
			
			AffineTransform transform = getTransform();
			transform.rotate(angle, xOffset, yOffset);
			transform.translate(0, -radius + textHeight / 2 + padding);
			
			graphics.setTransform(transform);
			graphics.drawString(text, x, y);
			
			angle += step;
		}
	}
}