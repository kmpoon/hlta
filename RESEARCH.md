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

This is the legacy page for HLTA package. You need v2.0 or v2.1 to run the following commands.
   
# HLTA
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


Remember to add the --output-hlcm option while converting to data file. PEM HLTA takes either hlcm or arff format.
 ```
 java -cp HLTA.jar:HLTA-deps.jar tm.text.Convert --output-hlcm datasetName ./source 1000
 ```
 See [*Progressive EM for Latent Tree Models and Hierarchical Topic Detection.*](http://www.aaai.org/ocs/index.php/AAAI/AAAI16/paper/view/11818)
Peixian Chen, Nevin L. Zhang, Leonard K. M. Poon and Zhourong Chen. AAAI 2016

# HLTA with Stepwise EM

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

For detail algorithm, see section 7 of [*Latent Tree Models for Hierarchical Topic Detection.*](https://arxiv.org/abs/1605.06650)  
Peixian Chen, Nevin L. Zhang et al. 

# Testing
- To test the model using .arff or .hlcm test data, you can use *PEM* as :
```
  java -Xmx15G -cp HLTA.jar:HLTA-deps.jar  PEM  modelname test_data outpath
```

# Assemble
Make sure SBT is installed in your machine.
0. Have sbt installed.
1. Change directory to the project directory. (e.g. user/git/hlta)
2. Run the following command to build the JAR files from source code:

   ```
   sbt clean assembly assemblyPackageDependency && ./rename-deps.sh
   ```

# Enquiry

* Developer: Chun Fai Leung (cfleungac@connect.ust.hk) (The Hong Kong University of Science and Techonology)
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
