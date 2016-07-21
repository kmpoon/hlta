package hk.ust.cse.lantern.data.io;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

public abstract class BaseWriter implements Writer {
    protected static String defaultEncoding = "UTF-8";

    protected static OutputStreamWriter createWriter(String name)
        throws UnsupportedEncodingException, FileNotFoundException {
        return new OutputStreamWriter(
            new FileOutputStream(name), defaultEncoding);
    }

    protected static OutputStreamWriter createWriter(OutputStream output)
        throws UnsupportedEncodingException, FileNotFoundException {
        return new OutputStreamWriter(output, defaultEncoding);
    }
}
