package hk.ust.cse.lantern.data.io;

/**
 * Indicates an exception occurred during parsing.
 * @author leonard
 *
 */
public class ParseException extends Exception {

	private static final long serialVersionUID = 2241163856172759878L;
	
	private static final String MESSAGE = "Parse error.";
	
	public ParseException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public ParseException(Throwable cause) {
		this(MESSAGE, cause);
	}

}
