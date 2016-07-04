package HLTA;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

/**
 * Select words according to frequency/entropy/TF-IDF measure.
 * For UAI-2012 paper experiments.
 * 
 * @author liutf
 *
 */

public class SelectWords {
	
	private Instances m_instances = null;
	private int m_total = 0;
	
	public static void main(String[] args)
	{
		if(args.length < 4)
		{
			System.out.println("Usage: Java SelectWords dataFile output method parameter_1 parameter_2");
			System.exit(-1);
		}
		
		SelectWords sel = new SelectWords();
		
		try {
			sel.run(args[0],args[1],args[2], args[3], args[4]);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void run(String input, String output, String method, String parameter_1, String parameter_2) throws Exception
	{
		FileReader data = new FileReader(input);
		m_instances = new Instances(data);
		m_total = m_instances.numInstances();
		
		//PrintWriter out = new PrintWriter(output);
		
		int type = Integer.parseInt(method);
		double para_1 = Double.parseDouble(parameter_1);
		double para_2 = Double.parseDouble(parameter_2);
		
		switch(type)
		{
			case 0:
				SelectByFrequency(output, para_1);
				break;
			case 1:
				SelectByEntropy(output, para_1);
				break;
			case 2:
				OutputHighestFrequencyWords(output, para_1);
				break;
			case 3:
				OutputHighestEntropyWords(output, para_1);
				break;
			case 4:
				selectByFreqRange(output, (int)para_1, (int)para_2);
				break;
			case 5:
				SelectByTFIDF(output, para_1);
				break;
			default:
				break;
		}	
	//	out.close();
	}
	
	/**
	 * Output the first few words with the highest frequency. 
	 * 
	 * @param output
	 * @param para
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	private void OutputHighestFrequencyWords(String output, double para) throws IOException, InterruptedException
	{
		
		int[] counts = new int[m_instances.numAttributes()];
		
		for(int k=0; k<counts.length; k++)
		{
			counts[k] = 0;
		}
		
		for(int j=0; j<m_instances.numInstances(); j++)
		{
			Instance ins = m_instances.instance(j);
			
			for(int i=0; i<m_instances.numAttributes(); i++)
			{
				if(ins.attribute(i).isNumeric() && ins.value(i) == 0)
				{
					counts[i] += 1;
				}			
			}
		}
		
		Map<String, Double> collection = new HashMap<String, Double>();
		
		for(int k=0; k<counts.length; k++)
		{
			if(m_instances.attribute(k).isNumeric())
			{
				//double frequency = 1 - ((double)counts[k])/((double)m_instances.numInstances());
				collection.put(m_instances.attribute(k).name(), (double)(m_instances.numInstances()-counts[k]));//frequency);
			}
		}
		
		
        List<Map.Entry<String,Double>> list = new ArrayList<Map.Entry<String,Double>>(collection.entrySet());
        
        Collections.sort(list, new Comparator<Map.Entry<String,Double>>(){
        	public int compare(Map.Entry<String,Double> o1, Map.Entry<String,Double> o2)
        	{
        		if(o1.getValue() > o2.getValue())
        		{
        			return -1;
        		}else if(o1.getValue() < o2.getValue()) 
        		{
        			return 1;
        		}else
        		{
        			return 0;
        		}
        }
        });
		
        
        //PrintWriter out = new PrintWriter(new FileWriter(output),true);//(new FileOutputStream(output),true);//;new PrintWriter(output);
        
        for(int i=0; i<(int)para; i++)
        {
        	System.out.println(list.get(i).getKey());//+", "+list.get(i).getValue());
        }
        
		//out.close();
	}
	
	/**
	 * Output the first few words with the highest entropy.
	 * 
	 * @param output
	 * @param para
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	
	private void OutputHighestEntropyWords(String output, double para) throws IOException, InterruptedException
	{		
		int[] counts = new int[m_instances.numAttributes()];
		
		for(int k=0; k<counts.length; k++)
		{
			counts[k] = 0;
		}
		
		for(int j=0; j<m_instances.numInstances(); j++)
		{
			Instance ins = m_instances.instance(j);
			
			for(int i=0; i<m_instances.numAttributes(); i++)
			{
				if(ins.attribute(i).isNumeric() && ins.value(i) == 0)
				{
					counts[i] += 1;
				}			
			}
		}
		
		Map<String, Double> collection = new HashMap<String, Double>();
		
		for(int k=0; k<counts.length; k++)
		{	
			if(m_instances.attribute(k).isNumeric())
			{
				double px = ((double)counts[k])/((double)m_instances.numInstances());
			 
				double entropy = px*Math.log(1/px)+(1-px)*Math.log(1/(1-px));

				collection.put(m_instances.attribute(k).name(), entropy);
			}
		}
		
		
        List<Map.Entry<String,Double>> list = new ArrayList<Map.Entry<String,Double>>(collection.entrySet());
        
        Collections.sort(list, new Comparator<Map.Entry<String,Double>>(){
        	public int compare(Map.Entry<String,Double> o1, Map.Entry<String,Double> o2)
        	{
        		
        		if(o1.getValue() > o2.getValue())
        		{
        			return -1;
        		}else if(o1.getValue() < o2.getValue()) 
        		{
        			return 1;
        		}else
        		{
        			return 0;
        		}
        }
        });
		
        //PrintWriter out = new PrintWriter(new FileWriter(output),true);//PrintWriter(output);
        
        for(int i=0; i<(int)para; i++)
        {
        	System.out.println(list.get(i).getKey());//+" "+list.get(i).getValue());
        	//out.flush();
        }
		
       //Thread.sleep(30000);
		//out.close();
	}
	
	/**
	 * Frequency: appear in how many documents (percentage). 
	 * 
	 * @param out
	 * @param para
	 * @throws Exception 
	 */
	private void SelectByFrequency(String output, double para) throws Exception
	{
				
		String[] options = new String[2];
		options[0] = "-R";
		
		int[] counts = new int[m_instances.numAttributes()];
		
		for(int k=0; k<counts.length; k++)
		{
			counts[k] = 0;
		}
		
		for(int j=0; j<m_instances.numInstances(); j++)
		{
			Instance ins = m_instances.instance(j);
			
			for(int i=0; i<m_instances.numAttributes(); i++)
			{
				if(ins.attribute(i).isNumeric() && ins.value(i) == 0)
				{
					counts[i] += 1;
				}			
			}
		}
		
		double threshold = 0;
		
		if(para<=1)
		{
			threshold = para; 
		}else
		{
			threshold = computeNewFrequencyThreshold(counts, para);
		}
		
		String index = null;
		int number = 0;
			
		for(int k = 0; k<counts.length; k++)
		{
			double frequency = 1- counts[k]/((double)m_instances.numInstances());
			
			if(frequency < threshold)
			{
				if(index == null)
				{
					index = (k+1)+"";
				}else
				{
					index += ","+(k+1);
				}
				
				number ++;
			}
		}
		
		System.out.println("Words total: "+ m_instances.numAttributes()+ "; Words left: "+ (m_instances.numAttributes()-number));
		
		options[1] = index;
		
		removeWords(output, options, false);
	}
	
	private void selectByFreqRange(String output, int start, int end) throws Exception
	{
		
		String[] options = new String[2];
		options[0] = "-R";
		
		int[] counts = new int[m_instances.numAttributes()];
		
		for(int k=0; k<counts.length; k++)
		{
			counts[k] = 0;
		}
		
		for(int j=0; j<m_instances.numInstances(); j++)
		{
			Instance ins = m_instances.instance(j);
			
			for(int i=0; i<m_instances.numAttributes(); i++)
			{
				if(ins.attribute(i).isNumeric() && ins.value(i) == 0)
				{
					counts[i] += 1;
				}			
			}
		}
		
		double start_threshold = computeNewFrequencyThreshold(counts, start);
		double end_threshold = computeNewFrequencyThreshold(counts, end);
		
		String index = null;
		int number = 0;
			
		for(int k = 0; k<counts.length; k++)
		{
			double frequency = 1- counts[k]/((double)m_instances.numInstances());
			
			if(frequency < end_threshold || (frequency > start_threshold && frequency < 1))
			{
				if(index == null)
				{
					index = (k+1)+"";
				}else
				{
					index += ","+(k+1);
				}
				
				number ++;
			}
		}
		
		System.out.println("Words total: "+ m_instances.numAttributes()+ "; Words left: "+ (m_instances.numAttributes()-number));
		
		options[1] = index;
		
		removeWords(output, options, false);
		
	}
	
	/**
	 * 
	 * @param counts
	 * @param para
	 * @return
	 */
	private double computeNewFrequencyThreshold(int[] counts,  double para)
	{
		
		Map<String, Double> collection = new HashMap<String, Double>();
		
		for(int k=0; k<counts.length; k++)
		{
			if(m_instances.attribute(k).isNumeric())
			{
				double frequency = 1 - ((double)counts[k])/((double)m_instances.numInstances());
				collection.put(m_instances.attribute(k).name(), frequency);
			}
		}
		
		return sortCollections(collection, para);

	}
	
	private double sortCollections(Map<String, Double> collection, double para)
	{
		
        List<Map.Entry<String,Double>> list = new ArrayList<Map.Entry<String,Double>>(collection.entrySet());
        
        Collections.sort(list, new Comparator<Map.Entry<String,Double>>(){
        	public int compare(Map.Entry<String,Double> o1, Map.Entry<String,Double> o2)
        	{
        		if(o1.getValue() > o2.getValue())
        		{
        			return -1;
        		}else if(o1.getValue() < o2.getValue()) 
        		{
        			return 1;
        		}else
        		{
        			return 0;
        		}
        }
        });
		
        return list.get(((int)para)-1).getValue();	
	}
	
	/**
	 * Entropy: H(X) = \sum_p(X)log(1/P(X)). P(X) the probability of  
	 * 
	 * @param out
	 * @param para
	 * @throws Exception 
	 */
	private void SelectByEntropy(String output, double para) throws Exception
	{
		String[] options = new String[2];
		options[0] = "-R";
		
		int[] counts = new int[m_instances.numAttributes()];
		
		for(int k=0; k<counts.length; k++)
		{
			counts[k] = 0;
		}
		
		for(int j=0; j<m_instances.numInstances(); j++)
		{
			Instance ins = m_instances.instance(j);
			
			for(int i=0; i<m_instances.numAttributes(); i++)
			{
				if(ins.attribute(i).isNumeric() && ins.value(i) == 0)
				{
					counts[i] += 1;
				}			
			}
		}
		
		//int threshold = (int) (m_instances.numInstances()*(1-para)); 
		
		double threshold = 0;
		
		if(para<=1)
		{
			threshold = para; 
		
		}else
		{
			threshold = computeNewEntropyThreshold(counts, para);
		}
		
		String index = null;
		int number = 0;
			
		for(int k = 0; k<counts.length; k++)
		{
			if(m_instances.attribute(k).isNumeric())
			{
				 double px = ((double)counts[k])/((double)m_instances.numInstances());	 
				 double entropy = px*Math.log(1/px)+(1-px)*Math.log(1/(1-px));
				
				if(entropy <= threshold)
				{
					if(index == null)
					{
						index = (k+1)+"";
					}else
					{
						index += ","+(k+1);
					}
					
					number ++;
				}
			}
		}
		
		System.out.println("Words total: "+ m_instances.numAttributes()+ "Words left: "+ (m_instances.numAttributes()-number));
		
		options[1] = index;
		
		removeWords(output, options, false);		
	}
	
	/**
	 * 
	 * @param counts
	 * @param para
	 * @return
	 */
	private double computeNewEntropyThreshold(int[] counts, double para)
	{
		Map<String, Double> collection = new HashMap<String, Double>();
		
		for(int k=0; k<counts.length; k++)
		{
			if(m_instances.attribute(k).isNumeric())
			{
				 double px = ((double)counts[k])/((double)m_instances.numInstances());
				 double entropy = px*Math.log(1/px)+(1-px)*Math.log(1/(1-px));

				collection.put(m_instances.attribute(k).name(), entropy);
			}
		}
		
		return sortCollections(collection, para);
	}
	
	
	/**
	 * TF-IDF: term frequency-inverse document frequency. 
	 * 
	 * @param out
	 * @param para
	 * @throws Exception 
	 */
	private void SelectByTFIDF(String output, double para) throws Exception
	{
		String[] options = new String[2];
		options[0] = "-R";
		
		int[] counts = new int[m_instances.numAttributes()];
		int[] total =  new int[m_instances.numInstances()];
		
		for(int k=0; k<counts.length; k++)
		{
			counts[k] = 0;
		}
		
		for(int k=0; k<m_instances.numInstances(); k++)
		{
			total[k] = 0;
		}
		
		for(int j=0; j<m_instances.numInstances(); j++)
		{
			Instance ins = m_instances.instance(j);
			
			for(int i=0; i<m_instances.numAttributes(); i++)
			{
				if(ins.attribute(i).isNumeric() && ins.value(i) == 0)
				{
					counts[i] += 1;
				}
				
				if(ins.attribute(i).isNumeric())
				{
					total[j] += ins.value(i);
				}
			}
		}
		
		//int threshold = (int) (m_instances.numInstances()*(1-para)); 
		
		double[][] tfidf = new double[m_instances.numInstances()][m_instances.numAttributes()];
		double[] averageTFIDF = new double[m_instances.numAttributes()];
		
		for(int i=0; i<m_instances.numAttributes(); i++)
		{
			averageTFIDF[i] = 0;
		}
		
		for(int j=0; j<m_instances.numInstances(); j++)
		{
			Instance ins = m_instances.instance(j);
			
			for(int i=0; i<m_instances.numAttributes(); i++)
			{
				if(ins.attribute(i).isNumeric())
				{
					tfidf[j][i] = ins.value(i)/total[j]*Math.log((m_instances.numInstances())/(m_instances.numInstances()-counts[i]));
					averageTFIDF[i] += tfidf[j][i]; 
				}
			}
		}
		
		Map<String, Double> collection = new HashMap<String, Double>();
		
		for(int i=0; i<m_instances.numAttributes(); i++)
		{
			if(m_instances.attribute(i).isNumeric())
			{
				collection.put(String.valueOf(i), averageTFIDF[i]);
			}
		}
		
		List<Map.Entry<String, Double>> list = sort(collection);
		
		for(int i=0; i<(int)para; i++)
		{
			System.out.println(m_instances.attribute(Integer.parseInt(list.get(i).getKey())).name());
		}
			
		String index = "1,2";
		
		for(int k = 0; k< (int)para; k++)
		{	
			if(index == null)
			{
				index += Integer.parseInt(list.get(k).getKey())+1;
			}else
			{
				index += ","+ (Integer.parseInt(list.get(k).getKey())+1);
			}
		}
		
		options[1] = index;
		
		removeWords(output, options, true);
	
		
	}
	
	/**
	 * Keep the words we want.
	 * 
	 * @param out
	 * @param options
	 * @throws Exception 
	 */
	private void removeWords(String output, String[] options, boolean inverse) throws Exception
	{
        Remove remove = new Remove(); 
        
        if(inverse)
        {
            remove.setAttributeIndices(options[1]);
            remove.setInvertSelection(true);
        }else
        {
        	remove.setOptions(options); 
        }
        
        remove.setInputFormat(m_instances); 
        
        Instances newData = Filter.useFilter(m_instances, remove);
        
        ArffSaver saver = new ArffSaver();
        saver.setInstances(newData);
        saver.setFile(new File(output));
        saver.writeBatch();
		
	}
	
	private List<Map.Entry<String,Double>> sort(Map<String, Double> collection)
	{
	       List<Map.Entry<String,Double>> list = new ArrayList<Map.Entry<String,Double>>(collection.entrySet());
	        
	        Collections.sort(list, new Comparator<Map.Entry<String,Double>>(){
	        	public int compare(Map.Entry<String,Double> o1, Map.Entry<String,Double> o2)
	        	{
	        		if(o1.getValue() > o2.getValue())
	        		{
	        			return -1;
	        		}else if(o1.getValue() < o2.getValue()) 
	        		{
	        			return 1;
	        		}else
	        		{
	        			return 0;
	        		}
	        }
	        });
	        
	        return list;
	}

}

