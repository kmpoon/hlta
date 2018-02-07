package hk.ust.cse.lantern.data.io;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.filechooser.FileFilter;

public class FormatCatalog {

    /**
     * Describes the format of data file.
     * 
     * @author leonard
     * 
     */
    public static class Format implements Comparable<Format> {

        /**
         * Name of format
         */
        public final String name;

        /**
         * Description of format
         */
        public final String description;

        /**
         * Extension of the file, not including the dot.
         */
        public final String extension;

        /**
         * Suffix to the file name based on the extension.
         */
        public final String suffix;

        private final String fullDescription;

        public final FileFilter filter = new FileFilter() {

            public boolean accept(File file) {
                return file.isDirectory() ? true : hasExtension(file.getName());
            }

            @Override
            public String getDescription() {
                return fullDescription;
            }

        };

        private Format(String name, String description, String extension) {
            this.name = name;
            this.description = description;
            this.extension = extension;
            this.suffix = "." + extension;

            fullDescription =
                String.format("%s - %s (*.%s)", name, description, extension);
        }

        @Override
        public String toString() {
            return fullDescription;
        }

        public int compareTo(Format o) {
            return name.compareTo(o.name);
        }

        /**
         * Returns whether the filename has extension of this format.
         * 
         * @param filename
         *            name of the file
         * @return whether the file name has the extension of this format
         */
        public boolean hasExtension(String filename) {
            return filename.endsWith(suffix)
                && filename.length() > suffix.length();
        }
    }

    public final static Format ARFF =
        new Format("ARFF", "Weka ARFF format", "arff");
    public final static Format ARFF_GZ =
        new Format("ARFF_GZ", "Gzipped Weka ARFF File", "arff.gz");
    public final static Format CSV =
        new Format("CSV", "Comma Separated Value (CSV) Document", "csv");
    //New standard, use .hlcm for HLCM format
    //To avoid conflict with general text
    public final static Format HLCM = new Format("HLCM", "HLCM format", "hlcm");
    @Deprecated
    public final static Format HLCM_ = new Format("HLCM", "HLCM format (.txt ext)", "txt");

    public final static SortedSet<Format> INPUT_FORMATS =
        Collections.unmodifiableSortedSet(new TreeSet<Format>(Arrays.asList(
            ARFF, ARFF_GZ, CSV, HLCM, HLCM_)));

    public final static SortedSet<Format> OUTPUT_FORMATS =
        Collections.unmodifiableSortedSet(new TreeSet<Format>(Arrays.asList(
            ARFF, HLCM)));

    public static Format getInputFormat(String filename) {
        for (Format format : INPUT_FORMATS) {
            if (format.hasExtension(filename))
                return format;
        }

        return null;
    }

    public static Format getOutputFormat(String filename) {
        for (Format format : OUTPUT_FORMATS) {
            if (format.hasExtension(filename))
                return format;
        }

        return null;
    }
}
