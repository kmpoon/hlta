export WORKDIR="/Users/tianzhiliang/Documents/Work/Code/LatentTree/hlta_parallel_0606/hlta_head_0620_forrun1/"
export MPJHOME="/Users/tianzhiliang/Documents/Work/Code/LatentTree/MPIJava/mpj-v0_44/"
export CLASSPATH=${CLASSPATH}":"${WORKDIR}"/lib/mymedialite.jar:"${WORKDIR}"/lib/opencsv-3.6.jar"
export CLASSPATH=${CLASSPATH}":"${MPJHOME}"/lib/mpj.jar:"${MPJHOME}"/lib/starter.jar" 


#java -cp "HLTA_without_mpi.jar:"${WORKDIR}"/lib/mymedialite.jar:"${WORKDIR}"/lib/opencsv-3.6.jar":${WORKDIR}"/lib/mpilib/starter.jar" StepwiseEMHLTA   # This is the way to use multiple jar

java -jar ${MPJHOME}"/lib/starter.jar" -cp ${WORKDIR}"/lib/mymedialite.jar:"${WORKDIR}"/lib/opencsv-3.6.jar" -np 4 StepwiseEMHLTA # MPI Mode. This is the way to run strater which depandency(external memory or from code)

#java StepwiseEMHLTA # Normal Mode (No MPI Mode)
