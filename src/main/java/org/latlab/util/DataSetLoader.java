package org.latlab.util;

import hk.ust.cse.lantern.data.Data;
import hk.ust.cse.lantern.data.io.FormatCatalog;
import hk.ust.cse.lantern.data.io.FormatCatalog.Format;
import hk.ust.cse.lantern.data.io.Manager;
import hk.ust.cse.lantern.data.io.Reader;
import hk.ust.cse.lantern.data.io.Writer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;

import org.latlab.util.DataSet;

/**
 * A helper class for loading data with different formats into {@code DataSet}.
 * It first converts the data to Hlcm format in memory if necessary, and then
 * uses the constructor of {@code DataSet} to read the data.
 * 
 * @author leonard
 * 
 */
public class DataSetLoader {
	/**
	 * Loads a data set from a file with the given name.
	 * 
	 * @param filename
	 *            name of file to load from
	 * @return a data set loaded from the given file
	 * @throws Exception
	 */
	public static DataSet load(String filename) throws Exception {
		try {
			return new DataSet(convert(filename));
		} catch (Exception e) {
			return new DataSet(filename);
		}
	}

	/**
	 * Loads a data set from a given input stream with the specified format.
	 * 
	 * @param input
	 *            input stream holding the data
	 * @param format
	 *            format of the data
	 * @return data set loaded from the given input stream
	 * @throws Exception
	 */
	public static DataSet load(InputStream input, Format format)
			throws Exception {
		return new DataSet(convert(input, format));
	}

	/**
	 * Converts a file with the given name into a input stream from which
	 * {@code DataSet} can read. It determines the format of the data using the
	 * extension of the file.
	 * 
	 * @param filename
	 *            name of the data file.
	 * @return an input stream from which the {@code DataSet} can read
	 * @throws Exception
	 */
	public static InputStream convert(String filename) throws Exception {
		Format format = FormatCatalog.getInputFormat(filename);
		
		if(format == FormatCatalog.HLCM)
		{
			return (new FileInputStream(filename));
		}
		
		return convert(new FileInputStream(filename), format);
	}

	/**
	 * Converts a data in a given input stream in the specified format into a
	 * input stream from which {@code DataSet} can read.
	 * 
	 * @param input
	 *            holds the data
	 * @param format
	 *            format of the data
	 * @return an input stream from which the {@code DataSet} can read
	 * @throws Exception
	 */
	public static InputStream convert(InputStream input, Format format)
			throws Exception {
		Reader reader = Manager.createReader(input, format);
		Data data = reader.read();

		ByteArrayOutputStream os = new ByteArrayOutputStream();
		Writer writer = Manager.createWriter(os, FormatCatalog.HLCM);
		writer.write(data);

		return new ByteArrayInputStream(os.toByteArray());
	}
}
