package HLTA;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.latlab.graph.AbstractNode;
import org.latlab.graph.DirectedNode;
import org.latlab.io.ParseException;
import org.latlab.io.Parser;
import org.latlab.io.bif.BifParser;
import org.latlab.model.BayesNet;
import org.latlab.model.BeliefNode;
import org.latlab.model.LTM;
import org.latlab.reasoner.CliqueTreePropagation;
import org.latlab.util.DataSet;
import org.latlab.util.Function;
import org.latlab.util.Utils;
import org.latlab.util.Variable;
import org.latlab.util.DataSet.DataCase;

/**
 * 
 * Output the topics of HLTA.
 * 
 * @author liutf
 *
 */

public class HLTAOutputTopics_html_Ltm{
	
	private LTM _model;
	private CliqueTreePropagation _posteriorCtp;
//	private String[] _frontSize = new String[]{"normalsize","small","scriptsize","tiny"};
	private String[] _frontSize = new String[]{"ten","nine","eight","seven","six"};
	private DataSet _samples;
	private HashMap<String, Integer> _semanticBaseMap;
	private HashMap<String, String> _semanticBaseString;
	private HashMap<Integer, Boolean> _semanticBaseStart;
	private HashMap<String, List<Map.Entry<Variable,Double>>> _pairwiseMIorderMap;
	private HashMap<Integer, HashSet<String>> _varDiffLevels;
	private int _SampleSize;
	private int _MaxBaseSize;
	private String _output;
	private boolean _outProbNum = false;
	private boolean _outBackground = false;
//	private boolean _groupStart = true;
	
	public static void main(String[] args) throws FileNotFoundException, ParseException
	{
		if(args.length != 6)
		{
			System.out.println("usage: java HLTAOutputTopics_hltm model outputPath outputProbabilityNum(yes/no) outputBackgroundTopics(yes/no)  SampleSize MaxBaseSize");
			System.exit(-1);
		}
		
		HLTAOutputTopics_html_Ltm out = new HLTAOutputTopics_html_Ltm();
		out.initialize(args);
		
	/*	System.out.println("Output table representation ...");
		out.printHierarchyTable();
		System.out.println("Done!\n\n\n");
		*/
		System.out.println("Output semantic base table ... ... (The computation may take a while ...)");
		out.printSemanticBaseTable();
		System.out.println("Done!\n\n\n");
		
		System.out.println("Output topic table ...");
		out.printTopicsTable();
		System.out.println("Done!");
	}
	
	private void initialize(String[] args) throws FileNotFoundException, ParseException
	{
		_model = new LTM();
		Parser parser = new BifParser(new FileInputStream(args[0]),"UTF-8");
		parser.parse(_model);
		_SampleSize			= Integer.parseInt(args[4]);
		_MaxBaseSize 		= Integer.parseInt(args[5]);
		_posteriorCtp = new CliqueTreePropagation(_model);
		_posteriorCtp.propagate();
		
		_samples = _model.sample(_SampleSize);
		
		_output             = args[1];   
		_outProbNum         = args[2].equalsIgnoreCase("yes")?true:false;
		_outBackground      = args[3].equalsIgnoreCase("yes")?true:false;
		_semanticBaseMap    = new HashMap<String, Integer>();
		_semanticBaseString = new HashMap<String, String>();
		_semanticBaseStart  = new HashMap<Integer, Boolean>();
		_pairwiseMIorderMap = new HashMap<String, List<Map.Entry<Variable,Double>>>();
		
		processVariables();
		
		for(int i=1; i<_varDiffLevels.size(); i++)
		{
			_semanticBaseStart.put(i, true);
		}
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
	
	private void printTopicsTable() throws FileNotFoundException
	{
		System.out.println("Save to "+ _output+File.separator+"TopicsTable.html");
		int levels = _varDiffLevels.size()-1;
		HashSet<String> topLevel = _varDiffLevels.get(levels);
		PrintWriter out = new PrintWriter(_output+File.separator+"TopicsTable.html");
		PrintHTMLhead_alltopics(levels, out);
		HashMap<Integer, PrintWriter> outLevelByLevel = new HashMap<Integer, PrintWriter>();
		
//		out.println("Note: Please copy the following commands and tex codes to a tex file and compile it. \n");
//		writeNewCommand(out);
//		
//		out.println("\\begin{table*}");
//		out.println("\\begin{tabular}{|c|p{410pt}|}");
//		out.println("\\hline");
		
		
		
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
			
//			out.println("\\hline");
		}
		
		for(int i=1; i<levels+1; i++)
		{
			PrintWriter output = outLevelByLevel.get(i);
			PrintHTMLtail(i,output);
			output.close();
		}
		
//		out.println("\\end{tabular}");
//		out.println("\\end{table*}");
		out.close();
	}
	
	private void DFSprintTopics(PrintWriter out, Variable var, int level, int VarLevel, HashMap<Integer, PrintWriter> outLevelByLevel)
	{	
		if(_varDiffLevels.get(0).contains(var.getName()))
			return;
		
		if(VarLevel != _varDiffLevels.size()-1 && _semanticBaseStart.get(VarLevel))
		{
			outLevelByLevel.get(VarLevel).println("<b style=\"font-size: 25;\">"+_semanticBaseString.get(_model.getNode(var).getParent().getName())+"</b>");
			_semanticBaseStart.put(VarLevel, false);
		}
		
		printTopicsForSingleVariable(out, var,level, VarLevel, outLevelByLevel);
		
		List<Map.Entry<Variable,Double>> list = null;
		
		if(_pairwiseMIorderMap.get(out) == null)
		{
			list =  SortChildren(var, _model.getNode(var).getChildren());
		}else
		{
			list =  _pairwiseMIorderMap.get(var);
		}
		
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
	
	private void printTopicsForSingleVariable(PrintWriter out,Variable latent, int level, int VarLevel, HashMap<Integer, PrintWriter> outLevelByLevel)
	{
		_posteriorCtp.clearEvidence();
		_posteriorCtp.propagate();
		
		Function p = _posteriorCtp.computeBelief(latent);
		
		Set<DirectedNode> setNode = new HashSet<DirectedNode>();
	
		Set<DirectedNode> childSet = _model.getNode(latent).getChildren();
		for(DirectedNode node : childSet)
		{
			Variable var = ((BeliefNode)node).getVariable();
			
			if(!_varDiffLevels.get(VarLevel-1).contains(var.getName()))
				continue;
			
			if(((BeliefNode)node).isLeaf())
			{
				setNode.add((DirectedNode) node);
			}else
			{
				for(AbstractNode nodeDes : _model.getNode(var).getDescendants())
				{
					if(((BeliefNode)nodeDes).isLeaf())
					{
						setNode.add((DirectedNode) nodeDes);
					}
				}
			}
		}
		
//		for(AbstractNode node : _model.getNode(latent).getDescendants())
//		{
//			if(((BeliefNode)node).isLeaf())
//			{
//				setNode.add((DirectedNode) node);
//			}
//		}
		
		List<Map.Entry<Variable,Double>> list =  SortChildren(latent, setNode);
		
		Set<Variable> allLeafVar = new HashSet<Variable>();
		allLeafVar.clear();
		
		for(int i=0; i<_semanticBaseMap.get(latent.getName())+1;i++)
		{
			allLeafVar.add(list.get(i).getKey());
		}
		
		_posteriorCtp.clearEvidence();
		_posteriorCtp.propagate();
		
		Variable[] latentArray = new Variable[1];
		latentArray[0] = latent;
		
		int[] states = new int[1];
				
		HashMap<Integer, HashMap<Variable,Double>> allTopics = new HashMap<Integer, HashMap<Variable, Double>>();

		// consider each latent state
		int startIndex = 0;
		if(!_outBackground)
		{
			startIndex = 1;
		}

		for (int i = startIndex; i < latent.getCardinality(); i++) 
		{
			
			HashMap<Variable, Double> ccpd = new HashMap<Variable, Double>();

			states[0] = i;
			
			// set evidence for latent state
			_posteriorCtp.setEvidence(latentArray, states);
			_posteriorCtp.propagate();

			// compute posterior for each manifest variable
			for (Variable manifest : allLeafVar) 
			{
				Function posterior = _posteriorCtp.computeBelief(manifest);
				ccpd.put(manifest, posterior.getCells()[1]);
			}
			
			allTopics.put(i,ccpd);
		}
		
		HashMap<Integer, HashMap<Variable,Double>> allTopics_C = identifyCharacterizingWordUseMI(latent, allLeafVar);
		
		PrintWriter levelOut = outLevelByLevel.get(VarLevel);

		for(int i=startIndex; i< latent.getCardinality(); i++)
		{	
			HashMap<Variable, Double> ccpd = allTopics.get(i);
			HashMap<Variable, Double> ccpd_C = allTopics_C.get(i);
			List<Map.Entry<Variable,Double>> order_C = Utils.sortByDescendingOrder(ccpd_C);
						
			NumberFormat nf = NumberFormat.getInstance();
			nf.setMaximumFractionDigits(2);
			nf.setMinimumFractionDigits(2);
			levelOut.print("<p style=\"text-indent:2em;\">&nbsp;&nbsp;&nbsp;&nbsp;");          //Revised by Peixian for HTML output
			for(int k=0; k<6*level; k++)
			{
				System.out.print(" ");
				
			}
			out.print("<p style=\"text-indent:"+3*(level)+"em;\">");           //Revised by Peixian for HTML output
			
			System.out.print(latent.getName()+"-"+i+" "+nf.format(p.getCells()[i])+" ");
//			out.print(latent.getName()+"-"+i+" "+nf.format(p.getCells()[i])+" ");
			out.print(" "+nf.format(p.getCells()[i])+" ");
			levelOut.print(" "+nf.format(p.getCells()[i])+" ");   //Revised by Peixian for HTML output
//			levelOut.print(" "+latent.getName()+"-"+i+" "+nf.format(p.getCells()[i])+" ");    
//			out.print("{\\small "+latent.getName()+"-"+i+" "+nf.format(p.getCells()[i])+"}&{ ");
			
			for(int k=0; k<order_C.size(); k++)
			{
				NameFormat(out,levelOut,k,order_C.size(),order_C.get(k).getKey().getName());
				NumberFormat(out, levelOut, nf, ccpd.get(order_C.get(k).getKey()));
			}
			
			System.out.println();
			out.println("</p>");       //Revised by Peixian for HTML output
			levelOut.println("</p>");  //Revised by Peixian for HTML output
//			out.println("}\\\\");		
		}
		
		_posteriorCtp.clearEvidence();
		_posteriorCtp.propagate();
	}
	
	private void NameFormat(PrintWriter out,PrintWriter LevelOut,int k, int size, String name)
	{
//		int avg  = (int) Math.round(size/5.0);
		
		System.out.print(name+" ");
		out.print(name+" ");
		LevelOut.print(name+" ");
		
//		if(avg!=0 && (k/avg)>4)
//		{
//			out.print("{\\"+_frontSize[_frontSize.length-1]+" "+name+"} ");
//		}
//		else if(avg == 0)
//		{
//			out.print("{\\"+_frontSize[k]+" "+name+"} ");
//		}else
//		{
//			out.print("{\\"+_frontSize[k/avg]+" "+name+"} ");
//		}
	}
	
	private void NumberFormat(PrintWriter out, PrintWriter LevelOut, NumberFormat nf, double num)
	{
		int index = _frontSize.length-1-(int) Math.floor(num/0.2);
		
		if (index < 0)
			index = 0;
		
		if(_outProbNum)
		{
			System.out.print(nf.format(num)+" ");
			out.print(nf.format(num)+" ");
			LevelOut.print(nf.format(num)+" ");
		}

//		 out.print("{\\"+_frontSize[index]+" "+nf.format(num)+"} ");
	}
	
	private void printSemanticBaseTable() throws FileNotFoundException
	{
		PrintWriter out = new PrintWriter(_output+File.separator+"TopicBase.txt");
		
		int totalLevel = _varDiffLevels.size();
		HashSet<String> topLevel = _varDiffLevels.get(totalLevel-1);
		
		System.out.println("Note: if the base size is larger than "+_MaxBaseSize+", only the top "+_MaxBaseSize+" will be shown. \n\n\n");
		out.println("Note: if the base size is larger than "+_MaxBaseSize+", only the top "+_MaxBaseSize+" words will be shown. \n\n\n");
		
		for(String var : topLevel)
		{
			DFSprint(out, _model.getNodeByName(var).getVariable(), 0, totalLevel-1);
		}
		
		out.close();
	}
	
	private void DFSprint(PrintWriter out, Variable var, int level, int VarLevel)
	{
		if(_varDiffLevels.get(0).contains(var.getName()))
			return;
		
		printOneBase(out, var, level, VarLevel);
		
		List<Map.Entry<Variable,Double>> list = null;
		
		if(_pairwiseMIorderMap.get(out) == null)
		{
			list =  SortChildren(var, _model.getNode(var).getChildren());
		}else
		{
			list =  _pairwiseMIorderMap.get(var);
		}
		
		for(int i=0; i<list.size(); i++)
		{
			Variable child = list.get(i).getKey();
			
			if(!_varDiffLevels.get(VarLevel-1).contains(child.getName()))
				continue;
			
			DFSprint(out, child, level+1, VarLevel-1);
		}
	}
	
	private void printOneBase(PrintWriter out, Variable v, int level, int VarLevel)
	{				
		Set<DirectedNode> setNode = new HashSet<DirectedNode>();
		
		Set<DirectedNode> childSet = _model.getNode(v).getChildren();
		for(DirectedNode node : childSet)
		{
			Variable var = ((BeliefNode)node).getVariable();
			
			if(!_varDiffLevels.get(VarLevel-1).contains(var.getName()))
				continue;
			
			if(((BeliefNode)node).isLeaf())
			{
				setNode.add((DirectedNode) node);	
			}else
			{
				for(AbstractNode nodeDes : _model.getNode(var).getDescendants())
				{
					if(((BeliefNode)nodeDes).isLeaf())
					{
						setNode.add((DirectedNode) nodeDes);
					}
				}
			}
		}
		
//		for(AbstractNode node : _model.getNode(v).getDescendants())
//		{
//			if(((BeliefNode)node).isLeaf())
//			{
//				setNode.add((DirectedNode) node);
//			}
//		}
		
		List<Map.Entry<Variable,Double>> list = SortChildren(v, setNode);
		
		Set<Variable> allLeafVar = new HashSet<Variable>();
		for(DirectedNode node : setNode)
		{
			allLeafVar.add(((BeliefNode)node).getVariable());
		}
		Set<Variable> parentVar = new HashSet<Variable>();
		parentVar.add(v);
		
	/*	double tmi = 1;	
		if(allLeafVar.size()/_MaxBaseSize < 8) // just want to speed up.
		{
			tmi = computeTMi(parentVar, allLeafVar);
		}
		
		allLeafVar.clear();       
	*/	
		for(int i=0; i<6*level; i++)
		{
			System.out.print(" ");
			out.print(" ");
		}
		
		for(int i=0; i<list.size(); i++)
		{
		//	allLeafVar.add(list.get(i).getKey());
			
		//	double cmi = 0;
		//	if(allLeafVar.size()/_MaxBaseSize < 8)
		//		cmi = computeMi(parentVar, allLeafVar);
			
		//	if(cmi/tmi > 0.95 || i == _MaxBaseSize-1)
			if(i == _MaxBaseSize-1||i==allLeafVar.size()-1)
			{					
				_semanticBaseMap.put(v.getName(), i);
				
				System.out.print(v.getName()+" & ");
				out.print(v.getName()+" & ");
				
				String words = "";
				for(int j=0; j<i+1; j++)
				{
					System.out.print(list.get(j).getKey().getName()+" ");
					out.print(list.get(j).getKey().getName()+" ");
					words += list.get(j).getKey().getName()+" ";
				}
				
				_semanticBaseString.put(v.getName(), words);
				System.out.println();
				out.println();				
				break;
			}
		}
		
		_posteriorCtp.clearEvidence();
		_posteriorCtp.propagate();
	}
	
	private double computeTMi(Collection<Variable> x, Collection<Variable> y) 
	{
		// exact computation in case of |x| = 1 and |y| = 1
		if (x.size() == 1 && y.size() == 1) {
			List<Variable> xyNodes = new ArrayList<Variable>();
			xyNodes.add(x.iterator().next());
			xyNodes.add(y.iterator().next());

			return Utils.computeMutualInformation(_posteriorCtp
					.computeBelief(xyNodes));
		}

		// xy = x union y
		ArrayList<Variable> xy = new ArrayList<Variable>();
		xy.addAll(x);
		xy.addAll(y);

		// samples over xy
		DataSet xySamples = _samples.project(xy);

		// array of xy
		Variable[] xyArray = xySamples.getVariables();
		int xySize = xyArray.length;

		// locate x and y in xyArray, respectively
		List<Integer> xPos = new ArrayList<Integer>();
		for (Variable var : x) {
			xPos.add(Arrays.binarySearch(xyArray, var));
		}

		List<Integer> yPos = new ArrayList<Integer>();
		for (Variable var : y) {
			yPos.add(Arrays.binarySearch(xyArray, var));
		}

		// initialization
		double mi = 0.0;
		int[] states = new int[xySize];
		double pxy, px, py;

		// I(x; y) = \sum_{x, y} P(x, y) log P(x, y) / P(x)P(y) \approx \sum_{x,
		// y} Q(x, y) log P(x, y) / P(x)P(y), where Q(x, y) is the empirical
		// distribution induced by the samples
		for (DataCase d : xySamples.getData()) {
			// compute P(dx, dy)
			_posteriorCtp.setEvidence(xyArray, d.getStates());
			pxy = _posteriorCtp.propagate();

			// compute P(dx)
			System.arraycopy(d.getStates(), 0, states, 0, xySize);
			for (int pos : yPos) {
				states[pos] = DataSet.MISSING_VALUE; // hide values of y
			}
			_posteriorCtp.setEvidence(xyArray, states);
			px = _posteriorCtp.propagate();

			// compute P(dy)
			System.arraycopy(d.getStates(), 0, states, 0, xySize);
			for (int pos : xPos) {
				states[pos] = DataSet.MISSING_VALUE; // hide values of x
			}
			_posteriorCtp.setEvidence(xyArray, states);
			py = _posteriorCtp.propagate();

			// increase MI by weight * log (pxy / (px * py))
			double increase = d.getWeight() * Math.log(pxy / (px * py));
			if (Double.isInfinite(increase) || Double.isNaN(increase)) {
				// numeric error
				System.err.println("Skip!");
			} else {
				mi += increase;
			}
		}

		// divided by the sample size
		mi /= xySamples.getTotalWeight();

		return mi;
	}
	
	private double computeMi(Collection<Variable> x, Collection<Variable> y)
	{
		// exact computation in case of |x| = 1 and |y| = 1
		if (x.size() == 1 && y.size() == 1) 
		{
			List<Variable> xyNodes = new ArrayList<Variable>();
			xyNodes.add(x.iterator().next());
			xyNodes.add(y.iterator().next());

			return Utils.computeMutualInformation(_posteriorCtp.computeBelief(xyNodes));
		}

		// xy = x union y
		ArrayList<Variable> xy = new ArrayList<Variable>();
		xy.addAll(x);
		xy.addAll(y);
		
		ArrayList<Variable> xVar = new ArrayList<Variable>();
		xVar.addAll(x);
		
		ArrayList<Variable> yVar = new ArrayList<Variable>();
		yVar.addAll(y);

		// samples over xy
		DataSet Samples = null;
		
		Samples = _samples.project(xy);
		double xyEntropy = computeEntropy(Samples);
		
		Samples  = _samples.project(xVar);
		double xEntropy = computeEntropy(Samples);
		
		Samples  = _samples.project(yVar);		
		double yEntropy = computeEntropy(Samples);

		return xEntropy + yEntropy - xyEntropy;
	}
	
	private double computeEntropy(DataSet data)
	{
		double entropy = 0;
		double totalWeight = data.getTotalWeight();
		
		for(DataCase d : data.getData())
		{
			double weight = d.getWeight();
			
			entropy += weight*Math.log(1/(weight/totalWeight));
		}
		
		return entropy/totalWeight;
	}
	
	private void printHierarchyTable() throws FileNotFoundException
	{		
		if(_varDiffLevels.size()<4)
		{
			System.out.println("There are less than 3 level latent variables in the model. We will omit the table representation.");
			return;
		}
		
		System.out.println("Save to "+_output+File.separator+"ModelTableRepresentation-Tex.txt");
		PrintWriter out = new PrintWriter(_output+File.separator+"ModelTableRepresentation-Tex.txt");
		
		out.println("Note: Please copy the following commands and tex codes to a tex file and compile it. \n"+
                "We only output the struture of first three levels (level-1, level-2 and level-3) in order to make the table simple.\n\n\n");
		
		writeNewCommand(out);
		
		out.println("\\begin{table*}");
		out.println("\\begin{tabular}{|c|c|p{390pt}|}");
		out.println("\\hline");
		
		int StartLevel = 3;
		Set<String> variables = _varDiffLevels.get(StartLevel);//_variables;
		
		for(String var : variables)
		{
			Variable v = _model.getNodeByName(var).getVariable();
			printTopics(v, out, StartLevel);
		}
		
		out.println("\\end{tabular}");
		out.println("\\end{table*}");
		out.close();
	}
	
	private void writeNewCommand(PrintWriter out)
	{
//		out.println("Note: Please copy the following commands and tex codes to a tex file and compile it. \n"+
//	                "We only output the struture of first three levels (level-1, level-2 and level-3) in order to make the table simple.\n\n\n");
		
		out.println("\\usepackage{multirow}");
		
		out.println("\\newcommand{\\ten}{\\fontsize{10pt}{9.5pt}\\selectfont}");
		out.println("\\newcommand{\\nine}{\\fontsize{9pt}{9.5pt}\\selectfont}");
		out.println("\\newcommand{\\eight}{\\fontsize{8pt}{9.5pt}\\selectfont}");
		out.println("\\newcommand{\\seven}{\\fontsize{7pt}{9.5pt}\\selectfont}");
		out.println("\\newcommand{\\six}{\\fontsize{6pt}{9.5pt}\\selectfont}");
	}
	
	private void printTopics(Variable var, PrintWriter out, int StartLevel)
	{
		int num_children = _model.getNode(var).getChildren().size();
		
		out.print("\\multirow{"+num_children+"}*{\\normalsize " + var.getName()+"("+var.getCardinality()+")"+"}");
		
		List<Map.Entry<Variable,Double>> list =  SortChildren(var, _model.getNode(var).getChildren());
		
		_pairwiseMIorderMap.put(var.getName(), list);
		
		for(int i=0; i<list.size(); i++)
		{
			Variable child=list.get(i).getKey();
			
			// if it is not the variable in the level below, skip it.
			if(!_varDiffLevels.get(StartLevel-1).contains(child.getName()))
				continue;
			
			out.print("&");
			
			if(i+1<_frontSize.length)
				out.print("{\\"+_frontSize[i+1]+" ");
			else
				out.print("{\\"+_frontSize[_frontSize.length-1]+" ");
			
			out.print(child.getName()+"("+child.getCardinality()+")}& ");
			
			List<Map.Entry<Variable,Double>> list_2 = SortChildren(child, _model.getNode(child).getChildren());
			_pairwiseMIorderMap.put(child.getName(), list_2);
			
			for(int j=0; j<list_2.size(); j++)
			{
				Variable child_2 = list_2.get(j).getKey();
				
				if(!_varDiffLevels.get(StartLevel-2).contains(child.getName()))
					continue;				
				
				List<Map.Entry<Variable,Double>> list_3 = SortChildren(child_2, _model.getNode(child_2).getChildren());
				
				if(j+1<_frontSize.length)
					out.print("{\\"+_frontSize[j+1]+" ");
				else
					out.print("{\\"+_frontSize[_frontSize.length-1]+" ");
				
				out.print(child_2.getName()+"("+child_2.getCardinality()+")}: ");
				
				for(int k=0; k<list_3.size(); k++)
				{
					if(k<_frontSize.length)
						out.print("{\\"+_frontSize[k]+" ");
					else
						out.print("{\\"+_frontSize[_frontSize.length-1]+" ");
					
					out.print(list_3.get(k).getKey().getName()+"} ");
				}
				if(j!=list_2.size()-1)
					out.print("; ");
			}
			
			out.println("\\\\ \\cline{2-3}");
		}
		out.println("\\hline");
	}
	
	private List<Map.Entry<Variable,Double>> SortChildren(Variable var, Set<DirectedNode> nodeSet)
	{
		Map<Variable, Double> children_mi = new HashMap<Variable, Double>();
		
		for(DirectedNode node : nodeSet)
		{
			Variable  child = ((BeliefNode)node).getVariable();
			double mi = computeMI(var, child);
			children_mi.put(child, mi);
		}
		
		List<Map.Entry<Variable,Double>> List = Utils.sortByDescendingOrder(children_mi);
		
		return List;
	}
	
	private double computeMI(Variable x, Variable y)
	{		
		List<Variable> xyNodes = new ArrayList<Variable>();
		xyNodes.add(x);
		xyNodes.add(y);

		return Utils.computeMutualInformation(_posteriorCtp.computeBelief(xyNodes));
	}
		
	private HashMap<Integer, HashMap<Variable,Double>> identifyCharacterizingWordUseMI(Variable Parent, Set<Variable> set)
	{
		HashMap<Integer, HashMap<Variable,Double>> topics = new HashMap<Integer, HashMap<Variable,Double>>();
		
		_posteriorCtp.clearEvidence();
		_posteriorCtp.propagate();
		
		for(int i=0; i<Parent.getCardinality(); i++)
		{
			HashMap<Variable,Double> topic = new HashMap<Variable,Double>();
			
			for(Variable manifest : set)
			{
//				double value = computeMIwithMergeStateVar(Parent, i, manifest);
				double value = computeMIwithMergedStates(Parent, i, manifest);
				topic.put(manifest, value);
			}
			
			topics.put(i, topic);
		}
		
		return topics;
	}
	
	private double computeMIwithMergedStates(Variable par, int card, Variable manifest)
	{
		ArrayList<Variable> list = new ArrayList<Variable>();
		list.add(par);
		list.add(manifest);
		
		if(par.getCardinality() == 2)
		{
			return Utils.computeMutualInformation(_posteriorCtp.computeBelief(list));
		}
		
		int[] statesToMerge = new int[par.getCardinality()-1];
		int index = 0;
		for(int i=0; i<par.getCardinality();i++)
		{
			if(i != card)
				statesToMerge[index++] = i; 
		}
		
		Variable newVar = new Variable(2);
		
		Function fun = _posteriorCtp.computeBelief(list);	
		Function fun2 = fun.combine(par, statesToMerge, card, newVar);
		fun2.normalize();
		
		double mi = Utils.computeMutualInformation(fun2);
		
		return mi;
	}
	
	private double computeMIwithMergeStateVar(Variable par, int card,Variable manifest)
	{
		ArrayList<Variable> xy = new ArrayList<Variable>();
		xy.add(par);
		xy.add(manifest);
		
		DataSet sample = _samples.project(xy);
		
		int[][] countInfo = new int[2][2];
		int index_par = sample.createVariableToIndexMap().get(par);
		int index_manifest = sample.createVariableToIndexMap().get(manifest);
		
		for(DataCase d : sample.getData())
		{
			int[] states = d.getStates();
			
			if(states[index_par]==card)
			{
				countInfo[states[index_manifest]][0] += (int)d.getWeight();
			}else
			{
				countInfo[states[index_manifest]][1] += (int)d.getWeight();
			}
		}
		
		//compute mi
		double mi=0;
		
		for(int i=0; i<2; i++)
		{
			for(int j=0; j<2; j++)
			{
				double pxy = countInfo[i][j]/sample.getTotalWeight();
				double px = (countInfo[i][0]+countInfo[i][1])/sample.getTotalWeight();
				double py = (countInfo[0][j]+countInfo[1][j])/sample.getTotalWeight();
				mi += (pxy)*Math.log(pxy/(px*py));
			}
		}
		
		return mi;
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


