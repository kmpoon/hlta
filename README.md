# HLTA
Provides functions for hierarchical latent tree analysis on text data

# Three main steps

1. Pre-processing
  * Extract text from PDF documents
  * Convert each document to bag-of-words representation
  * Main output: data file (in txt and ARFF formats)
2. Model building
  * Learn LTM from the bag-of-words data
  * Main output: model file
3. Post-processing
  * Topic hierarchy extraction (plain HTML)
  * Build a JavaScript topic tree
  * Main output: topic hierarchy (JavaScript)

# Prerequisites

You should first obtain a JAR file from the package, by either one of the following ways:

1. Build the SBT and run `sbt-assembly`.  Rename the generated JAR file to `HLTA.jar`, which we assume in the steps below.
2. Download the `HLTA.jar` and `HLTA-deps.jar` from the [Releases page](https://github.com/kmpoon/hlta/releases).

# Pre-processing

1. To extract text from PDF files:

  ```
  java -cp HLTA.jar:HLTA-deps.jar tm.pdf.ExtractText papers extracted
  ```

  Where: `papers` is input directory and `extracted` is output directory

2. To convert text files to bag-of-words representation:

  ```
  java -cp HLTA.jar:HLTA-deps.jar tm.pdf.Convert sample 20 3 extracted
  ```
  
  Where: `sample` is a name to give and `extracted` is directory of extracted text, `20` is the number of words to be included in the resulting data, `3` is the maximum of n to be considerd for n-grams.
 
  After conversion, you can find:
  - `sample.arff`: count data in ARFF format
  - `sample.txt`: binary data in format for LTM
  - `sample.dict-2.csv`: information of words after selection for up to 2-gram
  - `sample.whole_dict-2.csv`: information of words before selection for up to 2-gram

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

# Post-processing

1. To extract topic hierarchy:
  ```
  java -cp HLTA.jar:HLTA-deps.jar HLTAOutputTopics_html_Ltm model.bif topic_output no no 7
  ```

  Where: `model.bif` is the name of the model file from PEM, `topic_output` is the directory for output files

2. To generate topic tree:

  ```
  java -cp HLTA.jar:HLTA-deps.jar tm.hlta.RegenerateHTMLTopicTree topic_output/TopicsTable.html sample
  ```
  
  Where: `topic_output/TopicsTable.html` is the name of the topic file from topic extraction, `sample` is name of the files to be generated
  
  The output files include:
  * `sample.html`: HTML file for the topic tree
  * `sample.nodes.js`: data for the topic nodes
  * `lib`: Javascript and CSS files required by the main HTML file
  * `fonts`: fonts used by some CSS files

# References

* [Multidimensional Text Clustering for Hierarchical Topic Detection (IJCAI 2016 Tutorial)](http://www.cse.ust.hk/~lzhang/topic/ijcai2016/) by Nevin L. Zhang and Leonard K.M. Poon
* Peixian Chen et al. (AAAI 2016) [Progressive EM for Latent Tree Models and Hierarchical Topic Detection](https://www.aaai.org/ocs/index.php/AAAI/AAAI16/paper/download/11818/11764) [[Longer version](https://arxiv.org/abs/1605.06650)]

# Enquiry

* General questions: [Leonard Poon](mailto: kmpoon@eduhk.hk) (The Education University of Hong Kong)
* For questions specific to the model building using the PEM algorithm: [Peixian Chen](mailto: pchenac@cse.ust.hk) (The Hong Kong University of Science and Technology)

# Acknowledgement

Contributors: Prof. Nevin L. Zhang, Peixian Chen, Tao Chen, Zhourong Chen, Tengfei Liu, Leonard K.M. Poon, Yi Wang
