package org.latlab.util;

/**
 * This exception is to be thrown when the trial version is in use and the limit
 * on number of manifest variables is reached.
 * 
 * @author wangyi
 * 
 */
public class TrialVersionLimitException extends RuntimeException {

	/**
	 * Serial ID.
	 */
	private static final long serialVersionUID = -4915551464710995479L;

	/**
	 * Trial version or not.
	 */
	public static final boolean TRIAL_VERSION = false;

	/**
	 * Maximum number of manifest variables.
	 */
	public static final int MAX_NUM_MANIFEST = 50;

	/**
	 * Default constructor.
	 */
	public TrialVersionLimitException() {
		super("The trial version handles at most " + MAX_NUM_MANIFEST
				+ " manifest variables. ");
	}

	/**
	 * Constructor with customized error message.
	 * 
	 * @param msg
	 *            Error message.
	 */
	public TrialVersionLimitException(String msg) {
		super(msg);
	}

}
