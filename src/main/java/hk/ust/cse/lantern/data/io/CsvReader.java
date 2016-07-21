package hk.ust.cse.lantern.data.io;

import hk.ust.cse.lantern.data.Data;
import hk.ust.cse.lantern.data.Instance;
import hk.ust.cse.lantern.data.InstanceCollection;
import hk.ust.cse.lantern.data.NominalVariable;
import hk.ust.cse.lantern.data.VariableCollection;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 * Reads data from CSV file
 * 
 * @author leonard
 * 
 */
public class CsvReader extends BaseReader {
	protected char delimiter;
	protected String splitString;
	protected String missingText = "?";
	protected BufferedReader reader;

	private boolean shouldTrimSpaces = false;

	public CsvReader(InputStream input, char delimiter, String missingText)
			throws IOException {
		this(getReader(input), delimiter, missingText);
	}

	public CsvReader(Reader reader, char delimiter, String missingText) {
		this.reader = new BufferedReader(reader);
		this.delimiter = delimiter;
		this.splitString = "" + delimiter;
		this.missingText = missingText;
	}

	public CsvReader(String filename, char delimiter) throws IOException {
		this(filename, delimiter, "?");
	}

	public CsvReader(String filename, char delimiter, String missingText)
			throws IOException {
		this(new FileInputStream(filename), delimiter, missingText);
	}

	protected void convertMissingValues(List<List<String>> lines) {
		boolean firstLine = true;
		for (List<String> line : lines) {
			if (firstLine) {
				firstLine = false;
				continue;
			}

			for (int i = 0; i < line.size(); i++) {
				String item = line.get(i);
				if (item.equals(missingText) || item.length() == 0)
					line.set(i, NominalVariable.missingText);
			}
		}
	}

	/**
	 * Gets the possible values for a variable at the specified column. The
	 * return list is sorted in alphabetically order.
	 * 
	 * @param lines
	 *            lines in the input
	 * @param column
	 *            the column number
	 * @return a sorted list of values
	 * @throws ParseException
	 */
	protected List<String> getPossibleValues(List<List<String>> lines,
			int column) throws ParseException {
		TreeSet<String> values = new TreeSet<String>();

		// skip the first line which should be the header line
		for (int i = 1; i < lines.size(); i++) {
			try {
				List<String> line = lines.get(i);

				String value = line.get(column);
				if (value != NominalVariable.missingText)
					values.add(line.get(column));
			} catch (Exception e) {
				throw new ParseException(
						"Error during enumerating possible values at line: "
								+ i, e);
			}
		}

		List<String> result = new ArrayList<String>(values);
		if (IntegerText.isIntegerList(result))
			IntegerText.sort(result);

		return result;
	}

	protected List<String> getTokens(String s) {
		return Arrays.asList(s.split(splitString));
//		int count = 0;
//		for (int i = 0; i < s.length(); i++) {
//			if (s.charAt(i) == delimiter)
//				count++;
//		}
//
//		List<String> result = new ArrayList<String>(count + 1);
//
//		int start = 0;
//		int end = 0;
//
//		while (start < s.length()) {
//			end = s.indexOf(delimiter, start);
//			if (end < 0) {
//				end = s.length();
//			}
//
//			result.add(s.substring(start, end));
//
//			start = end + 1;
//		}
//
//		// if there is a trailing ',', add an empty string
//		if (s.charAt(s.length() - 1) == delimiter) {
//			result.add("");
//		}
//
//		return result;
	}

	/**
	 * Parses each row from the input stream. Returns null if it has reached the
	 * end.
	 * 
	 * @return instance represented by the row, or null if end-of-file
	 */
	protected Instance parseInstance(List<String> line,
			VariableCollection variables) {
		return new Instance(variables, line);
	}

	protected InstanceCollection parseInstances(List<List<String>> lines,
			VariableCollection variables) {
		InstanceCollection result = new InstanceCollection();

		boolean firstLine = true;
		for (List<String> line : lines) {
			if (firstLine)
				firstLine = false;
			else
				result.add(parseInstance(line, variables));
		}

		return result;
	}

	/**
	 * Parses the variables in the file.
	 * <p>
	 * It assumes the first line be the header line, which consists of the names
	 * of the variables. It reads all the records to enumerate the states of the
	 * variables.
	 * 
	 * @param lines
	 *            lines in this file
	 * @return the collection of variables
	 * @throws ParseException
	 */
	protected VariableCollection parseVariables(List<List<String>> lines)
			throws ParseException {
		List<String> heading = lines.get(0);
		VariableCollection result = new VariableCollection(heading.size());

		for (int column = 0; column < heading.size(); column++) {
			try {
				List<String> values = getPossibleValues(lines, column);

				boolean isIntegers = IntegerText.isIntegerList(values);
				NominalVariable variable = new NominalVariable(heading
						.get(column), values, isIntegers);
				result.add(variable);
			} catch (Exception e) {
				throw new ParseException(
						"Error during parsing variables at column: " + column,
						e);
			}
		}
		return result;
	}

	public Data read() throws IOException, ParseException {
		List<List<String>> lines = readAllLines();

		if (shouldTrimSpaces)
			trimSpaces(lines);

		convertMissingValues(lines);

		VariableCollection variables = parseVariables(lines);
		return new Data(variables, parseInstances(lines, variables));
	}

	protected List<List<String>> readAllLines() throws IOException {
		List<List<String>> result = new ArrayList<List<String>>();

		// read the first line as heading
		String line = reader.readLine();
		while (line != null) {
			result.add(getTokens(line));
			line = reader.readLine();
		}

		reader.close();

		return result;
	}

	/**
	 * Sets whether to trim the trailing spaces for the values
	 * 
	 * @param value
	 *            true if to trim, false otherwise
	 */
	public void setTrimTrailingSpace(boolean value) {
		shouldTrimSpaces = value;
	}

	protected void trimSpaces(List<List<String>> lines) {
		for (List<String> line : lines) {
			for (int i = 0; i < line.size(); i++) {
				line.set(i, line.get(i).trim());
			}
		}
	}
}
