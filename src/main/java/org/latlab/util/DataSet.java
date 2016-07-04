
package org.latlab.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StreamTokenizer;
import java.io.UnsupportedEncodingException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.latlab.model.BayesNet;

import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

import cern.jet.random.Uniform;

/**
 * This class provides an implementation for data sets.
 * 
 * @author Yi Wang
 * 
 */
public final class DataSet {

	/**
	 * This class provides an implementation for data cases.
	 * 
	 * @author Yi Wang
	 * 
	 */
	public final class DataCase implements Comparable<DataCase> {
		/**
		 * the data set to which this data case belongs.
		 */
		private DataSet _dataSet;

		/**
		 * the array of states of this data case.
		 */
		private int[] _states;

		/**
		 * the weight of this data case.
		 */
		private double _weight;

		/**
		 * Constructs a data case with the specified data set that contains it,
		 * and the specified states and weight.
		 * 
		 * @param dataSet
		 *            data set that contains this data case.
		 * @param states
		 *            states of this data case.
		 * @param weight
		 *            weight of this data case.
		 */
		private DataCase(DataSet dataSet, int[] states, double weight) {
			_dataSet = dataSet;
			_states = states;
			_weight = weight;
		}

		/**
		 * <p>
		 * Compares this data case with the specified object for order.
		 * </p>
		 * 
		 * <p>
		 * If the specified object is not a data case, this method will throw a
		 * <code>ClassCastException</code> (as data cases are comparable only
		 * to other data cases). Otherwise, the comparison is carried out based
		 * on the states of two data cases.
		 * </p>
		 * 
		 * @param object
		 *            the object to be compared.
		 * @return a negative or a positive integer if the states of this data
		 *         case procedes or succeeds that of the specified data case;
		 *         zero if their states are identical.
		 */
		public int compareTo(DataCase object) {
			DataCase dataCase = object;

			// two data cases must belong to the same data set
			assert _dataSet == dataCase._dataSet;

			for (int i = 0; i < _states.length; i++) {
				if (_states[i] < dataCase._states[i]) {
					return -1;
				} else if (_states[i] > dataCase._states[i]) {
					return 1;
				}
			}

			return 0;
		}

		/**
		 * Returns the states of this data case.
		 * 
		 * @return the states of this data case
		 */
		public int[] getStates() {
			return _states;
		}

		/**
		 * Returns the weight of this data case.
		 * 
		 * @return the weight of this data case.
		 */
		public double getWeight() {
			return _weight;
		}

		/**
		 * Updates the weight of this data case.
		 * 
		 * @param weight
		 *            new weight of this data case.
		 */
		public void setWeight(double weight) {
			// weight must be positive
			assert weight > 0.0;

			_weight = weight;
		}
	}
	
	/**
	 * the constant for missing value.
	 */
	public final static int MISSING_VALUE = -1;

	/**
	 * the prefix of default names of data sets.
	 */
	private final static String NAME_PREFIX = "DataSet";

	/**
	 * the number of created data sets.
	 */
	private static int _count = 0;

	/**
	 * the name of this data set.
	 */
	protected String _name;

	/**
	 * the array of variables involved in this data set.
	 */
	private Variable[] _variables;

	/**
	 * the list of distinct data cases. we use <code>ArrayList</code> for
	 * random access.
	 */
	private ArrayList<DataCase> _data;

	/**
	 * the total weight, namely, number of data cases, of this data set.
	 */
	private double _totalWeight;

	/**
	 * the flag that indicates whether this data set contains missing values.
	 */
	private boolean _missing;

	/**
	 * Reads the data set(HLTM format) from the specified input stream.
	 * 
	 * @param stream
	 *            input stream of the data set
	 * @throws IOException
	 *             thrown when an IO error occurs
	 */
	public DataSet(InputStream stream) throws IOException {
		StreamTokenizer tokenizer = new StreamTokenizer(new BufferedReader(
				new InputStreamReader(stream, "UTF8")));

		tokenizer.resetSyntax();

		// characters that will be ignored
		tokenizer.whitespaceChars(':', ':');
		tokenizer.whitespaceChars(' ', ' ');
		tokenizer.whitespaceChars('\t', '\t');

		// word characters
		tokenizer.wordChars('A', 'z');
		tokenizer.wordChars('\'', '\'');
		tokenizer.wordChars('(', '(');
		tokenizer.wordChars(')', ')');
		tokenizer.wordChars(']', ']');
		tokenizer.wordChars('[', '[');
		tokenizer.wordChars('-', '-');
		tokenizer.wordChars('0', '9');

		// we will parse numbers
		tokenizer.parseNumbers();

		// treats eol as a token
		tokenizer.eolIsSignificant(true);

		// ignores c++ comments
		tokenizer.slashSlashComments(true);

		NumberFormat nf = NumberFormat.getInstance();
		nf.setMinimumFractionDigits(0);

		// starts parsing
		int value = StreamTokenizer.TT_EOF;

		// name of data set
		do {
			value = tokenizer.nextToken();
		} while (value != StreamTokenizer.TT_WORD);

		do {
			value = tokenizer.nextToken();
		} while (value != StreamTokenizer.TT_WORD);
		_name = tokenizer.sval;

		// reads variables
		ArrayList<Variable> variables = new ArrayList<Variable>();

		while (true) {
			// goes to the next word or number
			do {
				value = tokenizer.nextToken();
			} while (!(value == StreamTokenizer.TT_WORD || value == StreamTokenizer.TT_NUMBER));
			
			// if we see a number, we have read all variables
			if (value == StreamTokenizer.TT_NUMBER) {
				tokenizer.pushBack();
				break;
			}

			// we have at least one more variable to read
			String name = tokenizer.sval;
			
			// read state names
			ArrayList<String> states = new ArrayList<String>();
			do {
				value = tokenizer.nextToken();
				if (value == StreamTokenizer.TT_WORD) {
					states.add(tokenizer.sval);
				} else if (value == StreamTokenizer.TT_NUMBER) {
					states.add(nf.format(tokenizer.nval));
				}
			} while (value != StreamTokenizer.TT_EOL);

			variables.add(new Variable(name, states));
		}

		// enforces order of variables
		_variables = variables.toArray(new Variable[variables.size()]);
		Arrays.sort(_variables);

		// reads data cases
		_data = new ArrayList<DataCase>();
		while (true) {
			// goes to the next number
			do {
				value = tokenizer.nextToken();
			} while (value != StreamTokenizer.TT_EOF
					&& value != StreamTokenizer.TT_NUMBER);

			// we have at least one more data case
			if (value == StreamTokenizer.TT_NUMBER) {
				tokenizer.pushBack();
			} else {
				break;
			}

			// reads states
			int[] states = new int[getDimension()];
			for (int i = 0; i < getDimension(); i++) {
				do {
					value = tokenizer.nextToken();
				} while (value != StreamTokenizer.TT_NUMBER);
				states[i] = (int) tokenizer.nval;
			}

			// reads weight
			do {
				value = tokenizer.nextToken();
			} while (value != StreamTokenizer.TT_NUMBER);
			double weight = tokenizer.nval;

			// adds data case
			addDataCase(states, weight);

			do {
				value = tokenizer.nextToken();
			} while (value != StreamTokenizer.TT_EOL
					&& value != StreamTokenizer.TT_EOF);

			if (value == StreamTokenizer.TT_EOF) {
				break;
			}
		}

		_count++;
	}
	
	public DataSet(Instances data)
	{
		_name = data.relationName();

		// reads variables
		ArrayList<Variable> variables = new ArrayList<Variable>();
		
		int num_variables = data.numAttributes();
		
		for(int i=0; i<num_variables; i++)
		{
			Attribute att= data.attribute(i);
			
			ArrayList<String> states = new ArrayList<String>();
			
			if(!att.isNominal())
			{
				System.out.println(" Non-nominal attribute is not currently surpported .");
				System.exit(-1);
			}
			
			for(int j=0; j<att.numValues(); j++)
				states.add(att.value(j));
			
			variables.add(new Variable(att.name(), states));
		}

		// enforces order of variables
		_variables = variables.toArray(new Variable[variables.size()]);
		Arrays.sort(_variables);

		// reads data cases
		_data = new ArrayList<DataCase>();
		
		for(int i=0; i<data.numInstances(); i++)
		{
			// reads states
			int[] states = new int[variables.size()];
			
			Instance ins = data.instance(i);
			
			for(int j=0; j<num_variables; j++)
			{
				states[j] = (int) ins.value(j);
			}
			
			// adds data case
			addDataCase(states, ins.weight());
		}
		
		_count++;
	}
	
	/**
	 * Constructs a data set defined by the specified data file.
	 * 
	 * @param file
	 *            The name of the specified data file.
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	public DataSet(String file) throws IOException {
			this(new FileInputStream(file));
	}



	/**
	 * Constructs an empty data set of the specified array of variables.
	 * 
	 * @param variables
	 *            array of variables to be involved.
	 */
	public DataSet(Variable[] variables) {
		// default name
		_name = NAME_PREFIX + _count++;

		// Added by Chen Tao
		_variables = variables;

		// enforces order of variables
	    Arrays.sort(_variables);

		_data = new ArrayList<DataCase>();
	}
 
	
	/**
	 * Constructs an empty data set of the specified array of variables.
	 * 
	 * @param variables
	 *            array of variables to be involved.
	 *        flag
	 *            decide whether to enforce the order. If following the order given, flag is false
	 */
	public DataSet(Variable[] variables, boolean flag) {
		// default name
		_name = NAME_PREFIX + _count++;

		// Added by Chen Tao
		_variables = variables;

		// enforces order of variables
	   if(flag){
		   Arrays.sort(_variables);
	   }

		_data = new ArrayList<DataCase>();
	}
	/**
	 * Adds the specified data case with the specified weight to this data set.
	 * 
	 * @param states
	 *            data case to be added to this data set.
	 * @param weight
	 *            weight of the data case.
	 */
	public void addDataCase(int[] states, double weight) {
		DataCase dataCase = new DataCase(this, states, weight);

		// finds the position for this data case
		int index = Collections.binarySearch(_data, dataCase);

		if (index < 0) {
			// adds unseen data case
			_data.add(-index - 1, dataCase);

			// updates missing value flag
			for (int state : states) {
				_missing |= (state == MISSING_VALUE);
			}
		} else {
			// increases weight for existing data case
			dataCase = _data.get(index);
			dataCase.setWeight(dataCase.getWeight() + weight);
		}

		// updates total weight
		_totalWeight += weight;
	}

	public double SearchDataCase(int[] states) {
		DataCase dataCase = new DataCase(this, states, 1.0);

		// finds the position for this data case
		int index = Collections.binarySearch(_data, dataCase);
		
		dataCase = _data.get(index);

		return dataCase.getWeight();
	}
	
	
	public int[] getDatacaseIndex(String originalDataFile) throws IOException
	{
		if(!(originalDataFile.endsWith("arff")))
		{
			System.out.println("The data is not arff format.Please check.");
			System.exit(1);
		}
		
		int[] index = new int[(int) _totalWeight];
		
		StreamTokenizer tokenizer = new StreamTokenizer(new BufferedReader(
				new InputStreamReader(new FileInputStream(originalDataFile), "UTF8")));

		tokenizer.resetSyntax();

		// characters that will be ignored
		tokenizer.whitespaceChars(':', ':');
		tokenizer.whitespaceChars(' ', ' ');
		tokenizer.whitespaceChars('\t', '\t');
		tokenizer.whitespaceChars('{', '{');
		tokenizer.whitespaceChars('}', '}');
		tokenizer.whitespaceChars(',', ',');

		// word characters
		tokenizer.wordChars('A', 'z');
		tokenizer.wordChars('\'', '\'');
		tokenizer.wordChars('(', '(');
		tokenizer.wordChars(')', ')');
		tokenizer.wordChars(']', ']');
		tokenizer.wordChars('[', '[');
		tokenizer.wordChars('-', '-');
		tokenizer.wordChars('0', '9');
		tokenizer.wordChars('@', '@');

		// we will parse numbers
		tokenizer.parseNumbers();

		// treats eol as a token
		tokenizer.eolIsSignificant(true);

		// ignores c++ comments
		tokenizer.slashSlashComments(true);
		
		// ignores arff comments
		tokenizer.commentChar('%');

		NumberFormat nf = NumberFormat.getInstance();
		nf.setMinimumFractionDigits(0);

		// starts parsing
		int value = StreamTokenizer.TT_EOF;

		// name of data set
		do {
			value = tokenizer.nextToken();
		} while (value != StreamTokenizer.TT_WORD);

		do {
			value = tokenizer.nextToken();
		} while (value != StreamTokenizer.TT_EOL);
//		_name = tokenizer.sval;

		// reads variables
		ArrayList<Variable> variables = new ArrayList<Variable>();

		while (true) {
			
			//skip the word "@attribute", "@data"
			do {
				value = tokenizer.nextToken();
			} while (value != StreamTokenizer.TT_WORD);
			
			// goes to the next word or number
			do {
				value = tokenizer.nextToken();
			} while (!(value == StreamTokenizer.TT_WORD || value == StreamTokenizer.TT_NUMBER));

			// if we see a number, we have read all variables
			if (value == StreamTokenizer.TT_NUMBER) {
				tokenizer.pushBack();
				break;
			}

			// we have at least one more variable to read
			String name = tokenizer.sval;

			// read state names
			ArrayList<String> states = new ArrayList<String>();
			do {
				value = tokenizer.nextToken();
				if (value == StreamTokenizer.TT_WORD) {
					states.add(tokenizer.sval);
				} else if (value == StreamTokenizer.TT_NUMBER) {
					states.add(nf.format(tokenizer.nval));
				}
			} while (value != StreamTokenizer.TT_EOL);

			variables.add(new Variable(name, states));
		}

		// enforces order of variables
//		_variables = variables.toArray(new Variable[variables.size()]);
//		Arrays.sort(_variables);

		// reads data cases
//		_data = new ArrayList<DataCase>();
		int dataIndex = 0;
		while (true) {
			// goes to the next number
			do {
				value = tokenizer.nextToken();
			} while (value != StreamTokenizer.TT_EOF
					&& value != StreamTokenizer.TT_NUMBER);

			// we have at least one more data case
			if (value == StreamTokenizer.TT_NUMBER) {
				tokenizer.pushBack();
			} else {
				break;
			}

			// reads states
			int[] states = new int[getDimension()];
			for (int i = 0; i < getDimension(); i++) {
				do {
					value = tokenizer.nextToken();
				} while (value != StreamTokenizer.TT_NUMBER);
				states[i] = (int) tokenizer.nval;
			}

			// reads weight
			double weight = 1.0;
			
			DataCase dataCase = new DataCase(this, states, weight);
			index[dataIndex++] = Collections.binarySearch(_data, dataCase);

			do {
				value = tokenizer.nextToken();
			} while (value != StreamTokenizer.TT_EOL
					&& value != StreamTokenizer.TT_EOF);

			if (value == StreamTokenizer.TT_EOF) {
				break;
			}
		}

//		_count++;

		return index;
		
	}


	
	/**
	 * Returns the list of distinct data cases in this data set.
	 * 
	 * @return the list of distinct data cases in this data set.
	 */
	public ArrayList<DataCase> getData() {
		return _data;
	}

	/**
	 * Returns the dimension, namely, the number of variables, of this data set.
	 * 
	 * @return the dimension of this data set.
	 */
	public int getDimension() {
		return _variables.length;
	}

	/**
	 * Returns the name of this data set.
	 * 
	 * @return the name of this data set.
	 */
	public String getName() {
		return _name;
	}

	/**
	 * Returns the number of distinct data cases in this data set.
	 * 
	 * @return the number of distinct data cases in this data set.
	 */
	public int getNumberOfEntries() {
		return _data.size();
	}

	/**
	 * Returns the total weight, namely, the number of data cases, of this data
	 * set.
	 * 
	 * @return the total weight of this data set.
	 */
	public double getTotalWeight() {
		return _totalWeight;
	}

	/**
	 * Returns the array of variables involved in this data set.
	 * 
	 * @return the array of variables involved in this data set.
	 */
	public Variable[] getVariables() {
		return _variables;
	}

	/**
	 * Returns <code>true</code> if this data set contains missing values.
	 * 
	 * @return <code>true</code> if this data set contains missing values.
	 */
	public boolean hasMissingValues() {
		return _missing;
	}
	/** Added by Peixian Chen
	 * Returns a data set that is the projection of this data set on the
	 * specified list of variables.
	 * 
	 * @param variables
	 *            list of variables onto which this data set is to be project.
	 * @return a projected data set on the specified list of variables(You can choose whether to enforce order).
	 */
	public DataSet project(ArrayList<Variable> variables, boolean flag) {
		// array representation
		Variable[] varArray = variables.toArray(new Variable[variables.size()]);

		// you can choose whether to enforce  order of variables
		DataSet dataSet = new DataSet(varArray, flag);

		// maps argument variables to that in this data set
		int dimension = variables.size();
		int[] map = new int[dimension];
		
	/*	for (int i = 0; i < dimension; i++) {
			map[i] = Arrays.binarySearch(_variables, varArray[i]);

			// argument variable must be involved in this data set
			assert map[i] >= 0;
		}
    */
		////////////////////////////////////////////////////////////////
		for(int i = 0; i < dimension; i++)
		{
			for(int j = 0; j < _variables.length; j++)
			{
				if(_variables[j].getName().compareTo(varArray[i].getName()) == 0)
				{
					map[i] = j;
				}
			}
		}
		/////////////////////////////////////////////////////////////////
		
		// projection
		// int[] projectedStates = new int[dimension];
		for (DataCase dataCase : _data) {
			int[] projectedStates = new int[dimension];

			int[] states = dataCase.getStates();
			for (int i = 0; i < dimension; i++) {
				projectedStates[i] = states[map[i]];
			}

			dataSet.addDataCase(projectedStates, dataCase.getWeight());
		}

		return dataSet;
	}
	
	/**
	 * Count the frequencies of every variable
	 * Author Peixian Chen
	 **/
	public Map<Variable,Double> getFreq(){
		Map<Variable,Double> Frequencies = new HashMap<Variable,Double>();
		for (DataCase dataCase : _data) {
			int[] states = dataCase.getStates();
			for(int i = 0; i<_variables.length;i++){
				if(!Frequencies.containsKey(_variables[i])){
					Frequencies.put(_variables[i], states[i]*dataCase.getWeight());
				}else{
				Double cnt = Frequencies.get(_variables[i])+states[i]*dataCase.getWeight();
				Frequencies.put(_variables[i], cnt);
				}
			}
		}
		return Frequencies;
		
	}
	
	
	/**
	 * Returns a data set that is the projection of this data set on the
	 * specified list of variables.
	 * 
	 * @param variables
	 *            list of variables onto which this data set is to be project.
	 * @return a projected data set on the specified list of variables(enforced order).
	 */
	public DataSet project(ArrayList<Variable> variables) {
		// array representation
		Variable[] varArray = variables.toArray(new Variable[variables.size()]);

		// order of variables will be enforced in the constructor
		DataSet dataSet = new DataSet(varArray);

		// maps argument variables to that in this data set
		int dimension = variables.size();
		int[] map = new int[dimension];
		
	/*	for (int i = 0; i < dimension; i++) {
			map[i] = Arrays.binarySearch(_variables, varArray[i]);

			// argument variable must be involved in this data set
			assert map[i] >= 0;
		}
    */
		////////////////////////////////////////////////////////////////
		for(int i = 0; i < dimension; i++)
		{
			for(int j = 0; j < _variables.length; j++)
			{
				if(_variables[j].getName().compareTo(varArray[i].getName()) == 0)
				{
					map[i] = j;
				}
			}
		}
		/////////////////////////////////////////////////////////////////
		
		// projection
		// int[] projectedStates = new int[dimension];
		for (DataCase dataCase : _data) {
			int[] projectedStates = new int[dimension];

			int[] states = dataCase.getStates();
			for (int i = 0; i < dimension; i++) {
				projectedStates[i] = states[map[i]];
			}

			dataSet.addDataCase(projectedStates, dataCase.getWeight());
		}

		return dataSet;
	}

	/**
	 * Outputs this data set to the specified file.
	 * 
	 * @param file
	 *            output of this BN.
	 * @throws FileNotFoundException
	 *             if the file exists but is a directory rather than a regular
	 *             file, does not exist but cannot be created, or cannot be
	 *             opened for any other reason.
	 * @throws UnsupportedEncodingException
	 */
	public void save(String file) throws FileNotFoundException,
			UnsupportedEncodingException {
		PrintWriter out = new PrintWriter(new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(file), "UTF8")));

		// outputs header
		out.println("// " + file);
//		out.println("// Produced by org.latlab at "
//				+ ((System.currentTimeMillis())));

		// outputs name
		out.println("Name: " + _name);
		out.println();

		// outputs variables
		out.println("// " + getDimension() + " variables");
		for (Variable variable : _variables) {
			// variable name
			out.printf(variable.getName() + ":");

			// state names
			for (String state : variable.getStates()) {
				out.print(" " + state);
			}

			out.println();
		}
		out.println();

		// outputs data cases
		out.println("// " + getNumberOfEntries()
				+ " distinct data cases with total weight " + getTotalWeight());
		for (DataCase dataCase : _data) {
			for (int state : dataCase.getStates()) {
				out.print(state + " ");
			}

			// outputs weight
			out.println(dataCase.getWeight());
		}

		out.close();
	}

	/**
	 * Outputs this data set to the specified file in ARFF format.
	 * 
	 * @param file
	 *            output of this BN.
	 *        useStatesName
	 *            use the states name or the index of states in each datacase
	 * @throws FileNotFoundException
	 *             if the file exists but is a directory rather than a regular
	 *             file, does not exist but cannot be created, or cannot be
	 *             opened for any other reason.
	 * @throws UnsupportedEncodingException
	 */
	public void saveAsArff(String file, boolean useStatesName) throws FileNotFoundException,
			UnsupportedEncodingException {
		PrintWriter out = new PrintWriter(new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(file), "UTF8")));

		// output relation
		out.println("@relation " + _name);
		out.println();

		// output attributes
		int dim = getDimension();
		out.println("% " + dim + " variables");

		for (Variable variable : _variables) {
			// output name
			out.print("@attribute " + variable.getName());

			// output domain
			out.print(" {");
			for (int k = 0; k < variable.getCardinality(); k++) {
				
				if(useStatesName)
				{
					out.print(variable.getStates().get(k));
				}else
				{
					out.print(k);
				}
				
				if (k < variable.getCardinality() - 1) {
					out.print(",");
				}
			}
			out.println("}");
		}

		out.println();

		// output data cases
		out.println("% " + getNumberOfEntries() + " distinct records, "
				+ getTotalWeight() + " in total");
		out.println("@data");

		for (DataCase datum : _data) {
			StringBuffer states = new StringBuffer();

			for (int i = 0; i < dim; i++) {
				int state = datum.getStates()[i];

				if (state == -1) {
					states.append('?');
				} else {
					
					if(useStatesName)
					{
						states.append(_variables[i].getStates().get(state));
					}else
					{
						states.append(state);
					}
				}

				if (i < dim - 1) {
					states.append(',');
				}
			}

			int nCopies = (int) datum.getWeight();
			for (int i = 0; i < nCopies; i++) {
				out.println(states);
			}
		}

		out.close();
	}

	/**
	 * Replaces the name of this data set.
	 * 
	 * @param name
	 *            new name of this data set.
	 */
	public void setName(String name) {
		name = name.trim();

		// name cannot be blank
		assert name.length() > 0;

		_name = name;
	}

	/**
	 * Returns a data set of the common variables shared by this data set and
	 * the specified BN. The new data set use the Variables in BayesNet.
	 * 
	 * @param bayesNet
	 *            BN on which a data set is to be returned.
	 * @return a data set of the common variables shared by this data set and
	 *         the specified BN.
	 * @suppressWarning("Unchecked")
	 */
	public DataSet synchronize(BayesNet bayesNet) {

		// common variables
		ArrayList<Variable> variables = new ArrayList<Variable>();
		// The map from a common Variable(in BN) to the index in the original
		// DataSet.
		HashMap<Variable, Integer> map = new HashMap<Variable, Integer>();

		for (int i = 0; i < getDimension(); i++) {
			for (Variable variable : bayesNet.getVariables()) {
				if (_variables[i].equals(variable)) {
					map.put(variable, i);
					variables.add(variable);
					break;
				}
			}
		}

		DataSet dataSet = new DataSet((Variable[]) variables
				.toArray(new Variable[variables.size()]));

		// projection
		int dimension = dataSet.getDimension();
		for (DataCase dataCase : _data) {
			int[] projectedStates = new int[dimension];
			int[] states = dataCase.getStates();
			for (int i = 0; i < dimension; i++) {
				projectedStates[i] = states[map.get(dataSet._variables[i])];
			}
			dataSet.addDataCase(projectedStates, dataCase.getWeight());
		}

		return dataSet;
	}

	/**
	 * Generate the training data of the given size. The original data becomes
	 * the testing data.
	 * 
	 * @param nOfTraining
	 * @return
	 */
	public DataSet splitIntoTrainingAndTesting(int nOfTraining) {
		DataSet training = new DataSet(_variables);
		for (int i = 0; i < nOfTraining; i++) {
			double total = getTotalWeight();
			double random = Uniform.staticNextDoubleFromTo(0.0, total);
			double accumulator = 0.0;
			// Find the datacase to be moved
			for (DataCase data : getData()) {
				double weight = data.getWeight();
				accumulator += weight;
				if (accumulator > random) {
					training.addDataCase(data.getStates(), 1.0);
					if (weight == 1.0) {
						getData().remove(data);
					} else {
						data.setWeight(weight - 1.0);
					}
					_totalWeight = _totalWeight - 1.0;
					break;
				}
			}
		}
		return training;
	}

	/**
	 * Generate the training data of the given size from the current data. The
	 * method is sample from the current data with replacement. No side effect
	 * on the input.
	 * 
	 * @param nOfTraining
	 * @return
	 */
	public DataSet sampleWithReplacement(int nOfTraining) {
		DataSet training = new DataSet(_variables);
		for (int i = 0; i < nOfTraining; i++) {
			double total = getTotalWeight();
			double random = Uniform.staticNextDoubleFromTo(0.0, total);
			double accumulator = 0.0;
			// Find the datacase to be moved
			for (DataCase data : getData()) {
				double weight = data.getWeight();
				accumulator += weight;
				if (accumulator > random) {
					training.addDataCase(data.getStates(), 1.0);
					break;
				}
			}
		}
		return training;
	}

	/**
	 * Creates a map from variable to its index used in this data set.
	 * 
	 * @return a map from variable to its index
	 */
	public Map<Variable, Integer> createVariableToIndexMap() {
		return Algorithm.createIndexMap(_variables);
	}
	
	public DataSet SampleWithOverSampling(Variable Lable)
	{
		DataSet AfterSample = new DataSet(getVariables());
		
		ArrayList<Variable> TargetVar = new ArrayList<Variable>(1);
		TargetVar.add(Lable);
		
		DataSet TargetSet = project(TargetVar);
		
		int Car= Lable.getCardinality();
			
		int[] Count = new int[Car];
		int MaxWeight = 0;
		int MaxState = 0;
		
		for(DataCase d : TargetSet.getData())
		{
			int state = d.getStates()[0];
			int weight = (int)d.getWeight();
			
			Count[state]= weight; 
			
			if(weight > MaxWeight)
			{
				MaxWeight = weight;
				MaxState = state;
			}
		}
		
		for(DataCase d : getData())
		{
			AfterSample.addDataCase(d.getStates(), d.getWeight());		
		}
		
		for(int i=0; i < Car; i++)
		{
			if(i == MaxState) continue;
			
			int[] map = new int[Count[i]];
			
			int size = getData().size();
			
			int index = 0;
			
			
			for(int j = 0; j < size; j++)
			{
				int LableIndex = Arrays.binarySearch(getVariables(), Lable);
				
				DataCase d =getData().get(j);
				
				if(d.getStates()[LableIndex] == i)
				{
					for(int k = 0; k < (int)d.getWeight(); k++)
					{
						map[index++] = j;
					}
				}
			}
			
			Random random = new Random();
			
			for(int j = 0; j < (MaxWeight-Count[i]); j++)
			{
				int randomIndex = random.nextInt(Count[i]);
				DataCase d = getData().get(map[randomIndex]);
				AfterSample.addDataCase(d.getStates(), 1.0);
			}
		}
			
		return AfterSample;		
	}
}