package org.latlab.io;

import java.awt.Color;
import java.awt.Point;

/**
 * Property of a belief node that can possibly be loaded from/saved to
 * a file.  If a particular property is not available, the getter
 * method returns null.
 * @author leonard
 *
 */
public class BeliefNodeProperty {
	
	public enum FrameType { NONE, OVAL, RECTANGLE }; 

    /**
     * Defines the types of connection constraints
     * @author leonard
     *
     */
	public enum ConnectionConstraint { 
		/**
		 * Default constraint, which depends on the type of this fig.
		 */
		Default, 
		/**
		 * Connects to the nearest point.
		 */
		Free, 
		/**
		 * Connects to the nearest midpoint.
		 */
		Middle,
		/**
		 * Connects to the nearest top or bottom midpoint,
		 */
		Vertical,
		/**
		 * Connects to the nearest left or right midpoint,
		 */
		Horizontal,
		/**
		 * Connects to the top.
		 */
		Top, 
		/**
		 * Connects to the right.
		 */
		Right,
		/**
		 * Connects to the left.
		 */
		Left, 
		/**
		 * Connects to the bottom.
		 */
		Bottom 
	};
	
	public Point getPosition() {
		return point;
	}
	
	public void setPosition(Point point) {
		this.point = point;
	}
	
	public int getRotation() {
		return rotation;
	}
	
	public void setRotation(int angle) {
		this.rotation = angle;
	}

	public FrameType getFrame() {
		return frame;
	}

	public void setFrame(FrameType frame) {
		this.frame = frame;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}
	
    public Color getBackColor() {
        return backColor;
    }

    public void setBackColor(Color backColor) {
        this.backColor = backColor;
    }

    public String getFontName() {
        return fontName;
    }

    public void setFontName(String fontName) {
        this.fontName = fontName;
    }

    public Color getForeColor() {
        return foreColor;
    }

    public void setForeColor(Color foreColor) {
        this.foreColor = foreColor;
    }
    
    public Color getLineColor() {
        return lineColor;
    }

    public void setLineColor(Color lineColor) {
        this.lineColor = lineColor;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }
    
    public ConnectionConstraint getConnectionConstraint() {
    	return connectionConstraint;
    }
    
    public void setConnectionConstraint(ConnectionConstraint constraint) {
    	connectionConstraint = constraint;
    }
   
	private Point point = null;
	private int rotation = DEFAULT_ROTATION;
	private String label = null;
	private FrameType frame = DEFAULT_FRAME_TYPE;
	private Color foreColor = null;
	private Color backColor = null;
    private Color lineColor = null;
	private String fontName = null;
    private int fontSize = -1;
    private ConnectionConstraint connectionConstraint = null;
    
    public final static FrameType DEFAULT_FRAME_TYPE = FrameType.OVAL;
    public final static int DEFAULT_ROTATION = 0;
}
