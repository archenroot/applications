CC = g++
#CPPFLAGS = -m64 -I$(HADOOP_HOME)/../../c++/Linux-amd64-64/include
CPPFLAGS = -m64 -I$(HADOOP_HOME)/../../src/c++/install/include

cpu-kmeans2D: cpu-kmeans2D.cc
#	$(CC) $(CPPFLAGS) $< -L$(HADOOP_HOME)/../../c++/Linux-amd64-64/lib -lhadooppipes -lhadooputils -lcrypto -lpthread -g -O2 -o $@
	$(CC) $(CPPFLAGS) $< -L$(HADOOP_HOME)/../../src/c++/install/lib -lhadooppipes -lhadooputils -lcrypto -lpthread -g -O2 -o $@

clean:
	rm -f cpu-kmeans2D
