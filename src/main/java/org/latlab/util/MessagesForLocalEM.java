package org.latlab.util;

public class MessagesForLocalEM
{
	private String _head;
	private String _tail;
	private Function _func;
	private Double _normalization;
	
	public MessagesForLocalEM(String head, String tail, Function func, Double normalization)
	{
		_head = head;
		_tail = tail;
		_func = func;
		_normalization = normalization;
	}
	
	public String getHead()
	{
		return _head;
	}
	
	public String getTail()
	{
		return _tail;
	}
	
	public Function getFunction()
	{
		return _func;
	}
	
	public Double getNormalization()
	{
		return _normalization;
	}
	
	
}