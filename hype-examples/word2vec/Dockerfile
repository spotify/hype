FROM us.gcr.io/datawhere-test/hype-examples-base:4
MAINTAINER Robert Gruener <robertg@spotify.com>

# Install gcc
RUN apt-get update && apt-get install -y gcc && apt-get install -y make
RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# Download and compile word2vec, placing it in bin folder
RUN wget https://github.com/yonromai/word2vec/archive/master.zip
RUN unzip master.zip
RUN make -C /word2vec-master/src
RUN mv /word2vec-master/bin/* /bin
RUN rm -rf /word2vec-master master.zip
