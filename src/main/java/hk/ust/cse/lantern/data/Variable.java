package hk.ust.cse.lantern.data;

/**
 * 
 * @author leonard
 * 
 * Represents a variable
 */
public abstract class Variable {

	/**
	 * name of the variable
	 */
	private String name;

	Variable(String name) {
		this.name = name;
	}

	/**
	 * Returns the name of this variable.
	 * 
	 * @return name of this variable
	 */
	public String name() {
		return name;
	}

	/**
	 * Sets the name of this variable.
	 * 
	 * @param name
	 *            name of this variable
	 */
	public void setName(String name) {
		this.name = name;
	}
	
	/**
	 * Gets the textual value for the specified numeric value
	 * 
	 * @param numericValue
	 *            numeric value of interest
	 * @return the textual value
	 */
	public abstract String getText(int numericValue);


	/**
	 * Gets the numeric value for the specified textual value
	 * 
	 * @param textualValue
	 *            textual value of interest
	 * @return the numeric value
	 */
	public abstract int getNumber(String textualValue);
}
