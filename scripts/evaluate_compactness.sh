model_id=rr31_nyt3

working_space=compactness_working_dir
topics_file_path=http://home.cse.ust.hk/~tianzhiliang//working_space_evaluate/
word2vec_embedding=/Users/tianzhiliang/Documents/Work/Code/LatentTree/tools/word2vec_embedding/GoogleNews-vectors-negative300.bin

topic_file=${working_space}/topics_${model_id}.nodes.json
log_compactness=${working_space}/log/log_compactness_${model_id}.log
err_compactness=${working_space}/log/err_compactness_${model_id}.err
result_compactness=${working_space}/result_compactness_${model_id}.log

mkdir -p ${working_space}/log/

wget ${topics_file_path}/topics_${model_id}.nodes.json
mv topics_${model_id}.nodes.json ${topic_file} 
rm -f wget-log*

python scripts/compactness_w2v_tool.py ${word2vec_embedding} ${topic_file} > ${log_compactness} 2>${err_compactness}
compactness=$(tail -2 ${log_compactness} | head -1 | awk '{print $NF}')
echo ${compactness} > ${result_compactness}

