###############################################################################
##### MatrixMultiplication based on Mahout Example                        #####
###############################################################################

# Use Apache Ant to build and run example

# Clean all files
ant clean

# Build GPU jar file
ant jar-gpu

# Build CPU jar file
ant jar-cpu

# Submit GPU Task to Hadoop
ant run-gpu -DnumRows='--numRows 100' -DnumCols='--numCols 100'

# Submit GPU native emulated Task to Hadoop
ant run-gpu-nemu -DnumRows='--numRows 100' -DnumCols='--numCols 100'

# Submit GPU Java emulated Task to Hadoop
ant run-gpu-jemu -DnumRows='--numRows 100' -DnumCols='--numCols 100'

# Submit CPU Task to Hadoop
ant run-cpu -DnumRows='--numRows 100' -DnumCols='--numCols 100'

###############################################################################