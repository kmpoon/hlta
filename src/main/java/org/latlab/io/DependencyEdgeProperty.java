package org.latlab.io;

import java.awt.Point;
import java.util.ArrayList;

/**
 * Property of an edge that can possibly be loaded from/saved to
 * a file.  If a particular property is not available, the getter
 * method returns null.
 * @author LIU Tengfei
 *
 */


public class DependencyEdgeProperty {
	
	// to store points
	private ArrayList<Point> _list = new ArrayList<Point>();
	
	//add all points in array to list
	public void setPoints(Point[] points)
	{
		for(int i=0; i<points.length; i++)
		{
			_list.add(points[i]);
		}
	}
	
	//add one point to list
	public void addPoint(Point point)
	{
		_list.add(point);
	}
	
	//return the list
	public ArrayList<Point> getPoints()
	{
		return _list;
	}
	
	//return the number of elements in list
	public int getNumberOfPoints()
	{
		return _list.size();
	}
	

}
