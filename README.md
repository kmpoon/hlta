# HLTA New Scala Branch

This branch simplifies and merges HLTA procedures. Working in progress.

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


# Quick Example

- Download the `HLTA.jar` and `HLTA-deps.jar` from the [Releases page](https://github.com/kmpoon/hlta/releases).

- An all-in-one command brings you through data conversion, model building, topic extraction and topic assignment.
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.HLTA ./quickstart modelName
   ``` 

  If you are in windows, remember to use semicolon instead
   ```
   java -cp HLTA.jar;HLTA-deps.jar tm.hlta.HLTA ./quickstart modelName
   ``` 
   
- You can also do
   ```
   java -cp HLTA.jar;HLTA-deps.jar tm.hlta.HLTA documents.txt modelName
   ``` 
   
  Your `documents.txt`:
   ```
   One line is one single document. You can have many sentences as you want.
   The quick brown fox jump over the lazy dog. But the lazy dog is too big to be jumped over!
   Lorem ipsum dolor sit amet, consectetur adipiscing elit
   Maecenas in ligula at odio convallis consectetur eu ut erat
   ```

# Convert Text Files to Data
 
- Convert text files to bag-of-words representation with 1000 words and 1 concatenation:
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.text.Convert datasetName ./source 1000 1
   ```
  After conversion, you can find:
  - `sample.sparse.txt`: binary data in sparse format for LTM
  - `sample.dict-2.csv`: information of words after selection after 2 possible concatenations
  - `sample.whole_dict-2.csv`: information of words before selection after 2 possible concatenations 
  
  You may put your files anywhere in ./source. It accepts txt and pdf.
   ```
   ./source/IAmPDF.pdf
   ./source/OneDocument.txt
   ./source/Folder1/Folder2/Folder3/HiddenSecret.txt
   ```

# Model Building

- Build model through StepwiseEM with maximum 50 em steps
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.StepwiseEmBuilder data.sparse.txt 50 modelName
   ```
The output files include:
  * `model.bif`: HLTA model file

- Or if you want Progressive EM, use `ProgressiveEmBuilder` instead:
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.ProgressiveEmBuilder data.sparse.txt 50 modelName
   ```

# Extract Topic Hierarchies

- Exract topic from topic model
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.ExtractTopics topicTreeName model.bif
   ```

  The output files include:
  * `topicTreeName.html`: HTML file for the topic tree
  * `topicTreeName.nodes.js`: data for the topic nodes
  * `lib`: Javascript and CSS files required by the main HTML file
  * `fonts`: fonts used by some CSS files

- If you want to extract narrowly defined topics, you can use `ExtractNarrowTopics` instead:
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.ExtractNarrowTopics topicTreeName model.bif data.sparse.txt
   ```

# Assign Topics to Documents

- Find out which documents belongs to that topic (i.e. inference)
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.AssignBroadTopics topicTreeName model.bif data.sparse.txt outputName
   ```
  The output files include:
  * `output.nodes.json`: topic assignments

- If you want to use narrowly defined topics, you can use `AssignNarrowTopics` instead:
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.hlta.AssignNarrowTopics topicTreeName model.bif data.sparse.txt outputName
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

# Assemble
1. Change directory to the base directory.
2. Run the following command to build the JAR files from source code:

   ```
   sbt clean assembly assemblyPackageDependency && ./rename-deps.sh
   ```
   
# Original HLTA

The original HLTA algorithm published in the paper [*Latent Tree Models for Hierarchical Topic Detection.*]. 

- To build the model with PEM:
   ```
   java -Xmx15G -cp HLTA.jar:HLTA-deps.jar PEM sample.hlcm 50  5  0.01 3 model 15 20
   ```

   Where: `sample.hlcm` the name of the binary data file, `model` is the name of output model file (the full name will be `model.bif`). 

   The full parameter list is: `PEM training_data max_EM_steps num_EM_restarts EM_threshold UD_test_threshold model_name max_island max_top`.  The numerical parameters can be divided into two parts:


   * EM parameters:
     * `max_EM_steps`: Maximum number of EM steps (e.g. 50).
     * `num_EM_restarts`: Number of restarts in EM (e.g. 5).
     * `EM_threshold`: Threshold of improvement to stop EM (e.g. 0.01).
   * Model construction parameters:
     * `UD_test_threshold`: The threshold used in unidimensionality test for constructing islands (e.g. 3).
     * `max_island`: Maximum number of variables in an island (e.g. 10).
     * `max_top`: Maximum number of variables in top level (e.g. 15).
     
- You can get HLCM format for PEM through
   ```
   java -cp HLTA.jar:HLTA-deps.jar tm.text.Convert --output-hlcm datasetName ./source 1000
   ```

- To run the HLTA using **stepwise EM**, replace the main class `PEM` by `StepwiseEMHLTA`, and build the model using 

   ```
   java -Xmx15G -cp HLTA.jar:HLTA-deps.jar StepwiseEMHLTA  sample.sparse.txt 50  5  0.01 3 model 10 15 1000 10 128 8000
   ```

   Where: `sample.sparse.txt` the name of the binary data file, `model` is the name of output model file (the full name will be `model.bif`). 

   The full parameter list is: `StepwiseEMHLTA training_data max_EM_steps num_EM_restarts EM_threshold UD_test_threshold model_name max_island max_top global_batch_size global_max_epochs global_max_EM_steps struct_batch_size`.  The numerical parameters can be divided into three parts:

   * Local EM parameters:
     * `max_EM_steps`: Maximum number of EM steps (e.g. 50).
     * `num_EM_restarts`: Number of restarts in EM (e.g. 5).
     * `EM_threshold`: Threshold of improvement to stop EM (e.g. 0.01).
   * Model construction parameters:
     * `UD_test_threshold`: The threshold used in unidimensionality test for constructing islands (e.g. 3).
     * `max_island`: Maximum number of variables in an island (e.g. 10).
     * `max_top`: Maximum number of variables in top level (e.g. 15).
   * Global parameters:
     * `global_batch_size`: Number of data cases used in each stepwise EM step (e.g. 1000).
     * `global_max_epochs`: Number of times the whole training dataset has been gone through (e.g. 10).
     * `global_max_EM_steps`: Maximum number of stepwise EM steps (e.g. 128).
     * `struct_batch_size`: Number of data cases used for building model structure.


# Enquiry

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
