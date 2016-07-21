package hk.ust.cse.lantern.data;

import hk.ust.cse.lantern.data.io.ArffWriter;
import hk.ust.cse.lantern.data.io.CsvReader;
import hk.ust.cse.lantern.data.io.FormatCatalog;
import hk.ust.cse.lantern.data.io.HlcmWriter;
import hk.ust.cse.lantern.data.io.Reader;
import hk.ust.cse.lantern.data.io.Writer;
import hk.ust.cse.lantern.data.io.FormatCatalog.Format;

/**
 * Converts data between different formats.
 * 
 * @author leonard
 * 
 */
public class Converter {
    public static void convert(
        String inputFile, Format inputFormat, String outputFile,
        Format outputFormat) throws Exception {

        Reader reader = null;
        if (inputFormat == FormatCatalog.CSV) {
            CsvReader csvReader = new CsvReader(inputFile, ',', "-1");
            csvReader.setTrimTrailingSpace(true);
            reader = csvReader;
        }

        Writer writer = null;
        if (outputFormat == FormatCatalog.HLCM) {
            writer = new HlcmWriter(outputFile, "converted_data");
        } else if (outputFormat == FormatCatalog.ARFF) {
            writer = new ArffWriter(outputFile, "converted_data");
        }

        writer.write(reader.read());
    }

}
