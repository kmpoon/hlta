
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.latlab.graph.AbstractNode;
import org.latlab.graph.DirectedNode;
import org.latlab.graph.Edge;
import org.latlab.io.Parser;
import org.latlab.io.bif.BifParser;
import org.latlab.learner.ParallelEmLearner;
import org.latlab.model.BeliefNode;
import org.latlab.model.LTM;
import org.latlab.reasoner.CliqueTreePropagation;
import org.latlab.util.DataSet;
import org.latlab.util.DataSet.DataCase;
import org.latlab.util.DataSetLoader;
import org.latlab.util.Function;
import org.latlab.util.Utils;
import org.latlab.util.Variable;

public class ExtractNarrowTopics_LCM{
	
	private LTM _model;
	private CliqueTreePropagation _posteriorCtp;

	private HashMap<Integer, HashSet<String>> _varDiffLevels;
	private int _MaxBaseSize;
	private String _output;
	private boolean _outProbNum = false;
	private boolean _outBackground = false;
	private CliqueTreePropagation _posteriorCtpSub;
	private DataSet _subData;
	private ArrayList<Variable> _collectionVar;
	private HashMap<Integer, Boolean> _semanticBaseStart;
	private HashMap<String, String> _semanticBaseString;
    private HashMap<String, String[]> _node2topics ;
	
	public static void main(String[] args) throws IOException, Exception
	{
		if(args.length != 6)
		{
			System.out.println("usage: java NarrowTopics model data outputpath outputNum outputBackground MaxBaseSize ");
			System.exit(-1);
		}
		
		ExtractNarrowTopics_LCM out = new ExtractNarrowTopics_LCM();
		out.initialize(args);
		out.run();

	}
	
	private void initialize(String[] args) throws IOException, Exception
	{
		_model = new LTM();
		Parser parser = new BifParser(new FileInputStream(args[0]),"UTF-8");
		parser.parse(_model);
		_posteriorCtp = new CliqueTreePropagation(_model);
		_posteriorCtp.propagate();
		_subData = new DataSet(DataSetLoader.convert(args[1]));
		_output = args[2];
		_outProbNum         = args[3].equalsIgnoreCase("yes")?true:false;
		_outBackground      = args[4].equalsIgnoreCase("yes")?true:false;
		_MaxBaseSize = Integer.parseInt(args[5]);
		processVariables();
		_semanticBaseStart  = new HashMap<Integer, Boolean>();
		_semanticBaseString = new HashMap<String, String>();
		_node2topics = new HashMap<String, String[]>();
		for(int i=1; i<_varDiffLevels.size(); i++)
		{
			_semanticBaseStart.put(i, true);
		}
	}
	
	
	private void run() throws IOException, Exception{
		
		System.out.println("Relearn the model...");
		extractTopics();
		System.out.println("Model has been reconstructed.");
		System.out.println("Output topic table ...");
		PrintOutAllTopics();
		System.out.println("Done!");
		
	}
	
	
	
	
	private void extractTopics() throws FileNotFoundException, UnsupportedEncodingException {
		// TODO Auto-generated method stub
		
		for(int VarLevel = 1; VarLevel<_varDiffLevels.size(); VarLevel++){
			
			for(String latent : _varDiffLevels.get(VarLevel)){

				Set<DirectedNode> setNode = new HashSet<DirectedNode>();
			    ArrayList<Variable> setVars = new ArrayList<Variable>();
				Set<DirectedNode> childSet = new HashSet<DirectedNode>(_model.getNodeByName(latent).getChildren());
				//Compute setNode and built a subtree rooted at latent.
			    LTM subtree = new LTM();
			    subtree.addNode(_model.getNodeByName(latent).getVariable());
				for(DirectedNode node : childSet)
				{
					Variable var = ((BeliefNode)node).getVariable();
					
					if(_varDiffLevels.get(VarLevel).contains(var.getName()))
						continue;
					if(((BeliefNode)node).isLeaf())
					{
						setNode.add((DirectedNode) node);
						setVars.add(((BeliefNode)node).getVariable());
					}else
					{   
						for(AbstractNode nodeDes : _model.getNode(var).getDescendants())
						{
							if(((BeliefNode)nodeDes).isLeaf())
							{
								setNode.add((DirectedNode) nodeDes);
								setVars.add(((BeliefNode)nodeDes).getVariable());
							}
						}
					}
				}
				
				
				if(setVars.size()<3){
					double[] count = new double[4];
					NumberFormat nf = NumberFormat.getInstance();
					nf.setMaximumFractionDigits(3);
					nf.setMinimumFractionDigits(3);
					ArrayList<Variable> vars2 = new ArrayList(setVars);
					DataSet leafdata = _subData.project(vars2, false);
					
					for(DataCase d : leafdata.getData()){
						int[] dstates = d.getStates();
						double dweight = d.getWeight();
						count[dstates[0]*2+dstates[1]] = +dweight;
					}
					double y01[] = new double[2];
					y01[0]= count[0]/leafdata.getTotalWeight();
					y01[1] = 1-y01[0];
					String base = "";
					String[] topics = {nf.format(y01[0]), nf.format(y01[1])};
					for(int i = 0; i<vars2.size();i++){
						base += vars2.get(i).getName()+" ";
						double[] cell = new double[4];
						//check!!!
						cell[0] = 1; cell[1] = count[i+1]/(leafdata.getTotalWeight()-count[0]);
						cell[2] = 0; cell[3] = 1-cell[1];
						if(_outProbNum){
							topics[0] += " "+ vars2.get(i).getName()+" "+ nf.format(cell[2]);
							topics[1] += " "+ vars2.get(i).getName()+" "+ nf.format(cell[3]);
						}else{
							topics[0] += " "+ vars2.get(i).getName();
							topics[1] += " "+ vars2.get(i).getName();
						}
	
					}
					System.out.println(latent+" "+ base);
					_semanticBaseString.put(latent,base);
					_node2topics.put(latent, topics);
					
				}else{
					
						List<Map.Entry<Variable,Double>> globallist =  SortChildren(_model.getNodeByName(latent).getVariable(), setNode,_posteriorCtp);

						//use the same base words as the global model
						setNode.clear();
						String words = "";
						_collectionVar = new ArrayList<Variable>();
						int size = Math.min(globallist.size(), _MaxBaseSize);
						for(int ind=0;ind<size;ind++){
							subtree.addNode(globallist.get(ind).getKey());
							subtree.addEdge(subtree.getNode(globallist.get(ind).getKey()), subtree.getNodeByName(latent));
							setNode.add(subtree.getNode(globallist.get(ind).getKey()));
							_collectionVar.add(globallist.get(ind).getKey());
							words += globallist.get(ind).getKey().getName()+" ";
						}
					
						_semanticBaseString.put(latent, words);
					
					   
				
						DataSet subData =_subData.project(new ArrayList<Variable>(subtree.getManifestVars()));
						subData.synchronize(subtree);
					 
						//System.out.println("Run EM on submodel, reorder the states of the root node");
						ParallelEmLearner emLearner = new ParallelEmLearner();
						emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
						emLearner.setMaxNumberOfSteps(64);
						emLearner.setNumberOfRestarts(100);
						emLearner.setReuseFlag(false);
						emLearner.setThreshold(0.01);
					    
						subtree = (LTM) emLearner.em(subtree, subData);
						
						
						
						_posteriorCtpSub = new CliqueTreePropagation(subtree);
						_posteriorCtpSub.propagate();
						
						List<Map.Entry<Variable,Double>> list =  SortChildren(subtree.getNodeByName(latent).getVariable(), setNode,_posteriorCtpSub);
						
						//reorder the state
						//here the setNode has been updated to all the leaf nodes in the subtree (not all the leaf nodes in the global model)
						subtree = reorderStates(subtree,subtree.getNodeByName(latent).getVariable(), setNode,list);
						
						Function p = _posteriorCtpSub.computeBelief(subtree.getNodeByName(latent).getVariable());					     
						
						_posteriorCtpSub.clearEvidence();
						_posteriorCtpSub.propagate();
						
						Variable[] latentArray = new Variable[1];
						latentArray[0] = subtree.getNodeByName(latent).getVariable();
						
						int[] states = new int[1];
								
						HashMap<Integer, HashMap<Variable,Double>> allTopics = new HashMap<Integer, HashMap<Variable, Double>>();

						//to save the topics for each node
						String[] topics = new String[subtree.getNodeByName(latent).getVariable().getCardinality()];
						for (int i = 0; i < subtree.getNodeByName(latent).getVariable().getCardinality(); i++) 
						{
							
							HashMap<Variable, Double> ccpd = new HashMap<Variable, Double>();

							states[0] = i;
							
							// set evidence for latent state
							_posteriorCtpSub.setEvidence(latentArray, states);
							_posteriorCtpSub.propagate();

							// compute posterior for each manifest variable
							for (Variable manifest : _collectionVar) 
							{
								Function posterior = _posteriorCtpSub.computeBelief(manifest);
								ccpd.put(manifest, posterior.getCells()[1]);
							}
							
							allTopics.put(i,ccpd);
						}
						
						

						for(int i=0; i< subtree.getNodeByName(latent).getVariable().getCardinality(); i++)
						{	
							HashMap<Variable, Double> ccpd = allTopics.get(i);
										
							NumberFormat nf = NumberFormat.getInstance();
							nf.setMaximumFractionDigits(3);
							nf.setMinimumFractionDigits(3);
							
							topics[i] = "";
							
							//System.out.print(latent+"-"+i+" "+nf.format(p.getCells()[i])+" ");
							System.out.print(latent+"-"+i+" ");
							topics[i] +=nf.format(p.getCells()[i])+" ";

							
							for(int k=0; k<ccpd.size(); k++)
							{
								topics[i]+=_collectionVar.get(k).getName()+" ";
								if(_outProbNum){
									topics[i]+= nf.format(ccpd.get(_collectionVar.get(k)))+" ";
								}
							}
							
							System.out.println(topics[i]);
						
						}
						
						_node2topics.put(latent, topics);
						
						
				}
			}
		}
		
	}

	
	private void PrintOutAllTopics() throws FileNotFoundException, UnsupportedEncodingException{
		System.out.println("Save to "+ _output+File.separator+"TopicsTable.html");
		HashSet<String> topLevel = _varDiffLevels.get(_varDiffLevels.size()-1);
		PrintWriter out = new PrintWriter(_output+File.separator+"TopicsTable.html");
		PrintHTMLhead_alltopics(_varDiffLevels.size()-1, out);
		HashMap<Integer, PrintWriter> outLevelByLevel = new HashMap<Integer, PrintWriter>();
		
		_posteriorCtp = new CliqueTreePropagation(_model);
		_posteriorCtp.propagate();
		
		for(int i=1; i<_varDiffLevels.size(); i++)
		{
			PrintWriter output = new PrintWriter(_output+File.separator+"TopicsTable-Level-"+i+".html");
			
			outLevelByLevel.put(i, output);
			PrintHTMLhead(i,_varDiffLevels.size()-1,output);
			
		}
			
		for(String var : topLevel)
		{
			Variable v = _model.getNodeByName(var).getVariable();
			
			DFSprintTopics(out, v, 0, _varDiffLevels.size()-1, outLevelByLevel);
			

		}
		
		for(int i=1; i<_varDiffLevels.size(); i++)
		{
			PrintWriter output = outLevelByLevel.get(i);
			PrintHTMLtail(i,output);
			output.close();
		}
		
        PrintHTMLtail(1, out);
		out.close();
		
	}
	
	

	private void DFSprintTopics(PrintWriter out, Variable var, int level, int VarLevel, HashMap<Integer, PrintWriter> outLevelByLevel) throws FileNotFoundException, UnsupportedEncodingException
	{	
		if(_varDiffLevels.get(0).contains(var.getName()))
			return;
		
		if(VarLevel != _varDiffLevels.size()-1 && _semanticBaseStart.get(VarLevel))
		{
			outLevelByLevel.get(VarLevel).println("<b style=\"font-size: 25;\">"+_semanticBaseString.get(_model.getNode(var).getParent().getName())+"</b>");
			_semanticBaseStart.put(VarLevel, false);
		}
		
		printTopicsForSingleVariable(out, var.getName(),level, VarLevel, outLevelByLevel);
		
		
		List<Map.Entry<Variable,Double>> list  =  SortChildren(var, _model.getNode(var).getChildren(),_posteriorCtp); 

		for(int i=0; i<list.size(); i++)
		{
			Variable child = list.get(i).getKey();
			//at the same level; continue;
			if(_varDiffLevels.get(VarLevel).contains(child.getName()))
				continue;
			
			DFSprintTopics(out, list.get(i).getKey(),level+1, VarLevel-1, outLevelByLevel);
		}
		
		if(VarLevel>1)
			outLevelByLevel.get(VarLevel-1).println();
		
		_semanticBaseStart.put(VarLevel-1, true);
	}
	


	private void processVariables()
	{
		_varDiffLevels = new HashMap<Integer, HashSet<String>>();
		
		Set<Variable> internalVar = _model.getInternalVars("tree");
		Set<Variable> leafVar     = _model.getLeafVars("tree");
		
		
		HashSet<String> levelOne = new HashSet<String>();
		for(Variable v : leafVar)
		{
			levelOne.add(v.getName());
		}
		_varDiffLevels.put(0, levelOne);
		
		int level=0;
		while(internalVar.size()>0)
		{
			HashSet<String> levelVar = _varDiffLevels.get(level);
			level++;
			
			HashSet<String> newSet = new HashSet<String>();
			for(String s : levelVar)
			{
				String parent = _model.getNodeByName(s).getParent().getName();
				
				if(parent != null)
				{
					internalVar.remove(_model.getNodeByName(parent).getVariable());
					newSet.add(parent);
				}
			}
			_varDiffLevels.put(level, newSet);
		}
	}
	
	private void printTopicsForSingleVariable(PrintWriter out,String latent, int level, int VarLevel, HashMap<Integer, PrintWriter> outLevelByLevel) throws FileNotFoundException, UnsupportedEncodingException{
		
		PrintWriter levelOut = outLevelByLevel.get(VarLevel);

		
		
		int startIndex = 0;
		if(!_outBackground)
		{
			startIndex = 1;
		}

    	for(int i=startIndex;i<_node2topics.get(latent).length;i++){
    		levelOut.print("<p style=\"text-indent:2em;\">&nbsp;&nbsp;&nbsp;&nbsp;");          //Revised by Peixian for HTML output

    		if(_model.getNodeByName(latent).isRoot()){
    			out.print("<p  name =\""+latent+"\" parent = \"none\" style=\"text-indent:"+3*(level)+"em;\">");           //Revised by Peixian for HTML output
    			
    		}else{
    			out.print("<p  name =\""+latent+"\" parent = \""+_model.getNodeByName(latent).getParent().getName()+"\" style=\"text-indent:"+3*(level)+"em;\">");           //Revised by Peixian for HTML output
    		}
    		out.print(" "+_node2topics.get(latent)[i]);
    		levelOut.print(" "+_node2topics.get(latent)[i]);   //Revised by Peixian for HTML output
    		
    		out.println("</p>");       //Revised by Peixian for HTML output
			levelOut.println("</p>");  
    	}
       
		
		
	
	}
	
	
	
	
	private List<Map.Entry<Variable, Double>> SortChildren(Variable var,
			Set<DirectedNode> nodeSet, CliqueTreePropagation ctp) {
		Map<Variable, Double> children_mi = new HashMap<Variable, Double>();

		for (DirectedNode node : nodeSet) {
			Variable child = ((BeliefNode) node).getVariable();
			double mi = computeMI(var, child, ctp);
			children_mi.put(child, mi);
		}

		List<Map.Entry<Variable, Double>> List =
				Utils.sortByDescendingOrder(children_mi);

		return List;
	}

	private double computeMI(Variable x, Variable y, CliqueTreePropagation ctp) {
		List<Variable> xyNodes = new ArrayList<Variable>();
		xyNodes.add(x);
		xyNodes.add(y);

		return Utils.computeMutualInformation(ctp.computeBelief(xyNodes));
	}
		
	
	private LTM reorderStates(LTM bn,
			Variable latent, Set<DirectedNode> setNode,List<Map.Entry<Variable, Double>> list ){
		// inference engine
		CliqueTreePropagation ctp = new CliqueTreePropagation(bn);
		ctp.clearEvidence();
		ctp.propagate();
		Variable[] latents = new Variable[1];
		int[] states = new int[1];

		latents[0] = latent;

		// calculate severity of each state
		int card = latent.getCardinality();
		double[] severity = new double[card];
		

		
		
		for (int i = 0; i < card; i++) {
			states[0] = i;

			ctp.setEvidence(latents, states);
			ctp.propagate();

			// accumulate expectation of each manifest variable
            //only use the first 3 for ording
			for (int c = 0;c<list.size();c++) {
				double[] dist = ctp.computeBelief(list.get(c).getKey()).getCells();

				for (int j = 1; j < dist.length; j++) {
					severity[i] += Math.log(j * dist[j]);
				}
				if(c>3) break;
			}

		}

		// initial order
		int[] order = new int[card];
		for (int i = 0; i < card; i++) {
			order[i] = i;
		}

		// for More than 2 states,but now we don't need bubble sort
		// bubble sort
		
		  for (int i = 0; i < card - 1; i++) {
			  for (int j = i + 1; j < card; j++) { 
				  if (severity[i] > severity[j]) { 
					  int tmpInt = order[i]; 
					  order[i] = order[j]; 
					  order[j] = tmpInt;
		 
					  double tmpReal = severity[i]; 
					  severity[i] = severity[j];
					  severity[j] = tmpReal; 
				  } 
			  } 
		  }
		 
		
		bn.getNode(latent).reorderStates(order);
		latent.standardizeStates();


		return bn;
	}
	
	//print html format by Peixian for HTML output
		private void PrintHTMLhead(int i, int levels,PrintWriter output)
		{
			output.println("<html style=\"color:#333;background-color:#eee;font-family:'Calibri';font-size:20\">");
			output.println("<head>");
			output.println("<title>Level-"+i+" Topics </title>");
			output.println("<style>");
			output.println("p{ line-height:18px;} ");
			output.println(".div{ margin:0 auto; width:1500px;} ");
			output.println("</style>");
			output.println("</head>");
			output.println("<body>");
			output.println("<div class=\"div\">");
			if(i == 1){
				output.println("<h1 style =\"text-align:center\"><a style=\"font-size:20\"; href=\"TopicsTable-Level-"+(i+1)+".html\">Next Level Up</a>  Level-"+i+" Topics      <a style= \"font-size:20\" href=\"TopicsTable.html\">All Topics</a> </h1>");
			}
			else if(i == levels){
				output.println("<h1 style =\"text-align:center\"><a style=\"font-size:20\"; href=\"TopicsTable.html\">All Topics</a>       Level-"+i+" Topics <a style=\"font-size:20\" href=\"TopicsTable-Level-"+(i-1)+".html\">Next Level Down</a> </h1>");
			}
			else{
				output.println("<h1 style =\"text-align:center\"><a style=\"font-size:20\"; href=\"TopicsTable-Level-"+(i+1)+".html\">Next Level Up</a>       Level-"+i+" Topics      <a style= \"font-size:20\" href=\"TopicsTable-Level-"+(i-1)+".html\">Next Level Down</a> </h1>");
			}

			
		}
		
	    private void PrintHTMLhead_alltopics(int levels, PrintWriter out)
	    {
	    	out.println("<html style=\"color:#333;background-color:#eee;font-family:'Calibri';font-size:20\">");
			out.println("<head>");
			out.println("<title>All Topics </title>");
			out.println("<style>");
			out.println("p{ line-height:10px;} ");
			out.println(".div{ margin:0 auto; width:1200px;} ");
			out.println("</style>");
			out.println("</head>");
			out.println("<body>");
			out.println("<h1 style =\"text-align:center\"><a style=\"font-size:20\"; href=\"TopicsTable-Level-1.html\">Bottom Level Topics</a>    All Topics   <a style= \"font-size:20\" href=\"TopicsTable-Level-"+(levels)+".html\">Top Level Topics</a>   </h1>");

			out.println("<div class=\"div\">");

	    }
		private void PrintHTMLtail(int i, PrintWriter output)
		{
		    output.println("</div>");
			output.println("</body>");
			output.println("</html>");
			
		}
		
}

