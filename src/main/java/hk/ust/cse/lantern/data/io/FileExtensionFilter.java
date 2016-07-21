package hk.ust.cse.lantern.data.io;

import java.io.File;
import java.io.FilenameFilter;

public class FileExtensionFilter implements FilenameFilter {
	private final String extension;

	public FileExtensionFilter(String extension) {
		this.extension = "." + extension;
	}

	public boolean accept(File dir, String name) {
		int index = name.lastIndexOf(extension);
		return index == name.length() - extension.length();
	}

}
