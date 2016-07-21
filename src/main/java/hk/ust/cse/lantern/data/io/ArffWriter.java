package hk.ust.cse.lantern.data.io;

import hk.ust.cse.lantern.data.Data;
import hk.ust.cse.lantern.data.Instance;
import hk.ust.cse.lantern.data.InstanceCollection;
import hk.ust.cse.lantern.data.NominalVariable;
import hk.ust.cse.lantern.data.Variable;
import hk.ust.cse.lantern.data.VariableCollection;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Iterator;

public class ArffWriter extends BaseWriter {
    String relationName;
    PrintWriter writer;

    public ArffWriter(String filename, String relationName)
        throws UnsupportedEncodingException, FileNotFoundException {
        this(createWriter(filename), relationName);
    }

    public ArffWriter(OutputStream output, String relationName)
        throws UnsupportedEncodingException, FileNotFoundException {
        this(createWriter(output), relationName);
    }

    public ArffWriter(Writer writer, String relationName) {
        this.writer = new PrintWriter(writer);
        this.relationName = relationName;
    }

    public void write(Data data) {
        writePreamble(data, relationName);
        writeInstances(data.instances());

        writer.close();
    }

    // doesn't handle missing data!
    private void writeInstance(Instance instance) {
        Iterator<String> iterator = instance.getTextualValues().iterator();
        while (iterator.hasNext()) {
            writer.print(iterator.next());

            if (iterator.hasNext())
                writer.print(',');
        }

        writer.println();
    }

    private void writeInstances(InstanceCollection instances) {
        writer.println("@data");
        for (Instance instance : instances) {
            writeInstance(instance);
        }
    }

    private void writePreamble(Data data, String relationName) {
        writer.printf("@relation %s\n", relationName);
        writeVariables(data.variables());
    }

    private void writeVariables(VariableCollection variables) {
        for (Variable variable : variables) {
            if (variable instanceof NominalVariable) {
                writer.printf(
                    "@attribute %s {%s}\n", variable.name(),
                    ((NominalVariable) variable).getNominalSpecification());
                    
//            	writer.println("@attribute "+variable.name()+" numeric");
            } else
                throw new IllegalArgumentException(
                    "Data contains non-nominal variables, which are not supported at this moment.");
        }
    }
}