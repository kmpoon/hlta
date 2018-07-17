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
  * `model.bif`: HLTA model file
  * `someName.html`: HTML visualization 
  * `someName.nodes.js`: a topic tree
  * `someName-topics.js`: a document catalog grouped by topics
  * `lib`: Javascript and CSS files required by the main HTML file
  * `fonts`: fonts used by some CSS files
   
- You can also do
   ```
   java -cp HLTA.jar;HLTA-deps.jar tm.hlta.HTD documents.txt modelName
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
   java -cp HLTA.jar:HLTA-deps.jar tm.text.Convert datasetName ./source 1000 1
   ```
  After conversion, you can find:
  - `sample.sparse.txt`: data in tuple format, i.e. lines of (docId, word) pair
  - `sample.dict-1.csv`: information of words after selection after 1 possible concatenations
  - `sample.whole_dict-1.csv`: information of words before selection after 1 possible concatenations 
  
  You may put your files anywhere in ./source. It accepts txt and pdf.
   ```
   ./source/IAmPDF.pdf
   ./source/OneDocument.txt
   ./source/Folder1/Folder2/Folder3/HiddenSecret.txt
   ```
- Split into training set and testing set if needed: (v2.1)
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.text.Convert --testset-ratio 0.2 datasetName ./source 1000 1
   ```

# Subroutine 2: Model Building

- Build model through with maximum 50 em steps (uses StepwiseEM)
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.HLTA data.sparse.txt 50 modelName
   ```
The output files include:
  * `model.bif`: HLTA model file

# Subroutine 3: Extract Topic Trees

- Exract topic from topic model
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.ExtractTopicTree someName model.bif data.sparse.txt
   ```

  The output files include:
  * `someName.html`: a website
  * `someName.nodes.js`: a topic tree stored in javascript
  * `someName.nodes.json`: a topic tree stored as json
  * `lib`: Javascript and CSS files required by the main HTML file
  * `fonts`: fonts used by some CSS files

- You may use the "broadly defined topics" to speed up the process. Under this definition, more document will be categorized into a topic. (ref [*paper*](https://arxiv.org/abs/1605.06650) section 8.2.1)
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.ExtractTopicTree --broad someName model.bif
   ```

# Subroutine 4: Doc2Vec Assignment

- Find out which documents belongs to that topic (i.e. inference)
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.Doc2VecAssignment model.bif data.sparse.txt outputName
   ```
  The output files include:
  * `output-topics.json`: a document catalog grouped by topic
  * `output-topics.js`: a document catalog stored as javascript variable
  * `output-topics.arff`: doc2vec assignments in arff format

- You may use the "broadly defined topics" to speed up the process. Under this definition, more document will be categorized into a topic. (ref [*paper*](https://arxiv.org/abs/1605.06650) section 8.2.1)
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.Doc2VecAssignment --broad model.bif data.sparse.txt outputName
   ``` 
  
# Evaluate Topic Model

- Evaluate by topic coherence
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.TopicCoherence topic.nodes.json data.sparse.txt
   ```

- Evaluate by topic compactness. 
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.TopicCompactness topic.nodes.json data.sparse.txt GoogleNews-vectors-negative300.bin
   ```
   Download pre-trained word2vec model from https://drive.google.com/file/d/0B7XkCwpI5KDYNlNUTTlSS21pQmM/edit?usp=sharing

- Compute topic compactness in Python
   Install gensim (https://radimrehurek.com/gensim/) before using the python codes for computing compactness scores in AAAI17 paper (http://www.aaai.org/Conferences/AAAI/2017/PreliminaryPapers/12-Chen-Z-14201.pdf). One pre-trained Word2Vec model by Google is available at https://drive.google.com/file/d/0B7XkCwpI5KDYNlNUTTlSS21pQmM/edit?usp=sharing. The description of the model can be found at https://code.google.com/archive/p/word2vec/ under the section "Pre-trained word and phrase vectors".
   
 - Evaluate by loglikelihood. You will need to create a testing set in advance. (v2.1)
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.PerDocumentLoglikelihood model.bif data.test.sparse.txt
   ```

   You may also see the Testing section of the [Old HLTA Page](https://github.com/kmpoon/hlta/blob/master/RESEARCH.md) (v2.0)

# Assemble
0. Have sbt installed.
1. Change directory to the project directory. (e.g. user/git/hlta)
2. Run the following command to build the JAR files from source code:

   ```
   sbt clean assembly assemblyPackageDependency && ./rename-deps.sh
   ```

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
