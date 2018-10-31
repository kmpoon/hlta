package tm.hlta

import tm.util.Arguments
import java.nio.file.Paths
import java.nio.file.Files
import tm.util.FileHelpers
import tm.util.Tree
import scala.io.Source
import org.latlab.model.LTM
import tm.hlta.HLTA._
import org.latlab.util.DataSet
import tm.util.Reader
import tm.util.Data
import org.slf4j.LoggerFactory

object ExtractTopicTree {
  class Conf(args: Seq[String]) extends Arguments(args) {    
    banner("Usage: tm.hlta.ExtractTopicTree [OPTION]... name model data")
    val name = trailArg[String](descr = "Name of the topic tree file to be generated")
    val model = trailArg[String](descr = "Model file (e.g. model.bif)")
    val data = trailArg[String](required = false, descr = "Data file, if using --broad, this is not required")
    
    val ldaVocab = opt[String](default = None, descr = "LDA vocab file, only required if lda data is provided")
    
    val broad = opt[Boolean](default = Some(false), descr = "use broad defined topic, run faster but more document will be categorized into the topic")
    val title = opt[String](default = Some("Topic Tree"), descr = "Title in the topic tree")
    val layer = opt[List[Int]](descr = "Layer number, i.e. --layer 1 3")
    val keywords = opt[Int](default = Some(7), descr = "number of keywords for each topic")
    val keywordsProb = opt[Boolean](default = Some(false), descr = "show probability of individual keyword")
    val tempDir = opt[String](default = Some("topic_output"),
      descr = "Temporary output directory for extracted topic files (default: topic_output)")
      
    verify
    checkDefaultOpts()
    if(data.isEmpty && !broad())
      throw new Exception("Missing parameter data or missing option --broad")
  }
  
  val logger = LoggerFactory.getLogger(ExtractTopicTree.getClass)

  def main(args: Array[String]) {
    val conf = new Conf(args)
    
    val topicTree = if(conf.broad()) {
      //Broad defined topic do not recompute parameters
      //Thus, no data are required
      val model = Reader.readModel(conf.model())
      logger.debug(s"will broad")
      broad(model, conf.layer.toOption, conf.keywords(), conf.keywordsProb())
    }
    else {
      //Narrow defined topic needs to re-do parameters estimation
      //Data is required
      val (model, data) = Reader.readModelAndData(conf.model(), conf.data(), ldaVocabFile = conf.ldaVocab.getOrElse(""))
      logger.debug(s"will narrow")
      val binaryData = data.binary()
      narrow(model, binaryData, conf.layer.toOption, conf.keywords(), conf.keywordsProb())
    }
    logger.info("Topic tree extraction is done.")
    logger.debug(s"narrow or broad done. will BuildWebsite")
    
    //BuildWebsite generates file in .js format
    BuildWebsite(conf.name(), conf.title(), topicTree)
    //Additionally generates .json file
    topicTree.saveAsJson(conf.name()+".nodes.json")
    topicTree.saveAsSimpleHtml(conf.name()+".simple.html")
    logger.info("The topic tree is available at "+conf.name()+".html")
    logger.debug(s"saveAsJson done. filename " + conf.name() + ".nodes.json") 
  }
  
  def broad(model: LTM, layer: Option[List[Int]] = None, keywords: Int = 7, keywordsProb: Boolean = false) = {
//    val output = Paths.get(tempDir)
//    FileHelpers.mkdir(output)

    val extractor = new BroadTopicsExtractor(model, keywords, layer, keywordsProb)
    extractor.extractTopics()
    
//    val bdtExtractor = new clustering.HLTAOutputTopics_html_Ltm()
    //val param = Array("", tempDir, "no", "no", keywords.toString())
//    bdtExtractor.initialize(model, tempDir, false, false, keywords)
//    bdtExtractor.run()
//
//    val topicFile = output.resolve("TopicsTable.html")
//    val topicTree = TopicTree.readHtml(topicFile.toString())
//    //val order = RegenerateHTMLTopicTree.readIslands(FindTopLevelSiblingClusters.getIslandsFileName(conf.name()))
//    //topicTree = topicTree.sortRoots { t => order(t.value.name) }
//    if(layer.isDefined){
//      val _layer = layer.get.map{l => if(l<=0) l+model.getHeight-1 else l}
//      topicTree.trimLevels(_layer)
//    }else
//      topicTree
  }
  
  def narrow(model: LTM, binaryData: Data, layer: Option[List[Int]] = None, keywords: Int = 7, keywordsProb: Boolean = false) = {
//    val output = Paths.get(tempDir)
//    FileHelpers.mkdir(output)
    
    val extractor = new NarrowTopicsExtractor(model, binaryData, keywords, layer, keywordsProb)
    extractor.extractTopics()
    
//    val lcmNdtExtractor = new tm.hlta.ExtractTopicTree.ExtractNarrowTopics_Scala(model, binaryData, keywords)
//    val param = Array("", "", tempDir, "no", "no", keywords.toString())
//    lcmNdtExtractor.apply()
//    logger.debug(s"narrow lcmNdtExtractor run done")
//    val topicFile = output.resolve("TopicsTable.html")
//    val topicTree = TopicTree.readHtml(topicFile.toString())
    //val order = RegenerateHTMLTopicTree.readIslands(FindTopLevelSiblingClusters.getIslandsFileName(conf.name()))
    //topicTree = topicTree.sortRoots { t => order(t.value.name) }
//    logger.debug(s"readHtml done")
//    if(layer.isDefined){
//      val _layer = layer.get.map{l => if(l<=0) l+model.getHeight-1 else l}
//      logger.debug(s"isDefined: will trimLevels")
//      topicTree.trimLevels(_layer)
//    }else{
//      logger.debug(s"not Defined: will topicTree")
//      topicTree
//    }
  }
  
  private class BroadTopicsExtractor(model: LTM, keywords: Int, 
      layers: Option[List[Int]] = None, outProbNum: Boolean = false, assignProb: Boolean = true){  
    import org.latlab.util.Variable
    import org.latlab.reasoner.CliqueTreePropagation
    import java.util.ArrayList
    import collection.JavaConverters._
    import tm.hlta.HLTA
    import tm.util.Tree
    
    val _posteriorCtp = new CliqueTreePropagation(model);
    
    def extractTopics(): TopicTree = {    
      val _varDiffLevels = model.getLevelVariables()
      val _layers = if(layers.isDefined) layers.get.sorted else (1 until _varDiffLevels.size).toList //in ascending order
      val topicNodeBank = scala.collection.mutable.Map[String, Tree[Topic]]()
      _layers.foreach { VarLevel =>
        _varDiffLevels.apply(VarLevel).map {latent =>
          val topic = topicForSingleVariable(latent)
          val descendentLatentVars = model.latentDescendentOf(latent.getName)
          val childs = descendentLatentVars.flatMap { v => 
            //remove and pops the topic from the bank
            topicNodeBank.remove(v.getName)
          }
          topicNodeBank.put(latent.getName, Tree.node[Topic](topic, childs))    
        }
      }
      val topicTree = TopicTree(topicNodeBank.values.toSeq)
      topicTree.reassignLevel()
      topicTree
    }
        
    /**
  	 * Rewritten from printTopicsForSingleVariable
  	 */
  	def topicForSingleVariable(latent: Variable) = {
  		_posteriorCtp.clearEvidence();
  		_posteriorCtp.propagate();
  		val p = _posteriorCtp.computeBelief(latent);
  				
  		val setNode = model.observedDescendentOf(latent.getName)
  		val globallist = SortChildren(latent, setNode, _posteriorCtp);
  		
      val observedVarOrder = globallist.take(keywords).map{ case(v, mi) => v }
  		
  		_posteriorCtp.clearEvidence();
  		_posteriorCtp.propagate();
  		
			val latentArray = Array(latent);
			val card = 1; //Only consider z=1 state
			val states = Array(card);
			
			// set evidence for latent state
			_posteriorCtp.setEvidence(latentArray, states);
			_posteriorCtp.propagate();

			// compute posterior for each manifest variable
			val words = observedVarOrder.map{ manifest =>
			  if(outProbNum){
				  val posterior = _posteriorCtp.computeBelief(manifest);
				  val prob = if(manifest.getCardinality()>1) posterior.getCells()(1) else 0.0
				  Word(manifest.getName, prob)
			  }else
				  Word(manifest.getName)
			}
			
			// set evidence for latent state
			_posteriorCtp.setEvidence(latentArray, Array(0));
			_posteriorCtp.propagate();

			// compute posterior for each manifest variable
			val stateZeroWordsProbLookup = observedVarOrder.map{ manifest =>
				  val posterior = _posteriorCtp.computeBelief(manifest);
				  val prob = if(manifest.getCardinality()>1) (Math.rint(posterior.getCells()(1) * 100) / 100) else 0.0
				  (manifest.getName, prob)
			}.toMap
			
			val newWords = words.map{w=> new Word(w.w+" "+stateZeroWordsProbLookup(w.w), w.probability)}
			
      val size = p.getCells()(card);
			new Topic(name = latent.getName, words = newWords, level = None, size = Some(size), mi = None)
  	}
  	
  	def SortChildren(latent: Variable, varSet: Seq[Variable], ctp: CliqueTreePropagation) = {
      varSet.map{ child =>
        val mi = computeMI(latent, child, ctp);
        (child, mi)
      }.sortBy(-_._2)
    }

    def computeMI(x: Variable, y: Variable, ctp: CliqueTreePropagation) = {
      val xyNodes = new java.util.ArrayList[Variable]();
      xyNodes.add(x);
      xyNodes.add(y);
      org.latlab.util.Utils.computeMutualInformation(ctp.computeBelief(xyNodes));
    }
  }
  
  private class NarrowTopicsExtractor(model: LTM, data: Data, keywords: Int, 
      layers: Option[List[Int]] = None, outProbNum: Boolean = false, keepProb: Boolean = true){
    import org.latlab.util.Variable
    import org.latlab.reasoner.CliqueTreePropagation
    import java.util.ArrayList
    import collection.JavaConverters._
    import tm.hlta.HLTA
    import tm.util.Tree
    
    val topicProbabilities = scala.collection.mutable.Map.empty[String, IndexedSeq[Double]]
    
    def extractTopics(): TopicTree = {    
      val _varDiffLevels = model.getLevelVariables()
      val _layers = if(layers.isDefined) layers.get.sorted else (1 until _varDiffLevels.size).toList //in ascending order
      val topicNodeBank = scala.collection.mutable.Map[String, Tree[Topic]]()
      _layers.foreach { VarLevel =>
        _varDiffLevels.apply(VarLevel).map {latent =>
          val setVars = model.observedDescendentOf(latent.getName)
          val topic = if (setVars.size < 3) {
            extractTopicsByCounting(latent, setVars);
          } else {
            extractTopicsBySubtree(latent, setVars);
          }
          val descendentLatentVars = model.latentDescendentOf(latent.getName)
          val childs = descendentLatentVars.flatMap { v => 
            //remove and pops the topic from the bank
            topicNodeBank.remove(v.getName)
          }
          topicNodeBank.put(latent.getName, Tree.node[Topic](topic, childs))    
        }
      }
      val topicTree = TopicTree(topicNodeBank.values.toSeq)
      topicTree.reassignLevel()
      topicTree
    }
      
    def extractTopicsByCounting(latent: Variable, observed: Seq[Variable]) = {              
      val (validObserved, indices) = observed.map{o => (o, data.variables.indexOf(o))}.filterNot(_._2 == -1).unzip
      val wordCounts = scala.collection.mutable.MutableList.fill(validObserved.size)(0.0)
      val topicProbs = data.instances.map { i =>
        val values = indices.map(i.values)
        if(outProbNum) values.zipWithIndex.foreach{case (v, j) => wordCounts(j) += v}
        val latentProb = if(values.find(_ > 0.0).isDefined) 1.0 else 0.0
        latentProb
      }
      
      if(keepProb) topicProbabilities += (latent.getName -> topicProbs)
      val size = topicProbs.count(_>=0.5)/topicProbs.size //Hard assignment, the same practice as in HLTA Java
      val words = validObserved.zip(wordCounts).sortBy(-_._2).map{case (o, count) =>
        if(outProbNum) Word(o.getName, count/data.size)
        else Word(o.getName)
      }
      new Topic(name = latent.getName, words = words, level = None, size = Some(size), mi = None)
    }
    
    def extractTopicsBySubtree(latent: Variable, setVars: List[Variable]) = {
      // the method is broken down into three parts to allow overriding.
      val (lcm, wordOrder) = extractTopicsBySubtree1(latent, setVars);
      val (learnedLcm, ctp) = extractTopicsBySubtree2(lcm);
      val topic = extractTopicsBySubtree3(learnedLcm, ctp, wordOrder);
      
      //If need probabilities for each document, use the learnedLcm to compute again
      if(keepProb){
        // find only observed variables
        val (observed, indices) = learnedLcm.getManifestVars.asScala.map { v =>
          val index = data.variables.indexOf(v)
          if (index >= 0) Some(v, index)
          else None
        }.collect(_ match {
          case Some(p) => p
        }).toArray.unzip
    
        def getObservedStates(instance: Data.Instance) =
          indices.map(instance.values).map(v => if (v > 0) 1 else 0)
    
        // check 
        val test = observed.map(learnedLcm.getNode)
        assert(test.forall(_ != null))
    
        ctp.clearEvidence();
        val probabilities = data.instances.map { i =>
          ctp.setEvidence(observed, getObservedStates(i))
          ctp.propagate();
          ctp.computeBelief(latent).getCells()(1)
        }
    
        topicProbabilities += (latent.getName -> probabilities)
      }
      
      topic
    }
    
    def extractTopicsBySubtree1(latent: Variable, setVars: List[Variable]) = {
      val posteriorCtp = new CliqueTreePropagation(model);
      posteriorCtp.propagate();
      val globallist = SortChildren(latent, setVars, posteriorCtp)
      
      val subtree = new LTM();
      subtree.addNode(latent);

      val lemma = tm.text.DictionaryLemmatizer.EnglishLemmatizer
      val observedVarOrder = globallist.take(keywords).map{ case(v, mi) =>
        subtree.addNode(v);
        subtree.addEdge(subtree.getNode(v), subtree.getNode(latent));
        v
      }
      (subtree, observedVarOrder)
    }

    def extractTopicsBySubtree2(subtree: LTM) = {
      val subData = data.project(subtree.getManifestVars.asScala.toIndexedSeq).toHlcmDataSet();
      subData.synchronize(subtree);

      val emLearner = new org.latlab.learner.ParallelEmLearner();
      emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
      emLearner.setMaxNumberOfSteps(64);
      emLearner.setNumberOfRestarts(100);
      emLearner.setReuseFlag(false);
      emLearner.setThreshold(0.01);

      val learnedSubtree = emLearner.em(subtree, subData).asInstanceOf[LTM];

      val posteriorCtpSub = new CliqueTreePropagation(learnedSubtree);
      posteriorCtpSub.propagate();

      val leafVar = learnedSubtree.getManifestVars().asScala.toSeq
      val list = SortChildren(learnedSubtree.getRoot.getVariable(), leafVar, posteriorCtpSub);

      // reorder the state
      // here the setNode has been updated to all the leaf nodes in the
      // subtree (not all the leaf nodes in the global model)
      val reorderedSubtree = reorderStates(learnedSubtree, list);
      (learnedSubtree, posteriorCtpSub)
    }

    def extractTopicsBySubtree3(subtree: LTM, posteriorCtpSub: CliqueTreePropagation, observedVarOrder: Seq[Variable]) = {
      val latent = subtree.getRoot.getVariable
      val p = posteriorCtpSub.computeBelief(latent);

      posteriorCtpSub.clearEvidence();
      posteriorCtpSub.propagate();

      // LP: Holds P(W=1|Z=z), where W is the word variable, for z=0 and z=1
      // to save the topics for each node
      val latentArray = Array(latent)
      val card = 1 // When rewriting the following code, we ignore the z=0 state because it is not useful in text mining
      val states = Array(card);

      // set evidence for latent state
      posteriorCtpSub.setEvidence(latentArray, states);
      posteriorCtpSub.propagate();

      // compute posterior for each manifest variable
      // wordMi is supposed to be sorted in descending order
      val words = observedVarOrder.map{ manifest =>
        if(outProbNum){
          val posterior = posteriorCtpSub.computeBelief(manifest);
          val prob = posterior.getCells()(1)
          Word(manifest.getName, prob)
        }
        else
          Word(manifest.getName)
      }
      val size = p.getCells()(card);
      new Topic(name = latent.getName, words = words, level = None, size = Some(size), mi = None)
    }
    
    /**
     * Directly copied and translated form ExtractNarrowTopics_LCM.java
     */
    def reorderStates(bn: LTM, list: Seq[(Variable, Double)]): LTM = {
      // inference engine
      val ctp: CliqueTreePropagation = new CliqueTreePropagation(bn)
      ctp.clearEvidence()
      ctp.propagate()

      // calculate severity of each state
      val latent = bn.getRoot.getVariable
      val card: Int = latent.getCardinality
      val severity: Array[Double] = Array.ofDim[Double](card)
      for (i <- 0 until card) {
        val states = Array(i)
        val latents = Array(latent)
        
        ctp.setEvidence(latents, states)
        ctp.propagate()
        
        // accumulate expectation of each manifest variable
        for (c <- 0 until Math.min(list.size, 3)) {
          val dist: Array[Double] = ctp.computeBelief(list(c)._1).getCells
          for (j <- 1 until dist.length) {
            severity(i) += Math.log(j * dist(j))
          }
        }
      }

      // initial order
      val order = Range(0, card).toArray   
      // for More than 2 states,but now we don't need bubble sort
      // bubble sort
      for (i <- 0 until card - 1; j <- i + 1 until card
           if severity(i) > severity(j)) {
        val tmpInt: Int = order(i)
        order(i) = order(j)
        order(j) = tmpInt
        val tmpReal: Double = severity(i)
        severity(i) = severity(j)
        severity(j) = tmpReal
      }
      bn.getNode(latent).reorderStates(order)
      latent.standardizeStates()
      bn
    }
      
    def SortChildren(latent: Variable, varSet: Seq[Variable], ctp: CliqueTreePropagation) = {
      varSet.map{ child =>
        val mi = computeMI(latent, child, ctp);
        (child, mi)
      }.sortBy(-_._2)
    }

    def computeMI(x: Variable, y: Variable, ctp: CliqueTreePropagation) = {
      val xyNodes = new java.util.ArrayList[Variable]();
      xyNodes.add(x);
      xyNodes.add(y);
      org.latlab.util.Utils.computeMutualInformation(ctp.computeBelief(xyNodes));
    }
    
  }
    
}
