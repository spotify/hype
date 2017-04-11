FROM openjdk:8-jre
MAINTAINER Robert Gruener <robertg@spotify.com>

ENV GOOGLE_APPLICATION_CREDENTIALS /etc/gcloud/key.json

# Install hype-run command
RUN /bin/sh -c "$(curl -fsSL https://goo.gl/kSogpF)"
ENTRYPOINT ["hype-run"]

# Install gcc
RUN apt-get update && apt-get install -y gcc && apt-get install -y make
RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# Download and compile word2vec, placing it in bin folder
RUN wget https://github.com/dav/word2vec/archive/master.zip
RUN unzip master.zip
RUN make -C /word2vec-master/src
RUN mv /word2vec-master/bin/* /bin
RUN rm -rf /word2vec-master master.zip
