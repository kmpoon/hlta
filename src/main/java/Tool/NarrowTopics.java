package Tool;

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
import org.latlab.io.Parser;
import org.latlab.io.bif.BifParser;
import org.latlab.learner.ParallelEmLearner;
import org.latlab.model.BeliefNode;
import org.latlab.model.LTM;
import org.latlab.reasoner.CliqueTreePropagation;
import org.latlab.util.DataSet;
import org.latlab.util.DataSetLoader;
import org.latlab.util.Function;
import org.latlab.util.Utils;
import org.latlab.util.Variable;

public class NarrowTopics{
	
	private LTM _model;
	private CliqueTreePropagation _posteriorCtp;

	private String _dataDir;
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

	
	public static void main(String[] args) throws IOException, Exception
	{
		if(args.length != 6)
		{
			System.out.println("usage: java NarrowTopics model data outputpath outputNum outputBackground MaxBaseSize ");
			System.exit(-1);
		}
		
		NarrowTopics out = new NarrowTopics();
		out.initialize(args);
		out.run();

	}
	
	private void initialize(String[] args) throws IOException, Exception
	{
		_model = new LTM();
		Parser parser = new BifParser(new FileInputStream(args[0]),"UTF-8");
		parser.parse(_model);
		//_dataDir = args[3];
		_subData = new DataSet(DataSetLoader.convert(args[1]));
		_output = args[2];
		_outProbNum         = args[3].equalsIgnoreCase("yes")?true:false;
		_outBackground      = args[4].equalsIgnoreCase("yes")?true:false;
		_MaxBaseSize = Integer.parseInt(args[5]);
		_posteriorCtp = new CliqueTreePropagation(_model);
		_posteriorCtp.propagate();
		processVariables();
		_semanticBaseStart  = new HashMap<Integer, Boolean>();
		_semanticBaseString = new HashMap<String, String>();

		for(int i=1; i<_varDiffLevels.size(); i++)
		{
			_semanticBaseStart.put(i, true);
		}
	}
	
	
	private void run() throws IOException, Exception{
		

		System.out.println("Output topic table ...");
		printTopicsTable();
		System.out.println("Done!");
		
		
	}
	
	
	
	
	private void printTopicsTable() throws FileNotFoundException, UnsupportedEncodingException {
		// TODO Auto-generated method stub
		System.out.println("Save to "+ _output+File.separator+"TopicsTable.html");
		int levels = _varDiffLevels.size()-1;
		HashSet<String> topLevel = _varDiffLevels.get(levels);
		PrintWriter out = new PrintWriter(_output+File.separator+"TopicsTable.html");
		PrintHTMLhead_alltopics(levels, out);
		HashMap<Integer, PrintWriter> outLevelByLevel = new HashMap<Integer, PrintWriter>();
		
		
		for(int i=1; i<levels+1; i++)
		{
			PrintWriter output = new PrintWriter(_output+File.separator+"TopicsTable-Level-"+i+".html");
			
			outLevelByLevel.put(i, output);
			PrintHTMLhead(i,levels,output);
			
		}
			
		for(String var : topLevel)
		{
			Variable v = _model.getNodeByName(var).getVariable();
			
			DFSprintTopics(out, v, 0, levels, outLevelByLevel);
			

		}
		
		for(int i=1; i<levels+1; i++)
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
			
			if(!_varDiffLevels.get(VarLevel-1).contains(child.getName()))
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
	
		Set<DirectedNode> setNode = new HashSet<DirectedNode>();
	    ArrayList<Variable> setVars = new ArrayList<Variable>();
	    ArrayList<String> setVarNames = new ArrayList<String>();
		Set<DirectedNode> childSet = _model.getNodeByName(latent).getChildren();
		//Compute setNode and built a subtree rooted at latent.
	    LTM subtree = new LTM();
	    subtree.addNode(_model.getNodeByName(latent).getVariable());

		for(DirectedNode node : childSet)
		{
			Variable var = ((BeliefNode)node).getVariable();
			
			if(!_varDiffLevels.get(VarLevel-1).contains(var.getName()))
				continue;
			subtree.addNode(((BeliefNode) node).getVariable());
			if(((BeliefNode)node).isLeaf())
			{
				setNode.add((DirectedNode) node);
				setVars.add(((BeliefNode)node).getVariable());
				setVarNames.add(node.getName());
			}else
			{   
				for(AbstractNode nodeDes : _model.getNode(var).getDescendants())
				{
					subtree.addNode(((BeliefNode) nodeDes).getVariable());
					if(((BeliefNode)nodeDes).isLeaf())
					{
						setNode.add((DirectedNode) nodeDes);
						setVars.add(((BeliefNode)nodeDes).getVariable());
						setVarNames.add(nodeDes.getName());
					}
				}
			}
		}
		
		//add edges and copy parameters in the subtree
		for(AbstractNode aNod:subtree.getNodes()){
			if(((BeliefNode)aNod).getName().equals(latent)){//Or name equal?
				continue;
			}
			else{
				BeliefNode parent = _model.getNodeByName(aNod.getName()).getParent();

				BeliefNode newParent =
						subtree.getNodeByName(parent.getName());

				subtree.addEdge((BeliefNode)aNod, newParent);
				ArrayList<Variable> var2s =
						new ArrayList<Variable>(
								_model.getNodeByName(aNod.getName()).getCpt().getVariables());
				subtree.getNodeByName(aNod.getName()).getCpt().setCells(var2s,
						_model.getNodeByName(aNod.getName()).getCpt().getCells());
			}
		}
	//	subtree.saveAsBif(_dataDir+"subtreebefore.bif");

		DataSet subData =  _subData.project(setVars);
		subData.synchronize(subtree);
	 
		System.out.println("Run EM on submodel, reorder the states of the root node");
		ParallelEmLearner emLearner = new ParallelEmLearner();
		emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
		emLearner.setMaxNumberOfSteps(10);
		emLearner.setNumberOfRestarts(1);
		emLearner.setReuseFlag(true);
		emLearner.setThreshold(0.01);
	    
		subtree = (LTM) emLearner.em(subtree, subData);
	
	/*	System.out.println("start stochastic EM at "+(new Date()).toString());
		
		
		File f = new File(_dataDir);  
		File[] files = f.listFiles(); 
        for(int indf = 0; indf<files.length; indf++) {  
        	_subData  = new DataSet(DataSetLoader.convert(files[indf].getAbsolutePath()));
        	DataSet subData = _subData.project(setVars).synchronize(subtree);
            System.out.println(files[indf].getAbsolutePath());
			ParallelEmLearner emLearner = new ParallelEmLearner();
			emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
			emLearner.setMaxNumberOfSteps(1);
			emLearner.setNumberOfRestarts(1);
			emLearner.setReuseFlag(true);
			emLearner.setThreshold(0.01);
			long startGEM = System.currentTimeMillis();
			subtree = (LTM)emLearner.em(subtree, subData);
			System.out.println("---The "+(indf +1)+"th--- Global EM Time: "
					+ (System.currentTimeMillis() - startGEM) + " ms ---");
		}
		
		System.out.println("Stochastic EM stopped at "+(new Date()).toString()); */
		_posteriorCtpSub = new CliqueTreePropagation(subtree);
		_posteriorCtpSub.propagate();
		
		List<Map.Entry<Variable,Double>> list =  SortChildren(subtree.getNodeByName(latent).getVariable(), setNode,_posteriorCtpSub);
		
		//reorder the state
		
		subtree = reorderStates(subtree,subtree.getNodeByName(latent).getVariable(), setNode,list);
		//subtree.saveAsBif(_dataDir+"subtreeafter.bif");

		//subtree.saveAsBif("subtree.bif");
		Function p = _posteriorCtpSub.computeBelief(subtree.getNodeByName(latent).getVariable());
		PrintWriter levelOut = outLevelByLevel.get(VarLevel);

	    System.out.println(p.getCells()[1]);
	     
		
		_posteriorCtpSub.clearEvidence();
		_posteriorCtpSub.propagate();
		
		Variable[] latentArray = new Variable[1];
		latentArray[0] = subtree.getNodeByName(latent).getVariable();
		
		int[] states = new int[1];
				
		HashMap<Integer, HashMap<Variable,Double>> allTopics = new HashMap<Integer, HashMap<Variable, Double>>();

		// consider each latent state
		int startIndex = 0;
		if(!_outBackground)
		{
			startIndex = 1;
		}

		for (int i = startIndex; i < subtree.getNodeByName(latent).getVariable().getCardinality(); i++) 
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
		
		

		for(int i=startIndex; i< subtree.getNodeByName(latent).getVariable().getCardinality(); i++)
		{	
			HashMap<Variable, Double> ccpd = allTopics.get(i);
						
			NumberFormat nf = NumberFormat.getInstance();
			nf.setMaximumFractionDigits(3);
			nf.setMinimumFractionDigits(3);
			
			
			levelOut.print("<p style=\"text-indent:2em;\">&nbsp;&nbsp;&nbsp;&nbsp;");          //Revised by Peixian for HTML output
			for(int k=0; k<6*level; k++)
			{
				System.out.print(" ");
				
			}
			int level_invert = _varDiffLevels.size()-level-1;
			if(_model.getNodeByName(latent).isRoot()){
				out.print("<p level =\""+level_invert+"\" name =\""+latent+"\" parent = \"none\" percentage =\""+nf.format(p.getCells()[i])+"\" style=\"text-indent:"+3*(level)+"em;\">");           //Revised by Peixian for HTML output
				
			}else{
				out.print("<p level =\""+level_invert+"\" name =\""+latent+"\" parent = \""+_model.getNodeByName(latent).getParent().getName()+"\" percentage =\""+nf.format(p.getCells()[i])+"\" style=\"text-indent:"+3*(level)+"em;\">");           //Revised by Peixian for HTML output
			}
			
			System.out.print(latent+"-"+i+" "+nf.format(p.getCells()[i])+" ");
			out.print(" "+nf.format(p.getCells()[i])+" ");
			levelOut.print(" "+nf.format(p.getCells()[i])+" ");   //Revised by Peixian for HTML output

			
			for(int k=0; k<ccpd.size(); k++)
			{
				NameFormat(out,levelOut,k,ccpd.size(),_collectionVar.get(k).getName());
				NumberFormat(out, levelOut, nf, ccpd.get(_collectionVar.get(k)));
			}
			
			System.out.println();
			out.println("</p>");       //Revised by Peixian for HTML output
			levelOut.println("</p>");  //Revised by Peixian for HTML output
		}
		
		_posteriorCtp.clearEvidence();
			
	    	
	}
	private void NameFormat(PrintWriter out,PrintWriter LevelOut,int k, int size, String name)
	{
		
		System.out.print(name+" ");
		out.print(name+" ");
		LevelOut.print(name+" ");
		

	}
	
	private void NumberFormat(PrintWriter out, PrintWriter LevelOut, NumberFormat nf, double num)
	{
	
		
		if(_outProbNum)
		{
			System.out.print(nf.format(num)+" ");
			out.print(nf.format(num)+" ");
			LevelOut.print(nf.format(num)+" ");
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
		

		String words = "";
		
		_collectionVar = new ArrayList<Variable>();
		if (list.size() > _MaxBaseSize) {
			for (int Id = 0; Id < _MaxBaseSize; Id++) {
				_collectionVar.add(list.get(Id).getKey());
				words += list.get(Id).getKey().getName()+" ";
			}
		} else {
			for (DirectedNode node : setNode) {
				Variable manifest = ((BeliefNode) node).getVariable();
				_collectionVar.add(manifest);
				words += manifest.getName()+" ";
			}
		}
		
		_semanticBaseString.put(latent.getName(), words);

		
		
		for (int i = 0; i < card; i++) {
			states[0] = i;

			ctp.setEvidence(latents, states);
			ctp.propagate();

			// accumulate expectation of each manifest variable

			for (Variable manifest : _collectionVar) {
				double[] dist = ctp.computeBelief(manifest).getCells();

				for (int j = 1; j < dist.length; j++) {
					severity[i] += Math.log(j * dist[j]);
				}
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
			output.println("p{ line-height:10px;} ");
			output.println(".div{ margin:0 auto; width:1200px;} ");
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

