package hk.ust.cse.lantern.data;

public class NumericVariable extends Variable {

	public NumericVariable(String name) {
		super(name);
	}

	@Override
	public int getNumber(String textualValue) {
		return Integer.parseInt(textualValue);
	}

	@Override
	public String getText(int numericValue) {
		return Integer.toString(numericValue);
	}

	@Override
	public String toString() {
		return String.format("%s numeric", name());
	}
}
