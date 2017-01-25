#Hierarchical Latent Tree Analysis (HLTA)
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
[Multidimensional Text Clustering for Hierarchical Topic Detection (IJCAI 2016 Tutorial)](http://www.cse.ust.hk/~lzhang/topic/ijcai2016/) by Nevin L. Zhang and Leonard K.M. Poon

This package provides functions for hierarchical latent tree analysis on text 
data.  The workflow supported may start with PDF files and result in a topic
tree given by HLTA.

# Functions Provided

- Convert PDF files to text files.
- Convert text files to data in bag-of-words representation (including n-grams)
- Build latent tree models from binary data.(Will extend HLTA to include word count information later)
- Extract topic hierarchies shown as HTML documents.


# Prerequisites

You should first obtain two JAR files for this package, by either one of the following ways:

1. Run the SBT.  Then run `assembly` and `assemblyPackageDependency`.  Rename the generated *dependency file* to `HLTA-deps.jar`, which we assume in the steps below.
2. Download the `HLTA.jar` and `HLTA-deps.jar` from the [Releases page](https://github.com/kmpoon/hlta/releases).

# Extract Text from PDF files

- To extract text from PDF files:

  ```
  java -cp HLTA.jar:HLTA-deps.jar tm.pdf.ExtractText papers extracted
  ```

  Where: `papers` is input directory and `extracted` is output directory
  
- The extraction step also does some preprocessing including:
  - Converting letters to lower case
  - Normalizing characters for accents, ligatures, etc. and replace non-alphabet characters by `_`
  - Lemmatization
  - Replace starting digits in words with `_`
  - Remove words shorter than 3 characters
  - Remove stop-words

# Convert Text Files to Data
 
- To convert text files to bag-of-words representation:

  ```
  java -cp HLTA.jar:HLTA-deps.jar tm.text.Convert sample 20 2 extracted
  ```
  
  Where: `sample` is a name to give and `extracted` is directory of extracted text, `20` is the number of words to be included in the resulting data, `2` is the number of concatenations used to build n-grams.
 
  After conversion, you can find:
  - `sample.arff`: count data in ARFF format
  - `sample.txt`: binary data in format for LTM
  - `sample.sparse.txt`: binary data in sparse format for LTM
  - `sample.dict-2.csv`: information of words after selection after 2 possible concatenations
  - `sample.whole_dict-2.csv`: information of words before selection after 2 possible concatenations

- In the input, each file represents the text content of one document.  The text content is assumed to have been preprocessed.

# Model Building

To build the model with PEM:

```
java -Xmx15G -cp HLTA.jar:HLTA-deps.jar PEM sample.txt sample.txt 50  5  0.01 3 model 10 15
```

Where: `sample.txt` the name of the binary data file, `model` is the name of output model file (the full name will be `model.bif`). 

The full parameter list is: `PEM training_data test_data max_EM_steps num_EM_restarts EM_threshold UD_test_threshold model_name max_island max_top`.  The numerical parameters can be divided into two parts:

* EM parameters:
  * `max_EM_steps`: Maximum number of EM steps (e.g. 50).
  * `num_EM_restarts`: Number of restarts in EM (e.g. 5).
  * `EM_threshold`: Threshold of improvement to stop EM (e.g. 0.01).
* Model construction parameters:
  * `UD_test_threshold`: The threshold used in unidimensionality test for constructing islands (e.g. 3).
  * `max_island`: Maximum number of variables in an island (e.g. 10).
  * `max_top`: Maximum number of variables in top level (e.g. 15).
  
To run the **stochastic version**, replace the main class `PEM` by `StochasticPEM`, e.g.:

```
java -Xmx15G -cp HLTA.jar:HLTA-deps.jar StochasticPEM sample.txt sample.txt 50  5  0.01 3 model 10 15
```

# Extract Topic Hierarchies

- To extract topic hierarchy:

  ```
  java -cp HLTA.jar:HLTA-deps.jar tm.hlta.ExtractTopics sample model.bif
  ```

  Where: `sample` is the name of the files to be generated, `model.bif` is the name of the model file from PEM.

  The output files include:
  * `sample.html`: HTML file for the topic tree
  * `sample.nodes.js`: data for the topic nodes
  * `lib`: Javascript and CSS files required by the main HTML file
  * `fonts`: fonts used by some CSS files
  * `topic_output`: directory holding some information of the extracted topics

- If you want to extract narrowly defined topics, you can use `ExtractNarrowTopics` instead:

  ```
  java -cp HLTA.jar:HLTA-deps.jar tm.hlta.ExtractTopics sample model.bif data.txt
  ```

  Where: `data.txt` is the data file.


# Enquiry

* General questions: [Leonard Poon](mailto: kmpoon@eduhk.hk) (The Education University of Hong Kong)
* PEM questions: [Peixian Chen](mailto: pchenac@cse.ust.hk) (The Hong Kong University of Science and Technology)

# Contributors

* Prof. Nevin L. Zhang
* Peixian Chen
* Tao Chen
* Zhourong Chen
* Farhan Khawar
* Tengfei Liu
* Leonard K.M. Poon
* Yi Wang
