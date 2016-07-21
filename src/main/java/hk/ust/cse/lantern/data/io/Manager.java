package hk.ust.cse.lantern.data.io;

import hk.ust.cse.lantern.data.io.FormatCatalog.Format;
import hk.ust.cse.lantern.data.io.arff.ArffReader;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Provides methods for creating readers and writers of different formats.
 * 
 * @author leonard
 * 
 */
public class Manager {
	/**
	 * Creates a reader on the given {@code input} with given {@code format}.
	 * 
	 * @param input
	 *            input stream
	 * @param format
	 *            format of the input
	 * @return a reader for reading from the given {@code input} with given
	 *         {@code format}
	 * @throws IOException
	 *             when IO error occurs
	 * @throws UnsupportedOperationException
	 *             when the given format is not supported
	 */
	public static Reader createReader(InputStream input, Format format)
			throws IOException {
		if (format == FormatCatalog.ARFF) {
			return new ArffReader(input);
		} else if (format == FormatCatalog.ARFF_GZ) {
			return new ArffReader(input, true);
		} else if (format == FormatCatalog.CSV) {
			CsvReader csvReader = new CsvReader(input, ',', "?");
			csvReader.setTrimTrailingSpace(true);
			return csvReader;
		}else {
			throw new UnsupportedOperationException(
					"Unsupported input format is used.");
		}
	}

	/**
	 * Creates a reader on the file specified by the given {@code filename}. The
	 * format is specified by its extension.
	 * 
	 * @param filename
	 *            name of the file to read from
	 * @return a reader
	 * @throws IOException
	 *             when IO error occurs
	 */
	public static Reader createReader(String filename)
			throws FileNotFoundException, IOException {
		return createReader(new FileInputStream(filename), FormatCatalog
				.getInputFormat(filename));
	}

	/**
	 * Creates a reader on the given {@code output} with given {@code format}.
	 * 
	 * @param output
	 *            output stream
	 * @param format
	 *            format of the output
	 * @return a writer for writing to the given {@code output} with given
	 *         {@code format}
	 * @throws IOException
	 *             when IO error occurs
	 * @throws UnsupportedOperationException
	 *             when the given format is not supported
	 */
	public static Writer createWriter(OutputStream output, Format format)
			throws IOException {
		if (format == FormatCatalog.HLCM) {
			return new HlcmWriter(output, "converted_data");
		} else if (format == FormatCatalog.ARFF) {
			return new ArffWriter(output, "converted_data");
		} else
			throw new UnsupportedOperationException(
					"Unsupported output format is used.");

	}

	/**
	 * Creates a writer on the file specified by the given {@code filename}. The
	 * format is specified by its extension.
	 * 
	 * @param filename
	 *            name of the file to write to
	 * @return a writer
	 * @throws IOException
	 *             when IO error occurs
	 */
	public static Writer createWriter(String filename) throws IOException {
		return createWriter(new FileOutputStream(filename), FormatCatalog
				.getOutputFormat(filename));
	}
}
