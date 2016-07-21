package hk.ust.cse.lantern.data.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class of a reader.
 * 
 * It provides support for commonly used encoding and tries to automatically
 * determines that of a file when it is not specified.
 * 
 * @author leonard
 * 
 */
public abstract class BaseReader implements Reader {

	private final static List<Charset> charsets;

	static {
		/**
		 * The supported character sets in the order of preference.
		 */
		final String[] names = { "UTF-8", "GB2312", "Big5", "Big5-HKSCS",
				"GB18030" };

		charsets = new ArrayList<Charset>(names.length + 1);
		charsets.add(Charset.defaultCharset());

		for (String name : names) {
			try {
				charsets.add(Charset.forName(name));
			} catch (IllegalArgumentException e) {
				System.out.format("Charset [%s] is not found.\n", name);
			}
		}
	}

	/**
	 * Returns an input stream reader from an input stream. The character set of
	 * the input stream reader is now set to default rather than automatically 
	 * detected.
	 * 
	 * @param input
	 *            input stream
	 * @return input stream reader
	 * @throws IOException
	 *             when there is IO error
	 */
	protected static InputStreamReader getReader(InputStream input)
			throws IOException {
		return new InputStreamReader(input, Charset.defaultCharset());
		
//		byte[] bytes = convert(input);
//		Charset encoding = detect(bytes);
//		return new InputStreamReader(new ByteArrayInputStream(bytes), encoding);
	}

	/**
	 * Converts an input stream to a byte array.
	 * 
	 * @param input
	 *            input stream
	 * @return converted byte array
	 * @throws IOException
	 *             when there is IO error
	 */
	private static byte[] convert(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		byte[] buffer = new byte[1024];
		int length = input.read(buffer);
		while (length > 0) {
			output.write(buffer, 0, length);
			length = input.read(buffer);
		}

		output.close();
		return output.toByteArray();
	}

	/**
	 * Detects the character set used in a byte array. If none of the supported
	 * character set can decode the given byte array, the default character set
	 * used by the system is returned.
	 * 
	 * @param bytes
	 *            bytes of which the encoding is to be determined
	 * @return character set of the byte array
	 */
	private static Charset detect(byte[] bytes) {
		char[] buffer = new char[bytes.length];

		for (Charset charset : charsets) {
			ByteBuffer input = ByteBuffer.wrap(bytes);
			CharBuffer output = CharBuffer.wrap(buffer);

			CharsetDecoder decoder = charset.newDecoder()
					.onUnmappableCharacter(CodingErrorAction.REPORT);
			CoderResult result = decoder.decode(input, output, true);

			if (!result.isError())
				return charset;
		}

		return Charset.defaultCharset();
	}
}
