__author__ = 'chen zhourong'
from gensim.models import Word2Vec

def compactness_score(model_path, topic_file_path):
	"""
	model_path:	Word2Vec model file
	topic_file_path:Each line in the file is a topic, represented as 
			a list of words separated by spaces
			
	Output:		Print compactness score for each topic and a final score for all the topics.
	"""

	# Loading can be very slow if the model is large. 
	# User should consider loading the model just once for all the topic files.
	print("Loading Word2Vec model: " + model_path)
	model = Word2Vec.load_word2vec_format(model_path, binary=True)
	print("Loading Done.")

	print("Processing topic file: " + topic_file_path)

	line_count = 0
	result = []
	with open(topic_file_path, 'r') as inputfile:
		for line in inputfile:
			line_count += 1
			sims = []

			line = line.strip(' \n').split(' ')
			print(line),
			for i in range(len(line)):
				if line[i] not in model.vocab:
					continue
				for j in range(i+1, len(line)):
					if line[j] in model.vocab:
						sims.append( model.similarity(line[i], line[j]) )
			print(str(len(sims)) + " Pairs: "),
			if len(sims) > 0:
				result.append(sum(sims)/len(sims))
				print(result[-1])

		if len(result) > 0:
			print("\n\nFinal scores:"),
			print(sum(result)/len(result))
		else:
			print("No topics applicable!")

	print("End.")
