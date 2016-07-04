package org.latlab.io;

import org.latlab.model.BayesNet;

public abstract class AbstractParser implements Parser {
	public BayesNet parse() throws ParseException {
		BayesNet result = new BayesNet("");
		parse(result);
		return result;
	}
}
