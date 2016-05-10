package tm.hlta
import sys.process._

object CheckWordCountWS {
  val dir = "/Users/kmpoon/Documents/research/workspace/TextMining/exp/"
                                                  //> dir  : String = /Users/kmpoon/Documents/research/workspace/TextMining/exp/

  val state = CheckWordCount.read(dir + "model.bif",
    dir + "aaai-ijcai.20160426.arff",
    dir + "aaai-ijcai.20160426.files.txt")        //> Reading ARFF data
                                                  //| Getting attributes
                                                  //| Getting instances
                                                  //| state  : tm.hlta.CheckWordCount.State = State(HLCM219550 {
                                                  //| 	number of nodes = 15046;
                                                  //| 	nodes = {
                                                  //| 		directed node {
                                                  //| 			name = "Z1297";
                                                  //| 			incoming degree = 1;
                                                  //| 			parents = { "Z2108" };
                                                  //| 			outgoing degree = 5;
                                                  //| 			children = { "transcription" "transcript" "phoneme" "mfc
                                                  //| c" "vowel" };
                                                  //| 		};
                                                  //| 		directed node {
                                                  //| 			name = "transcription";
                                                  //| 			incoming degree = 1;
                                                  //| 			parents = { "Z1297" };
                                                  //| 			outgoing degree = 0;
                                                  //| 			children = { };
                                                  //| 		};
                                                  //| 		directed node {
                                                  //| 			name = "transcript";
                                                  //| 			incoming degree = 1;
                                                  //| 			parents = { "Z1297" };
                                                  //| 			outgoing degree = 0;
                                                  //| 			children = { };
                                                  //| 		};
                                                  //| 		directed node {
                                                  //| 			name = "phoneme";
                                                  //| 			incoming degree = 1;
                                                  //| 			parents = { "Z1297" };
                                                  //| 			outgoing degree = 0;
                                                  //| 			children = { };
                                                  //| 		};
                                                  //| 		directed node {
                                                  //| 			name = "mfcc";
                                                  //| 			incoming degree = 1;
                                                  //| 			parents = { "Z1297" 
                                                  //| Output exceeds cutoff limit.
  println("Welcome to the Scala worksheet")       //> Welcome to the Scala worksheet
  
  val find = CheckWordCount.findTitlesContaining(state)(_)
                                                  //> find  : String => Seq[tm.hlta.ConvertTitlesToJSON.Document] = <function1>
  
}