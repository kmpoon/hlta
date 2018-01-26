package org.latlab.learner;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntIterator;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;


import org.latlab.util.DataSet;
import org.latlab.util.Variable;

import org.mymedialite.data.EntityMapping;
import org.mymedialite.data.IEntityMapping;
import org.mymedialite.data.IPosOnlyFeedback;
import org.mymedialite.io.ItemData;
import org.mymedialite.util.Random;

import com.opencsv.CSVReader;

public class SparseDataSet {

	/**
	 * @param args
	 */
	/**
	 * indicated whether the datacases indexes have been randomized
	 */
	boolean isRandomised = false;
	
	/**
	 * Each index stores the original datacase index. The order of the array is our
	 * Randomized order
	 * 
	 */
	ArrayList<Integer> _randomToOriginalDataCaseIndexMap;
	
	/**
	 * Total number of datacases
	 */
	int _totalDatacases;
	
	
	/**
	 * Map between variable index in _VariablesSet and variable name(external ID)
	 */
	HashMap<String,Integer> _mapNameToIndex;
	
	/**
	 * The sparse Dataset
	 */
	IPosOnlyFeedback SDataSet;
	
	/**
	 * Store the mapping from datacase ID from input file to internal user ID
	 * Changed to non-static by Leung Chun Fai
	 */
	IEntityMapping _user_mapping;  
	
	/**
	 * Store the mapping between variable name from file and internal item ID
	 * Changed to non-static by Leung Chun Fai
	 */
	IEntityMapping _item_mapping;  
	
	
	/**
	 * Store the variables for the dense dataset
	 */
	Variable[] _VariablesSet;
	
	/**
	 * Constructor to load the SparseDataSet from the tuple dataset format
	 * @param DataFileName the dataset converted by convertCSVtoTuples()
	 * @throws Exception 
	 */
	public SparseDataSet(String DataFileName) throws Exception{
		
        _user_mapping      = new EntityMapping();  
        
        _item_mapping      = new EntityMapping();
		
		SDataSet = ItemData.read(DataFileName, _user_mapping, _item_mapping, false); // false means include first line
	
		_totalDatacases = SDataSet.maxUserID()+1; 
		
		/*
		 * Creating variables that correspond to the variable name(external ID). These variables
		 * will be used when we convert from sparse to dense representation
		 */
        
		//Since we are working with implicit feedback the manifest variables will have 2 states: s0,s1
        ArrayList<String> states = new ArrayList<String>();
        states.add("s" + 0);
        states.add("s" + 1);
        
        // Make name to index MAP
        _mapNameToIndex = new  HashMap<String,Integer>();
        _VariablesSet = new Variable[SDataSet.maxItemID()+1];
        
        // creating variables corresponding to the external ID and adding them
        int i = 0;
        for(Integer internal_ID : SDataSet.allItems()){
        	String item_external_ID = _item_mapping.toOriginalID(internal_ID);
        	Variable var = new Variable(item_external_ID, states);
        	_VariablesSet[i] = var;
        	_mapNameToIndex.put(item_external_ID,i);
        	i++;
        }
        
   //     Arrays.sort(_VariablesSet); // check if this is needed , if need then change mapNameToIndex 
	}
	
	/**
	 * Convert into SparseDataSet without IO
	 * For Data.scala call
	 * 
	 * @author Leung Chun Fai
	 * 
	 * @param userMapping
	 * @param itemMapping
	 * @param sDataSet
	 * @throws Exception
	 */
	public SparseDataSet(Variable[] variables, IEntityMapping userMapping, IEntityMapping itemMapping, IPosOnlyFeedback sDataSet) throws Exception{
		
        _user_mapping      = userMapping;  
        
        _item_mapping      = itemMapping;
		
		SDataSet = sDataSet;
	
		_totalDatacases = SDataSet.maxUserID()+1;
        
        // Make name to index MAP
        _mapNameToIndex = new  HashMap<String,Integer>();
        _VariablesSet = variables;//new Variable[SDataSet.maxItemID()+1];
        
        // creating variables corresponding to the external ID and adding them
        int i = 0;
        for(Variable variable : variables){
        	_mapNameToIndex.put(variable.getName(),i);
        	i++;
        }
        
   //     Arrays.sort(_VariablesSet); // check if this is needed , if need then change mapNameToIndex 
	}
	
	/*public static void main(String[] args) {
		// TODO Auto-generated method stub
 
		try {
			convertCSVtoTuples(args[0],args[1]);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}*/
	
	/**
	 * Number of distinct datacases
	 * 
	 * @author Leung Chun Fai
	 * 
	 * @return
	 */
	public int getNumOfDatacase(){
		return _totalDatacases;
	}
	
	/**
	 * _VariablesSet is originally a private attribute
	 * Becareful not to change its element value
	 * 
	 * @author Leung Chun Fai
	 * 
	 * @return
	 */
	public Variable[] getVariables(){
		return _VariablesSet;
	}
	
	/**
	 * Converts the csv input data , where rows represent datacase and columns represent variables, to 
	 * the form of tuples. For Example in the original dataset if each datacase is a documnet, then we would 
	 * have tuples like (doc,word1),(doc(word2)...
	 * Here word1 represents the name of the corresponding variable in the .csv file
	 * We then write this converted dataset to a file for future use.
	 * @param DataSetNameCsv .csv datafile name
	 * @param OutputDataSetPath the path where the converted input data format will be saved
	 * @throws IOException 
	 */
	
	public static void convertCSVtoTuples(String DataSetNameCsv, String OutputDataSetPath) throws IOException{
		
		PrintWriter out = new PrintWriter(OutputDataSetPath+File.separator+"SparseDataInputFormat.txt");
		
		// get the reader, split char = , and quotation char = " 
		// We start from line 0
		CSVReader reader = new CSVReader(new FileReader(DataSetNameCsv), ',', '"', 0);
		
		Iterator<String[]> iter = reader.iterator();
		
		//Line 0 should contain the variable names so read them first
		String[] varName = iter.next();
		
		int row_id = 1;
		while(iter.hasNext()){
			
			String[] datacase = iter.next();
			
			for(int i = 0 ; i < datacase.length ; i++){
				// For each datacase get the variables which are 1
				if(Integer.parseInt(datacase[i]) == 1){
					out.println(Integer.toString(row_id)+","+varName[i]);// write a (doc,varName) tuple
				}
			}
			
			row_id++;
		}
		
		out.close();
		reader.close();
	} 

	/**
	 * Randomize the order of the datacases. create an list with size _totalDatacases and in which
	 * each entry creates a randomized index to original datacase
	 */
	private void randomiseDatacaseIndex(){
		if(isRandomised == false){
			// First build the index if it does not exist or is of less size
			if (_randomToOriginalDataCaseIndexMap == null || _randomToOriginalDataCaseIndexMap.size() != _totalDatacases) {
				_randomToOriginalDataCaseIndexMap = new ArrayList(_totalDatacases);
			      for (int index = 0; index < _totalDatacases; index++)
			    	  _randomToOriginalDataCaseIndexMap.add(index, index);
			    }
			// Then randomize it
			    Collections.shuffle(_randomToOriginalDataCaseIndexMap, Random.getInstance());
			    isRandomised = true;
		
		}
		else{ // other wise if required to re-randomize
			isRandomised = false;
			_randomToOriginalDataCaseIndexMap = null;
			randomiseDatacaseIndex();
		}
	}
	// give a dense dataset of M datacases after randomization
	/**
	 * Return a dense dataset of size batchSize
	 * Assume that there are no missing values, later can take it as a param.
	 */
	public DataSet GiveDenseBatch(int batchSize){
		return SparseToDense(batchSize,0);
	}
	
	/**
	 * Return the whole dataset in dense form
	 * @return
	 */
	public DataSet getWholeDenseData(){
		
		//build index
		if (_randomToOriginalDataCaseIndexMap == null || _randomToOriginalDataCaseIndexMap.size() != _totalDatacases) {
			_randomToOriginalDataCaseIndexMap = new ArrayList(_totalDatacases);
		      for (int index = 0; index < _totalDatacases; index++)
		    	  _randomToOriginalDataCaseIndexMap.add(index, index);
		 }
		//force not to randomize
		boolean origFlag = isRandomised;
		isRandomised = true;
		DataSet wholeDenseData  = SparseToDense(_totalDatacases,0);
		isRandomised = origFlag;
		return wholeDenseData;
	}
	
	public int getTotalWeight(){
		return _totalDatacases;
	}
	
	private DataSet SparseToDense(int batchSize, int start){
		
		if(isRandomised == false){
			randomiseDatacaseIndex();
		}
		/*
		 *  convert from sparse to dense
		 */
		
		// create a dataset over variables corresponding to the external IDs
		DataSet Dense = new DataSet(_VariablesSet);
		
		// adding datacases
		for(int i = start ; i < batchSize + start ; i++){
			
			int[] states = new int[_VariablesSet.length];
			IntCollection row = SDataSet.userMatrix().get(_randomToOriginalDataCaseIndexMap.get(i));
			
			// Filling in the positive entries
			IntIterator iter = row.iterator();
			while(iter.hasNext()){
			    int internal_ID = iter.nextInt(); // the id of the item
			    String name = _item_mapping.toOriginalID(internal_ID);
			    states[_mapNameToIndex.get(name)] = 1;
			}
			Dense.addDataCase(states, 1);
			
		}
		
		return Dense;
	}
	
/**
 * 
 * Return a batch of size batchSize and batchNumber specified by batchNumber. If the last partition is less than
 * batchSize then we just throw it away
 * @param batchSize the size of batch in sparse dataset
 * @param batchNumber batchNumber starts from 0
 * @return
 */
	public DataSet GetNextPartition(int batchSize, int batchNumber){// (int batchSize){
		int Start = ((batchNumber) * batchSize);
		if(_totalDatacases - Start >= batchSize){
			return SparseToDense(batchSize,Start);
		}
		else
			return null;
	}
	
	
	/**
	 * 
	 * Return the number of batches 
	 */
	 public int getNumofBatches (int batchSize){
		 
		return (int) Math.floor(_totalDatacases/batchSize);
		 
	 }
}
