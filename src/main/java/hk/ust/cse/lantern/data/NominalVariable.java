package hk.ust.cse.lantern.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

import hk.ust.cse.lantern.data.io.IntegerText;

/**
 * 
 * @author leonard
 * 
 * Represents a variable
 */
public class NominalVariable extends Variable {

	public static final int missingNumber = -1;
	public static final String missingText = "?";

	/**
	 * a map from the numeric value of a variable to its textual value
	 */
	private TreeMap<Integer, String> numberToTextMap = new TreeMap<Integer, String>();

	/**
	 * a map from the textual value of a variable to its numeric value
	 */
	private TreeMap<String, Integer> textToNumberMap;

	/**
	 * Constructor
	 * 
	 * @param name
	 *            name of the variable
	 * @param integerValues
	 *            whether this variable has integer textual values
	 */
	protected NominalVariable(String name, boolean integerValues) {
		super(name);

		if (integerValues)
			textToNumberMap = new TreeMap<String, Integer>(
					new IntegerText.TextComparator());
		else
			textToNumberMap = new TreeMap<String, Integer>();
	}

	public NominalVariable(String name, Iterable<String> textualValues,
			boolean integerValues) {
		this(name, integerValues);

		for (String textualValue : textualValues)
			addValue(textualValue);
	}

	public NominalVariable(String name, String[] textualValues,
			boolean integerValues) {
		this(name, Arrays.asList(textualValues), integerValues);
	}

	/**
	 * Adds a value to the domain of the variable
	 * 
	 * @param textualValue
	 *            textual value
	 * @param numericValue
	 *            numeric value
	 */
	protected void addValue(String textualValue) {
		if (textualValue == missingText)
			return;

		int numericValue = textToNumberMap.size();
		textToNumberMap.put(textualValue, numericValue);
		numberToTextMap.put(numericValue, textualValue);
	}

	/**
	 * Gets the numeric value for the specified textual value
	 * 
	 * @param textualValue
	 *            textual value of interest
	 * @return the numeric value
	 */
	@Override
	public int getNumber(String textualValue) {
		return (textualValue == missingText || textualValue == null) ? missingNumber
				: textToNumberMap.get(textualValue);
	}

	/**
	 * Gets the textual value for the specified numeric value
	 * 
	 * @param numericValue
	 *            numeric value of interest
	 * @return the textual value
	 */
	@Override
	public String getText(int numericValue) {
		return (numericValue == missingNumber) ? missingText : numberToTextMap
				.get(numericValue);
	}

	/**
	 * Gets the number of possible values for this variable
	 * 
	 * @return number of possible values
	 */
	public int numberOfValues() {
		return numberToTextMap.size();
	}

	/**
	 * Gets the set of textual values contained in this variable. The order is
	 * in ascending order of the values.
	 * 
	 * @return textual domain of this variable
	 */
	public List<String> textualDomain() {
		List<String> result = new ArrayList<String>(numberToTextMap.size());
		for (Integer value : numberToTextMap.keySet()) {
			result.add(numberToTextMap.get(value));
		}
		return result;
	}

	/**
	 * Lists the states and returns this list in string.
	 * 
	 * @return string of the list of states
	 */
	public String getNominalSpecification() {
		StringBuilder builder = new StringBuilder();

		for (String string : textualDomain()) {
			builder.append(string).append(",");
		}

		// delete the last comma
		builder.deleteCharAt(builder.length() - 1);

		return builder.toString();
	}

	public String toString() {
		return String.format("%s {%s}", name(), getNominalSpecification());
	}
}
