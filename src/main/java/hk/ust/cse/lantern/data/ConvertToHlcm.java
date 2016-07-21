package hk.ust.cse.lantern.data;

import hk.ust.cse.lantern.data.io.FormatCatalog;
import hk.ust.cse.lantern.data.io.Manager;
import hk.ust.cse.lantern.data.io.Reader;
import hk.ust.cse.lantern.data.io.Writer;
import hk.ust.cse.lantern.data.io.FormatCatalog.Format;

import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * Converts data from known formats to HLCM format.
 * 
 * @author leonard
 * 
 */
public class ConvertToHlcm {

    /**
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out
                .println("ConvertToHlcm input_file_name output_file_name");
            return;
        }

        FileInputStream input = new FileInputStream(args[0]);
        FileOutputStream output = new FileOutputStream(args[1]);

        Format format = FormatCatalog.getInputFormat(args[0]);

        Reader reader = Manager.createReader(input, format);
        Data data = reader.read();

        Writer writer = Manager.createWriter(output, FormatCatalog.HLCM);
        writer.write(data);
    }
}
