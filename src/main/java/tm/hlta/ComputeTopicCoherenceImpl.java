package tm.hlta;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.latlab.util.DataSet;
import org.latlab.util.DataSet.DataCase;
import org.latlab.util.DataSetLoader;
import org.latlab.util.Variable;

public class ComputeTopicCoherenceImpl {
	
	public static void main(String[] args) throws IOException, Exception
	{
		if(args.length != 4)
		{
			System.out.println("java ComputeTopicCoherence topic-file data M random");
			System.exit(-1);
		}
		
		ComputeTopicCoherenceImpl computer = new ComputeTopicCoherenceImpl();
		computer.run(args);
	}

	public static double compute(int M, DataSet data, String[][] topics)
	{
//		BufferedReader topic = new BufferedReader(new FileReader(args[0]));
//		DataSet data = new DataSet(DataSetLoader.convert(args[1]));
//		int M = Integer.parseInt(args[2]);
//		int random = Integer.parseInt(args[3]);
//		
//		HashMap<Integer, String[]> topics = readTopics(topic, M);
		int numTopics = topics.length;
		
//		boolean[] bool = generateRandom(numTopics, random);
		
		double totalCoherence = 0;
		int topicSelect=0;
		for(int i=0; i<numTopics; i++)
		{
//			if(!bool[i])
//				continue;
				
			String[] thisTopic = topics[i];
			double coherenceScore = 0;
			
			for(int j=1; j<M; j++)
			{
				for(int k=0; k<j;k++)
				{
					String word1 = thisTopic[j];
					String word2 = thisTopic[k];
					coherenceScore += computeCoherenceScore(data, word1, word2);
				}
			}
			
			totalCoherence += coherenceScore;
			topicSelect++;
		}
		
		return totalCoherence/topicSelect;
		//		System.out.println("Per-topic coherence: "+totalCoherence/topicSelect+"  , num of topics: "+topicSelect);		
	}


	private void run(String[] args) throws IOException, Exception
	{
		BufferedReader topic = new BufferedReader(new FileReader(args[0]));
		DataSet data = new DataSet(DataSetLoader.convert(args[1]));
		int M = Integer.parseInt(args[2]);
		int random = Integer.parseInt(args[3]);
		
		HashMap<Integer, String[]> topics = readTopics(topic, M);
		int numTopics = topics.size();
		
		boolean[] bool = generateRandom(numTopics, random);
		
		double totalCoherence = 0;
		int topicSelect=0;
		for(int i=0; i<numTopics; i++)
		{
//			if(!bool[i])
//				continue;
				
			String[] thisTopic = topics.get(i);
			double coherenceScore = 0;
			
			for(int j=1; j<M; j++)
			{
				for(int k=0; k<j;k++)
				{
					String word1 = thisTopic[j];
					String word2 = thisTopic[k];
					coherenceScore += computeCoherenceScore(data, word1, word2);
				}
			}
			
			totalCoherence += coherenceScore;
			topicSelect++;
		}
		
		System.out.println("Per-topic coherence: "+totalCoherence/topicSelect+"  , num of topics: "+topicSelect);		
	}
	
	private boolean[] generateRandom(int range, int numRand)
	{
		Random random = new Random ();  
		boolean[]  bool = new boolean[range];  
		int randInt = 0;  
		for(int j = 0; j < numRand ; j++) {   
			do{  
				randInt  = random.nextInt(range);  
			}while(bool[randInt]);   
			bool[randInt] = true;
		}
		return bool;
	}
	
	
	public static double computeCoherenceScore(DataSet data, String word1, String word2)
	{
		Variable[] var = data.getVariables();
		
		Variable wordVarOne = null, wordVarTwo = null;
		
		for(int i=0; i<var.length; i++)
		{
			if(var[i].getName().equalsIgnoreCase(word1))
			{
				wordVarOne = var[i];
			}else if(var[i].getName().equalsIgnoreCase(word2))
			{
				wordVarTwo = var[i];
			}
		}
		
		ArrayList<Variable> list = new ArrayList<Variable>();
		list.add(wordVarOne);
		list.add(wordVarTwo);
		
		DataSet smallData = data.project(list);
		
		Map<Variable, Integer> map = smallData.createVariableToIndexMap();
		int index1 = map.get(wordVarOne);
		int index2 = map.get(wordVarTwo);
		
		double joint = 0, single = 0;
		
		for(DataCase d : smallData.getData())
		{
			int[] states = d.getStates();
			
			if(states[index1]==1 && states[index2] ==1)
			{
				joint = d.getWeight();
			}
			
			if(states[index2] == 1)
			{
				single += d.getWeight();
			}
		}
		
		return Math.log((joint+1)/single);
	}
	
	private HashMap<Integer, String[]> readTopics(BufferedReader topic, int M) throws IOException
	{
		HashMap<Integer, String[]> topics = new HashMap<Integer, String[]>();
		
		String line;
		int index = 0;
		while((line = topic.readLine()) != null)
		{
			String[] word = line.split(" +");
			String[] thisTopic = new String[M];
			
			for(int i=0; i<M; i++)
			{
				 thisTopic[i] = word[i].toLowerCase();
			}
			
			topics.put(index++, thisTopic);
		}
		
		return topics;
	}
}
