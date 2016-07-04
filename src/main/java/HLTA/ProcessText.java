package HLTA;

  

import java.io.IOException;  
  



import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;

import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
  
public class ProcessText {  
    public static void main(String[] args){      
        try {  
           
	        	if(args.length != 2)
	    		{
	    			System.out.println("usage: java ProcessText inputfile.txt outputfile.csv");
	    			System.exit(-1);
	    		}
	        	
	        	String _inputfile = args[0];
	        	String _outputfile = args[1];
	        	
	        	InputStream input = new FileInputStream(_inputfile);
	   		 
	     		@SuppressWarnings("resource")
	     		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(input, "UTF-8"));   
	            BufferedWriter BWriter = new  BufferedWriter(new FileWriter(_outputfile));
	            
	            Map<String, Map<Integer, Integer>> map= new HashMap<String, Map<Integer,Integer>>();  
		        String word="";  
		     
		        String Abs = null;  
		        int i = 0;
		        
		        
		        while ((Abs = bufferedReader.readLine()) != null){
      
                    String regEx="[`~!@#$%^&*()+=|{}':;',\\[\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";  
    		        Pattern   p   =   Pattern.compile(regEx);     
    		        Matcher   m   =   p.matcher(Abs);     
    		        Abs=   m.replaceAll(" ").trim();//Remove all the symbols
    		      //  System.out.println(Abs);   
    		        
    		        String abs = Abs.toLowerCase();//turn to lowcase
    		        
    		        
    		        Pattern pattern=Pattern.compile("[a-z]+ ");  
    		        Matcher matcher=pattern.matcher(abs);  
    		        int count; 
    		        while(matcher.find()){  
    		            word=matcher.group();  
    		            if(map.containsKey(word)){  
                            Map<Integer, Integer> doc = map.get(word);
    		                if(doc.containsKey(i)){
    		                	count = doc.get(i);
    		                	doc.put(i, count+1);} 
    		                else{
    		                	doc.put(i,1);}
    		                map.put(word, doc);
    		            }else{
    		            	Map<Integer, Integer> newword = new HashMap<Integer,Integer>();
    		            	newword.put(i, 1);
    		                map.put(word, newword);  
    		            }  
    		        }  
    		       i =i+1;  
             }  
             
             //写入csv文件 
		             int y;
		             int items  = i;
		             Set<String> set = map.keySet();
		             Iterator<String> it = set.iterator();
		
		             while (it.hasNext()){
		                     String key = (String) it.next();
		                     BWriter.write(key + "," ); 
		            
		            	  
		                     for(y=0;y<items;y++){
		            	    	 if(map.get(key).containsKey(y)){
		            	    		 BWriter.write(1 + "," ); 
		                   		 }
		            	    	 else{
		            	    		 BWriter.write(0 + "," );
		            	    	 }
		            	     }
		                     BWriter.newLine();
		            }
		          
		             System.out.println("Done!");
		        
		             BWriter.close();
        
        } catch (IOException e) {  
            e.printStackTrace();  
        }  
          
    }  
}  