package hk.ust.cse.lantern.data.io;

import hk.ust.cse.lantern.data.Data;
import hk.ust.cse.lantern.data.GroupedData;
import hk.ust.cse.lantern.data.Instance;
import hk.ust.cse.lantern.data.NominalVariable;
import hk.ust.cse.lantern.data.Variable;
import hk.ust.cse.lantern.data.VariableCollection;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.Map;

/**
 * Writes the data in HLCM format.
 * 
 * @author leonard
 * 
 */
public class HlcmWriter extends BaseWriter {
	private String name;
	private PrintWriter writer;

	/**
	 * Whether it allows the state names to begin with a digit.
	 */
	private boolean allowBeginningWithDigit = false;

	public HlcmWriter(OutputStreamWriter writer, String dataName) {
		this.writer = new PrintWriter(writer);
		this.name = dataName;
	}

	public HlcmWriter(OutputStream output, String dataName)
			throws UnsupportedEncodingException, FileNotFoundException {
		this(createWriter(output), dataName);
	}

	public HlcmWriter(String filename, String dataName)
			throws UnsupportedEncodingException, FileNotFoundException {
		this(createWriter(filename), dataName);
	}

	private String getNominalSpecification(NominalVariable variable) {
		StringBuilder builder = new StringBuilder();

		for (String string : variable.textualDomain()) {
			// prepend character 's' to a value if it is a number
			if (!allowBeginningWithDigit && Character.isDigit(string.charAt(0)))
				builder.append("s");

			builder.append(string).append(" ");
		}

		// delete the last space
		builder.deleteCharAt(builder.length() - 1);

		return builder.toString();
	}

	public void write(Data data) {
		GroupedData groupedData = new GroupedData(data);
		writePreamble(groupedData);
		writeInstances(groupedData.getInstances());

		writer.close();
	}

	private void writeInstance(Instance instance, int count) {
		Iterator<Integer> iterator = instance.getNumericValues().iterator();
		while (iterator.hasNext()) {
			writer.print(iterator.next());

			if (iterator.hasNext())
				writer.print(' ');
		}

		writer.print("  ");
		writer.print(count);

		writer.println();
	}

	private void writeInstances(Map<Instance, Integer> instances) {
		for (Map.Entry<Instance, Integer> entry : instances.entrySet()) {
			writeInstance(entry.getKey(), entry.getValue());
		}
	}

	private void writePreamble(GroupedData data) {
		writer.printf("Name: %s\n\n", name);
		writeVariables(data.getVariables());
	}

	private void writeVariables(VariableCollection variables) {
		for (Variable variable : variables) {
			if (variable instanceof NominalVariable) {
				writer.printf("%s: %s\n", variable.name(),
						getNominalSpecification((NominalVariable) variable));
			} else
				throw new IllegalArgumentException(
						"Data contains non-nominal variables, which are not supported at this moment.");
		}

		writer.println();
	}

}
