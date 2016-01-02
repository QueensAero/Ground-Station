
/**
 * Copyright (c) Bartosz Zaczynski, 2010
 * http://syntaxcandy.blogspot.com
 */
public class Point {
	
	public double x;
	public double y;
	
	public static double getDistance(Point a, Point b) {
		
		double dx = b.x - a.x;
		double dy = b.y - a.y;
		
		return Math.sqrt(dx*dx + dy*dy);
	}
	
	public Point(double x, double y) {
		this.x = x;
		this.y = y;
	}
	
	public Point rotate(double radians) {
		return new Point(
			x * Math.cos(radians) - y * Math.sin(radians),
			x * Math.sin(radians) + y * Math.cos(radians)
		);
	}
	
	public Point toScreen(Point center) {
		return new Point(center.x + x, center.y - y);
	}
	
	public String toString() {
		return "(" + x + ", " + y + ")";
	}
}