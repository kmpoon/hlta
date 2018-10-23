# Hierarchical Latent Tree Analysis (HLTA)
HLTA is a novel method for hierarchical topic detection. Specifically, it models document collections using a class of graphical models called *hierarchical latent tree models (HLTMs)*. The variables at the bottom level of an HLTM are observed binary variables that represent the presence/absence of words in a document. The variables at other levels are binary latent variables, with those at the lowest latent level representing word co-occurrence patterns and those at higher levels representing co-occurrence of patterns at the level below. Each latent variable gives a soft partition of the documents, and document clusters in the partitions are interpreted as topics. Unlike LDA-based topic models,  HLTMs do not refer to a document generation process and use word variables instead of token variables. They use a tree structure to model the relationships between topics and words, which is conducive to the discovery of meaningful topics and topic hierarchies.

A basic version of HLTA is proposed here: 
[*Hierarchical Latent Tree Analysis for Topic Detection.*](http://www.cse.ust.hk/~lzhang/paper/pspdf/liu-n-ecml14.pdf)
Tengfei Liu, Nevin L. Zhang and Peixian Chen. ECML/PKDD 2014: 256-272

An accelarated version of HLTA is proposed by using Progressive EM:
[*Progressive EM for Latent Tree Models and Hierarchical Topic Detection.*](http://www.aaai.org/ocs/index.php/AAAI/AAAI16/paper/view/11818)
Peixian Chen, Nevin L. Zhang, Leonard K. M. Poon and Zhourong Chen. AAAI 2016


A full version of HLTA with comprehensive discription as well as several extensions can be found at:
[*Latent Tree Models for Hierarchical Topic Detection.*](https://arxiv.org/abs/1605.06650)  
Peixian Chen, Nevin L. Zhang et al. 

An IJCAI tutorial and demonstration can be found at:
[*Multidimensional Text Clustering for Hierarchical Topic Detection (IJCAI 2016 Tutorial)*](http://www.cse.ust.hk/~lzhang/topic/ijcai2016/) by Nevin L. Zhang and Leonard K.M. Poon

The original HLTA java call associated to the papers: [Old HLTA Page](https://github.com/kmpoon/hlta/blob/master/RESEARCH.md)

# Quick Example

- Download the `HLTA.jar` and `HLTA-deps.jar` from the [Release page](https://github.com/kmpoon/hlta/releases).

- An all-in-one command for hierarchical topic detection. It brings you through data conversion, model building, topic extraction and topic assignment.
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.HTD ./quickstart someName
   ``` 

  If you are in windows, remember to use semicolon instead
   ```
   java -cp HLTA.jar;HLTA-deps.jar tm.hlta.HTD ./quickstart someName
   ``` 
   The output files include:
  * `someName.sparse.txt`: the converted data, generated if data conversion is necessary
  * `someName.bif`: HLTA model file
  * `someName.html`: HTML visualization 
  * `someName.nodes.js`: a topic tree
  * `someName-topics.js`: a document catalog grouped by topics
  * `lib`: Javascript and CSS files required by the main HTML file
  * `fonts`: fonts used by some CSS files
   
- You can also do
   ```
   java -cp HLTA.jar;HLTA-deps.jar tm.hlta.HTD documents.txt someName
   ``` 
   
  Your `documents.txt`:
   ```
   One line is one single document. You can have many sentences as you want.
   The quick brown fox jump over the lazy dog. But the lazy dog is too big to be jumped over!
   Lorem ipsum dolor sit amet, consectetur adipiscing elit
   Maecenas in ligula at odio convallis consectetur eu ut erat
   ```
   
 - You may also run through the following subroutines to do  data conversion, model building, topic extraction and topic assignment step by step.

# Subroutine 1: Convert Text Files to Data
 
- Convert text files to bag-of-words representation with 1000 words and 1 concatenation:
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.text.Convert myData ./source 1000 1
   ```
  After conversion, you can find:
  - `myData.sparse.txt`: data in tuple format, i.e. lines of (docId, word) pair
  - `myData.dict.csv`: the vocabulary list ('.dict-0.csv' is the list w/o concatenation, '.dict-1.csv' is after 1 concatenation, etc.)
  
  You may put your files anywhere in ./source. It accepts txt and pdf.
   ```
   ./source/IAmPDF.pdf
   ./source/OneDocument.txt
   ./source/Folder1/Folder2/Folder3/HiddenSecret.txt
   ```
- Split into training set and testing set if needed: (v2.1)
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.text.Convert --testset-ratio 0.2 myData ./source 1000 1
   ```

# Subroutine 2: Model Building

- Build model through with maximum 50 em steps (uses StepwiseEM)
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.HLTA myData.sparse.txt 50 myModel
   ```
The output files include:
  * `myModel.bif`: HLTA model file

# Subroutine 3: Extract Topic Trees

- Exract topic from topic model
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.ExtractTopicTree myTopicTree myModel.bif myDataset.sparse.txt
   ```

  The output files include:
  * `myTopicTree.html`: a website
  * `myTopicTree.nodes.js`: a topic tree stored in javascript
  * `myTopicTree.nodes.json`: a topic tree stored as json
  * `lib`: Javascript and CSS files required by the main HTML file
  * `fonts`: fonts used by some CSS files

- You may use the "broadly defined topics" to speed up the process. Under this definition, more document will be categorized into a topic. (ref [*paper*](https://arxiv.org/abs/1605.06650) section 8.2.1)
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.ExtractTopicTree --broad myTopicTree myModel.bif
   ```

# Subroutine 4: Doc2Vec Assignment

- Find out which documents belongs to that topic (i.e. inference)
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.Doc2VecAssignment myModel.bif myData.sparse.txt myAssignment
   ```
  The output files include:
  * `myAssignment.topics.json`: a document catalog grouped by topic
  * `myAssignment.topics.js`: a document catalog stored as javascript variable
  * `myAssignment.arff`: doc2vec assignments in arff format

- You may use the "broadly defined topics" to speed up the process. Under this definition, more document will be categorized into a topic. (ref [*paper*](https://arxiv.org/abs/1605.06650) section 8.2.1)
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.Doc2VecAssignment --broad myModel.bif myData.sparse.txt topics
   ``` 
  
# Evaluate Topic Model

- Evaluate by topic coherence
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.TopicCoherence myTopicTree.nodes.json myData.sparse.txt
   ```

- Evaluate by topic compactness. 
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.TopicCompactness myTopicTree.nodes.json myData.sparse.txt GoogleNews-vectors-negative300.bin
   ```
   Download pre-trained word2vec model from https://drive.google.com/file/d/0B7XkCwpI5KDYNlNUTTlSS21pQmM/edit?usp=sharing

- Compute topic compactness in Python
   Install gensim (https://radimrehurek.com/gensim/) before using the python codes for computing compactness scores in AAAI17 paper (http://www.aaai.org/Conferences/AAAI/2017/PreliminaryPapers/12-Chen-Z-14201.pdf). One pre-trained Word2Vec model by Google is available at https://drive.google.com/file/d/0B7XkCwpI5KDYNlNUTTlSS21pQmM/edit?usp=sharing. The description of the model can be found at https://code.google.com/archive/p/word2vec/ under the section "Pre-trained word and phrase vectors".
   
 - Evaluate by loglikelihood. You will need to create a testing set in advance. (v2.1)
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.PerDocumentLoglikelihood myModel.bif myData.test.sparse.txt
   ```

   You may also see the Testing section of the [Old HLTA Page](https://github.com/kmpoon/hlta/blob/master/RESEARCH.md) (v2.0)

# Options
As introduced in Subroutine2 of Quick Example, we can train HLTA with default hyper-parameters by :
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.HLTA myData.sparse.txt 50 myModel
   ```
   
HLTA also supports to tune hyper-parameters by :
   ```
   java -cp HLTA.jar:HLTA-deps.jar clustering.StepwiseEMHLTA $trainingdata $EmMaxSteps $EmNumRestarts $EM-threshold $UDtest-threshold $CT-threshold $outputmodel $MaxIsland $MaxTop $GlobalsizeBatch $GlobalMaxEpochs $GlobalEMmaxsteps $FirstBatch $IslandNotBridging $SampleSizeForstructureLearn $MaxCoreNumber $parallelIslandFindingLevel
   ```

For example,
   ```
   java -cp HLTA.jar:HLTA-deps.jar clustering.StepwiseEMHLTA myData.sparse.txt 50 3 0.01 3 off myModel 15 30 500 10 100 8000 1 10000 2 1
   ```

Notice that, to speed up the training:
1. $trainingdata: the file name of training data
2. $EmMaxSteps: max steps in EM (default: 50)
3. $EmNumRestarts: numner of restarters in EM (default: 3)
4. $EM-threshold: threshold to control the stop of EM (default: 0.01)
5. $UDtest-threshold: threshold to control whether the islands can pass UDtest (default: 3)
6. $CT-threshold: threshold to control whether the island can pass correlation test (default: off, that is no correlation test)
7. $outputmodel: name of output model
8. $MaxIsland: The maximum number of variables in one island (default: 15)
9. $MaxTop: max variable numbers for top level (default: 30)
10. $GlobalsizeBatch: batch size in global stepwise EM for parameter learning (default: 500)
11. $GlobalMaxEpochs: max epoch number in global stepwise EM for parameter learning (default: 10)
12. $GlobalEMmaxsteps: step numbers  in global stepwise EM for parameter learning (default: 100)
13. $FirstBatch: training samples size in the first batch (default: "all", means use all the training samples in first batch)
14. $IslandNotBridging: remove island bridging or not, the default value is 1 meaning to remove island bridging. (default: 1)
15. $SampleSizeForstructureLearn: how many samples are used in structure leanring. (default: 10000)
16. $MaxCoreNumber: means the number of parallel CPU process. (default: 2) Users can choose a suitable core number considering the scale of their dataset. The further analysis on the balance of speed and performance can be found [*paper*](https://github.com/kmpoon/hlta/wiki/Document-for-Speeding-up-HLTA). Notice that, this number should not exceed the CPU core number of your machine, otherwise, it will slow HLTA.
17. $parallelIslandFindingLevel: when $MaxCoreNumber > 1, $parallelIslandFindingLevel means the max level that use parallel island finding. For example, $parallelIslandFindingLevel == 3 means level1, level2 and level3 use parallel island finding; while other levels use serial island finding.

# Assemble
If you need to modify source code and recompile HLTA, please follow next steps to build a sbt directory and compile HLTA. If not, please skip this session.

0. Have sbt installed.
1. Git clone this repository
2. Change directory to the project directory. (e.g. user/git/hlta)
3. Run the following command to build the JAR files from source code:

   ```
   sbt clean assembly assemblyPackageDependency && ./rename-deps.sh
   ```
   The output of Assemble is "HLTA.jar" and "HLTA-deps.jar" under "target/scala-2.11/", which can be executed following the instruction of "Quick Example".
# Enquiry

* Current Maintainer: Chun Fai Leung (cfleungac@connect.ust.hk) (The Hong Kong University of Science and Techonology)
* General questions: Leonard Poon (kmpoon@eduhk.hk) (The Education University of Hong Kong)
* PEM questions: Peixian Chen (pchenac@cse.ust.hk) (The Hong Kong University of Science and Technology)

# Contributors

* Prof. Nevin L. Zhang
* Peixian Chen
* Tao Chen
* Zhourong Chen
* Farhan Khawar
* Chun Fai Leung
* Tengfei Liu
* Leonard K.M. Poon
* Yi Wang
