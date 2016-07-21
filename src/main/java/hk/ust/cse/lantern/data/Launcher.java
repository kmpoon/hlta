package hk.ust.cse.lantern.data;

import java.io.File;
import java.io.FileInputStream;

import hk.ust.cse.lantern.data.io.ArffWriter;
import hk.ust.cse.lantern.data.io.CsvReader;
import hk.ust.cse.lantern.data.io.HlcmWriter;
import hk.ust.cse.lantern.data.io.Writer;
import hk.ust.cse.lantern.data.io.arff.ArffReader;
/**
 * This class provide some simple functions to transform data.
 * 
 * To do: we would like to integrate this function in lantern soon.
 * 
 * @author liutf
 *
 */
public class Launcher {
	
	
	public static void main(String[] args) throws Exception
	{
		if(args.length != 2)
		{
			//Note: the input are folders. It will process all data files in input folder!
			System.out.println("Java Launcher data-file-folder data-file-output-path");
			System.exit(-1);
		}
		
		Launcher convert = new Launcher();
		convert.run(args[0],args[1]);
	}

	private void run(String inputFolder, String outputFolder) throws Exception
	{
		File dir = new File(inputFolder);
		File[] files = dir.listFiles();
		
		System.out.println("Started.");
		
		for(int i=0; i<files.length; i++)
		{
			String fileName = files[i].getAbsolutePath().toLowerCase();
			//ArfftoHlcm(fileName,outputFolder);
			CSVtoFormat(fileName,outputFolder,0);
		}
		
		System.out.println("Finished.");
	}
	
	
	private void CSVtoFormat(String file, String outputFolder, int format) throws Exception
	{
		
		CsvReader csvReader = new CsvReader(file, ',', "?");
        //csvReader.setTrimTrailingSpace(true);
        
        Data data = csvReader.read();
        
		//get the original name of the data 
		String fileName = fileName(file, 4);
        Writer writer = null;
		switch(format)
		{
		case 0:
			//HlcmWriter for EAST format.
	        writer = new HlcmWriter(outputFolder+File.separator+fileName+".txt", fileName);
	        break;
		case 1:
		default:
			//arffWriter for weka format.
			writer = new ArffWriter(outputFolder+File.separator+fileName+".arff", fileName);
		}
		
        writer.write(data);
	}
	
	private void ArfftoHlcm(String file, String outputFolder) throws Exception
	{
		
		ArffReader reader = new ArffReader(new FileInputStream(file));
		//reader.setTrimTrailingSpace(true);

		Data data = reader.read();

		//get the original name of the data 
		String fileName = fileName(file, 5);
		
		//write data
		Writer writer = new HlcmWriter(outputFolder+File.separator+fileName+".txt", fileName);
		writer.write(data);
		
	}
	
	/**
	 * Return the name of the data.
	 * e.g.  Given c:/Users/Data/iris.txt, it will return "iris". 
	 * for parameter "numOfSuffix":
	 * 	e.g  file "iris.csv", numOfSuffix is the length of ".csv" = 4;
	 *  e.g  file "iris.arff", numOfSuffix is the length of ".arff" = 5.
	 * @param path
	 * @return
	 */
	private String fileName(String path, int numOfSuffix)
	{
		//get the original name of the data 
		int beginIndex = path.lastIndexOf("\\")+1;
		int endIndex = path.length()-numOfSuffix;
		String fileName = path.substring(beginIndex, endIndex);
		
		return fileName;
	}
	
//	/**
//	 * @param args
//	 */
//	public static void main(String[] args) {
//		try {
//			System.out.println("Started.");
//			ArffReader reader = new ArffReader(new FileInputStream("D:/Users/Data/Multi-Clustering/australian/australian-discretized.arff"));
//			//reader.setTrimTrailingSpace(true);
//
//			Data data = reader.read();
//
//			//Writer writer = new HlcmWriter("D:/Users/Data/Multi-Clustering/all/1.txt", "parties");
//			Writer writer = new ArffWriter("D:/Users/Data/Multi-Clustering/all/1.txt", "parties");
//			writer.write(data);
//			System.out.println("Finished.");
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//	}
}
